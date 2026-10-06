package kz.mbmmotors.sync;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import kz.mbmmotors.catalog.CatalogImportService;
import kz.mbmmotors.cloudshop.CloudShopGateway;
import kz.mbmmotors.cloudshop.CloudShopProduct;
import org.springframework.stereotype.Service;

@Service
public class CatalogSynchronizer {
  private final CloudShopGateway gateway;
  private final CatalogImportService catalog;
  private final SyncProperties properties;
  private final DatabaseSyncLock lock;
  private final Clock clock;

  public CatalogSynchronizer(
      CloudShopGateway gateway,
      CatalogImportService catalog,
      SyncProperties properties,
      DatabaseSyncLock lock,
      Clock clock) {
    this.gateway = gateway;
    this.catalog = catalog;
    this.properties = properties;
    this.lock = lock;
    this.clock = clock;
  }

  public boolean synchronize(boolean forceFull) {
    return lock.execute(
        () -> {
          Instant started = clock.instant();
          var state = catalog.state();
          boolean full =
              forceFull
                  || state.checkpoint() == null
                  || state.lastFullAt() == null
                  || !state.lastFullAt().plus(properties.fullInterval()).isAfter(started);
          UUID run = UUID.randomUUID();
          catalog.start(run, full ? "FULL" : "INCREMENTAL", started);
          try {
            List<CloudShopProduct> incoming =
                full
                    ? fetchFull()
                    : distinct(gateway.updated(state.checkpoint().minus(properties.overlap())));
            catalog.apply(run, incoming, started, clock.instant(), full);
          } catch (RuntimeException failure) {
            catalog.fail(run, clock.instant(), "SYNC_FAILED");
            // Do not log upstream messages, URLs with credentials or response bodies.
            throw new IllegalStateException("Catalog sync failed; previous checkpoint preserved");
          }
        });
  }

  List<CloudShopProduct> fetchFull() {
    Map<String, CloudShopProduct> all = new LinkedHashMap<>();
    int offset = 0;
    for (int page = 0; page < properties.maxPages(); page++) {
      List<CloudShopProduct> rows = gateway.products(properties.pageSize(), offset);
      if (rows.isEmpty()) return List.copyOf(all.values());
      int before = all.size();
      for (CloudShopProduct product : rows) {
        if (all.putIfAbsent(product.id(), product) != null) {
          throw new IllegalStateException("Catalog pagination contains duplicate IDs");
        }
      }
      if (before == all.size())
        throw new IllegalStateException("Catalog pagination repeated a page");
      offset += rows.size();
      try {
        Thread.sleep(properties.requestDelay());
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Catalog sync interrupted");
      }
    }
    throw new IllegalStateException("Catalog pagination exceeded maximum pages");
  }

  private List<CloudShopProduct> distinct(List<CloudShopProduct> rows) {
    Map<String, CloudShopProduct> result = new LinkedHashMap<>();
    rows.forEach(p -> result.putIfAbsent(p.id(), p));
    return List.copyOf(result.values());
  }
}
