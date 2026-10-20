package kz.mbmmotors.delivery;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DeliverySnapshot(
    UUID orderId,
    long number,
    String storeId,
    String customerName,
    String phone,
    String comment,
    BigDecimal total,
    int itemCount,
    List<Item> items) {
  public DeliverySnapshot {
    items = List.copyOf(items);
  }

  public record Item(
      String productId,
      String name,
      String sku,
      String unit,
      int quantity,
      BigDecimal price,
      BigDecimal lineTotal) {}

  void validate() {
    validateId(storeId);
    if (orderId == null
        || number <= 0
        || customerName == null
        || customerName.isBlank()
        || customerName.length() > 80
        || customerName.chars().anyMatch(Character::isISOControl)
        || phone == null
        || !phone.matches("\\+[1-9][0-9]{9,14}")
        || comment == null
        || comment.length() > 1000
        || items.isEmpty()
        || items.size() > 50) throw new IllegalArgumentException("Invalid delivery snapshot");
    BigDecimal sum = BigDecimal.ZERO;
    int count = 0;
    for (var item : items) {
      validateId(item.productId());
      if (!("шт".equals(item.unit()) || "шт.".equals(item.unit()))
          || item.quantity() < 1
          || item.quantity() > 999
          || item.price() == null
          || item.price().signum() <= 0
          || item.lineTotal() == null
          || item.lineTotal().compareTo(item.price().multiply(BigDecimal.valueOf(item.quantity())))
              != 0) throw new IllegalArgumentException("Invalid delivery item");
      sum = sum.add(item.lineTotal());
      count += item.quantity();
    }
    if (total == null || total.signum() <= 0 || total.compareTo(sum) != 0 || itemCount != count)
      throw new IllegalArgumentException("Invalid delivery totals");
  }

  static void validateId(String id) {
    if (id == null || !id.matches("[a-f0-9]{24}"))
      throw new IllegalArgumentException("Invalid external identifier");
  }
}
