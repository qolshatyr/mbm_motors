package kz.mbmmotors.cart;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;

/** Session-local selection; it neither reserves stock nor creates a CloudShop document. */
public final class Cart implements Serializable {
  private static final long serialVersionUID = 1L;
  String storeId;
  final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

  public synchronized String storeId() {
    return storeId;
  }

  public synchronized boolean isEmpty() {
    return entries.isEmpty();
  }

  public synchronized int itemCount() {
    return entries.values().stream().mapToInt(Entry::quantity).sum();
  }

  /** Clear local selection after the order transaction has committed; no database access. */
  public synchronized void clearSelection() {
    entries.clear();
    storeId = null;
  }

  record Entry(int quantity, BigDecimal acceptedPrice) implements Serializable {}
}
