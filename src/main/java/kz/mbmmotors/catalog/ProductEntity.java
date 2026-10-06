package kz.mbmmotors.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import kz.mbmmotors.cloudshop.CloudShopProduct;

@Entity
@Table(name = "products")
class ProductEntity {
  @Id
  @Column(name = "id", length = 24)
  private String id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "sku")
  private String sku;

  @Column(name = "code")
  private String code;

  @Column(name = "unit")
  private String unit;

  @Column(name = "catalog_price")
  private BigDecimal catalogPrice;

  @Column(name = "category_id")
  private String categoryId;

  @Column(name = "remote_updated_at")
  private Instant remoteUpdatedAt;

  @Column(name = "imported_at", nullable = false)
  private Instant importedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @ElementCollection
  @CollectionTable(name = "product_offers", joinColumns = @JoinColumn(name = "product_id"))
  @MapKeyColumn(name = "store_id")
  private Map<String, Offer> offers = new HashMap<>();

  @ElementCollection
  @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
  @OrderColumn(name = "position")
  @Column(name = "url")
  private List<String> images = new ArrayList<>();

  protected ProductEntity() {}

  ProductEntity(String id) {
    this.id = id;
  }

  String id() {
    return id;
  }

  void update(CloudShopProduct product, Instant imported, Set<String> stores) {
    if (product.updatedAt() == null)
      throw new IllegalArgumentException("Missing remote update timestamp");
    if (remoteUpdatedAt != null
        && product.updatedAt() != null
        && product.updatedAt().isBefore(remoteUpdatedAt)) {
      return; // Do not regress a newer local row with an older remote representation.
    }
    name = product.name();
    sku = product.sku();
    code = product.code();
    unit = product.unit();
    catalogPrice = product.price();
    categoryId = product.categoryId();
    remoteUpdatedAt = product.updatedAt();
    importedAt = imported;
    offers.clear();
    for (String store : stores) {
      BigDecimal price =
          product.storePrices().containsKey(store)
              ? product.storePrices().get(store)
              : product.price();
      offers.put(store, new Offer(product.stock().getOrDefault(store, BigDecimal.ZERO), price));
    }
    images.clear();
    images.addAll(product.imageUrls());
  }
}

@Embeddable
class Offer {
  @Column(name = "quantity", nullable = false)
  private BigDecimal quantity;

  @Column(name = "price")
  private BigDecimal price;

  protected Offer() {}

  Offer(BigDecimal quantity, BigDecimal price) {
    this.quantity = quantity;
    this.price = price;
  }
}
