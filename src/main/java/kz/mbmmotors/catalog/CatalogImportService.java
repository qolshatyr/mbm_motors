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

  @Transactional
  public void apply(
      UUID runId,
      List<CloudShopProduct> incoming,
      Instant started,
      Instant finished,
      boolean full) {
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
