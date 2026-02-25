package org.springframework.data.mongodb.datatables;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Extension of MongoRepository that adds DataTables server-side processing support.
 * Provides findAll methods that accept DataTablesInput and return DataTablesOutput.
 * Compatible with Azure CosmosDB MongoDB API.
 */
@NoRepositoryBean
public interface DataTablesRepository<T, ID> extends MongoRepository<T, ID> {

  /**
   * Find all entities matching the DataTables request parameters.
   */
  DataTablesOutput<T> findAll(DataTablesInput input);

  /**
   * Find all entities matching the DataTables request parameters
   * with additional MongoDB criteria.
   */
  DataTablesOutput<T> findAll(DataTablesInput input, Criteria additionalCriteria);

  /**
   * Find all entities matching the DataTables request parameters
   * with additional MongoDB criteria and a pre-computed total count.
   * This avoids expensive count queries on large collections.
   *
   * @param input DataTables input parameters
   * @param additionalCriteria Additional MongoDB criteria
   * @param cachedTotalCount Pre-computed total record count (use -1 to query)
   */
  DataTablesOutput<T> findAll(DataTablesInput input, Criteria additionalCriteria, long cachedTotalCount);
}
