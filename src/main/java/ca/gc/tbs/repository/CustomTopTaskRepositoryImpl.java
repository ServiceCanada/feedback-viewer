package ca.gc.tbs.repository;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;

import ca.gc.tbs.domain.TopTaskSurvey; // Import your domain class

public class CustomTopTaskRepositoryImpl implements CustomTopTaskRepository {

  private final MongoTemplate mongoTemplate;

  @Autowired
  public CustomTopTaskRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public List<Map> findDistinctTaskCountsWithFilters(Criteria criteria) {
    Aggregation aggregation =
        Aggregation.newAggregation(
            Aggregation.match(criteria), // Apply the filter criteria
            Aggregation.project("task"), // Include only the 'task' field in the projection
            Aggregation.group(
                "task") // Group by the 'task' field, effectively getting distinct tasks
            );

    // Assuming 'TopTaskSurvey' is the domain class representing your collection
    AggregationResults<Map> results =
        mongoTemplate.aggregate(aggregation, TopTaskSurvey.class, Map.class);
    return results.getMappedResults();
  }

  @Override
  public List<String> findTaskNamesBySearchWithFilters(String search, Criteria criteria) {
    // Add the task search regex to the existing criteria
    Criteria taskSearchCriteria = Criteria.where("task").regex(search, "i");
    Criteria combinedCriteria = new Criteria().andOperator(criteria, taskSearchCriteria);

    Aggregation aggregation =
        Aggregation.newAggregation(
            Aggregation.match(combinedCriteria), // Apply both filter criteria and search
            Aggregation.group("task"), // Group by task to get distinct values
            Aggregation.sort(org.springframework.data.domain.Sort.Direction.ASC, "_id") // Sort alphabetically
        );

    AggregationResults<Map> results =
        mongoTemplate.aggregate(aggregation, TopTaskSurvey.class, Map.class);

    // Extract the task names from the aggregation results
    return results.getMappedResults().stream()
        .map(map -> (String) map.get("_id"))
        .toList();
  }
}
