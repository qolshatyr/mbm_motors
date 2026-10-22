package kz.mbmmotors.cloudshop;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CloudShopProduct(
    String id,
    String name,
    String sku,
    String code,
    String unit,
    BigDecimal price,
    Map<String, BigDecimal> stock,
    Map<String, BigDecimal> storePrices,
    Instant updatedAt,
    String categoryId,
    List<String> imageUrls,
    List<String> tags) {
  public CloudShopProduct {
    stock = Map.copyOf(stock);
    storePrices = Map.copyOf(storePrices);
    imageUrls = List.copyOf(imageUrls);
    tags = List.copyOf(tags);
  }
}
