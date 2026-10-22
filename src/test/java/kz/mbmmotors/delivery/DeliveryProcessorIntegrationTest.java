package kz.mbmmotors.delivery;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "MBM_TEST_DATABASE_URL", matches = ".*/mbm_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeliveryProcessorIntegrationTest {
  static final String CUSTOMER = "bbbbbbbbbbbbbbbbbbbbbbbb";
  static final String SALE = "cccccccccccccccccccccccc";
  static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
  final String schema = "delivery_" + UUID.randomUUID().toString().replace("-", "");
  JdbcTemplate jdbc;
  JdbcTemplate admin;
  DataSourceTransactionManager manager;
  DeliveryRepository repository;
  UUID id;
  Stub gateway;
  DeliveryProcessor processor;

  @BeforeAll
  void database() {
    var source =
        new DriverManagerDataSource(
            System.getenv("MBM_TEST_DATABASE_URL"),
            System.getenv().getOrDefault("MBM_TEST_DATABASE_USER", "mbm"),
            System.getenv().getOrDefault("MBM_TEST_DATABASE_PASSWORD", ""));
    admin = new JdbcTemplate(source);
    assertEquals("mbm_test", admin.queryForObject("select current_database()", String.class));
    Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
    var isolated =
        new DriverManagerDataSource(
            System.getenv("MBM_TEST_DATABASE_URL") + "?currentSchema=" + schema,
            source.getUsername(),
            source.getPassword());
    jdbc = new JdbcTemplate(isolated);
    manager = new DataSourceTransactionManager(isolated);
    repository = new DeliveryRepository(jdbc, manager);
  }

  @AfterAll
  void cleanup() {
    if (admin != null) admin.execute("drop schema if exists " + schema + " cascade");
  }

  @BeforeEach
  void fixture() {
    jdbc.execute("delete from local_order_items; delete from local_orders; delete from partners");
    id = UUID.randomUUID();
    jdbc.update(
        "insert into local_orders(id,idempotency_key,owner_id,request_hash,revision,status,store_id,store_name,customer_name,phone,comment,consent_at,created_at,total,total_text,item_count) values (?,?,?,repeat('a',64),repeat('b',64),'RECEIVED','aaaaaaaaaaaaaaaaaaaaaaaa','Synthetic store','Synthetic Buyer','+77000000000','Synthetic',now(),now(),5000.02,'5000.02',2)",
        id,
        UUID.randomUUID(),
        UUID.randomUUID());
    jdbc.update(
        "insert into local_order_items(order_id,position,product_id,product_name,sku,unit,quantity,price,line_total) values (?,0,'dddddddddddddddddddddddd','Synthetic part','DEMO','шт',2,2500.01,5000.02)",
        id);
    jdbc.update(
        "insert into delivery_outbox(order_id,state,created_at) values (?,'WAITING_CONFIGURATION',now())",
        id);
    gateway = new Stub();
    processor = new DeliveryProcessor(repository, gateway, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  String state() {
    return jdbc.queryForObject(
        "select state from delivery_outbox where order_id=?", String.class, id);
  }

  void ready() {
    assertTrue(processor.prepare(id));
    assertFalse(processor.prepare(id));
  }

  @Test
  void explicitPreparationSuccessAndImmutableSnapshot() {
    processor.process(id);
    assertEquals("WAITING_CONFIGURATION", state());
    assertEquals(0, gateway.customers.get());
    ready();
    processor.process(id);
    processor.process(id);
    assertEquals("SENT", state());
    assertEquals(1, gateway.customers.get());
    assertEquals(1, gateway.sales.get());
    assertEquals(
        CUSTOMER, jdbc.queryForObject("select customer_id from delivery_outbox", String.class));
    assertEquals(SALE, jdbc.queryForObject("select sale_id from delivery_outbox", String.class));
    assertEquals(123L, jdbc.queryForObject("select sale_number from delivery_outbox", Long.class));
    assertEquals("RECEIVED", jdbc.queryForObject("select status from local_orders", String.class));
    assertEquals("2500.01", gateway.command.snapshot().items().getFirst().price().toPlainString());
    assertEquals(NOW, gateway.command.date());
    assertFalse(gateway.command.status());
    assertThrows(
        UnsupportedOperationException.class, () -> gateway.command.snapshot().items().clear());
  }

  @Test
  void parallelClaimsMakeOnlyOneCustomerAndSale() throws Exception {
    ready();
    gateway.entered = new CountDownLatch(1);
    gateway.release = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> processor.process(id));
      assertTrue(gateway.entered.await(5, TimeUnit.SECONDS));
      var second = executor.submit(() -> processor.process(id));
      second.get(5, TimeUnit.SECONDS);
      assertEquals("CUSTOMER_IN_FLIGHT", state());
      gateway.release.countDown();
      first.get(5, TimeUnit.SECONDS);
    }
    assertEquals("SENT", state());
    assertEquals(1, gateway.customers.get());
    assertEquals(1, gateway.sales.get());
  }

  void partnerOrder(String customerId, boolean enabled) {
    UUID partner = UUID.randomUUID();
    jdbc.update(
        "insert into partners(id,login,password_hash,company,contact_name,phone,cloudshop_customer_id,enabled,created_at) values (?,?,?,'Synthetic Partner','Synthetic Buyer','+77000000000',?,?,now())",
        partner,
        "p" + partner.toString().substring(0, 8),
        "$2a$10$" + "a".repeat(53),
        customerId,
        enabled);
    jdbc.update("update local_orders set partner_id=? where id=?", partner, id);
  }

  @Test
  void partnerOrdersUseTheLinkedCardAndSkipCustomerCreation() {
    assertEquals(java.util.List.of(), processor.deliverable(10));
    partnerOrder(CUSTOMER, true);
    assertEquals(java.util.List.of(id), processor.deliverable(10));
    processor.deliverPartnerOrder(id);
    processor.deliverPartnerOrder(id);
    assertEquals("SENT", state());
    assertEquals(0, gateway.customers.get());
    assertEquals(1, gateway.sales.get());
    assertEquals(CUSTOMER, gateway.command.customerId());
    assertEquals(java.util.List.of(), processor.deliverable(10));
  }

  @Test
  void partnersWithoutCardOrDisabledAreNotDelivered() {
    partnerOrder(null, true);
    assertEquals(java.util.List.of(), processor.deliverable(10));
    processor.deliverPartnerOrder(id);
    assertEquals("WAITING_CONFIGURATION", state());
    jdbc.update("update partners set cloudshop_customer_id=?, enabled=false", CUSTOMER);
    assertEquals(java.util.List.of(), processor.deliverable(10));
    processor.deliverPartnerOrder(id);
    assertEquals("WAITING_CONFIGURATION", state());
    assertEquals(0, gateway.sales.get());
  }

  @Test
  void lostSaleAnswerForPartnerOrderBecomesUnknownOnce() {
    partnerOrder(CUSTOMER, true);
    gateway.saleFailure = true;
    processor.deliverPartnerOrder(id);
    processor.deliverPartnerOrder(id);
    assertEquals("UNKNOWN", state());
    assertEquals(1, gateway.sales.get());
    assertEquals(java.util.List.of(), processor.deliverable(10));
    assertTrue(
        jdbc.queryForObject("select failure_reason from delivery_outbox", String.class)
            .startsWith("Продажа: "));
  }

  @Test
  void lostCustomerAnswerNeverRetries() {
    ready();
    gateway.customerFailure = true;
    processor.process(id);
    processor.process(id);
    assertEquals("UNKNOWN", state());
    assertEquals(1, gateway.customers.get());
    assertEquals(0, gateway.sales.get());
    assertFalse(processor.prepare(id));
  }

  @Test
  void lostSaleAnswerKeepsCustomerAndNeverRetries() {
    ready();
    gateway.saleFailure = true;
    processor.process(id);
    processor.process(id);
    assertEquals("UNKNOWN", state());
    assertEquals(
        CUSTOMER, jdbc.queryForObject("select customer_id from delivery_outbox", String.class));
    assertEquals(1, gateway.customers.get());
    assertEquals(1, gateway.sales.get());
  }

  @Test
  void confirmedCustomerResumesOnlySaleAfterRestart() {
    jdbc.update("update delivery_outbox set state='CUSTOMER_CREATED',customer_id=?", CUSTOMER);
    processor.process(id);
    assertEquals("SENT", state());
    assertEquals(0, gateway.customers.get());
    assertEquals(1, gateway.sales.get());
  }

  @Test
  void orphanAttemptsBecomeUnknownAndDoNotRetry() {
    for (String flight : new String[] {"CUSTOMER_IN_FLIGHT", "SALE_IN_FLIGHT"}) {
      jdbc.update(
          "update delivery_outbox set state=?,customer_id=?,updated_at=?",
          flight,
          flight.startsWith("SALE") ? CUSTOMER : null,
          java.sql.Timestamp.from(NOW.minusSeconds(60)));
      processor.process(id);
      assertEquals(flight, state());
      assertEquals(1, processor.staleInFlight(NOW.minusSeconds(30)));
      assertEquals("UNKNOWN", state());
      assertTrue(
          jdbc.queryForObject("select failure_reason from delivery_outbox", String.class)
              .startsWith("Попытка прервана"));
      processor.process(id);
    }
    assertEquals(0, gateway.customers.get());
    assertEquals(0, gateway.sales.get());
    assertThrows(IllegalArgumentException.class, () -> processor.staleInFlight(NOW.plusSeconds(1)));
  }

  @Test
  void recoveryDoesNotOverwriteFreshAttempt() {
    jdbc.update(
        "update delivery_outbox set state='CUSTOMER_IN_FLIGHT',updated_at=?",
        java.sql.Timestamp.from(NOW));
    assertEquals(0, processor.staleInFlight(NOW));
    assertEquals("CUSTOMER_IN_FLIGHT", state());
  }

  @Test
  void lateCustomerResponseCannotOverwriteUnknownOrCreateSale() throws Exception {
    ready();
    gateway.entered = new CountDownLatch(1);
    gateway.release = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var future = executor.submit(() -> processor.process(id));
      assertTrue(gateway.entered.await(5, TimeUnit.SECONDS));
      jdbc.update(
          "update delivery_outbox set updated_at=?", java.sql.Timestamp.from(NOW.minusSeconds(60)));
      assertEquals(1, processor.staleInFlight(NOW));
      gateway.release.countDown();
      future.get(5, TimeUnit.SECONDS);
    }
    assertEquals("UNKNOWN", state());
    assertNull(jdbc.queryForObject("select customer_id from delivery_outbox", String.class));
    assertEquals(0, gateway.sales.get());
  }

  @Test
  void lateSaleResponseCannotOverwriteUnknown() throws Exception {
    jdbc.update("update delivery_outbox set state='CUSTOMER_CREATED',customer_id=?", CUSTOMER);
    var saleEntered = new CountDownLatch(1);
    var saleRelease = new CountDownLatch(1);
    gateway =
        new Stub() {
          @Override
          public SaleResult createSale(SaleCommand command) {
            saleEntered.countDown();
            try {
              assertTrue(saleRelease.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
              throw new IllegalStateException(e);
            }
            return super.createSale(command);
          }
        };
    processor = new DeliveryProcessor(repository, gateway, Clock.fixed(NOW, ZoneOffset.UTC));
    try (var executor = Executors.newSingleThreadExecutor()) {
      var future = executor.submit(() -> processor.process(id));
      assertTrue(saleEntered.await(5, TimeUnit.SECONDS));
      jdbc.update(
          "update delivery_outbox set updated_at=?", java.sql.Timestamp.from(NOW.minusSeconds(60)));
      assertEquals(1, processor.staleInFlight(NOW));
      saleRelease.countDown();
      future.get(5, TimeUnit.SECONDS);
    }
    assertEquals("UNKNOWN", state());
    assertEquals(
        CUSTOMER, jdbc.queryForObject("select customer_id from delivery_outbox", String.class));
    assertNull(jdbc.queryForObject("select sale_id from delivery_outbox", String.class));
  }

  @Test
  void failedCustomerCheckpointCannotStartSale() {
    ready();
    jdbc.execute(
        "create function fail_checkpoint() returns trigger language plpgsql as $$ begin if NEW.state='CUSTOMER_CREATED' then raise exception 'Synthetic checkpoint failure'; end if; return NEW; end $$");
    jdbc.execute(
        "create trigger fail_checkpoint before update on delivery_outbox for each row execute function fail_checkpoint()");
    try {
      processor.process(id);
      assertEquals("UNKNOWN", state());
      assertEquals(1, gateway.customers.get());
      assertEquals(0, gateway.sales.get());
      assertNull(jdbc.queryForObject("select customer_id from delivery_outbox", String.class));
      processor.process(id);
      assertEquals(1, gateway.customers.get());
    } finally {
      jdbc.execute(
          "drop trigger fail_checkpoint on delivery_outbox; drop function fail_checkpoint()");
    }
  }

  @Test
  void invalidSnapshotMakesNoExternalCall() {
    ready();
    jdbc.update("update local_order_items set unit='' where order_id=?", id);
    processor.process(id);
    assertEquals("UNKNOWN", state());
    assertEquals(0, gateway.customers.get());
    assertEquals(0, gateway.sales.get());
  }

  @Test
  void malformedCustomerAndSaleResponsesAreUnknown() {
    ready();
    gateway.invalidCustomer = true;
    processor.process(id);
    assertEquals("UNKNOWN", state());
    assertEquals(0, gateway.sales.get());
    jdbc.update("update delivery_outbox set state='CUSTOMER_CREATED',customer_id=?", CUSTOMER);
    gateway.invalidSale = true;
    processor.process(id);
    assertEquals("UNKNOWN", state());
    assertNull(jdbc.queryForObject("select sale_id from delivery_outbox", String.class));
  }

  @Test
  void ambientTransactionRejectedBeforeClaim() {
    ready();
    new TransactionTemplate(manager)
        .executeWithoutResult(
            tx -> assertThrows(IllegalStateException.class, () -> processor.process(id)));
    assertEquals("READY", state());
    assertEquals(0, gateway.customers.get());
  }

  static class Stub implements DeliveryGateway {
    final AtomicInteger customers = new AtomicInteger();
    final AtomicInteger sales = new AtomicInteger();
    boolean customerFailure, saleFailure, invalidCustomer, invalidSale;
    CountDownLatch entered, release;
    SaleCommand command;

    public CustomerResult createCustomer(DeliverySnapshot snapshot) {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      customers.incrementAndGet();
      if (entered != null) {
        entered.countDown();
        try {
          if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
      }
      if (customerFailure) throw new IllegalStateException("Synthetic lost response");
      return new CustomerResult(invalidCustomer ? "" : CUSTOMER);
    }

    public SaleResult createSale(SaleCommand command) {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      this.command = command;
      sales.incrementAndGet();
      if (saleFailure) throw new IllegalStateException("Synthetic lost response");
      return new SaleResult(SALE, invalidSale ? 0 : 123);
    }
  }
}
