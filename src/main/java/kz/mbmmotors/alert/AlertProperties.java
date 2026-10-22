package kz.mbmmotors.alert;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Staff alerts about orders that need attention. Off while the bot token is empty. */
@ConfigurationProperties(prefix = "alerts")
public record AlertProperties(
    Telegram telegram,
    String siteUrl,
    @DefaultValue("15m") Duration waitingAfter,
    @DefaultValue("true") boolean newOrders) {

  public record Telegram(
      @DefaultValue("https://api.telegram.org") String apiUrl,
      String botToken,
      String chatId,
      @DefaultValue("10s") Duration timeout) {
    // The bot token grants full control of the bot; keep it out of record diagnostics.
    @Override
    public String toString() {
      return "Telegram[apiUrl=" + apiUrl + ", botToken=REDACTED, chatId=" + chatId + "]";
    }
  }
}
