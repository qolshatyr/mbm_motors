package kz.mbmmotors.sync;

import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

class SyncSchedulerStartupTest {
  @Test
  void firstUpdateRunsImmediatelyEvenWithFiveMinuteInterval() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context
          .getEnvironment()
          .getPropertySources()
          .addFirst(
              new MapPropertySource(
                  "test", Map.of("catalog.sync.enabled", "true", "catalog.sync.interval", "5m")));
      context.register(Config.class);
      context.refresh();
      verify(context.getBean(CatalogSynchronizer.class), timeout(2000)).synchronize(false);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @Import(SyncScheduler.class)
  static class Config {
    @Bean
    CatalogSynchronizer synchronizer() {
      return mock(CatalogSynchronizer.class);
    }
  }
}
