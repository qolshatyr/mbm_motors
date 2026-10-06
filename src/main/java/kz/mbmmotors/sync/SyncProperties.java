package kz.mbmmotors.sync;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("catalog.sync")
public record SyncProperties(
    boolean enabled,
    Duration interval,
    Duration overlap,
    Duration fullInterval,
    int pageSize,
    int maxPages,
    Duration requestDelay,
    Mode startupMode) {
  public SyncProperties {
    if (interval == null
        || interval.isNegative()
        || interval.isZero()
        || overlap == null
        || overlap.isNegative()
        || overlap.isZero()
        || fullInterval == null
        || fullInterval.isNegative()
        || fullInterval.isZero()
        || requestDelay == null
        || requestDelay.isNegative()
        || pageSize < 1
        || pageSize > 1000
        || maxPages < 1
        || maxPages > 1000
        || startupMode == null) {
      throw new IllegalArgumentException("Invalid catalog sync configuration");
    }
  }

  public enum Mode {
    NONE,
    FULL,
    INCREMENTAL
  }
}
