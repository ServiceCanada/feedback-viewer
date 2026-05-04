package ca.gc.tbs.repository;

import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.query.Criteria;

public interface CustomTopTaskRepository {
  List<Map> findDistinctTaskCountsWithFilters(Criteria criteria);
  List<String> findTaskNamesBySearchWithFilters(String search, Criteria criteria);
}
