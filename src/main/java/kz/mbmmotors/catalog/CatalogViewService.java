package kz.mbmmotors.catalog;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogViewService {
  public static final String DEFAULT_STORE = "64feaaa51c9799143b532fcf";
  private static final int PAGE_SIZE = 24;
  private static final String SELECT =
      """
        select p.id,p.name,p.sku,p.code,p.unit,o.price,o.quantity,
          (select url from product_images where product_id=p.id order by position limit 1) as image
        from products p join product_offers o on o.product_id=p.id
        """;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public CatalogViewService(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public CatalogPage list(String query, String storeId, boolean available, int page) {
    String q = query == null ? "" : query.strip();
    if (q.length() > 120 || page < 0 || page > 1_000_000) throw new BadCatalogQuery();
    List<StoreView> stores = stores();
    StoreView store = requireStore(stores, storeId);
    Freshness freshness = freshness();
    String where = " where o.store_id=?";
    List<Object> params = new ArrayList<>();
    params.add(store.id());
    if (available) where += " and o.quantity > 0";
    if (!q.isEmpty()) {
      where +=
          " and (p.name ilike ? escape '!' or coalesce(p.sku,'') ilike ? escape '!' or coalesce(p.code,'') ilike ? escape '!'";
      String pattern = "%" + escapeLike(q) + "%";
      params.add(pattern);
      params.add(pattern);
      params.add(pattern);
      String compact = q.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
      if (!compact.isEmpty()) {
        where +=
            " or regexp_replace(lower(coalesce(p.sku,'')), '[^[:alnum:]]', '', 'g') like ? escape '!'";
        params.add("%" + escapeLike(compact) + "%");
      }
      where += ")";
    }
    Long count =
        jdbc.queryForObject(
            "select count(*) from products p join product_offers o on o.product_id=p.id" + where,
            Long.class,
            params.toArray());
    long total = count == null ? 0 : count;
    int pages = (int) ((total + PAGE_SIZE - 1) / PAGE_SIZE);
    if (page > 0 && page >= pages) throw new CatalogNotFound();
    List<Object> queryParams = new ArrayList<>(params);
    queryParams.add(PAGE_SIZE);
    queryParams.add((long) page * PAGE_SIZE);
    List<ProductView> products =
        jdbc.query(
            SELECT + where + " order by lower(p.name),p.id limit ? offset ?",
            (rs, row) -> product(rs, freshness.stale()),
            queryParams.toArray());
    return new CatalogPage(
        products,
        q,
        store.id(),
        store.name(),
        stores,
        page,
        pages,
        total,
        freshness.text(),
        freshness.stale());
  }

  @Transactional(readOnly = true)
  public Detail detail(String id, String storeId) {
    if (id == null || !id.matches("[a-fA-F0-9]{24}")) throw new CatalogNotFound();
    List<StoreView> stores = stores();
    StoreView selected = requireStore(stores, storeId);
    Freshness freshness = freshness();
    List<ProductView> products =
        jdbc.query(
            SELECT + " where p.id=? and o.store_id=?",
            (rs, row) -> product(rs, freshness.stale()),
            id,
            selected.id());
    if (products.isEmpty()) throw new CatalogNotFound();
    List<OfferView> offers =
        jdbc.query(
            """
            select s.id,s.name,o.price,o.quantity,p.unit from product_offers o
            join stores s on s.id=o.store_id join products p on p.id=o.product_id
            where o.product_id=? order by case s.id when '64feaaa51c9799143b532fcf' then 0
              when '698c479d4bb8342bd30e3c33' then 1 when '64feaa7d401f7a28d35cda23' then 2 else 3 end
            """,
            (rs, row) ->
                new OfferView(
                    rs.getString("id"),
                    rs.getString("name"),
                    priceText(rs.getBigDecimal("price")),
                    stockText(
                        rs.getBigDecimal("quantity"), rs.getString("unit"), freshness.stale()),
                    canAdd(
                        rs.getBigDecimal("price"),
                        rs.getBigDecimal("quantity"),
                        rs.getString("unit"),
                        freshness.stale())),
            id);
    return new Detail(
        products.getFirst(), offers, selected.id(), freshness.text(), freshness.stale());
  }

  private List<StoreView> stores() {
    return jdbc.query(
        """
            select id,name from stores order by case id when '64feaaa51c9799143b532fcf' then 0
              when '698c479d4bb8342bd30e3c33' then 1 when '64feaa7d401f7a28d35cda23' then 2 else 3 end
            """,
        (rs, row) -> new StoreView(rs.getString("id"), rs.getString("name")));
  }

  private StoreView requireStore(List<StoreView> stores, String id) {
    String selected = id == null || id.isBlank() ? DEFAULT_STORE : id;
    return stores.stream()
        .filter(s -> s.id().equals(selected))
        .findFirst()
        .orElseThrow(BadCatalogQuery::new);
  }

  private Freshness freshness() {
    List<Instant> times =
        jdbc.query(
            "select max(finished_at) from sync_runs where status='SUCCESS'",
            (rs, row) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant());
    Instant latest = times.getFirst();
    boolean stale = latest == null || latest.plus(Duration.ofMinutes(15)).isBefore(clock.instant());
    String text =
        latest == null
            ? null
            : DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(ZoneId.of("Asia/Almaty"))
                .format(latest);
    return new Freshness(text, stale);
  }

  private ProductView product(ResultSet rs, boolean stale) throws SQLException {
    return new ProductView(
        rs.getString("id"),
        rs.getString("name"),
        rs.getString("sku"),
        rs.getString("code"),
        rs.getString("unit"),
        priceText(rs.getBigDecimal("price")),
        stockText(rs.getBigDecimal("quantity"), rs.getString("unit"), stale),
        safeImage(rs.getString("image")));
  }

  static String escapeLike(String value) {
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  static String priceText(BigDecimal price) {
    if (price == null || price.signum() <= 0) return "Цена уточняется";
    return decimalText(price) + " ₸";
  }

  static String stockText(BigDecimal quantity, String unit, boolean stale) {
    if (stale) return "Наличие уточняется";
    if (quantity == null || quantity.signum() <= 0) return "Нет в наличии";
    return "В наличии: "
        + decimalText(quantity)
        + (unit == null || unit.isBlank() ? "" : " " + unit);
  }

  static boolean canAdd(BigDecimal price, BigDecimal quantity, String unit, boolean stale) {
    return !stale
        && price != null
        && price.signum() > 0
        && quantity != null
        && quantity.compareTo(BigDecimal.ONE) >= 0
        && unit != null
        && (unit.strip().equalsIgnoreCase("шт") || unit.strip().equalsIgnoreCase("шт."));
  }

  private static String decimalText(BigDecimal value) {
    var format = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));
    format.setMaximumFractionDigits(Math.max(2, value.stripTrailingZeros().scale()));
    return format.format(value);
  }

  static String safeImage(String value) {
    if (value == null) return null;
    try {
      URI uri = URI.create(value);
      return "https".equalsIgnoreCase(uri.getScheme())
              && uri.getHost() != null
              && uri.getUserInfo() == null
          ? uri.toString()
          : null;
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  public record CatalogPage(
      List<ProductView> products,
      String query,
      String storeId,
      String storeName,
      List<StoreView> stores,
      int page,
      int totalPages,
      long totalProducts,
      String lastSyncText,
      boolean stale) {}

  public record ProductView(
      String id,
      String name,
      String sku,
      String code,
      String unit,
      String priceText,
      String stockText,
      String imageUrl) {}

  public record StoreView(String id, String name) {}

  public record OfferView(
      String storeId, String storeName, String priceText, String stockText, boolean canAdd) {}

  public record Detail(
      ProductView product,
      List<OfferView> offers,
      String selectedStore,
      String lastSyncText,
      boolean stale) {}

  private record Freshness(String text, boolean stale) {}

  public static class BadCatalogQuery extends RuntimeException {}

  public static class CatalogNotFound extends RuntimeException {}
}
