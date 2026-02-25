package org.springframework.data.mongodb.datatables;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.repository.query.MongoEntityInformation;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

import java.io.Serializable;

/**
 * Factory bean that creates DataTablesRepository instances.
 * Register via @EnableMongoRepositories(repositoryFactoryBeanClass = DataTablesRepositoryFactoryBean.class)
 */
public class DataTablesRepositoryFactoryBean<R extends Repository<T, ID>, T, ID extends Serializable>
    extends MongoRepositoryFactoryBean<R, T, ID> {

  public DataTablesRepositoryFactoryBean(Class<? extends R> repositoryInterface) {
    super(repositoryInterface);
  }

  @Override
  protected RepositoryFactorySupport getFactoryInstance(MongoOperations operations) {
    return new DataTablesRepositoryFactory(operations);
  }

  private static class DataTablesRepositoryFactory extends MongoRepositoryFactory {

    private final MongoOperations mongoOperations;

    public DataTablesRepositoryFactory(MongoOperations mongoOperations) {
      super(mongoOperations);
      this.mongoOperations = mongoOperations;
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
      if (DataTablesRepository.class.isAssignableFrom(metadata.getRepositoryInterface())) {
        return DataTablesRepositoryImpl.class;
      }
      return super.getRepositoryBaseClass(metadata);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object getTargetRepository(RepositoryInformation information) {
      if (DataTablesRepository.class.isAssignableFrom(information.getRepositoryInterface())) {
        MongoEntityInformation<?, Serializable> entityInformation =
            getEntityInformation(information.getDomainType());
        return new DataTablesRepositoryImpl<>(entityInformation, mongoOperations);
      }
      return super.getTargetRepository(information);
    }
  }
}
