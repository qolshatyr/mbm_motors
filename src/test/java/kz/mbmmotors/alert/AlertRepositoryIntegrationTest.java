package kz.mbmmotors.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "MBM_TEST_DATABASE_URL", matches = ".*/mbm_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlertRepositoryIntegrationTest {
  static final Instant NOW = Instant.parse("2026-10-23T06:00:00Z");
  static final Instant BOUNDARY = NOW.minus(Duration.ofMinutes(15));
  final String schema = "alert_" + UUID.randomUUID().toString().replace("-", "");
  JdbcTemplate admin;
  JdbcTemplate jdbc;
  AlertRepository repository;
  long number;

  @BeforeAll
  void database() {
    var source =
        new DriverManagerDataSource(
            System.getenv("MBM_TEST_DATABASE_URL"),
            System.getenv().getOrDefault("MBM_TEST_DATABASE_USER", "mbm"),
            System.getenv().getOrDefault("MBM_TEST_DATABASE_PASSWORD", ""));
    admin = new JdbcTemplate(source);
    assertThat(admin.queryForObject("select current_database()", String.class))
        .isEqualTo("mbm_test");
    Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                System.getenv("MBM_TEST_DATABASE_URL") + "?currentSchema=" + schema,
                source.getUsername(),
                source.getPassword()));
    repository = new AlertRepository(jdbc);
  }

  @AfterAll
  void cleanup() {
    if (admin != null) admin.execute("drop schema if exists " + schema + " cascade");
  }

  @BeforeEach
  void empty() {
    jdbc.execute(
        "delete from delivery_outbox; delete from local_order_items; delete from local_orders; delete from partners");
  }

  /** An order of a partner (enabled, with or without a card) created at {@code created}. */
  UUID order(Instant created, String card, boolean enabled) {
    UUID partner = UUID.randomUUID();
    jdbc.update(
        "insert into partners(id,login,password_hash,company,contact_name,phone,cloudshop_customer_id,enabled,created_at) values (?,?,?,'Synthetic Partner','Synthetic Buyer','+77000000000',?,?,now())",
        partner,
        "p" + partner.toString().substring(0, 8),
        "$2a$10$" + "a".repeat(53),
        card,
        enabled);
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into local_orders(id,idempotency_key,owner_id,request_hash,revision,status,store_id,store_name,customer_name,phone,comment,consent_at,created_at,total,total_text,item_count,partner_id) values (?,?,?,repeat('a',64),repeat('b',64),'RECEIVED','aaaaaaaaaaaaaaaaaaaaaaaa','Synthetic store','Synthetic Buyer','+77000000000','Synthetic',now(),?,1.00,'1.00',1,?)",
        id,
        UUID.randomUUID(),
        UUID.randomUUID(),
        Timestamp.from(created),
        partner);
    jdbc.update(
        "insert into delivery_outbox(order_id,state,created_at) values (?,'WAITING_CONFIGURATION',?)",
        id,
        Timestamp.from(created));
    return id;
  }

  void unknown(UUID id, String reason) {
    jdbc.update(
        "update delivery_outbox set state='UNKNOWN',customer_id='bbbbbbbbbbbbbbbbbbbbbbbb',failure_reason=? where order_id=?",
        reason,
        id);
  }

  List<UUID> pendingIds(Instant boundary) {
    return repository.pending(boundary, 15).stream().map(AlertRepository.Pending::orderId).toList();
  }

  @Test
  void unknownOrdersAreReportedWithTheirReasonUntilReconciled() {
    UUID open = order(NOW, "bbbbbbbbbbbbbbbbbbbbbbbb", true);
    unknown(open, "Продажа: HTTP 404");
    UUID reconciled = order(NOW, "bbbbbbbbbbbbbbbbbbbbbbbb", true);
    unknown(reconciled, null);
    jdbc.update(
        "update delivery_outbox set reconciled_result='NOT_CREATED',reconciled_by='staff',reconciled_at=now() where order_id=?",
        reconciled);
    var pending = repository.pending(BOUNDARY, 15);
    assertThat(pending).extracting(AlertRepository.Pending::orderId).containsExactly(open);
    assertThat(pending.getFirst().reason()).isEqualTo("Продажа: HTTP 404");
    assertThat(pending.getFirst().state()).isEqualTo("UNKNOWN");
    assertThat(pending.getFirst().number()).isPositive();
  }

  @Test
  void waitingOrdersAreReportedOnlyAfterTheBoundaryWithTheCause() {
    UUID stuck = order(BOUNDARY.minusSeconds(1), null, true);
    UUID blocked = order(BOUNDARY.minusSeconds(2), "bbbbbbbbbbbbbbbbbbbbbbbb", false);
    order(BOUNDARY.plusSeconds(1), null, true);
    var pending = repository.pending(BOUNDARY, 15);
    assertThat(pending)
        .extracting(AlertRepository.Pending::orderId)
        .containsExactly(blocked, stuck);
    assertThat(pending)
        .extracting(AlertRepository.Pending::reason)
        .containsExactly("партнёр заблокирован", "у партнёра нет карточки CloudShop");
    assertThat(pendingIds(null)).isEmpty();
  }

  @Test
  void alertedOrdersAreNotReportedAgainAndKeepTheirFirstTime() {
    UUID id = order(NOW, null, true);
    unknown(id, "reason");
    assertThat(repository.markAlerted(List.of(id), NOW)).isEqualTo(1);
    assertThat(repository.markAlerted(List.of(id), NOW.plusSeconds(60))).isZero();
    assertThat(pendingIds(BOUNDARY)).isEmpty();
    assertThat(
            jdbc.queryForObject("select alerted_at from delivery_outbox", Timestamp.class)
                .toInstant())
        .isEqualTo(NOW);
    assertThat(repository.markAlerted(List.of(), NOW)).isZero();
  }

  @Test
  void deliveredOrdersAreNeverReported() {
    UUID id = order(BOUNDARY.minus(Duration.ofHours(1)), "bbbbbbbbbbbbbbbbbbbbbbbb", true);
    jdbc.update(
        "update delivery_outbox set state='SENT',customer_id='bbbbbbbbbbbbbbbbbbbbbbbb',sale_id='cccccccccccccccccccccccc',sale_number=5 where order_id=?",
        id);
    assertThat(pendingIds(BOUNDARY)).isEmpty();
  }
}
