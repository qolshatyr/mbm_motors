package kz.mbmmotors.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import kz.mbmmotors.catalog.CatalogViewService;
import kz.mbmmotors.partner.NewPartner;
import kz.mbmmotors.partner.PartnerAccountService;
import kz.mbmmotors.partner.PartnerRepository;
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
class CartStoreIntegrationTest {
  @Autowired CartStore store;
  @Autowired PartnerRepository partners;
  @Autowired PartnerAccountService accounts;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void partner() {
    jdbc.update("delete from partner_cart_items");
    jdbc.update("delete from partners where login in ('cart_a','cart_b')");
    for (String login : new String[] {"cart_a", "cart_b"}) {
      partners.create(
          NewPartner.validate(login, "СТО " + login, "Ерлан", "+77015551234"),
          "$2a$04$abcdefghijklmnopqrstuuAbCdEfGhIjKlMnOpQrStUvWxYz01234",
          Instant.parse("2026-11-05T00:00:00Z"));
    }
  }

  @Test
  void cartSurvivesANewSessionInOrderAndPerPartner() {
    var cart = new Cart();
    cart.storeId = CatalogViewService.DEFAULT_STORE;
    cart.entries.put("000000000000000000000002", new Cart.Entry(3, new BigDecimal("1800")));
    cart.entries.put("000000000000000000000001", new Cart.Entry(1, null));
    store.save("cart_a", cart);

    var restored = store.load("cart_a");
    assertThat(restored.storeId()).isEqualTo(CatalogViewService.DEFAULT_STORE);
    assertThat(restored.entries.keySet())
        .containsExactly("000000000000000000000002", "000000000000000000000001");
    assertThat(restored.entries.get("000000000000000000000002").acceptedPrice())
        .isEqualByComparingTo("1800");
    assertThat(restored.itemCount()).isEqualTo(4);
    assertThat(store.load("cart_b").isEmpty()).isTrue();

    cart.clearSelection();
    store.save("cart_a", cart);
    assertThat(store.load("cart_a").isEmpty()).isTrue();
    // A login without a partner record is ignored rather than failing the cart request.
    store.save("nobody", restored);
    assertThat(store.load("nobody").isEmpty()).isTrue();
  }

  @Test
  void consentIsRecordedOnceAndShownAsGiven() {
    var partner = partners.find("cart_a").orElseThrow();
    assertThat(accounts.consentGiven(partner)).isFalse();
    accounts.recordConsent(partner);
    assertThat(accounts.consentGiven(partner)).isTrue();
    assertThat(accounts.consentGiven(partners.find("cart_b").orElseThrow())).isFalse();
  }
}
