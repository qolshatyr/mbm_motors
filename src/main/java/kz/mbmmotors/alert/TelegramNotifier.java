package kz.mbmmotors.alert;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends plain-text messages to one Telegram chat through the Bot API. The bot token is part of the
 * request URL, and HTTP client errors quote that URL, so no client error text is ever passed on.
 */
public final class TelegramNotifier {
  static final int MAX_TEXT = 4000;
  private static final int MAX_RESPONSE_BYTES = 64 * 1024;
  private final RestClient client;
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final String token;
  private final String chatId;

  public TelegramNotifier(RestClient.Builder builder, AlertProperties.Telegram telegram) {
    URI base = telegram.apiUrl() == null ? null : URI.create(telegram.apiUrl());
    boolean official =
        base != null
            && "https".equals(base.getScheme())
            && "api.telegram.org".equals(base.getHost())
            && (base.getPort() == -1 || base.getPort() == 443);
    boolean local =
        base != null
            && "http".equals(base.getScheme())
            && ("localhost".equals(base.getHost()) || "127.0.0.1".equals(base.getHost()))
            && base.getPort() > 0;
    if ((!official && !local)
        || !(base.getRawPath().isEmpty() || "/".equals(base.getRawPath()))
        || base.getRawUserInfo() != null
        || base.getRawQuery() != null)
      throw new IllegalArgumentException("Invalid Telegram API URL");
    if (telegram.botToken() == null
        || !telegram.botToken().matches("\\d{5,15}:[A-Za-z0-9_-]{30,64}"))
      throw new IllegalArgumentException("Telegram bot token has an invalid format");
    if (telegram.chatId() == null || !telegram.chatId().matches("-?\\d{1,20}|@[A-Za-z0-9_]{5,32}"))
      throw new IllegalArgumentException("Telegram chat id must be a number or @channel");
    Duration timeout = telegram.timeout();
    if (timeout == null
        || timeout.compareTo(Duration.ofMillis(1)) < 0
        || timeout.compareTo(Duration.ofMinutes(1)) > 0)
      throw new IllegalArgumentException("Telegram timeout must be 1 ms to 1 minute");
    var http =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(timeout);
    client =
        builder
            .clone()
            .baseUrl(base.toString().replaceAll("/$", ""))
            .requestFactory(factory)
            .build();
    this.token = telegram.botToken();
    this.chatId = telegram.chatId();
  }

  /** Delivers the text or throws {@link AlertException} with a message safe to log. */
  public void send(String text) {
    if (text == null || text.isBlank() || text.length() > MAX_TEXT)
      throw new IllegalArgumentException("Alert text must be 1 to " + MAX_TEXT + " characters");
    try {
      client
          .post()
          // A URI variable would encode the token's ':' as %3A; the token matched a strict
          // pattern in the constructor, so it is safe as a literal path segment.
          .uri(uri -> uri.path("/bot" + token + "/sendMessage").build())
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .body(
              mapper.writeValueAsBytes(
                  Map.of(
                      "chat_id",
                      chatId,
                      "text",
                      text,
                      "link_preview_options",
                      Map.of("is_disabled", true))))
          .exchange(
              (request, response) -> {
                int status = response.getStatusCode().value();
                byte[] bytes = response.getBody().readNBytes(MAX_RESPONSE_BYTES);
                if (status != 200)
                  throw new AlertException("Telegram answered HTTP " + status + description(bytes));
                JsonNode ok = mapper.readTree(bytes).get("ok");
                if (ok == null || !ok.isBoolean() || !ok.booleanValue())
                  throw new AlertException("Telegram did not confirm the message");
                return null;
              });
    } catch (AlertException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new AlertException("Telegram request failed: " + rootType(exception));
    }
  }

  /** Bot API errors explain themselves ("Bad Request: chat not found"); keep that text short. */
  private String description(byte[] body) {
    try {
      JsonNode description = mapper.readTree(body).get("description");
      if (description == null || !description.isString()) return "";
      String text = description.stringValue().replaceAll("\\p{Cntrl}", " ").strip();
      if (text.isEmpty() || text.contains(token)) return "";
      return ": " + (text.length() > 120 ? text.substring(0, 120) + "…" : text);
    } catch (RuntimeException exception) {
      return "";
    }
  }

  private static String rootType(Throwable exception) {
    Throwable root = exception;
    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
    return root.getClass().getSimpleName();
  }

  @Override
  public String toString() {
    return "TelegramNotifier[chatId=" + chatId + "]";
  }
}
