package kz.mbmmotors.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "catalog.sync", name = "enabled", havingValue = "true")
class SyncScheduler {
  private static final Logger LOG = LoggerFactory.getLogger(SyncScheduler.class);
  private final CatalogSynchronizer synchronizer;

  SyncScheduler(CatalogSynchronizer synchronizer) {
    this.synchronizer = synchronizer;
  }

  @Scheduled(fixedDelayString = "${catalog.sync.interval}", initialDelayString = "0")
  void tick() {
    try {
      synchronizer.synchronize(false);
    } catch (RuntimeException failure) {
      LOG.warn("Catalog synchronization failed; details recorded in sync_runs");
    }
  }
}
