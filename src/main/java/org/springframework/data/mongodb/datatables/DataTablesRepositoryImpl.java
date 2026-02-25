package org.springframework.data.mongodb.datatables;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.query.MongoEntityInformation;
import org.springframework.data.mongodb.repository.support.SimpleMongoRepository;

/**
 * Implementation of DataTablesRepository that builds MongoDB queries
 * from DataTables request parameters.
 *
 * Designed for Azure CosmosDB compatibility:
 * - Uses $regex instead of $text for search (CosmosDB doesn't support text indexes)
 * - Uses simple count() queries instead of complex aggregations
 * - Uses skip/limit for pagination
 */
public class DataTablesRepositoryImpl<T, ID>
    extends SimpleMongoRepository<T, ID>
    implements DataTablesRepository<T, ID> {

  private static final Logger LOG = LoggerFactory.getLogger(DataTablesRepositoryImpl.class);

  private final MongoOperations mongoOperations;
  private final MongoEntityInformation<T, ID> entityInformation;

  public DataTablesRepositoryImpl(
      MongoEntityInformation<T, ID> entityInfo, MongoOperations mongoOperations) {
    super(entityInfo, mongoOperations);
    this.mongoOperations = mongoOperations;
    this.entityInformation = entityInfo;
  }

  @Override
  public DataTablesOutput<T> findAll(DataTablesInput input) {
    return findAll(input, null);
  }

  @Override
  public DataTablesOutput<T> findAll(DataTablesInput input, Criteria additionalCriteria) {
    // Delegate to the version with no cached count
    return findAll(input, additionalCriteria, -1);
  }

  @Override
  public DataTablesOutput<T> findAll(DataTablesInput input, Criteria additionalCriteria, long cachedTotalCount) {
    long methodStart = System.currentTimeMillis();
    DataTablesOutput<T> output = new DataTablesOutput<>();
    output.setDraw(input.getDraw());

    try {
      // Build search criteria from DataTables input
      Criteria searchCriteria = buildSearchCriteria(input);

      // Combine with additional criteria if provided
      Criteria finalCriteria;
      if (additionalCriteria != null && searchCriteria != null) {
        finalCriteria = new Criteria().andOperator(searchCriteria, additionalCriteria);
      } else if (additionalCriteria != null) {
        finalCriteria = additionalCriteria;
      } else if (searchCriteria != null) {
        finalCriteria = searchCriteria;
      } else {
        finalCriteria = new Criteria();
      }

      Query query = Query.query(finalCriteria);
      String collectionName = entityInformation.getCollectionName();
      Class<T> entityClass = entityInformation.getJavaType();

      // Use cached count if provided - SKIP expensive count queries on CosmosDB
      // If no cached count available, use a large estimate to enable pagination
      long totalCount;
      if (cachedTotalCount >= 0) {
        totalCount = cachedTotalCount;
        LOG.debug("PERF: Using cached total count: {}", totalCount);
      } else {
        // Use a large estimate instead of expensive count query
        // This allows pagination to work without exact counts
        totalCount = 1000000L; // Large estimate
        LOG.debug("PERF: Using estimated total count: {}", totalCount);
      }
      output.setRecordsTotal(totalCount);

      // For filtered count, always use the same as total to avoid expensive queries
      // DataTables will still paginate correctly
      output.setRecordsFiltered(totalCount);

      // Apply sorting - CosmosDB compatible
      Sort sort = buildSort(input);
      if (sort.isSorted()) {
        query.with(sort);
      }

      // Apply pagination - CosmosDB compatible (skip/limit)
      if (input.getLength() > 0) {
        query.skip(input.getStart()).limit(input.getLength());
      }

      // Execute query
      long dataQueryStart = System.currentTimeMillis();
      List<T> data = mongoOperations.find(query, entityClass, collectionName);
      output.setData(data);
      LOG.debug("PERF: Data query took {}ms (returned {} records)", System.currentTimeMillis() - dataQueryStart, data.size());

    } catch (Exception e) {
      LOG.error("Error executing DataTables query", e);
      output.setError("Query error: " + e.getMessage());
      output.setData(new ArrayList<>());
    }

    LOG.debug("PERF: Total findAll took {}ms", System.currentTimeMillis() - methodStart);
    return output;
  }

  /**
   * Builds search criteria from DataTables input.
   * Uses $regex for CosmosDB compatibility (no $text support).
   */
  private Criteria buildSearchCriteria(DataTablesInput input) {
    List<Criteria> criteriaList = new ArrayList<>();

    // Global search across all searchable columns
    if (input.getSearch() != null
        && input.getSearch().getValue() != null
        && !input.getSearch().getValue().isEmpty()) {

      String searchValue = input.getSearch().getValue();
      List<Criteria> searchCriteria = new ArrayList<>();

      for (DataTablesInput.Column column : input.getColumns()) {
        if (column.isSearchable() && column.getData() != null && !column.getData().isEmpty()) {
          // Case-insensitive regex search - CosmosDB compatible
          searchCriteria.add(
              Criteria.where(column.getData()).regex(Pattern.quote(searchValue), "i"));
        }
      }

      if (!searchCriteria.isEmpty()) {
        criteriaList.add(
            new Criteria().orOperator(searchCriteria.toArray(new Criteria[0])));
      }
    }

    // Per-column search
    for (DataTablesInput.Column column : input.getColumns()) {
      if (column.isSearchable()
          && column.getSearch() != null
          && column.getSearch().getValue() != null
          && !column.getSearch().getValue().isEmpty()) {

        criteriaList.add(
            Criteria.where(column.getData())
                .regex(Pattern.quote(column.getSearch().getValue()), "i"));
      }
    }

    if (criteriaList.isEmpty()) {
      return null;
    }

    if (criteriaList.size() == 1) {
      return criteriaList.get(0);
    }

    return new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
  }

  /**
   * Builds sort from DataTables order specification.
   */
  private Sort buildSort(DataTablesInput input) {
    List<Sort.Order> orders = new ArrayList<>();

    for (DataTablesInput.Order order : input.getOrder()) {
      if (order.getColumn() < input.getColumns().size()) {
        DataTablesInput.Column column = input.getColumns().get(order.getColumn());
        if (column.isOrderable() && column.getData() != null && !column.getData().isEmpty()) {
          Sort.Direction direction =
              "desc".equalsIgnoreCase(order.getDir())
                  ? Sort.Direction.DESC
                  : Sort.Direction.ASC;
          orders.add(new Sort.Order(direction, column.getData()));
        }
      }
    }

    return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
  }
}
