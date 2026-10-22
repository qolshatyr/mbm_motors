package kz.mbmmotors.cloudshop;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

final class RestClientCloudShopGateway implements CloudShopGateway {
  private static final DateTimeFormatter API_DATE =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
  private final RestClient client;
  private final String token;
  private final CloudShopProductParser parser = new CloudShopProductParser();

  RestClientCloudShopGateway(RestClient client, String token) {
    this.client = client;
    this.token = token;
  }

  RestClientCloudShopGateway(RestClient.Builder builder, CloudShopProperties properties) {
    URI base;
    try {
      base = URI.create(properties.baseUrl());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid CloudShop base URL");
    }
    boolean official =
        "https".equals(base.getScheme())
            && "connect-api.cloudshop.ru".equals(base.getHost())
            && (base.getPort() == -1 || base.getPort() == 443)
            && "/api/v4".equals(base.getPath());
    boolean local =
        "http".equals(base.getScheme())
            && ("localhost".equals(base.getHost()) || "127.0.0.1".equals(base.getHost()));
    if ((!official && !local)
        || base.getUserInfo() != null
        || base.getQuery() != null
        || base.getFragment() != null)
      throw new IllegalArgumentException(
          "CloudShop base URL must use the official HTTPS API or localhost");
    if (properties.timeout() == null
        || properties.timeout().isNegative()
        || properties.timeout().isZero())
      throw new IllegalArgumentException("CloudShop timeout must be positive");
    this.token = properties.token();
    HttpClient http =
        HttpClient.newBuilder()
            .connectTimeout(properties.timeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(properties.timeout());
    this.client = builder.baseUrl(base.toString()).requestFactory(factory).build();
  }

  @Override
  public CloudShopBatch products(int limit, int offset) {
    if (limit < 1 || limit > 1000 || offset < 0)
      throw new IllegalArgumentException("Invalid CloudShop pagination");
    return read(
        uri ->
            uri.path("/product").queryParam("limit", limit).queryParam("offset", offset).build());
  }

  @Override
  public CloudShopBatch updated(Instant since) {
    if (since == null)
      throw new IllegalArgumentException("CloudShop update start time is required");
    return read(
        uri ->
            uri.path("/product/updated").queryParam("start_time", API_DATE.format(since)).build());
  }

  @Override
  public List<String> deleted(Instant since) {
    if (since == null)
      throw new IllegalArgumentException("CloudShop deletion start time is required");
    return read(
        uri ->
            uri.path("/product/deleted").queryParam("start_time", API_DATE.format(since)).build(),
        parser::parseIds);
  }

  private CloudShopBatch read(Function<UriBuilder, URI> uri) {
    return read(uri, parser::parseBatch);
  }

  private <T> T read(Function<UriBuilder, URI> uri, Function<String, T> parse) {
    if (token == null || token.isBlank())
      throw new CloudShopException("CloudShop token is not configured");
    try {
      return client
          .get()
          .uri(uri)
          .header("X-CloudShop-API-Access-Token", token)
          .accept(MediaType.APPLICATION_JSON)
          .exchange(
              (request, response) -> {
                if (response.getStatusCode().value() != 200)
                  throw new CloudShopException(
                      "CloudShop GET failed with HTTP " + response.getStatusCode().value());
                String body =
                    new String(
                        response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                return parse.apply(body);
              });
    } catch (CloudShopException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new CloudShopException("CloudShop GET transport failed");
    }
  }
}
