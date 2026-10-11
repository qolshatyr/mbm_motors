package kz.mbmmotors.delivery;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Real PostgreSQL + real HTTP; external writes cannot be enabled through this test. */
@EnabledIfEnvironmentVariable(named = "MBM_TEST_DATABASE_URL", matches = ".*/mbm_test")
@EnabledIfEnvironmentVariable(
    named = "MBM_DELIVERY_MOCK_URL",
    matches = "http://127\\.0\\.0\\.1:4900/api/v4")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CloudShopDeliverySimulationTest {
  final String schema = "http_delivery_" + UUID.randomUUID().toString().replace("-", "");
  final HttpClient http =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  final JsonMapper json = JsonMapper.builder().build();
  JdbcTemplate admin, jdbc;
  DeliveryRepository repository;
  DeliveryProcessor processor;
  UUID id;

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
    repository = new DeliveryRepository(jdbc, new DataSourceTransactionManager(isolated));
    processor =
        new DeliveryProcessor(
            repository,
            new CloudShopDeliveryGateway(
                RestClient.builder(),
                URI.create(System.getenv("MBM_DELIVERY_MOCK_URL")),
                "synthetic-local-only",
                Duration.ofSeconds(2)),
            Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC));
  }

  @AfterAll
  void cleanup() {
    if (admin != null) admin.execute("drop schema if exists " + schema + " cascade");
  }

  void fixture(String fault) throws Exception {
    var response =
        http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:4900/__test__/reset"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"fault\":\"" + fault + "\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    jdbc.execute("delete from local_order_items; delete from local_orders");
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
  }

  JsonNode state() throws Exception {
    var response =
        http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:4900/__test__/state"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    return json.readTree(response.body());
  }

  String deliveryState() {
    return jdbc.queryForObject(
        "select state from delivery_outbox where order_id=?", String.class, id);
  }

  @Test
  void confirmedHttpFlowIsSentOnce() throws Exception {
    fixture("");
    processor.process(id);
    assertEquals(0, state().path("customers").size());
    assertTrue(processor.prepare(id));
    processor.process(id);
    processor.process(id);
    assertEquals("SENT", deliveryState());
    var result = state();
    assertEquals(1, result.path("customers").size());
    assertEquals(1, result.path("sales").size());
    assertFalse(result.path("sales").get(0).path("status").asBoolean());
    assertFalse(result.path("sales").get(0).has("payment_details"));
    assertEquals(
        1L,
        jdbc.queryForObject(
            "select sale_number from delivery_outbox where order_id=?", Long.class, id));
  }

  @Test
  void lostCustomerResponseStopsBeforeSaleAndNeverRepeats() throws Exception {
    fixture("customer-lost");
    processor.prepare(id);
    processor.process(id);
    processor.process(id);
    assertEquals("UNKNOWN", deliveryState());
    assertEquals(1, state().path("customers").size());
    assertEquals(0, state().path("sales").size());
    assertNull(repository.customerId(id));
  }

  @Test
  void lostSaleResponseKeepsCustomerCheckpointAndNeverRepeats() throws Exception {
    fixture("sale-lost");
    processor.prepare(id);
    processor.process(id);
    processor.process(id);
    assertEquals("UNKNOWN", deliveryState());
    assertEquals(1, state().path("customers").size());
    assertEquals(1, state().path("sales").size());
    assertNotNull(repository.customerId(id));
    assertNull(
        jdbc.queryForObject(
            "select sale_id from delivery_outbox where order_id=?", String.class, id));
  }
}
