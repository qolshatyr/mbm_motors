package kz.mbmmotors.cloudshop;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class CloudShopProductParser {
  private static final DateTimeFormatter LEGACY_DATE =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
  private final JsonMapper mapper = JsonMapper.builder().build();

  /** Strict form: any unreadable card rejects the whole response. */
  List<CloudShopProduct> parse(String body) {
    CloudShopBatch batch = parseBatch(body);
    if (batch.products().size() != batch.size())
      throw new CloudShopException("CloudShop returned an unsupported product response");
    return batch.products();
  }

  /**
   * Cards are edited in CloudShop by hand and in bulk, so one odd card must not stop the whole
   * catalog. Only a response that is not a JSON array is rejected; an unreadable card is reported
   * by ID (when it has one) so the catalog can withdraw it instead of keeping a stale price.
   */
  CloudShopBatch parseBatch(String body) {
    JsonNode root;
    try {
      root = mapper.readTree(body);
    } catch (Exception exception) {
      root = null;
    }
    if (root == null || !root.isArray())
      throw new CloudShopException("CloudShop returned an unsupported product response");
    List<CloudShopProduct> products = new ArrayList<>();
    List<String> unreadable = new ArrayList<>();
    for (JsonNode node : root) {
      try {
        CloudShopProduct product = product(node);
        if (product.updatedAt() == null) throw new IllegalArgumentException();
        products.add(product);
      } catch (Exception exception) {
        JsonNode id = node.isObject() ? node.get("id") : null;
        if (id != null && id.isString() && id.asString().matches("[a-fA-F0-9]{24}"))
          unreadable.add(id.asString());
      }
    }
    return new CloudShopBatch(products, unreadable, root.size());
  }

  private CloudShopProduct product(JsonNode node) {
    if (!node.isObject()) throw new IllegalArgumentException();
    String id = text(node.get("id"));
    if (id == null || !id.matches("[a-fA-F0-9]{24}")) throw new IllegalArgumentException();
    String name = text(node.path("options").get("name"));
    if (name == null || name.isBlank()) throw new IllegalArgumentException();
    return new CloudShopProduct(
        id,
        name,
        text(node.get("sku")),
        text(node.get("code")),
        unit(text(node.get("unit"))),
        decimal(node.get("price")),
        numbers(node.get("stock"), false),
        numbers(node.get("store_prices"), true),
        date(text(node.get("updated_at"))),
        text(node.get("category_id")),
        images(node.get("img")),
        tags(node.get("tags")));
  }

  /**
   * {@code /product/deleted} lists full cards; only their IDs matter, other fields may be stale.
   */
  List<String> parseIds(String body) {
    try {
      JsonNode root = mapper.readTree(body);
      if (root == null || !root.isArray()) throw new IllegalArgumentException();
      Set<String> ids = new LinkedHashSet<>();
      for (JsonNode node : root) {
        String id = node.isObject() ? text(node.get("id")) : null;
        if (id == null || !id.matches("[a-fA-F0-9]{24}")) throw new IllegalArgumentException();
        ids.add(id);
      }
      return List.copyOf(ids);
    } catch (Exception exception) {
      throw new CloudShopException("CloudShop returned an unsupported deleted product response");
    }
  }

  /** Owner's rule (20.10.2026): every product is sold by the piece; a blank unit means «шт». */
  static String unit(String value) {
    return value == null || value.isBlank() ? "шт" : value.strip();
  }

  private static String text(JsonNode node) {
    if (node == null || node.isNull()) return null;
    if (!node.isString()) throw new IllegalArgumentException();
    return node.asString();
  }

  private static BigDecimal decimal(JsonNode node) {
    if (node == null || node.isNull()) return null;
    if (!node.isNumber() && !node.isString()) throw new IllegalArgumentException();
    return new BigDecimal(node.asString());
  }

  private static Map<String, BigDecimal> numbers(JsonNode node, boolean allowMissing) {
    if (node == null || node.isNull()) {
      if (allowMissing) return Map.of();
      throw new IllegalArgumentException();
    }
    if (node.isArray() && node.isEmpty()) return Map.of();
    if (!node.isObject()) throw new IllegalArgumentException();
    Map<String, BigDecimal> values = new LinkedHashMap<>();
    for (String key : node.propertyNames()) {
      BigDecimal value = decimal(node.get(key));
      // CloudShop sends null store prices (seen 22.10.2026); like a missing key, they mean
      // "no price for this place". A null stock quantity is still rejected.
      if (value == null && allowMissing) continue;
      if (value == null) throw new IllegalArgumentException();
      values.put(key, value);
    }
    return values;
  }

  private static Instant date(String value) {
    if (value == null) return null;
    if (value.contains("T")) return OffsetDateTime.parse(value).toInstant();
    // Working integration policy based on verified API/UI observations; UTC is explicit.
    return LocalDateTime.parse(value, LEGACY_DATE).toInstant(ZoneOffset.UTC);
  }

  /** CloudShop tags in their stored order; blanks and repeats are dropped. */
  private static List<String> tags(JsonNode node) {
    if (node == null || node.isNull()) return List.of();
    if (!node.isArray()) throw new IllegalArgumentException();
    Set<String> values = new LinkedHashSet<>();
    for (JsonNode tag : node) {
      String value = text(tag);
      if (value != null && !value.isBlank()) values.add(value.strip());
    }
    return List.copyOf(values);
  }

  private static List<String> images(JsonNode node) {
    if (node == null || node.isNull()) return List.of();
    if (!node.isArray()) throw new IllegalArgumentException();
    List<String> values = new ArrayList<>();
    for (JsonNode image : node) {
      String url = text(image);
      if (url == null || url.isBlank()) throw new IllegalArgumentException();
      values.add(url);
    }
    return values;
  }
}
