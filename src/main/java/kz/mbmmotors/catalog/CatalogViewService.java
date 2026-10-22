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
  /** СКЛАД: the partner portal shows stock and wholesale prices of this place only. */
  public static final String DEFAULT_STORE = "6967a39f62918c7e760f3747";

  private static final int PAGE_SIZE = 24;
  private static final String SELECT =
      """
        select p.id,p.name,p.sku,p.code,p.unit,o.price,o.quantity,
          (select url from product_images where product_id=p.id order by position limit 1) as image,
          (select string_agg(name, ', ' order by position) from product_tags
            where product_id=p.id and kind='BRAND') as brand
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
    return list(query, storeId, available, page, null);
  }

  /**
   * {@code sort}: {@code name} (default), {@code price} (cheapest priced first) or {@code stock}
   * (largest stock first). With a query, exact article or code matches always come first.
   */
  @Transactional(readOnly = true)
  public CatalogPage list(String query, String storeId, boolean available, int page, String sort) {
    return list(query, storeId, available, page, sort, CatalogFilter.NONE);
  }

  /**
   * {@code sort}: {@code name} (default), {@code price} (cheapest priced first) or {@code stock}
   * (largest stock first). With a query, exact article or code matches always come first. {@code
   * filter} narrows the list by CloudShop tags; the facets count the products of every option with
   * all other filters applied.
   */
  @Transactional(readOnly = true)
  public CatalogPage list(
      String query,
      String storeId,
      boolean available,
      int page,
      String sort,
      CatalogFilter filter) {
    String q = query == null ? "" : query.strip();
    if (q.length() > 120 || page < 0 || page > 1_000_000) throw new BadCatalogQuery();
    CatalogFilter tags = (filter == null ? CatalogFilter.NONE : filter).normalized();
    String order =
        switch (sort == null || sort.isEmpty() ? "name" : sort) {
          case "name" -> "lower(p.name),p.id";
          case "price" -> "(o.price is null or o.price<=0),o.price,lower(p.name),p.id";
          case "stock" -> "o.quantity desc nulls last,lower(p.name),p.id";
          default -> throw new BadCatalogQuery();
        };
    List<StoreView> stores = stores();
    StoreView store = requireStore(stores, storeId);
    Freshness freshness = freshness();
    Where where = where(store.id(), available, q, tags);
    Long count =
        jdbc.queryForObject(
            "select count(*) from products p join product_offers o on o.product_id=p.id"
                + where.sql(),
            Long.class,
            where.params().toArray());
    long total = count == null ? 0 : count;
    int pages = (int) ((total + PAGE_SIZE - 1) / PAGE_SIZE);
    if (page > 0 && page >= pages) throw new CatalogNotFound();
    List<Object> queryParams = new ArrayList<>(where.params());
    String compactQuery = q.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    if (!compactQuery.isEmpty()) {
      // Quick order relies on an exact article/code match being on the first page.
      order =
          "(regexp_replace(lower(coalesce(p.sku,'')), '[^[:alnum:]]', '', 'g')=?"
              + " or regexp_replace(lower(coalesce(p.code,'')), '[^[:alnum:]]', '', 'g')=?) desc,"
              + order;
      queryParams.add(compactQuery);
      queryParams.add(compactQuery);
    }
    queryParams.add(PAGE_SIZE);
    queryParams.add((long) page * PAGE_SIZE);
    List<ProductView> products =
        jdbc.query(
            SELECT + where.sql() + " order by " + order + " limit ? offset ?",
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
        freshness.stale(),
        tags,
        facets(store.id(), available, q, tags));
  }

  private Facets facets(String storeId, boolean available, String q, CatalogFilter f) {
    var noCategory = new CatalogFilter(null, null, f.make(), f.model(), f.brands());
    var noCar = new CatalogFilter(f.section(), f.subsection(), null, null, f.brands());
    return new Facets(
        facet(where(storeId, available, q, noCategory), "SECTION", null),
        f.section() == null
            ? List.of()
            : facet(
                where(storeId, available, q, noCategory.with(f.section(), null)),
                "SUBSECTION",
                f.section()),
        facet(where(storeId, available, q, noCar), "MAKE", null),
        f.make() == null
            ? List.of()
            : facet(where(storeId, available, q, f.withCar(f.make(), null)), "MODEL", f.make()),
        facet(where(storeId, available, q, f.withBrands(List.of())), "BRAND", null));
  }

  private List<FacetValue> facet(Where where, String kind, String parent) {
    List<Object> params = new ArrayList<>();
    params.add(kind);
    String join = " join product_tags t on t.product_id=p.id and t.kind=?";
    if (parent != null) {
      join += " and t.parent=?";
      params.add(parent);
    }
    params.addAll(where.params());
    return jdbc.query(
        "select t.name,count(distinct p.id) as products from products p"
            + " join product_offers o on o.product_id=p.id"
            + join
            + where.sql()
            + " group by t.name order by lower(t.name),t.name",
        (rs, row) -> new FacetValue(rs.getString("name"), rs.getLong("products")),
        params.toArray());
  }

  private static Where where(String storeId, boolean available, String q, CatalogFilter tags) {
    String where = " where o.store_id=?";
    List<Object> params = new ArrayList<>();
    params.add(storeId);
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
    String tag = " and exists(select 1 from product_tags t where t.product_id=p.id and t.kind=";
    if (tags.section() != null) {
      where += tag + "'SECTION' and t.name=?)";
      params.add(tags.section());
    }
    if (tags.subsection() != null) {
      where += tag + "'SUBSECTION' and t.name=? and t.parent=?)";
      params.add(tags.subsection());
      params.add(tags.section());
    }
    if (tags.make() != null) {
      where += tag + "'MAKE' and t.name=?)";
      params.add(tags.make());
    }
    if (tags.model() != null) {
      where += tag + "'MODEL' and t.name=? and t.parent=?)";
      params.add(tags.model());
      params.add(tags.make());
    }
    if (!tags.brands().isEmpty()) {
      where +=
          tag
              + "'BRAND' and t.name in ("
              + String.join(",", Collections.nCopies(tags.brands().size(), "?"))
              + "))";
      params.addAll(tags.brands());
    }
    return new Where(where, params);
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
            where o.product_id=? and o.store_id=?
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
                        freshness.stale()),
                    ProductAvailability.problem(
                        rs.getBigDecimal("price"),
                        rs.getBigDecimal("quantity"),
                        rs.getString("unit"),
                        1,
                        freshness.stale())),
            // Partners see only the warehouse; other places' stock and prices stay internal.
            id,
            selected.id());
    return new Detail(
        products.getFirst(),
        offers,
        selected.id(),
        freshness.text(),
        freshness.stale(),
        classification(id));
  }

  private Classification classification(String id) {
    List<ProductTags.Tag> tags =
        jdbc.query(
            "select name,kind,parent from product_tags where product_id=? order by position",
            (rs, row) ->
                new ProductTags.Tag(
                    rs.getString("name"),
                    ProductTags.Kind.valueOf(rs.getString("kind")),
                    rs.getString("parent")),
            id);
    String section = null;
    String subsection = null;
    List<String> brands = new ArrayList<>();
    Map<String, List<String>> models = new LinkedHashMap<>();
    for (ProductTags.Tag tag : tags) {
      switch (tag.kind()) {
        case SECTION -> section = tag.name();
        case SUBSECTION -> subsection = tag.name();
        case BRAND -> brands.add(tag.name());
        case MAKE -> models.putIfAbsent(tag.name(), new ArrayList<>());
        case MODEL ->
            models.computeIfAbsent(tag.parent(), make -> new ArrayList<>()).add(tag.name());
        case OTHER -> {}
      }
    }
    List<Fitment> fitment =
        models.entrySet().stream().map(e -> new Fitment(e.getKey(), e.getValue())).toList();
    return new Classification(section, subsection, brands, fitment);
  }

  @Transactional(readOnly = true)
  public List<StoreView> stores() {
    return jdbc.query(
        "select id,name from stores where id=?",
        (rs, row) -> new StoreView(rs.getString("id"), rs.getString("name")),
        DEFAULT_STORE);
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
        safeImage(rs.getString("image")),
        rs.getString("brand"),
        canAdd(
            rs.getBigDecimal("price"), rs.getBigDecimal("quantity"), rs.getString("unit"), stale),
        positive(rs.getBigDecimal("price")),
        stale ? null : positive(rs.getBigDecimal("quantity")));
  }

  private static BigDecimal positive(BigDecimal value) {
    return value == null || value.signum() <= 0 ? null : value;
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
    return ProductAvailability.problem(price, quantity, unit, 1, stale) == null;
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
      boolean stale,
      CatalogFilter filter,
      Facets facets) {}

  /**
   * CloudShop tag filters; null means «any». A subsection needs its section and a model its make.
   */
  public record CatalogFilter(
      String section, String subsection, String make, String model, List<String> brands) {
    public static final CatalogFilter NONE = new CatalogFilter(null, null, null, null, List.of());

    public CatalogFilter {
      brands = brands == null ? List.of() : List.copyOf(brands);
    }

    CatalogFilter normalized() {
      String section = value(this.section);
      String make = value(this.make);
      String subsection = value(this.subsection);
      String model = value(this.model);
      if ((subsection != null && section == null)
          || (model != null && make == null)
          || brands.size() > 20) throw new BadCatalogQuery();
      List<String> selected = brands.stream().map(CatalogFilter::value).distinct().toList();
      if (selected.contains(null)) throw new BadCatalogQuery();
      return new CatalogFilter(section, subsection, make, model, selected);
    }

    CatalogFilter with(String section, String subsection) {
      return new CatalogFilter(section, subsection, make, model, brands);
    }

    CatalogFilter withCar(String make, String model) {
      return new CatalogFilter(section, subsection, make, model, brands);
    }

    CatalogFilter withBrands(List<String> brands) {
      return new CatalogFilter(section, subsection, make, model, brands);
    }

    private static String value(String text) {
      if (text == null || text.isBlank()) return null;
      String value = text.strip();
      if (value.length() > 120) throw new BadCatalogQuery();
      return value;
    }
  }

  /** Options of each tag filter with the number of matching products. */
  public record Facets(
      List<FacetValue> sections,
      List<FacetValue> subsections,
      List<FacetValue> makes,
      List<FacetValue> models,
      List<FacetValue> brands) {}

  public record FacetValue(String name, long count) {}

  private record Where(String sql, List<Object> params) {}

  public record ProductView(
      String id,
      String name,
      String sku,
      String code,
      String unit,
      String priceText,
      String stockText,
      String imageUrl,
      // Part brands from the «Бренд …» tags, comma-separated; null when none.
      String brand,
      boolean canAdd,
      // Null when the price is not set or zero.
      BigDecimal price,
      // Null when stock is absent or the catalog is stale.
      BigDecimal availableQuantity) {}

  public record StoreView(String id, String name) {}

  public record OfferView(
      String storeId,
      String storeName,
      String priceText,
      String stockText,
      boolean canAdd,
      String unavailabilityReason) {}

  public record Detail(
      ProductView product,
      List<OfferView> offers,
      String selectedStore,
      String lastSyncText,
      boolean stale,
      Classification classification) {}

  /** The product's CloudShop tags by role; fitment lists each make with its models. */
  public record Classification(
      String section, String subsection, List<String> brands, List<Fitment> fitment) {}

  public record Fitment(String make, List<String> models) {}

  private record Freshness(String text, boolean stale) {}

  public static class BadCatalogQuery extends RuntimeException {}

  public static class CatalogNotFound extends RuntimeException {}
}
