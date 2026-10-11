package kz.mbmmotors.delivery;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import kz.mbmmotors.cloudshop.CloudShopException;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Explicitly constructed only. This adapter does not authorize or schedule live delivery. */
public final class CloudShopDeliveryGateway implements DeliveryGateway {
  private static final int MAX_RESPONSE_BYTES = 64 * 1024;
  private static final DateTimeFormatter API_DATE =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
  private final RestClient client;
  private final JsonMapper mapper =
      JsonMapper.builder()
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  public CloudShopDeliveryGateway(
      RestClient.Builder builder, URI base, String token, Duration timeout) {
    boolean official =
        base != null
            && "https".equals(base.getScheme())
            && "connect-api.cloudshop.ru".equals(base.getHost())
            && (base.getPort() == -1 || base.getPort() == 443);
    boolean local =
        base != null
            && "http".equals(base.getScheme())
            && ("localhost".equals(base.getHost()) || "127.0.0.1".equals(base.getHost()))
            && base.getPort() > 0
            && base.getPort() <= 65535;
    if ((!official && !local)
        || !"/api/v4".equals(base.getRawPath())
        || base.getRawUserInfo() != null
        || base.getRawQuery() != null
        || base.getRawFragment() != null)
      throw new IllegalArgumentException("Invalid CloudShop delivery base URL");
    if (builder == null
        || timeout == null
        || timeout.compareTo(Duration.ofMillis(1)) < 0
        || timeout.compareTo(Duration.ofMinutes(2)) > 0)
      throw new IllegalArgumentException("CloudShop delivery timeout must be 1 ms to 2 minutes");
    if (token == null || token.isBlank() || token.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("CloudShop delivery token is required");
    var http =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(timeout);
    client = builder.clone().baseUrl(base.toString()).requestFactory(factory).build();
    this.token = token;
  }

  private final String token;

  @Override
  public CustomerResult createCustomer(DeliverySnapshot snapshot) {
    snapshot.validate();
    JsonNode result =
        post(
            "/customers",
            Map.of(
                "type",
                "person",
                "name",
                snapshot.customerName(),
                "phones",
                List.of(snapshot.phone())),
            false);
    return new CustomerResult(id(result));
  }

  @Override
  public SaleResult createSale(SaleCommand command) {
    DeliverySnapshot snapshot = command.snapshot();
    snapshot.validate();
    var products =
        snapshot.items().stream()
            .map(
                item ->
                    Map.<String, Object>of(
                        "product_id",
                        item.productId(),
                        "quantity",
                        item.quantity(),
                        "unit",
                        item.unit(),
                        "price",
                        item.price(),
                        "discount_percent",
                        0))
            .toList();
    String comment =
        "MBM-"
            + snapshot.orderId()
            + (snapshot.comment().isBlank() ? "" : "\n" + snapshot.comment());
    JsonNode result =
        post(
            "/sales",
            Map.of(
                "status",
                command.status(),
                "store_id",
                snapshot.storeId(),
                "customer_id",
                command.customerId(),
                "date",
                API_DATE.format(command.date()),
                "discount_percent",
                0,
                "discount_sum",
                0,
                "products",
                products,
                "comment",
                comment),
            true);
    String id = id(result);
    JsonNode number = result.get("number");
    if (number == null
        || !number.isIntegralNumber()
        || !number.canConvertToLong()
        || number.longValue() <= 0)
      throw new CloudShopException("CloudShop delivery response has an invalid sale number");
    return new SaleResult(id, number.longValue());
  }

  private JsonNode post(String path, Object body, boolean sale) {
    try {
      return client
          .post()
          .uri(path)
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .header("X-CloudShop-API-Access-Token", token)
          .body(mapper.writeValueAsBytes(body))
          .exchange(
              (request, response) -> {
                int status = response.getStatusCode().value();
                if (status != 201 && !(sale && status == 200))
                  throw new CloudShopException("CloudShop delivery failed with HTTP " + status);
                byte[] bytes = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES)
                  throw new CloudShopException("CloudShop delivery response exceeds size limit");
                JsonNode result = mapper.readTree(bytes);
                if (result == null || !result.isObject())
                  throw new CloudShopException("CloudShop delivery response is invalid");
                return result;
              });
    } catch (CloudShopException exception) {
      throw exception;
    } catch (Exception exception) {
      // Neither causes nor bodies are retained: they can contain tokens and customer data.
      throw new CloudShopException("CloudShop delivery transport or response failed");
    }
  }

  private String id(JsonNode result) {
    JsonNode id = result.get("id");
    if (id == null || !id.isString() || !id.stringValue().matches("[a-f0-9]{24}"))
      throw new CloudShopException("CloudShop delivery response has an invalid identifier");
    return id.stringValue();
  }
}
