package ca.gc.tbs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.gc.tbs.domain.Problem;
import ca.gc.tbs.service.DashboardService;
import ca.gc.tbs.service.ErrorKeywordService;
import ca.gc.tbs.service.ProblemDateService;
import ca.gc.tbs.service.UserService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class DashboardControllerExportTest {

  private static final String HEADER_LINE =
      "\uFEFFDepartment,URL,Total Comments,Language,Section,Theme\n";

  private MongoTemplate mongoTemplate;
  private DashboardController controller;
  private MockHttpServletRequest request;
  private MockHttpSession session;

  @BeforeEach
  void setUp() {
    mongoTemplate = mock(MongoTemplate.class);
    controller =
        new DashboardController(
            mock(ProblemDateService.class),
            mock(DashboardService.class),
            mock(UserService.class),
            mock(ErrorKeywordService.class),
            mongoTemplate);
    session = new MockHttpSession();
    session.setAttribute("lang", "en");
    request = new MockHttpServletRequest();
    request.setSession(session);
  }

  @Test
  void csvKeepsItsFormatWithTheFormulaGuardAndPlainNumbers() throws Exception {
    givenAggregatedRows(
        row("ESDC", "https://www.canada.ca/a", 42, "=HYPERLINK(\"x\")"),
        row(null, "https://www.canada.ca/b\t", 7, null));
    MockHttpServletResponse response = new MockHttpServletResponse();

    controller.exportCSV(request, response);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getHeader("Content-Disposition"))
        .isEqualTo("attachment; filename*=UTF-8''Page_feedback-" + LocalDate.now() + ".csv");
    assertThat(response.getContentAsString(StandardCharsets.UTF_8))
        .isEqualTo(
            HEADER_LINE
                + "\"ESDC\",\"https://www.canada.ca/a\",42,\"en\",\"Section\",\"'=HYPERLINK(\"\"x\"\")\"\n"
                + "\"\",\"https://www.canada.ca/b \",7,\"en\",\"Section\",\n");
  }

  @Test
  void csvUsesThePageLanguageForTheFileNameAndDepartment() throws Exception {
    session.setAttribute("lang", "fr");
    givenAggregatedRows(row("ESDC", "https://www.canada.ca/a", 1, "Theme"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    controller.exportCSV(request, response);

    assertThat(response.getHeader("Content-Disposition"))
        .isEqualTo("attachment; filename*=UTF-8''Outil_de_retroaction-" + LocalDate.now() + ".csv");
    assertThat(response.getContentAsString(StandardCharsets.UTF_8))
        .contains("\"EDSC\",\"https://www.canada.ca/a\",1,");
  }

  @Test
  void excelKeepsTotalCommentsNumericAndTextUnchanged() throws Exception {
    givenAggregatedRows(row("ESDC", "https://www.canada.ca/a", 42, "=HYPERLINK(\"x\")"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    controller.exportExcel(request, response);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getHeader("Content-Disposition"))
        .isEqualTo("attachment; filename=\"Page_feedback-" + LocalDate.now() + ".xlsx\"");
    try (XSSFWorkbook workbook =
        new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
      Sheet sheet = workbook.getSheet("Dashboard Data");
      assertThat(sheet).isNotNull();
      assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Total Comments");
      Row row = sheet.getRow(1);
      assertThat(row.getCell(0).getStringCellValue()).isEqualTo("ESDC");
      assertThat(row.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(row.getCell(2).getNumericCellValue()).isEqualTo(42);
      // The formula guard only applies to the CSV; Excel cells are plain strings already
      assertThat(row.getCell(5).getCellType()).isEqualTo(CellType.STRING);
      assertThat(row.getCell(5).getStringCellValue()).isEqualTo("=HYPERLINK(\"x\")");
    }
  }

  @Test
  void emptyResultsStillDownloadAHeaderOnlyFile() throws Exception {
    givenAggregatedRows();
    MockHttpServletResponse response = new MockHttpServletResponse();

    controller.exportCSV(request, response);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(HEADER_LINE);
  }

  private void givenAggregatedRows(Problem... rows) {
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("problem"), eq(Problem.class)))
        .thenReturn(new AggregationResults<>(List.of(rows), new Document()));
  }

  private static Problem row(String institution, String url, int comments, String theme) {
    Problem problem = new Problem();
    problem.setInstitution(institution);
    problem.setUrl(url);
    problem.setUrlEntries(comments);
    problem.setLanguage("en");
    problem.setSection("Section");
    problem.setTheme(theme);
    return problem;
  }
}
