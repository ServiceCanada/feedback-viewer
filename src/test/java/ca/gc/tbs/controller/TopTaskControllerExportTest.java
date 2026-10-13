package ca.gc.tbs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.gc.tbs.domain.TopTaskSurvey;
import ca.gc.tbs.repository.TopTaskRepository;
import ca.gc.tbs.security.JWTUtil;
import ca.gc.tbs.service.ProblemDateService;
import ca.gc.tbs.service.UserService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TopTaskControllerExportTest {

  private static final List<String> HEADERS =
      List.of(
          "Date Time", "Time Stamp (UTC)", "Survey Referrer", "Language", "Device", "Screener",
          "Department", "Theme", "Theme Other", "Grouping", "Task", "Task Other",
          "Task Satisfaction", "Task Ease", "Task Completion", "Task Improve",
          "Task Improve Comment", "Task Why Not", "Task Why Not Comment", "Task Sampling",
          "Sampling Invitation", "Sampling GC", "Sampling Canada", "Sampling Theme",
          "Sampling Institution", "Sampling Grouping", "Sampling Task");

  private MongoTemplate mongoTemplate;
  private TopTaskController controller;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    mongoTemplate = mock(MongoTemplate.class);
    controller =
        new TopTaskController(
            mock(TopTaskRepository.class),
            mock(UserService.class),
            mock(ProblemDateService.class),
            mongoTemplate,
            mock(JWTUtil.class));
    request = new MockHttpServletRequest();
    request.setParameter("startDate", "2026-07-01");
    request.setParameter("endDate", "2026-09-30");
  }

  @Test
  void csvEmptyResultReturnsNoContentWithoutCounting() throws Exception {
    givenRows(Stream.empty());
    MockHttpServletResponse response = new MockHttpServletResponse();

    controller.exportTopTaskCSV(request, response);

    assertThat(response.getStatus()).isEqualTo(204);
    verify(mongoTemplate, never()).count(any(Query.class), any(Class.class));
  }

  @Test
  void excelEmptyResultReturnsNoContentWithoutCounting() throws Exception {
    givenRows(Stream.empty());
    MockHttpServletResponse response = new MockHttpServletResponse();

    controller.exportTopTaskExcel(request, response);

    assertThat(response.getStatus()).isEqualTo(204);
    verify(mongoTemplate, never()).count(any(Query.class), any(Class.class));
  }

  @Test
  void exportQueryUsesLargeBatchesAndNoProjection() throws Exception {
    givenRows(Stream.of(survey("2026-07-01", "plain")));

    controller.exportTopTaskCSV(request, new MockHttpServletResponse());

    ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).stream(query.capture(), eq(TopTaskSurvey.class));
    assertThat(query.getValue().getMeta().getCursorBatchSize()).isEqualTo(5000);
    assertThat(query.getValue().getQueryObject().get("processed")).isEqualTo("true");
    // Cosmos DB charges more RU to project fields than to return whole documents
    assertThat(query.getValue().getFieldsObject()).isEmpty();
  }

  @Test
  void csvIsUtf8WithHeadersAndPreservesAccentsQuotesAndMultilineComments() throws Exception {
    String comment = "Très “bien”, l’accès – \"OK\"\nsecond line 😀";
    TopTaskSurvey withComment = survey("2026-07-01", comment);
    TopTaskSurvey withNulls = new TopTaskSurvey();
    withNulls.setDateTime("2026-07-02");
    givenRows(Stream.of(withComment, withNulls));
    MockHttpServletResponse response = new MockHttpServletResponse();

    controller.exportTopTaskCSV(request, response);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
    assertThat(response.getHeader("Content-Encoding")).isNull();
    assertThat(response.getHeader("Content-Disposition"))
        .matches("attachment; filename=\"top_task_survey_export_\\d{4}-\\d{2}-\\d{2}\\.csv\"");

    String body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
    assertThat(body).startsWith("\uFEFF");
    try (CSVParser parser =
        CSVParser.parse(
            new StringReader(body.substring(1)),
            CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get())) {
      assertThat(parser.getHeaderNames()).isEqualTo(HEADERS);
      List<CSVRecord> records = parser.getRecords();
      assertThat(records).hasSize(2);
      assertThat(records).allSatisfy(r -> assertThat(r.size()).isEqualTo(27));

      CSVRecord first = records.get(0);
      assertThat(first.get("Date Time")).isEqualTo("2026-07-01");
      assertThat(first.get("Task Improve Comment")).isEqualTo(comment);
      assertThat(first.get("Task Why Not Comment")).isEqualTo("=1+1");
      assertThat(first.get("Sampling Task")).isEqualTo("Sampling task");

      CSVRecord second = records.get(1);
      assertThat(second.get("Date Time")).isEqualTo("2026-07-02");
      assertThat(second.get("Task Improve Comment")).isEmpty();
    }
  }

  @Test
  void csvIsGzippedWhenTheBrowserAcceptsItAndDecompressesToTheSameFile() throws Exception {
    String comment = "Très “bien”, l’accès\nsecond line";
    givenRows(Stream.of(survey("2026-07-01", comment), survey("2026-07-02", "plain")));
    MockHttpServletResponse plain = new MockHttpServletResponse();
    controller.exportTopTaskCSV(request, plain);

    givenRows(Stream.of(survey("2026-07-01", comment), survey("2026-07-02", "plain")));
    request.addHeader("Accept-Encoding", "gzip, deflate, br, zstd");
    MockHttpServletResponse gzipped = new MockHttpServletResponse();
    controller.exportTopTaskCSV(request, gzipped);

    assertThat(gzipped.getStatus()).isEqualTo(200);
    assertThat(gzipped.getHeader("Content-Encoding")).isEqualTo("gzip");
    assertThat(gzipped.getHeader("Vary")).isEqualTo("Accept-Encoding");
    assertThat(gunzip(gzipped.getContentAsByteArray())).isEqualTo(plain.getContentAsByteArray());
  }

  @Test
  void csvIsNotGzippedWhenTheClientRefusesIt() throws Exception {
    givenRows(Stream.of(survey("2026-07-01", "plain")));
    request.addHeader("Accept-Encoding", "gzip;q=0, identity");
    MockHttpServletResponse response = new MockHttpServletResponse();

    controller.exportTopTaskCSV(request, response);

    assertThat(response.getHeader("Content-Encoding")).isNull();
    assertThat(response.getContentAsString(StandardCharsets.UTF_8)).startsWith("\uFEFFDate Time,");
  }

  @Test
  void excelHasSheetNameHeadersAndStringCellValues() throws Exception {
    String comment = "Très “bien”, l’accès\nsecond line";
    givenRows(Stream.of(survey("2026-07-01", comment), survey("2026-07-02", "plain")));
    MockHttpServletResponse response = new MockHttpServletResponse();

    controller.exportTopTaskExcel(request, response);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType())
        .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    try (XSSFWorkbook workbook =
        new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
      assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
      Sheet sheet = workbook.getSheet("Top Task Survey Data");
      assertThat(sheet).isNotNull();
      assertThat(sheet.getLastRowNum()).isEqualTo(2);

      Row header = sheet.getRow(0);
      for (int i = 0; i < HEADERS.size(); i++) {
        assertThat(header.getCell(i).getStringCellValue()).isEqualTo(HEADERS.get(i));
      }

      Row first = sheet.getRow(1);
      assertThat(first.getCell(0).getStringCellValue()).isEqualTo("2026-07-01");
      assertThat(first.getCell(16).getStringCellValue()).isEqualTo(comment);
      // Survey text that looks like a formula stays a plain string
      assertThat(first.getCell(18).getCellType()).isEqualTo(CellType.STRING);
      assertThat(first.getCell(18).getStringCellValue()).isEqualTo("=1+1");
      assertThat(first.getCell(26).getStringCellValue()).isEqualTo("Sampling task");
      assertThat(sheet.getRow(2).getCell(16).getStringCellValue()).isEqualTo("plain");
    }
  }

  @Test
  void csvFailureBeforeAnythingIsSentReturnsServerErrorWithoutPartialData() throws Exception {
    AtomicBoolean closed = new AtomicBoolean();
    givenRows(failingAfter(1, closed));
    MockHttpServletResponse response = new MockHttpServletResponse();

    controller.exportTopTaskCSV(request, response);

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.getContentAsString()).isEqualTo("Error exporting data");
    assertThat(closed).isTrue();
  }

  @Test
  void excelFailureBeforeAnythingIsSentReturnsServerErrorWithoutPartialData() throws Exception {
    AtomicBoolean closed = new AtomicBoolean();
    // The workbook starts streaming as soon as the first row exists, so fail on the first fetch
    givenRows(failingAfter(0, closed));
    MockHttpServletResponse response = new MockHttpServletResponse();

    controller.exportTopTaskExcel(request, response);

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
    assertThat(response.getContentAsString()).isEqualTo("Error exporting data");
    assertThat(closed).isTrue();
  }

  @Test
  void csvFailureAfterDataWasSentAbortsInsteadOfEndingNormally() {
    AtomicBoolean closed = new AtomicBoolean();
    givenRows(failingAfter(3000, closed));
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setBufferSize(1024);
    AtomicBoolean outputClosed = new AtomicBoolean();

    assertThatThrownBy(
            () -> controller.exportTopTaskCSV(request, trackingClose(response, outputClosed)))
        .isInstanceOf(IOException.class);
    assertThat(response.isCommitted()).isTrue();
    assertThat(closed).isTrue();
    assertThat(outputClosed).isFalse();
  }

  @Test
  void gzippedCsvFailureAfterDataWasSentLeavesTheGzipStreamUnterminated() {
    AtomicBoolean closed = new AtomicBoolean();
    givenRows(failingAfter(3000, closed));
    request.addHeader("Accept-Encoding", "gzip");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setBufferSize(1024);

    assertThatThrownBy(() -> controller.exportTopTaskCSV(request, response))
        .isInstanceOf(IOException.class);
    assertThat(response.isCommitted()).isTrue();
    assertThat(closed).isTrue();
    // A finished gzip stream would let the browser save the truncated file as if it were complete
    assertThatThrownBy(() -> gunzip(response.getContentAsByteArray()))
        .isInstanceOf(EOFException.class);
  }

  @Test
  void excelFailureAfterDataWasSentAbortsInsteadOfEndingNormally() {
    AtomicBoolean closed = new AtomicBoolean();
    givenRows(failingAfter(3000, closed));
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setBufferSize(1024);
    AtomicBoolean outputClosed = new AtomicBoolean();

    assertThatThrownBy(
            () -> controller.exportTopTaskExcel(request, trackingClose(response, outputClosed)))
        .isInstanceOf(IOException.class);
    assertThat(response.isCommitted()).isTrue();
    assertThat(closed).isTrue();
    // Closing the servlet stream would end the response normally, delivering a truncated file
    assertThat(outputClosed).isFalse();
  }

  /** Wraps a response so the test can see whether the export closed the servlet output stream. */
  private static HttpServletResponse trackingClose(
      MockHttpServletResponse response, AtomicBoolean outputClosed) {
    return new HttpServletResponseWrapper(response) {
      @Override
      public ServletOutputStream getOutputStream() throws IOException {
        ServletOutputStream delegate = response.getOutputStream();
        return new ServletOutputStream() {
          @Override
          public void write(int b) throws IOException {
            delegate.write(b);
          }

          @Override
          public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
          }

          @Override
          public void flush() throws IOException {
            delegate.flush();
          }

          @Override
          public void close() throws IOException {
            outputClosed.set(true);
            delegate.close();
          }

          @Override
          public boolean isReady() {
            return true;
          }

          @Override
          public void setWriteListener(WriteListener writeListener) {}
        };
      }
    };
  }

  private static byte[] gunzip(byte[] gzipped) throws IOException {
    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
      return in.readAllBytes();
    }
  }

  private void givenRows(Stream<TopTaskSurvey> rows) {
    when(mongoTemplate.stream(any(Query.class), eq(TopTaskSurvey.class))).thenReturn(rows);
  }

  /** A cursor that yields {@code rows} surveys and then fails, like a dropped database connection. */
  private static Stream<TopTaskSurvey> failingAfter(int rows, AtomicBoolean closed) {
    Iterator<TopTaskSurvey> iterator =
        new Iterator<>() {
          private int served;

          @Override
          public boolean hasNext() {
            if (served >= rows) {
              throw new IllegalStateException("cursor failed");
            }
            return true;
          }

          @Override
          public TopTaskSurvey next() {
            // Random text keeps the rows from compressing to almost nothing under gzip
            return survey("2026-07-01", "row " + served++ + " " + UUID.randomUUID());
          }
        };
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
        .onClose(() -> closed.set(true));
  }

  private static TopTaskSurvey survey(String dateTime, String improveComment) {
    TopTaskSurvey survey = new TopTaskSurvey();
    survey.setDateTime(dateTime);
    survey.setTimeStamp(dateTime + "T12:00:00Z");
    survey.setSurveyReferrer("https://www.canada.ca/fr/services.html");
    survey.setLanguage("fr");
    survey.setDevice("Desktop");
    survey.setScreener("Yes");
    survey.setDept("ESDC / EDSC");
    survey.setTheme("Benefits / Prestations");
    survey.setThemeOther("Autre thème");
    survey.setGrouping("Grouping");
    survey.setTask("Task");
    survey.setTaskOther("Task other");
    survey.setTaskSatisfaction("5");
    survey.setTaskEase("4");
    survey.setTaskCompletion("Yes");
    survey.setTaskImprove("Improve");
    survey.setTaskImproveComment(improveComment);
    survey.setTaskWhyNot("Why not");
    survey.setTaskWhyNotComment("=1+1");
    survey.setTaskSampling("Sampling");
    survey.setSamplingInvitation("Invitation");
    survey.setSamplingGC("GC");
    survey.setSamplingCanada("Canada");
    survey.setSamplingTheme("Sampling theme");
    survey.setSamplingInstitution("Institution");
    survey.setSamplingGrouping("Sampling grouping");
    survey.setSamplingTask("Sampling task");
    return survey;
  }
}
