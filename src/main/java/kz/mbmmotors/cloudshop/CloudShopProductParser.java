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
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class CloudShopProductParser {
  private static final DateTimeFormatter LEGACY_DATE =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
  private final JsonMapper mapper = JsonMapper.builder().build();

  List<CloudShopProduct> parse(String body) {
    try {
      JsonNode root = mapper.readTree(body);
      if (root == null || !root.isArray()) throw new IllegalArgumentException();
      List<CloudShopProduct> products = new ArrayList<>();
      for (JsonNode node : root) {
        if (!node.isObject()) throw new IllegalArgumentException();
        String id = text(node.get("id"));
        if (id == null || !id.matches("[a-fA-F0-9]{24}")) throw new IllegalArgumentException();
        String name = text(node.path("options").get("name"));
        if (name == null || name.isBlank()) throw new IllegalArgumentException();
        products.add(
            new CloudShopProduct(
                id,
                name,
                text(node.get("sku")),
                text(node.get("code")),
                text(node.get("unit")),
                decimal(node.get("price")),
                numbers(node.get("stock"), false),
                numbers(node.get("store_prices"), true),
                date(text(node.get("updated_at"))),
                text(node.get("category_id")),
                images(node.get("img"))));
      }
      return List.copyOf(products);
    } catch (Exception exception) {
      throw new CloudShopException("CloudShop returned an unsupported product response");
    }
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
