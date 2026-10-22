package kz.mbmmotors.cloudshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientCloudShopGatewayTest {
  private final RestClient.Builder builder =
      RestClient.builder().baseUrl("https://connect-api.cloudshop.ru/api/v4");
  private final CloudShopProperties properties =
      new CloudShopProperties(
          "https://connect-api.cloudshop.ru/api/v4", "local-test-token", Duration.ofSeconds(30));
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final CloudShopGateway gateway =
      new RestClientCloudShopGateway(builder.build(), properties.token());

  @Test
  void requestsOnlyGetWithPaginationAndAccessHeader() {
    server
        .expect(requestTo("https://connect-api.cloudshop.ru/api/v4/product?limit=1000&offset=2000"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("X-CloudShop-API-Access-Token", "local-test-token"))
        .andExpect(header("Accept", "application/json"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    assertThat(gateway.products(1000, 2000).products()).isEmpty();
    server.verify();
  }

  @Test
  void formatsUpdateStartTimeAsUtcSeconds() {
    server
        .expect(
            requestTo(
                "https://connect-api.cloudshop.ru/api/v4/product/updated?start_time=2026-10-04%2010:15:46"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    assertThat(gateway.updated(Instant.parse("2026-10-04T10:15:46.123Z")).products()).isEmpty();
    server.verify();
  }

  @Test
  void readsOnlyIdsOfDeletedProducts() {
    // Deleted cards may carry fields the product parser rejects, such as null store prices.
    server
        .expect(
            requestTo(
                "https://connect-api.cloudshop.ru/api/v4/product/deleted?start_time=2026-10-04%2010:15:46"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "[{\"id\":\"6aa155719b6c1d0d46466561\",\"store_prices\":{\"x\":null}},"
                    + "{\"id\":\"6aa155719b6c1d0d46466561\"}]",
                MediaType.APPLICATION_JSON));
    assertThat(gateway.deleted(Instant.parse("2026-10-04T10:15:46.123Z")))
        .containsExactly("6aa155719b6c1d0d46466561");
    server.verify();
  }

  @Test
  void rejectsDeletedProductsWithoutValidIds() {
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("/product/deleted")))
        .andRespond(withSuccess("[{\"id\":\"bad\"}]", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> gateway.deleted(Instant.parse("2026-10-04T10:15:46Z")))
        .isInstanceOf(CloudShopException.class);
  }

  @Test
  void rejectsRedirectsAndDoesNotRetryOrLeakBody() {
    server
        .expect(requestTo("https://connect-api.cloudshop.ru/api/v4/product?limit=1000&offset=0"))
        .andRespond(
            withStatus(HttpStatus.FOUND)
                .header("Location", "https://example.org")
                .body("local-test-token private response"));
    assertThatThrownBy(() -> gateway.products(1000, 0))
        .isInstanceOf(CloudShopException.class)
        .hasMessage("CloudShop GET failed with HTTP 302")
        .hasNoCause();
    server.verify();
  }

  @Test
  void failsBeforeHttpWhenTokenIsMissingAndRejectsUntrustedHosts() {
    CloudShopGateway missing =
        new RestClientCloudShopGateway(
            RestClient.builder(),
            new CloudShopProperties(properties.baseUrl(), "", properties.timeout()));
    assertThatThrownBy(() -> missing.products(1000, 0))
        .hasMessage("CloudShop token is not configured");
    assertThatThrownBy(
            () ->
                new RestClientCloudShopGateway(
                    RestClient.builder(),
                    new CloudShopProperties(
                        "https://example.org/api/v4", "token", properties.timeout())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(properties.toString()).doesNotContain("local-test-token");
  }
}
