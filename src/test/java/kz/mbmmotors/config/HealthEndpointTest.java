package kz.mbmmotors.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import kz.mbmmotors.Application;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** The uptime monitor sees UP or DOWN without signing in, and nothing else of the actuator. */
@EnabledIfEnvironmentVariable(named = "MBM_TEST_DATABASE_URL", matches = ".*/mbm_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HealthEndpointTest {
  private ConfigurableApplicationContext context;
  private final HttpClient http = HttpClient.newHttpClient();
  private String base;

  @BeforeAll
  void start() {
    context =
        new SpringApplicationBuilder(Application.class)
            .run(
                "--server.port=0",
                "--spring.datasource.url=" + System.getenv("MBM_TEST_DATABASE_URL"),
                "--spring.datasource.username="
                    + System.getenv().getOrDefault("MBM_TEST_DATABASE_USER", "mbm"),
                "--spring.datasource.password="
                    + System.getenv().getOrDefault("MBM_TEST_DATABASE_PASSWORD", ""));
    base = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
  }

  @AfterAll
  void stop() {
    if (context != null) context.close();
  }

  private HttpResponse<String> get(String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void healthIsPublicAndShowsOnlyTheStatus() throws Exception {
    var response = get("/actuator/health");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/actuator",
        "/actuator/env",
        "/actuator/health/db",
        "/actuator/beans",
        "/actuator/info"
      })
  void otherActuatorEndpointsStayClosed(String path) throws Exception {
    var response = get(path);
    assertThat(response.statusCode()).isNotEqualTo(200);
    assertThat(response.body())
        .doesNotContain("spring.datasource")
        .doesNotContain("\"components\"");
  }
}
