package ca.gc.tbs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.gc.tbs.domain.TopTaskSurvey;
import ca.gc.tbs.repository.TopTaskRepository;
import ca.gc.tbs.security.JWTUtil;
import ca.gc.tbs.service.ProblemDateService;
import ca.gc.tbs.service.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.datatables.DataTablesInput;
import org.springframework.data.mongodb.datatables.DataTablesOutput;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class TopTaskControllerTableTest {

  private MongoTemplate mongoTemplate;
  private TopTaskRepository repository;
  private TopTaskController controller;
  private List<String> storedDepartments;
  private List<String> storedThemes;

  @BeforeEach
  @SuppressWarnings("rawtypes")
  void setUp() {
    mongoTemplate = mock(MongoTemplate.class);
    repository = mock(TopTaskRepository.class);
    storedDepartments = new ArrayList<>(List.of("IRCC / IRCC", "ESDC / EDSC", " / "));
    storedThemes = new ArrayList<>(List.of("Immigration / Immigration", "Taxes / Impôts"));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(TopTaskSurvey.class), eq(Document.class)))
        .thenAnswer(
            invocation -> {
              boolean dept = invocation.getArgument(0).toString().contains("$dept");
              List<Document> rows = new ArrayList<>();
              for (String value : dept ? storedDepartments : storedThemes) {
                rows.add(new Document("_id", value));
              }
              return new AggregationResults<>(rows, new Document());
            });
    when(repository.findDistinctTaskCountsWithFilters(any(Criteria.class)))
        .thenReturn(List.<Map>of(Map.of("_id", "a"), Map.of("_id", "b")));
    when(repository.findAll(any(DataTablesInput.class), any(Criteria.class), anyLong()))
        .thenReturn(new DataTablesOutput<>());
    controller =
        new TopTaskController(
            repository,
            mock(UserService.class),
            mock(ProblemDateService.class),
            mongoTemplate,
            mock(JWTUtil.class));
  }

  @Test
  void departmentFilterUsesTheStoredSpellingsExactly() {
    Document query = tableQuery(request("department", "IRCC"));

    assertThat(query.toJson()).doesNotContain("$regularExpression").doesNotContain("$or");
    assertThat(departmentValues(query)).containsExactly("IRCC / IRCC");
  }

  @Test
  void departmentWithoutAStoredSpellingFallsBackToTheRegex() {
    storedDepartments.clear();

    Document query = tableQuery(request("department", "IRCC"));

    // The old query: one case-insensitive anchored regex per known variation
    assertThat(query.toJson())
        .contains("$or")
        .contains("{\"pattern\": \"^\\\\QIRCC / IRCC\\\\E$\", \"options\": \"i\"}");
  }

  @Test
  void themeFilterUsesTheStoredThemesThatContainTheText() {
    Document query = tableQuery(request("theme", "immigration"));

    assertThat(query.get("theme")).isEqualTo(new Document("$in", List.of("Immigration / Immigration")));
  }

  @Test
  void themeWithoutAStoredMatchFallsBackToTheRegex() {
    Document query = tableQuery(request("theme", "Travel"));

    assertThat(query.get("theme"))
        .isInstanceOfSatisfying(
            Pattern.class,
            p -> {
              assertThat(p.pattern()).isEqualTo(Pattern.quote("Travel"));
              assertThat(p.flags() & Pattern.CASE_INSENSITIVE).isNotZero();
            });
  }

  @Test
  void pagingWithTheSameFiltersReusesTheDistinctTaskCount() {
    MockHttpServletRequest request = request("department", "IRCC");
    DataTablesInput page1 = new DataTablesInput();
    DataTablesInput page2 = new DataTablesInput();
    page2.setStart(50);

    controller.list(page1, request);
    controller.list(page2, request);
    assertThat(controller.totalDistinctTasks()).isEqualTo("2");
    verify(repository, times(1)).findDistinctTaskCountsWithFilters(any(Criteria.class));
    // The row count is still computed for every page
    verify(repository, times(2)).findAll(any(DataTablesInput.class), any(Criteria.class), eq(-1L));

    controller.list(page1, request("department", "ESDC"));
    verify(repository, times(2)).findDistinctTaskCountsWithFilters(any(Criteria.class));
  }

  @Test
  void dropdownListsTheAddedDepartments() {
    List<String> values =
        controller.departmentData(request("unused", "")).stream().map(d -> d.get("value")).toList();

    assertThat(values)
        .contains(
            "ELECTIONS / ÉLECTIONS",
            "FEDDEV / FEDDEV",
            "FINTRAC / CANAFE",
            "PACIFICAN / PACIFICAN",
            "PRAIRIESCAN / PRAIRIESCAN");
  }

  @Test
  void anAddedDepartmentFindsItsStoredSpelling() {
    storedDepartments.addAll(List.of("PacifiCan / PacifiCan", "PrairiesCan / PrairiesCan"));

    Document query = tableQuery(request("department", "PACIFICAN / PACIFICAN"));

    assertThat(departmentValues(query)).containsExactly("PacifiCan / PacifiCan");
  }

  @Test
  void misspelledNamesCountUnderTheirDepartment() {
    storedDepartments.addAll(
        List.of("CBSA / ASFC", "CBSA / ASF", "CBSA / ${survey-task2-institution-fr}", "DFO / GCC"));

    Document query = tableQuery(request("department", "CBSA / ASFC"));

    assertThat(departmentValues(query))
        .containsExactlyInAnyOrder(
            "CBSA / ASFC", "CBSA / ASF", "CBSA / ${survey-task2-institution-fr}");
  }

  private Document tableQuery(MockHttpServletRequest request) {
    controller.list(new DataTablesInput(), request);
    ArgumentCaptor<Criteria> criteria = ArgumentCaptor.forClass(Criteria.class);
    verify(repository).findAll(any(DataTablesInput.class), criteria.capture(), anyLong());
    return criteria.getValue().getCriteriaObject();
  }

  @SuppressWarnings("unchecked")
  private static List<String> departmentValues(Document query) {
    // The department criteria is combined with the others by $and
    for (Object part : (List<Object>) query.get("$and")) {
      Object dept = ((Document) part).get("dept");
      if (dept != null) {
        return (List<String>) ((Document) dept).get("$in");
      }
    }
    throw new AssertionError("no department filter in " + query.toJson());
  }

  private static MockHttpServletRequest request(String name, String value) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("lang", "en");
    request.setSession(session);
    request.setParameter("startDate", "2026-07-01");
    request.setParameter("endDate", "2026-09-30");
    request.setParameter(name, value);
    return request;
  }
}
