package kz.mbmmotors.alert;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(AlertConfiguration.ENABLED)
class AlertConfiguration {
  /** Staff alerts are on when a Telegram bot token is configured. */
  static final String ENABLED = "!'${alerts.telegram.bot-token:}'.isBlank()";

  // Fails startup on a malformed token or chat rather than at the first alert.
  @Bean
  TelegramNotifier telegramNotifier(RestClient.Builder builder, AlertProperties properties) {
    return new TelegramNotifier(builder, properties.telegram());
  }
}
