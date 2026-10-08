package ca.gc.tbs.config;

import ca.gc.tbs.service.DashboardService;
import ca.gc.tbs.service.ProblemCacheService;
import ca.gc.tbs.service.ProblemDateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class CachePreloader implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CachePreloader.class);

    private static final List<String> CACHE_NAMES =
        List.of("processedProblems", "distinctUrls", "dashboardStats", "problemDates");

    private final ProblemCacheService problemCacheService;
    private final ProblemDateService problemDateService;
    private final DashboardService dashboardService;
    private final CacheManager cacheManager;

    public CachePreloader(ProblemCacheService problemCacheService,
                          ProblemDateService problemDateService,
                          DashboardService dashboardService,
                          CacheManager cacheManager) {
        this.problemCacheService = problemCacheService;
        this.problemDateService = problemDateService;
        this.dashboardService = dashboardService;
        this.cacheManager = cacheManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info("Preloading caches before web server starts...");
        warmCaches();
    }

    /**
     * Reloads the caches once a day at 4 am EST (09:00 UTC; 5 am during daylight time), when nobody
     * is using the app, so the day's first user doesn't wait about a minute for the reload. The caches are cleared
     * first rather than swapped: holding two copies of the processed problems at once could run the
     * 3 GB heap short.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "UTC")
    public void reloadDaily() {
        LOGGER.info("Reloading caches for the day...");
        CACHE_NAMES.stream().map(cacheManager::getCache).filter(Objects::nonNull).forEach(Cache::clear);
        warmCaches();
    }

    private void warmCaches() {
        long start = System.currentTimeMillis();

        // 1. Parallel load of base data (lowest tier)
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<Void> problems =
                CompletableFuture.runAsync(problemCacheService::getProcessedProblems, executor);
            CompletableFuture<Void> urls =
                CompletableFuture.runAsync(problemCacheService::getDistinctProcessedUrlsForCache, executor);
            CompletableFuture<Void> dates =
                CompletableFuture.runAsync(problemDateService::getProblemDates, executor);

            CompletableFuture.allOf(problems, urls, dates).join();
        }

        LOGGER.info("DB caches loaded in {}ms.", System.currentTimeMillis() - start);

        // 2. Sequential load of derived data (Dashboard tier)
        // This MUST happen after base data is fully joined to avoid triggering redundant re-calculatons
        // and ensure the dashboard tier is also warm.
        dashboardService.getDashboardStats();
        
        LOGGER.info("All caches warm. Total time: {}ms", System.currentTimeMillis() - start);
        
        // Verify caches are populated
        CACHE_NAMES.forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                Object val = cache.get("all");
                LOGGER.info("Cache '{}' verification: {}", name, val != null ? "HIT ✓" : "MISS ✗");
            }
        });
    }
}
