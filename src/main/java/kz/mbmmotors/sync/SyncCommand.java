package kz.mbmmotors.sync;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
class SyncCommand implements ApplicationRunner {
  private final SyncProperties properties;
  private final CatalogSynchronizer synchronizer;
  private final ConfigurableApplicationContext context;

  SyncCommand(
      SyncProperties properties,
      CatalogSynchronizer synchronizer,
      ConfigurableApplicationContext context) {
    this.properties = properties;
    this.synchronizer = synchronizer;
    this.context = context;
  }

  public void run(ApplicationArguments args) {
    if (properties.startupMode() == SyncProperties.Mode.NONE) return;
    try {
      if (!synchronizer.synchronize(properties.startupMode() == SyncProperties.Mode.FULL)) {
        throw new IllegalStateException("Catalog synchronization already running");
      }
    } finally {
      context.close();
    }
  }
}
