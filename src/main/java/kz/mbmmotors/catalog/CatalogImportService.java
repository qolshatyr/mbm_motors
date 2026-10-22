package kz.mbmmotors.catalog;

import java.time.Instant;
import java.util.*;
import kz.mbmmotors.cloudshop.CloudShopProduct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogImportService {
  private final ProductRepository products;
  private final JdbcTemplate jdbc;

  public CatalogImportService(ProductRepository products, JdbcTemplate jdbc) {
    this.products = products;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public State state() {
    return jdbc.queryForObject(
        "select checkpoint,last_full_at from sync_state where id=1",
        (rs, row) ->
            new State(
                rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
                rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant()));
  }

  @Transactional
  public void start(UUID id, String mode, Instant started) {
    jdbc.update(
        "insert into sync_runs(id,mode,status,started_at) values (?,?,'RUNNING',?)",
        id,
        mode,
        java.sql.Timestamp.from(started));
  }

  /** Records that an incremental run continued as a full listing. */
  @Transactional
  public void mode(UUID id, String mode) {
    jdbc.update("update sync_runs set mode=? where id=?", mode, id);
  }

  @Transactional
  public void apply(
      UUID runId,
      List<CloudShopProduct> incoming,
      Collection<String> deleted,
      Instant started,
      Instant finished,
      boolean full) {
    // A deleted or unreadable card wins over an update of the same card within one run.
    Set<String> removed = new HashSet<>(deleted);
    incoming = incoming.stream().filter(p -> !removed.contains(p.id())).toList();
    if (full && !incoming.isEmpty()) {
      // Products missing from a complete listing no longer exist in CloudShop. An empty listing
      // is more likely an upstream fault than an empty catalog, so it removes nothing.
      Set<String> listed = new HashSet<>();
      incoming.forEach(p -> listed.add(p.id()));
      jdbc.queryForList("select id from products", String.class).stream()
          .filter(id -> !listed.contains(id))
          .forEach(removed::add);
    }
    Set<String> stores = new HashSet<>(jdbc.queryForList("select id from stores", String.class));
    Map<String, ProductEntity> existing = new HashMap<>();
    products
        .findAllById(incoming.stream().map(CloudShopProduct::id).toList())
        .forEach(p -> existing.put(p.id(), p));
    List<ProductEntity> changed = new ArrayList<>();
    for (CloudShopProduct product : incoming) {
      ProductEntity entity = existing.computeIfAbsent(product.id(), ProductEntity::new);
      entity.update(product, finished, stores);
      changed.add(entity);
    }
    products.saveAll(changed);
    products.flush();
    if (!removed.isEmpty()) {
      // Orders keep their own copy of names and prices, so nothing else references these rows.
      Object[] ids = {removed.toArray(String[]::new)};
      for (String table : List.of("product_offers", "product_images", "product_tags"))
        jdbc.update("delete from " + table + " where product_id = any(?)", ids);
      jdbc.update("delete from products where id = any(?)", ids);
    }
    // Checkpoint + all product changes + success record commit in one transaction.
    jdbc.update(
        "update sync_state set checkpoint=?, last_full_at=case when ? then ? else last_full_at end where id=1",
        java.sql.Timestamp.from(started),
        full,
        java.sql.Timestamp.from(started));
    jdbc.update(
        "update sync_runs set status='SUCCESS',finished_at=?,product_count=? where id=?",
        java.sql.Timestamp.from(finished),
        incoming.size(),
        runId);
  }

  @Transactional
  public void fail(UUID id, Instant finished, String code) {
    jdbc.update(
        "update sync_runs set status='FAILED',finished_at=?,error_code=? where id=?",
        java.sql.Timestamp.from(finished),
        code,
        id);
  }

  public record State(Instant checkpoint, Instant lastFullAt) {}
}
