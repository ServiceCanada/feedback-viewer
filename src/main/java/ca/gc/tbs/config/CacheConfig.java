package ca.gc.tbs.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableCaching
public class CacheConfig {

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager(
      "problemDates",
      "distinctUrls",
      "processedProblems",
      "dashboardStats",
      "gcIpCache"
    );
    manager.setCaffeine(Caffeine.newBuilder()
      .expireAfterWrite(24, TimeUnit.HOURS)
      .maximumSize(1000));
    return manager;
  }
}
