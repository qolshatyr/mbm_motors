package kz.mbmmotors.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import kz.mbmmotors.cloudshop.CloudShopException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CloudShopDeliveryGatewayTest {
  private static final String ID = "bbbbbbbbbbbbbbbbbbbbbbbb";
  private static final String TOKEN = "synthetic-http-test-token";
  private final JsonMapper mapper = JsonMapper.builder().build();
  private HttpServer server;
  private java.util.concurrent.ExecutorService executor;
  private URI base;
  private final AtomicInteger calls = new AtomicInteger();
  private String received;
  private String auth;
  private String method;
  private String contentType;
  private volatile int status = 201;
  private volatile String reply = "{\"id\":\"" + ID + "\"}";
  private volatile boolean stall;
  private volatile boolean drop;
  private volatile boolean partial;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    server.createContext("/api/v4/customers", this::respond);
    server.createContext("/api/v4/sales", this::respond);
    server.start();
    base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v4");
  }

  @AfterEach
  void stop() {
    server.stop(0);
    executor.shutdownNow();
  }

  private void respond(HttpExchange exchange) throws IOException {
    calls.incrementAndGet();
    received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    auth = exchange.getRequestHeaders().getFirst("X-CloudShop-API-Access-Token");
    method = exchange.getRequestMethod();
    contentType = exchange.getRequestHeaders().getFirst("Content-Type");
    try {
      if (drop) return;
      if (stall) Thread.sleep(500);
      byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.getResponseHeaders().set("Location", base + "/customers");
      exchange.sendResponseHeaders(status, bytes.length);
      if (partial) {
        exchange.getResponseBody().write(bytes, 0, 1);
        exchange.getResponseBody().flush();
        Thread.sleep(500);
      } else exchange.getResponseBody().write(bytes);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    } finally {
      exchange.close();
    }
  }

  private CloudShopDeliveryGateway gateway(Duration timeout) {
    return new CloudShopDeliveryGateway(RestClient.builder(), base, TOKEN, timeout);
  }

  private DeliverySnapshot snapshot() {
    return new DeliverySnapshot(
        UUID.fromString("a1111111-1111-1111-1111-111111111111"),
        1,
        "aaaaaaaaaaaaaaaaaaaaaaaa",
        "Синтетический покупатель",
        "+70000000000",
        "Позвонить",
        new BigDecimal("2469.12"),
        2,
        List.of(
            new DeliverySnapshot.Item(
                "dddddddddddddddddddddddd",
                "Деталь",
                "SKU",
                "шт",
                2,
                new BigDecimal("1234.56"),
                new BigDecimal("2469.12"))));
  }

  private SaleCommand command() {
    return new SaleCommand(snapshot(), ID, Instant.parse("2026-10-12T01:02:03Z"));
  }

  @Test
  void customerPayloadUsesSeparatePersonCardAndStrict201() {
    assertThat(gateway(Duration.ofSeconds(2)).createCustomer(snapshot()).id()).isEqualTo(ID);
    JsonNode body = mapper.readTree(received);
    assertThat(body.size()).isEqualTo(3);
    assertThat(body.get("type").stringValue()).isEqualTo("person");
    assertThat(body.get("name").stringValue()).isEqualTo(snapshot().customerName());
    assertThat(body.get("phones").get(0).stringValue()).isEqualTo(snapshot().phone());
    assertThat(method).isEqualTo("POST");
    assertThat(auth).isEqualTo(TOKEN);
    assertThat(contentType).startsWith("application/json");
    assertThat(calls.get()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 201})
  void salePayloadIsUnpostedWithExactPricesUtcAndCorrelation(int success) {
    status = success;
    reply = "{\"id\":\"" + ID + "\",\"number\":9223372036854775807}";
    var result = gateway(Duration.ofSeconds(2)).createSale(command());
    assertThat(result.number()).isEqualTo(Long.MAX_VALUE);
    JsonNode body = mapper.readTree(received);
    assertThat(body.get("status").booleanValue()).isFalse();
    assertThat(body.get("date").stringValue()).isEqualTo("2026-10-12 01:02:03");
    assertThat(body.get("customer_id").stringValue()).isEqualTo(ID);
    assertThat(body.get("store_id").stringValue()).isEqualTo(snapshot().storeId());
    assertThat(body.get("discount_sum").intValue()).isZero();
    assertThat(body.get("discount_percent").intValue()).isZero();
    assertThat(body.has("payment_details")).isFalse();
    assertThat(body.get("comment").stringValue())
        .isEqualTo(
            "Заявка MBM-"
                + snapshot().number()
                + " с сайта партнёров\nКонтакт: "
                + snapshot().customerName()
                + ", "
                + snapshot().phone()
                + "\nПозвонить\nКлюч: "
                + snapshot().orderId());
    var item = body.get("products").get(0);
    assertThat(item.get("product_id").stringValue())
        .isEqualTo(snapshot().items().getFirst().productId());
    assertThat(item.get("quantity").intValue()).isEqualTo(2);
    assertThat(item.get("unit").stringValue()).isEqualTo("шт");
    assertThat(item.get("price").isNumber()).isTrue();
    assertThat(item.get("price").decimalValue()).isEqualByComparingTo("1234.56");
    assertThat(item.get("discount_percent").intValue()).isZero();
    // The order contact travels only inside the comment, not as separate customer fields.
    assertThat(received).contains("1234.56");
    assertThat(body.has("phone")).isFalse();
    assertThat(auth).isEqualTo(TOKEN);
    assertThat(calls.get()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 202, 301, 302, 307, 308, 400, 401, 403, 429, 500})
  void customerRejectsOtherStatusesWithoutFollowingOrRetrying(int responseStatus) {
    status = responseStatus;
    reply = TOKEN + snapshot().phone() + "x".repeat(100_000);
    assertSanitized(() -> gateway(Duration.ofSeconds(2)).createCustomer(snapshot()));
    assertThat(calls.get()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "[]",
        "null",
        "broken",
        "{\"id\":123}",
        "{\"id\":\"BBBBBBBBBBBBBBBBBBBBBBBB\"}",
        "{\"data\":{\"id\":\"bbbbbbbbbbbbbbbbbbbbbbbb\"}}"
      })
  void malformedCustomerReplyFailsClosed(String response) {
    reply = response;
    assertSanitized(() -> gateway(Duration.ofSeconds(2)).createCustomer(snapshot()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "0", "-1", "1.0", "1.5", "\"1\"", "9223372036854775808"})
  void invalidSaleNumberFailsClosed(String number) {
    reply = "{\"id\":\"" + ID + "\",\"number\":" + number + "}";
    assertSanitized(() -> gateway(Duration.ofSeconds(2)).createSale(command()));
  }

  @Test
  void oversizedSuccessReplyIsRejected() {
    reply = "{\"id\":\"" + ID + "\",\"padding\":\"" + "x".repeat(65_536) + "\"}";
    assertSanitized(() -> gateway(Duration.ofSeconds(2)).createCustomer(snapshot()));
  }

  @Test
  void duplicateFieldsAndTrailingJsonFailClosed() {
    for (String response :
        List.of("{\"id\":\"" + ID + "\",\"id\":\"" + ID + "\"}", "{\"id\":\"" + ID + "\"} {}")) {
      reply = response;
      assertSanitized(() -> gateway(Duration.ofSeconds(2)).createCustomer(snapshot()));
    }
  }

  @Test
  void missingSaleNumberIsRejected() {
    assertSanitized(() -> gateway(Duration.ofSeconds(2)).createSale(command()));
  }

  @Test
  void lostResponseDoesNotRetry() {
    drop = true;
    assertSanitized(() -> gateway(Duration.ofSeconds(2)).createCustomer(snapshot()));
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void headersTimeoutDoesNotRetry() {
    stall = true;
    assertSanitized(() -> gateway(Duration.ofMillis(150)).createCustomer(snapshot()));
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void incompleteBodyTimesOutWithoutRetry() {
    partial = true;
    assertSanitized(() -> gateway(Duration.ofMillis(150)).createCustomer(snapshot()));
    assertThat(calls.get()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://example.com/api/v4",
        "https://connect-api.cloudshop.ru.evil/api/v4",
        "http://127.0.0.1/api/v4",
        "http://127.0.0.1:80/other",
        "http://127.0.0.1:80/api/v4/",
        "http://user@127.0.0.1:80/api/v4",
        "http://127.0.0.1:80/api/v4?token=x",
        "http://127.0.0.1:80/api/v4#x",
        "http://127.0.0.1:80/api/%76%34",
        "https://connect-api.cloudshop.ru:444/api/v4"
      })
  void rejectsUnsafeUrlsBeforeAnyRequest(String url) {
    assertThatThrownBy(
            () ->
                new CloudShopDeliveryGateway(
                    RestClient.builder(), URI.create(url), TOKEN, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(calls.get()).isZero();
  }

  @Test
  void missingTokenAndInvalidTimeoutFailBeforeHttp() {
    for (String token : List.of("", " ", "secret\nheader")) {
      assertThatThrownBy(
              () ->
                  new CloudShopDeliveryGateway(
                      RestClient.builder(), base, token, Duration.ofSeconds(1)))
          .isInstanceOf(IllegalArgumentException.class);
    }
    for (Duration timeout : List.of(Duration.ZERO, Duration.ofMillis(-1), Duration.ofMinutes(3))) {
      assertThatThrownBy(
              () -> new CloudShopDeliveryGateway(RestClient.builder(), base, TOKEN, timeout))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(calls.get()).isZero();
  }

  @Test
  void officialConstructorRemainsInertAndNullConfigurationIsRejected() {
    new CloudShopDeliveryGateway(
        RestClient.builder(),
        URI.create("https://connect-api.cloudshop.ru/api/v4"),
        TOKEN,
        Duration.ofSeconds(30));
    assertThatThrownBy(
            () ->
                new CloudShopDeliveryGateway(
                    RestClient.builder(), null, TOKEN, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new CloudShopDeliveryGateway(
                    RestClient.builder(), base, null, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CloudShopDeliveryGateway(RestClient.builder(), base, TOKEN, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(calls.get()).isZero();
  }

  @Test
  void invalidSnapshotIsRejectedBeforeBothPosts() {
    var valid = snapshot();
    var invalid =
        new DeliverySnapshot(
            valid.orderId(),
            1,
            valid.storeId(),
            valid.customerName(),
            valid.phone(),
            valid.comment(),
            BigDecimal.ONE,
            valid.itemCount(),
            valid.items());
    var gateway = gateway(Duration.ofSeconds(2));
    assertThatThrownBy(() -> gateway.createCustomer(invalid))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> gateway.createSale(new SaleCommand(invalid, ID, command().date())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(calls.get()).isZero();
  }

  private void assertSanitized(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
    assertThatThrownBy(action)
        .isInstanceOf(CloudShopException.class)
        .hasNoCause()
        .hasMessageNotContaining(TOKEN)
        .hasMessageNotContaining(snapshot().phone());
  }
}
