package kz.mbmmotors.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kz.mbmmotors.Application;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

@EnabledIfEnvironmentVariable(named = "MBM_TEST_DATABASE_URL", matches = ".*/mbm_test")
class AlertConfigurationTest {
  private static String[] settings(String token, String chat) {
    return new String[] {
      "--spring.datasource.url=" + System.getenv("MBM_TEST_DATABASE_URL"),
      "--spring.datasource.username="
          + System.getenv().getOrDefault("MBM_TEST_DATABASE_USER", "mbm"),
      "--spring.datasource.password="
          + System.getenv().getOrDefault("MBM_TEST_DATABASE_PASSWORD", ""),
      // A long interval keeps the worker from contacting anything during the test.
      "--alerts.interval=1h",
      "--alerts.telegram.api-url=http://127.0.0.1:9",
      "--alerts.telegram.bot-token=" + token,
      "--alerts.telegram.chat-id=" + chat
    };
  }

  private static SpringApplicationBuilder application() {
    return new SpringApplicationBuilder(Application.class).web(WebApplicationType.NONE);
  }

  @Test
  void alertsAreOffWithoutToken() {
    try (var context = application().run(settings("", ""))) {
      assertThat(context.containsBean("alertWorker")).isFalse();
    }
  }

  @Test
  void configuredTokenStartsTheWorker() {
    try (var context =
        application().run(settings("123456789:synthetic-telegram-token-AAAAAAAAAAAA", "-100123"))) {
      assertThat(context.containsBean("alertWorker")).isTrue();
    }
  }

  @Test
  void tokenWithoutChatFailsAtStartup() {
    assertThatThrownBy(
            () ->
                application().run(settings("123456789:synthetic-telegram-token-AAAAAAAAAAAA", "")))
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .rootCause()
        .hasMessageContaining("chat id");
  }
}
