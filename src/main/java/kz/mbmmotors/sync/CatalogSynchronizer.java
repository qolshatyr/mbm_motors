package kz.mbmmotors.sync;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import kz.mbmmotors.catalog.CatalogImportService;
import kz.mbmmotors.cloudshop.CloudShopBatch;
import kz.mbmmotors.cloudshop.CloudShopGateway;
import kz.mbmmotors.cloudshop.CloudShopProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CatalogSynchronizer {
  private static final Logger LOG = LoggerFactory.getLogger(CatalogSynchronizer.class);
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
            Fetched fetched = null;
            if (!full) {
              try {
                fetched = fetchIncremental(state.checkpoint().minus(properties.overlap()));
              } catch (RuntimeException failure) {
                // A bulk edit in CloudShop makes the update and deletion feeds as large as the
                // catalog and they may time out every time; the paged listing still gets through.
                LOG.warn("Incremental catalog sync failed; falling back to a full paged listing");
                catalog.mode(run, "FULL");
                full = true;
              }
            }
            if (full) fetched = fetchFull();
            if (fetched.skipped() > 0)
              LOG.warn(
                  "Catalog sync withdrew unreadable CloudShop cards: count={}, ids={}",
                  fetched.skipped(),
                  fetched.unreadable().stream().limit(10).toList());
            catalog.apply(
                run, fetched.products(), fetched.removed(), started, clock.instant(), full);
          } catch (RuntimeException failure) {
            catalog.fail(run, clock.instant(), "SYNC_FAILED");
            // Do not log upstream messages, URLs with credentials or response bodies.
            throw new IllegalStateException("Catalog sync failed; previous checkpoint preserved");
          }
        });
  }

  /**
   * Products to apply, IDs to remove and unreadable cards. A full listing replaces the catalog, so
   * anything absent from it, unreadable cards included, is removed without being listed here.
   */
  record Fetched(
      List<CloudShopProduct> products,
      List<String> removed,
      List<String> unreadable,
      int skipped) {}

  /** Updates and deletions; deleted and unreadable cards are withdrawn from the site. */
  Fetched fetchIncremental(Instant since) {
    CloudShopBatch updated = gateway.updated(since);
    List<String> removed = new ArrayList<>(gateway.deleted(since));
    removed.addAll(updated.unreadable());
    return new Fetched(
        distinct(updated.products()), removed, updated.unreadable(), updated.skipped());
  }

  Fetched fetchFull() {
    Map<String, CloudShopProduct> all = new LinkedHashMap<>();
    Set<String> seen = new HashSet<>();
    List<String> unreadable = new ArrayList<>();
    int skipped = 0;
    int offset = 0;
    for (int page = 0; page < properties.maxPages(); page++) {
      CloudShopBatch batch = gateway.products(properties.pageSize(), offset);
      if (batch.size() == 0)
        return new Fetched(List.copyOf(all.values()), List.of(), unreadable, skipped);
      // A repeated or shifted page shows up as a known ID, readable or not.
      for (CloudShopProduct product : batch.products()) {
        if (!seen.add(product.id()))
          throw new IllegalStateException("Catalog pagination contains duplicate IDs");
        all.put(product.id(), product);
      }
      for (String id : batch.unreadable())
        if (!seen.add(id))
          throw new IllegalStateException("Catalog pagination contains duplicate IDs");
      unreadable.addAll(batch.unreadable());
      skipped += batch.skipped();
      offset += batch.size();
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
