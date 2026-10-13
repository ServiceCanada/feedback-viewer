package ca.gc.tbs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ca.gc.tbs.service.DashboardService;
import ca.gc.tbs.service.ProblemCacheService;
import ca.gc.tbs.service.ProblemDateService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.scheduling.annotation.Scheduled;

class CachePreloaderTest {

  @Test
  void dailyReloadClearsTheCachesThenLoadsThemAgain() {
    ProblemCacheService problems = mock(ProblemCacheService.class);
    ProblemDateService dates = mock(ProblemDateService.class);
    DashboardService dashboard = mock(DashboardService.class);
    CacheManager cacheManager =
        new ConcurrentMapCacheManager("processedProblems", "distinctUrls", "dashboardStats", "problemDates");
    cacheManager.getCache("processedProblems").put("all", "yesterday");
    cacheManager.getCache("dashboardStats").put("all", "yesterday");

    new CachePreloader(problems, dates, dashboard, cacheManager).reloadDaily();

    // Cleared: the mocked loaders don't put anything back
    assertThat(cacheManager.getCache("processedProblems").get("all")).isNull();
    assertThat(cacheManager.getCache("dashboardStats").get("all")).isNull();
    InOrder order = inOrder(problems, dashboard);
    order.verify(problems).getProcessedProblems();
    order.verify(dashboard).getDashboardStats();
    verify(problems).getDistinctProcessedUrlsForCache();
    verify(dates).getProblemDates();
  }

  @Test
  void dailyReloadRunsAt4amEst() throws Exception {
    Method reload = CachePreloader.class.getMethod("reloadDaily");
    Scheduled scheduled = reload.getAnnotation(Scheduled.class);

    assertThat(scheduled.cron()).isEqualTo("0 0 9 * * *");
    assertThat(scheduled.zone()).isEqualTo("UTC");
  }
}
