package kz.mbmmotors.admin;

import static org.assertj.core.api.Assertions.assertThat;

import kz.mbmmotors.Application;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

@EnabledIfEnvironmentVariable(named = "MBM_TEST_DATABASE_URL", matches = ".*/mbm_test")
class DeliveryEnabledConfigurationTest {
  private static String[] settings(String token) {
    return new String[] {
      "--spring.datasource.url=" + System.getenv("MBM_TEST_DATABASE_URL"),
      "--spring.datasource.username="
          + System.getenv().getOrDefault("MBM_TEST_DATABASE_USER", "mbm"),
      "--spring.datasource.password="
          + System.getenv().getOrDefault("MBM_TEST_DATABASE_PASSWORD", ""),
      "--delivery.enabled=true",
      // Long delays keep the worker from contacting anything during the test.
      "--delivery.interval=1h",
      "--cloudshop.base-url=http://127.0.0.1:9/api/v4",
      "--cloudshop.token=" + token
    };
  }

  @Test
  void enabledDeliveryStartsTheWorker() {
    try (var context =
        new SpringApplicationBuilder(Application.class)
            .web(WebApplicationType.NONE)
            .run(settings("synthetic-token"))) {
      assertThat(context.containsBean("deliveryWorker")).isTrue();
    }
  }

  @Test
  void enabledDeliveryWithoutTokenFailsAtStartup() {
    var builder = new SpringApplicationBuilder(Application.class).web(WebApplicationType.NONE);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> builder.run(settings("")))
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .rootCause()
        .hasMessageContaining("token");
  }
}
