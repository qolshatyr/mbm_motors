package kz.mbmmotors.catalog;

import java.math.BigDecimal;

/** Shared eligibility rules for the product page and server-side cart validation. */
public final class ProductAvailability {
  private ProductAvailability() {}

  public static String problem(
      BigDecimal price, BigDecimal stock, String unit, int quantity, boolean stale) {
    if (stale)
      return "Данные о наличии устарели. Попробуйте позже или уточните наличие в магазине.";
    if (unit == null
        || !(unit.strip().equalsIgnoreCase("шт") || unit.strip().equalsIgnoreCase("шт.")))
      return "Для товара нужно уточнить единицу измерения. Сейчас доступны только штуки.";
    if (price == null || price.signum() <= 0) return "Цену товара нужно уточнить.";
    if (stock == null || stock.signum() <= 0) return "Товара нет в наличии в выбранном магазине.";
    if (stock.compareTo(BigDecimal.valueOf(quantity)) < 0)
      return "Запрошенное количество превышает остаток в выбранном магазине.";
    return null;
  }
}
