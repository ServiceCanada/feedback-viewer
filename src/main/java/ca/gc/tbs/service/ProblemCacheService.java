package ca.gc.tbs.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import ca.gc.tbs.domain.Problem;
import ca.gc.tbs.repository.ProblemRepository;

@Service
public class ProblemCacheService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProblemCacheService.class);

  private final ProblemRepository problemRepository;

  public ProblemCacheService(ProblemRepository problemRepository) {
    this.problemRepository = problemRepository;
  }

  @Scheduled(cron = "0 0 0 * * *")
  @CacheEvict(value = {"distinctUrls", "processedProblems"}, allEntries = true)
  public void clearCacheDaily() {
    LOGGER.info("Evicting caches at midnight");
  }

  @Cacheable(value = "processedProblems", key = "'all'", sync = true)
  public List<Problem> getProcessedProblems() {
    LOGGER.info("Loading processed problems cache...");
    return problemRepository.findAllProcessedProblems();
  }

  @Cacheable(value = "distinctUrls", key = "'all'", sync = true)
  public List<String> getDistinctProcessedUrlsForCache() {
    LOGGER.info("Loading distinct URLs cache...");
    return problemRepository.findDistinctProcessedUrls();
  }
}
