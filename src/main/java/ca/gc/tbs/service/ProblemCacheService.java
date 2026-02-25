package ca.gc.tbs.service;

import ca.gc.tbs.domain.Problem;
import ca.gc.tbs.repository.ProblemRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ProblemCacheService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProblemCacheService.class);
  @Autowired private ProblemRepository problemRepository;

  @Scheduled(cron = "0 0 0 * * *")
  @CacheEvict(value = {"distinctUrls", "processedProblems"}, allEntries = true)
  public void clearCacheDaily() {
    LOGGER.info("Evicting caches at midnight");
  }

  @Cacheable("processedProblems")
  public List<Problem> getProcessedProblems() {
    LOGGER.info("Loading processed problems cache...");
    return problemRepository.findAllProcessedProblems();
  }

  @Cacheable("distinctUrls")
  public List<String> getDistinctProcessedUrlsForCache() {
    LOGGER.info("Loading distinct URLs cache...");
    return problemRepository.findDistinctProcessedUrls();
  }
}
