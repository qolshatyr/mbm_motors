package kz.mbmmotors.partner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
class PartnerRepositoryIntegrationTest {
  @Autowired PartnerRepository partners;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clean() {
    jdbc.update("update local_orders set partner_id=null");
    jdbc.update("delete from partners");
    partners.create(
        NewPartner.validate("turbo", "ТОО «Turbo»", "Ерлан", "+77015551234", "123456789012"),
        "$2a$04$abcdefghijklmnopqrstuuAbCdEfGhIjKlMnOpQrStUvWxYz01234",
        Instant.parse("2026-10-21T00:00:00Z"));
    partners.create(
        NewPartner.validate("motor_50", "СТО 100%", "Асель", "+77015550000"),
        "$2a$04$abcdefghijklmnopqrstuuAbCdEfGhIjKlMnOpQrStUvWxYz01234",
        Instant.parse("2026-10-21T01:00:00Z"));
  }

  @Test
  void searchIsLiteralNewestFirstAndShowsBin() {
    assertThat(partners.clients("").stream().map(PartnerRepository.Client::login))
        .containsExactly("motor_50", "turbo");
    assertThat(partners.clients("turbo").getFirst().bin()).isEqualTo("123456789012");
    assertThat(partners.clients("%")).hasSize(1).first().matches(c -> c.login().equals("motor_50"));
    assertThat(partners.clients("асель")).hasSize(1);
  }

  @Test
  void passwordChangeRaisesVersionAndLoginIsRecorded() {
    assertThat(partners.find("turbo").orElseThrow().credentialsVersion()).isZero();
    assertThat(
            partners.changePassword(
                "turbo", "$2a$04$bbcdefghijklmnopqrstuuAbCdEfGhIjKlMnOpQrStUvWxYz01234"))
        .isTrue();
    assertThat(partners.find("turbo").orElseThrow().credentialsVersion()).isEqualTo(1);
    partners.recordLogin("turbo", Instant.parse("2026-10-21T02:00:00Z"));
    assertThat(partners.client("turbo").orElseThrow().lastLoginAt())
        .isEqualTo(Instant.parse("2026-10-21T02:00:00Z"));
    assertThat(partners.setEnabled("turbo", false)).isTrue();
    assertThat(partners.find("turbo").orElseThrow().enabled()).isFalse();
  }

  @Test
  void cardRequestIsClaimedOnceAndRetriedOnlyAfterCheck() {
    assertThat(partners.claimCustomerRequest("turbo", false)).isTrue();
    assertThat(partners.claimCustomerRequest("turbo", false)).isFalse();
    partners.markCustomerUnknown("turbo");
    assertThat(partners.client("turbo").orElseThrow().cloudshopCustomerState())
        .isEqualTo("UNKNOWN");
    assertThat(partners.claimCustomerRequest("turbo", false)).isFalse();
    assertThat(partners.claimCustomerRequest("turbo", true)).isTrue();
    assertThat(partners.linkCustomer("turbo", "6ac110842dd87801040fed88")).isTrue();
    var linked = partners.client("turbo").orElseThrow();
    assertThat(linked.cloudshopCustomerId()).isEqualTo("6ac110842dd87801040fed88");
    assertThat(linked.cloudshopCustomerState()).isNull();
    assertThat(partners.claimCustomerRequest("turbo", true)).isFalse();
  }
}
