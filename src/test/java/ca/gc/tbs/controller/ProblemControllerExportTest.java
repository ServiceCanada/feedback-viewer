package ca.gc.tbs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.gc.tbs.domain.Problem;
import ca.gc.tbs.repository.ProblemRepository;
import ca.gc.tbs.security.JWTUtil;
import ca.gc.tbs.service.ErrorKeywordService;
import ca.gc.tbs.service.ProblemCacheService;
import ca.gc.tbs.service.ProblemDateService;
import ca.gc.tbs.service.UserService;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ProblemControllerExportTest {

  private static final List<String> HEADERS =
      List.of(
          "Problem Date", "Time Stamp (UTC)", "Problem Details", "Language", "Title", "URL",
          "Institution", "Section", "Theme", "Device Type", "Browser");

  private static final String HEADER_LINE = "\uFEFF" + String.join(",", HEADERS) + "\n";

  private MongoTemplate mongoTemplate;
  private ProblemController controller;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    mongoTemplate = mock(MongoTemplate.class);
    controller =
        new ProblemController(
            mock(ProblemRepository.class),
            mock(ProblemDateService.class),
            mock(ErrorKeywordService.class),
            mock(UserService.class),
            mock(ProblemCacheService.class),
            mongoTemplate,
            mock(JWTUtil.class));
    request = new MockHttpServletRequest();
    request.setParameter("startDate", "2026-07-01");
    request.setParameter("endDate", "2026-09-30");
    request.setParameter("language", "fr");
  }

  @Test
  void csvKeepsItsFileNameColumnsAndValues() throws Exception {
    String details = "Très “bien”, \"merci\"\nligne 2";
    givenRows(Stream.of(problem(details)));
    MockHttpServletResponse response = new MockHttpServletResponse();

    controller.exportCSV(request, response);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
    assertThat(response.getHeader("Content-Disposition"))
        .isEqualTo("attachment; filename*=UTF-8''feedback_export.csv");
    String body = response.getContentAsString(StandardCharsets.UTF_8);
    assertThat(body).startsWith(HEADER_LINE);
    try (CSVParser parser =
        CSVParser.parse(
            new StringReader(body.substring(1)),
            CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get())) {
      assertThat(parser.getHeaderNames()).isEqualTo(HEADERS);
      List<CSVRecord> records = parser.getRecords();
      assertThat(records).hasSize(1);
      assertThat(records.get(0).get("Problem Date")).isEqualTo("2026-07-01");
      assertThat(records.get(0).get("Problem Details")).isEqualTo(details);
      assertThat(records.get(0).get("Browser")).isEqualTo("Firefox");
    }
  }

  @Test
  void excelKeepsItsFileNameSheetAndValues() throws Exception {
    givenRows(Stream.of(problem("=1+1")));
    MockHttpServletResponse response = new MockHttpServletResponse();

    controller.exportExcel(request, response);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getHeader("Content-Disposition"))
        .isEqualTo("attachment; filename=\"feedback_export.xlsx\"");
    try (XSSFWorkbook workbook =
        new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
      Sheet sheet = workbook.getSheet("Feedback Data");
      assertThat(sheet).isNotNull();
      for (int i = 0; i < HEADERS.size(); i++) {
        assertThat(sheet.getRow(0).getCell(i).getStringCellValue()).isEqualTo(HEADERS.get(i));
      }
      Row row = sheet.getRow(1);
      assertThat(row.getCell(0).getStringCellValue()).isEqualTo("2026-07-01");
      // Comment text that looks like a formula stays a plain string
      assertThat(row.getCell(2).getCellType()).isEqualTo(CellType.STRING);
      assertThat(row.getCell(2).getStringCellValue()).isEqualTo("=1+1");
      assertThat(row.getCell(10).getStringCellValue()).isEqualTo("Firefox");
    }
  }

  @Test
  void emptyResultsStillDownloadAHeaderOnlyFile() throws Exception {
    // The page downloads by navigating to the export URL, so a 204 would silently do nothing
    givenRows(Stream.empty());
    MockHttpServletResponse csv = new MockHttpServletResponse();
    controller.exportCSV(request, csv);

    assertThat(csv.getStatus()).isEqualTo(200);
    assertThat(csv.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(HEADER_LINE);

    givenRows(Stream.empty());
    MockHttpServletResponse excel = new MockHttpServletResponse();
    controller.exportExcel(request, excel);

    assertThat(excel.getStatus()).isEqualTo(200);
    try (XSSFWorkbook workbook =
        new XSSFWorkbook(new ByteArrayInputStream(excel.getContentAsByteArray()))) {
      Sheet sheet = workbook.getSheet("Feedback Data");
      assertThat(sheet.getLastRowNum()).isZero();
      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Problem Date");
    }
  }

  @Test
  void queriesKeepEachExportsFiltersAndUseNoProjection() throws Exception {
    givenRows(Stream.empty());
    controller.exportExcel(request, new MockHttpServletResponse());
    givenRows(Stream.empty());
    controller.exportCSV(request, new MockHttpServletResponse());

    ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate, times(2)).stream(queries.capture(), eq(Problem.class));
    Query excel = queries.getAllValues().get(0);
    Query csv = queries.getAllValues().get(1);
    for (Query query : List.of(excel, csv)) {
      // Cosmos DB charges more RU to project fields than to return whole documents
      assertThat(query.getFieldsObject()).isEmpty();
      assertThat(query.getMeta().getCursorBatchSize()).isEqualTo(5000);
      assertThat(query.getQueryObject().get("processed")).isEqualTo("true");
      assertThat(query.getQueryObject().get("problemDate"))
          .isEqualTo(new Document("$gte", "2026-07-01").append("$lte", "2026-09-30"));
    }
    // Excel has always matched language exactly and CSV case-insensitively
    assertThat(excel.getQueryObject().get("language")).isEqualTo("fr");
    assertThat(csv.getQueryObject().get("language"))
        .isInstanceOfSatisfying(
            Pattern.class,
            pattern -> {
              assertThat(pattern.pattern()).isEqualTo(Pattern.quote("fr"));
              assertThat(pattern.flags() & Pattern.CASE_INSENSITIVE).isNotZero();
            });
  }

  private void givenRows(Stream<Problem> rows) {
    when(mongoTemplate.stream(any(Query.class), eq(Problem.class))).thenReturn(rows);
  }

  private static Problem problem(String details) {
    Problem problem = new Problem();
    problem.setProblemDate("2026-07-01");
    problem.setTimeStamp("2026-07-01T12:00:00Z");
    problem.setProblemDetails(details);
    problem.setLanguage("fr");
    problem.setTitle("Prestations");
    problem.setUrl("https://www.canada.ca/fr/services/prestations.html");
    problem.setInstitution("ESDC / EDSC");
    problem.setSection("Section");
    problem.setTheme("Benefits / Prestations");
    problem.setDeviceType("Desktop");
    problem.setBrowser("Firefox");
    return problem;
  }
}
