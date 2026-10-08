package ca.gc.tbs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.gc.tbs.domain.TopTaskSurvey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

class SurveyFieldValuesTest {

  private static final List<String> DEPARTMENTS =
      Arrays.asList("IRCC / IRCC", "ESDC / EDSC", " / ", "AGENCE CANADIENNE D’ÉVALUATION", null);

  private MongoTemplate mongoTemplate;
  private SurveyFieldValues values;

  @BeforeEach
  void setUp() {
    mongoTemplate = mock(MongoTemplate.class);
    givenStoredValues(DEPARTMENTS);
    values = new SurveyFieldValues(mongoTemplate);
  }

  @Test
  void equalityMatchesWhatTheCaseInsensitiveAnchoredRegexMatched() {
    List<String> names = List.of("IRCC", "irCC / ircc", "agence canadienne d’évaluation");

    assertThat(values.equalToAnyIgnoringCase("dept", names))
        .containsExactly("IRCC / IRCC", "AGENCE CANADIENNE D’ÉVALUATION")
        .isEqualTo(regexMatches(names, true));
  }

  @Test
  void containsMatchesWhatTheCaseInsensitiveRegexMatched() {
    assertThat(values.containingIgnoringCase("dept", "edsc")).containsExactly("ESDC / EDSC");
    // Regex metacharacters in the text are literal, as Pattern.quote made them
    assertThat(values.containingIgnoringCase("dept", ".*")).isEmpty();
    assertThat(values.containingIgnoringCase("dept", " / ")).isEqualTo(regexMatches(List.of(" / "), false));
  }

  @Test
  void noMatchGivesAnEmptyList() {
    assertThat(values.equalToAnyIgnoringCase("dept", List.of("NEW / NOUVEAU"))).isEmpty();
  }

  @Test
  void valuesAreLoadedOncePerField() {
    values.equalToAnyIgnoringCase("dept", List.of("IRCC"));
    values.containingIgnoringCase("dept", "ESDC");
    values.containingIgnoringCase("theme", "Immigration");

    verify(mongoTemplate, times(2))
        .aggregate(any(Aggregation.class), eq(TopTaskSurvey.class), eq(Document.class));
  }

  @Test
  void aLoadFailureGivesAnEmptyListSoTheCallerFallsBackToTheRegex() {
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(TopTaskSurvey.class), eq(Document.class)))
        .thenThrow(new IllegalStateException("Request timed out"));

    assertThat(values.equalToAnyIgnoringCase("dept", List.of("IRCC"))).isEmpty();
  }

  private void givenStoredValues(List<String> stored) {
    List<Document> rows = new ArrayList<>();
    for (String value : stored) {
      rows.add(new Document("_id", value));
    }
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(TopTaskSurvey.class), eq(Document.class)))
        .thenReturn(new AggregationResults<>(rows, new Document()));
  }

  /** What the regexes the controller used to send would have matched among the stored values. */
  private static List<String> regexMatches(List<String> names, boolean anchored) {
    List<Pattern> patterns =
        names.stream()
            .map(n -> anchored ? "^" + Pattern.quote(n) + "$" : Pattern.quote(n))
            .map(r -> Pattern.compile(r, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
            .toList();
    return DEPARTMENTS.stream()
        .filter(v -> v != null && patterns.stream().anyMatch(p -> p.matcher(v).find()))
        .toList();
  }
}
