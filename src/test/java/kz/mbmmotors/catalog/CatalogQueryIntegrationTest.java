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
      jdbc.update(
          "insert into product_offers(product_id,store_id,quantity,price) values (?,'6967a39f62918c7e760f3747',62,0)",
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
  void storePricesStockAvailabilityAndDetailAreLocal() {
    assertThat(service.list("", null, true, 0).totalProducts()).isEqualTo(29);
    var warehouse = service.list("", "6967a39f62918c7e760f3747", false, 0);
    assertThat(warehouse.products())
        .allSatisfy(
            p -> {
              assertThat(p.priceText()).isEqualTo("Цена уточняется");
              assertThat(p.stockText()).contains("62");
            });
    assertThat(service.detail("000000000000000000000001", null).offers()).hasSize(2);
    assertThatThrownBy(() -> service.list("", "untrusted-store", false, 0))
        .isInstanceOf(CatalogViewService.BadCatalogQuery.class);
  }
}
