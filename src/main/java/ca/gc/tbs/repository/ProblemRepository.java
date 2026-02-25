package ca.gc.tbs.repository;

import ca.gc.tbs.domain.Problem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import jakarta.validation.Valid;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.datatables.DataTablesInput;
import org.springframework.data.mongodb.datatables.DataTablesOutput;
import org.springframework.data.mongodb.datatables.DataTablesRepository;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.stereotype.Repository;

@Repository
public interface ProblemRepository extends DataTablesRepository<Problem, String> {
  default DataTablesOutput<Problem> findAllWithErrorKeywords(
      @Valid DataTablesInput input, Set<String> keywords, Criteria baseCriteria) {
    if (!keywords.isEmpty()) {
      // Combine all keywords into a single regex pattern
      String combinedPattern =
          keywords.stream()
              .map(Pattern::quote)
              .reduce((a, b) -> a + "|" + b)
              .map(p -> "(" + p + ")")
              .orElse("");

      // Add a single regex criteria for all keywords
      baseCriteria.and("problemDetails").regex(combinedPattern, "i");
    }
    return findAll(input, baseCriteria);
  }

  List<Problem> findByAirTableSync(String syncd);

  List<Problem> findByProcessed(String processed);

  List<Problem> findByPersonalInfoProcessed(String processed);

  List<Problem> findByAutoTagProcessed(String processed);

  @Aggregation(
      pipeline = {
        "{ '$match': { 'processed': 'true' } }", // Optional, adjust based on your requirements
        "{ '$project': { "
            + "'_id': 0, "
            + // Excludes the _id field
            "'url': 1, "
            + // Includes the url field
            "'problemDate': 1, "
            + // Includes the problemDate field
            "'institution': 1, "
            + // Includes the institution field
            "'title': 1, "
            + // Includes the title field
            "'language': 1, "
            + // Includes the language field
            "'section': 1, "
            + // Includes the section field
            "'theme': 1 "
            + // Includes the theme field
            "}}"
      })
  List<Problem> findAllProcessedProblems();

  @Aggregation(
      pipeline = {
        // Optional match stage
        "{ '$match': { 'processed': 'true' } }",
        "{ '$group': { "
            + "'_id': { 'url': '$url', 'day': { '$substr': ['$problemDate', 0, 10] } }, "
            + "'count': { '$sum': 1 }, "
            + "'institution': { '$first': '$institution' }, "
            + "'title': { '$first': '$title' }, "
            + "'problemDate': { '$first': '$problemDate' }, "
            + "'language': { '$first': '$language' }, "
            + "'section': { '$first': '$section' }, "
            + "'theme': { '$first': '$theme' } "
            + "}}, ",
        "{ '$project': { "
            + "'url': '$_id.url', "
            + "'day': '$_id.day', "
            + "'_id': 0, "
            + "'count': 1, "
            + "'institution': 1, "
            + "'title': 1, "
            + "'problemDate': 1, "
            + "'language': 1, "
            + "'section': 1, "
            + "'theme': 1 "
            + "}}"
      })
  AggregationResults<Map> findDistinctUrlsWithDetails();

  List<Problem> findByProcessedAndInstitution(String processed, String institution);

  @Aggregation(pipeline = {"{ '$group': { '_id' : '$url' } }"})
  DataTablesOutput<Problem> findDistinctUrls(@Valid DataTablesInput input);

  // New method to find the earliest and latest problemDate

  @Aggregation(
      pipeline = {
        "{ '$group': { '_id': null, 'earliestDate': { '$min': '$problemDate' }, 'latestDate': {"
            + " '$max': '$problemDate' } } }",
        "{ '$project': { '_id': 0, 'earliestDate': 1, 'latestDate': 1 } }"
      })
  AggregationResults<Map> findEarliestAndLatestProblemDate();

  @Aggregation(
      pipeline = {
        "{ '$match': { 'processed': 'true' } }", // Optional, adjust based on your requirements
        "{ '$group': { '_id': '$title' } }",
        "{ '$sort': { '_id': 1 } }" // Optional, sorts the page names alphabetically
      })
  List<String> findDistinctPageNames();

  @Aggregation(
      pipeline = {
        "{ '$match': { 'processed': 'true', 'title': { '$regex': ?0, '$options': 'i' } } }",
        "{ '$group': { '_id': '$title' } }",
        "{ '$sort': { '_id': 1 } }"
      })
  List<String> findPageTitlesBySearch(String search);
  @Aggregation(
      pipeline = {
        "{ '$match': { 'processed': 'true' } }",
        "{ '$group': { '_id': '$url' } }",
        "{ '$project': { '_id': 0, 'url': '$_id' } }"
      })
  List<String> findDistinctProcessedUrls();
}
