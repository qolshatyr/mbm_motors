package kz.mbmmotors.catalog;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import kz.mbmmotors.cart.Cart;
import kz.mbmmotors.cart.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@EnabledIfEnvironmentVariable(named = "MBM_TEST_DATABASE_URL", matches = ".*/mbm_test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.datasource.url=${MBM_TEST_DATABASE_URL}",
      "spring.datasource.username=${MBM_TEST_DATABASE_USER:mbm}",
      "spring.datasource.password=${MBM_TEST_DATABASE_PASSWORD:}"
    })
@Transactional
class CatalogQueryIntegrationTest {
  @Autowired CatalogViewService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired CartService cartService;

  @BeforeEach
  void fixtures() {
    jdbc.update("delete from product_images");
    jdbc.update("delete from product_offers");
    jdbc.update("delete from products");
    jdbc.update("delete from sync_runs");
    jdbc.update(
        "insert into sync_runs(id,mode,status,started_at,finished_at,product_count) values (?,'FULL','SUCCESS',?,?,30)",
        UUID.randomUUID(),
        java.sql.Timestamp.from(Instant.now()),
        java.sql.Timestamp.from(Instant.now()));
    for (int i = 0; i < 30; i++) {
      String id = String.format("%024x", i + 1);
      jdbc.update(
          "insert into products(id,name,sku,code,unit,imported_at) values (?,?,?,?,'шт',now())",
          id,
          "Опора SONATA " + i,
          i < 2 ? "54610-2T000" : "SKU" + i,
          "C" + i);
      jdbc.update(
          "insert into product_offers(product_id,store_id,quantity,price) values (?,?,?,?)",
          id,
          CatalogViewService.DEFAULT_STORE,
          i == 0 ? -1 : 4,
          3000);
      // Another place's stock and price must never reach partners.
      jdbc.update(
          "insert into product_offers(product_id,store_id,quantity,price) values (?,'64feaaa51c9799143b532fcf',62,0)",
          id);
    }
  }

  @Test
  void literalSearchCompactSkuDuplicatesAndPageBoundaries() {
    var found = service.list("546102t000", null, false, 0);
    assertThat(found.totalProducts()).isEqualTo(2);
    assertThat(service.list("sonata", null, false, 0).products()).hasSize(24);
    assertThat(service.list("sonata", null, false, 1).products()).hasSize(6);
    assertThat(service.list("%", null, false, 0).totalProducts()).isZero();
    assertThat(service.list("' OR 1=1 --", null, false, 0).totalProducts()).isZero();
    assertThatThrownBy(() -> service.list("", null, false, 2))
        .isInstanceOf(CatalogViewService.CatalogNotFound.class);
  }

  @Test
  void exactArticleFirstSortsAndMachineReadableFields() {
    jdbc.update(
        "update product_offers set price=? , quantity=? where product_id=? and store_id=?",
        100,
        90,
        String.format("%024x", 30),
        CatalogViewService.DEFAULT_STORE);
    var cheapest = service.list("", null, false, 0, "price").products().getFirst();
    assertThat(cheapest.id()).isEqualTo(String.format("%024x", 30));
    assertThat(cheapest.price()).isEqualByComparingTo("100");
    assertThat(cheapest.availableQuantity()).isEqualByComparingTo("90");
    assertThat(cheapest.canAdd()).isTrue();
    assertThat(service.list("", null, false, 0, "stock").products().getFirst().id())
        .isEqualTo(String.format("%024x", 30));
    // "SKU2" also matches SKU20..SKU29 by substring; the exact article must lead.
    assertThat(service.list("sku2", null, false, 0, "name").products().getFirst().sku())
        .isEqualTo("SKU2");
    var negative = service.list("", null, false, 1, "stock").products().getLast();
    assertThat(negative.availableQuantity()).isNull();
    assertThat(negative.canAdd()).isFalse();
    assertThatThrownBy(() -> service.list("", null, false, 0, "drop table"))
        .isInstanceOf(CatalogViewService.BadCatalogQuery.class);
  }

  @Test
  void cartRevalidatesDatabasePricesStockAndUnits() {
    var cart = new Cart();
    String id = "000000000000000000000002";
    cartService.add(cart, id, CatalogViewService.DEFAULT_STORE, 2);
    assertThat(cartService.view(cart).totalText()).contains("6", "000");
    jdbc.update(
        "update product_offers set price=3500 where product_id=? and store_id=?",
        id,
        CatalogViewService.DEFAULT_STORE);
    assertThat(cartService.view(cart).items().getFirst().priceChanged()).isTrue();
    assertThatThrownBy(() -> cartService.confirmPrice(cart, id, new BigDecimal("3000")))
        .isInstanceOf(CartService.CartProblem.class);
    cartService.confirmPrice(cart, id, new BigDecimal("3500"));
    assertThat(cartService.view(cart).items().getFirst().priceChanged()).isFalse();
    jdbc.update(
        "update product_offers set quantity=1 where product_id=? and store_id=?",
        id,
        CatalogViewService.DEFAULT_STORE);
    assertThat(cartService.view(cart).hasIssues()).isTrue();
    cartService.update(cart, id, 1);
    assertThat(cartService.view(cart).hasIssues()).isFalse();
    jdbc.update("update products set unit=null where id=?", id);
    assertThat(cartService.view(cart).items().getFirst().problem()).contains("единицу");
    assertThatThrownBy(() -> cartService.update(cart, id, 1))
        .isInstanceOf(CartService.CartProblem.class);
  }

  @Test
  void missingOrOldSynchronizationDoesNotPresentExactStockAsCurrent() {
    jdbc.update("update sync_runs set finished_at=now()-interval '16 minutes'");
    var stale = service.list("", null, true, 0);
    assertThat(stale.stale()).isTrue();
    assertThat(stale.products())
        .allSatisfy(p -> assertThat(p.stockText()).isEqualTo("Наличие уточняется"));
    assertThat(stale.totalProducts()).isEqualTo(29);
    jdbc.update("delete from sync_runs");
    var missing = service.list("", null, false, 0);
    assertThat(missing.stale()).isTrue();
    assertThat(missing.lastSyncText()).isNull();
  }

  @Test
  void onlyWarehousePricesAndStockAreVisible() {
    assertThat(service.list("", null, true, 0).totalProducts()).isEqualTo(29);
    assertThat(service.stores())
        .extracting(CatalogViewService.StoreView::id)
        .containsExactly(CatalogViewService.DEFAULT_STORE);
    assertThat(service.list("", null, false, 0).products())
        .allSatisfy(p -> assertThat(p.stockText()).doesNotContain("62"));
    var offers = service.detail("000000000000000000000002", null).offers();
    assertThat(offers).hasSize(1);
    assertThat(offers.getFirst().stockText()).contains("4").doesNotContain("62");
    for (String store : new String[] {"64feaaa51c9799143b532fcf", "untrusted-store"})
      assertThatThrownBy(() -> service.list("", store, false, 0))
          .isInstanceOf(CatalogViewService.BadCatalogQuery.class);
  }
}
