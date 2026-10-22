package kz.mbmmotors.catalog;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import kz.mbmmotors.cloudshop.CloudShopProduct;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CatalogIntegrationTest {
  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired CatalogImportService catalog;
  @Autowired JdbcTemplate jdbc;

  @Test
  void migrationsImportDuplicateSkuAndRollbackInvalidOfferAtomically() {
    verify(catalog, jdbc);
  }

  static void verify(CatalogImportService catalog, JdbcTemplate jdbc) {
    jdbc.update("delete from product_tags");
    jdbc.update("delete from product_images");
    jdbc.update("delete from product_offers");
    jdbc.update("delete from products");
    jdbc.update("delete from sync_runs");
    jdbc.update("update sync_state set checkpoint=null,last_full_at=null where id=1");
    Instant now = Instant.parse("2026-10-05T12:00:00Z");
    UUID run = UUID.randomUUID();
    catalog.start(run, "FULL", now);
    String car = "64feaaa51c9799143b532fcf", warehouse = "6967a39f62918c7e760f3747";
    CloudShopProduct a =
        new CloudShopProduct(
            "000000000000000000000001",
            "Товар",
            "duplicate",
            "1",
            null,
            new BigDecimal("3000"),
            Map.of(car, new BigDecimal("4")),
            Map.of(warehouse, BigDecimal.ZERO),
            now,
            null,
            List.of(),
            List.of());
    CloudShopProduct b =
        new CloudShopProduct(
            "000000000000000000000002",
            "Другой",
            "duplicate",
            "2",
            "шт.",
            null,
            Map.of(car, new BigDecimal("-1")),
            Map.of(car, new BigDecimal("100")),
            now,
            null,
            List.of("https://example.com/image.png"),
            List.of("Двигатель", "Поршневая группа", "Hyundai", "Sonata", "Бренд NGP"));
    catalog.apply(run, List.of(a, b), List.of(), now, now, true);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from products where sku='duplicate'", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select price from product_offers where product_id=? and store_id=?",
                BigDecimal.class,
                a.id(),
                warehouse))
        .isEqualByComparingTo("0");
    assertThat(
            jdbc.queryForObject(
                "select quantity from product_offers where product_id=? and store_id=?",
                BigDecimal.class,
                b.id(),
                car))
        .isEqualByComparingTo("-1");
    assertThat(catalog.state().checkpoint()).isEqualTo(now);
    UUID failed = UUID.randomUUID();
    catalog.start(failed, "INCREMENTAL", now.plusSeconds(300));
    CloudShopProduct invalid =
        new CloudShopProduct(
            "000000000000000000000003",
            null,
            "bad",
            "3",
            null,
            null,
            Map.of(),
            Map.of(),
            now,
            null,
            List.of(),
            List.of());
    assertThatThrownBy(
            () ->
                catalog.apply(
                    failed,
                    List.of(invalid),
                    List.of(),
                    now.plusSeconds(300),
                    now.plusSeconds(301),
                    false))
        .isInstanceOf(RuntimeException.class);
    assertThat(catalog.state().checkpoint()).isEqualTo(now);
    assertThat(jdbc.queryForObject("select count(*) from products", Integer.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject("select count(*) from product_images", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "select kind||':'||name||':'||coalesce(parent,'') from product_tags where product_id=? order by position",
                String.class,
                b.id()))
        .containsExactly(
            "SECTION:Двигатель:",
            "SUBSECTION:Поршневая группа:Двигатель",
            "MAKE:Hyundai:",
            "MODEL:Sonata:Hyundai",
            "BRAND:NGP:");

    // CloudShop deleted b: an incremental run removes it with its offers, images and tags.
    UUID removal = UUID.randomUUID();
    catalog.start(removal, "INCREMENTAL", now.plusSeconds(600));
    catalog.apply(
        removal, List.of(), List.of(b.id()), now.plusSeconds(600), now.plusSeconds(601), false);
    assertThat(jdbc.queryForList("select id from products", String.class)).containsExactly(a.id());
    assertThat(
            jdbc.queryForObject(
                "select (select count(*) from product_offers where product_id=?)"
                    + " + (select count(*) from product_images where product_id=?)"
                    + " + (select count(*) from product_tags where product_id=?)",
                Integer.class,
                b.id(),
                b.id(),
                b.id()))
        .isZero();

    // A full listing without a removes it; an empty listing removes nothing.
    UUID empty = UUID.randomUUID();
    catalog.start(empty, "FULL", now.plusSeconds(900));
    catalog.apply(empty, List.of(), List.of(), now.plusSeconds(900), now.plusSeconds(901), true);
    assertThat(jdbc.queryForList("select id from products", String.class)).containsExactly(a.id());
    UUID replaced = UUID.randomUUID();
    catalog.start(replaced, "FULL", now.plusSeconds(1200));
    catalog.apply(
        replaced, List.of(b), List.of(), now.plusSeconds(1200), now.plusSeconds(1201), true);
    assertThat(jdbc.queryForList("select id from products", String.class)).containsExactly(b.id());
  }
}
