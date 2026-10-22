package kz.mbmmotors.cloudshop;

import java.util.List;

/**
 * One CloudShop product response: readable cards, IDs of unreadable ones and the raw record count
 * (pagination advances by it, not by the readable count).
 */
public record CloudShopBatch(List<CloudShopProduct> products, List<String> unreadable, int size) {
  public CloudShopBatch {
    products = List.copyOf(products);
    unreadable = List.copyOf(unreadable);
    if (size < products.size() + unreadable.size())
      throw new IllegalArgumentException("Batch size is smaller than its records");
  }

  public static CloudShopBatch of(List<CloudShopProduct> products) {
    return new CloudShopBatch(products, List.of(), products.size());
  }

  /** Cards that could not be read, including those without a usable ID. */
  public int skipped() {
    return size - products.size();
  }
}
