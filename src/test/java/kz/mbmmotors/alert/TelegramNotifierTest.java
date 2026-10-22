package kz.mbmmotors.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TelegramNotifierTest {
  private static final String TOKEN = "123456789:synthetic-telegram-token-AAAAAAAAAAAA";
  private static final String CHAT = "-1001234567890";
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final AtomicInteger calls = new AtomicInteger();
  private HttpServer server;
  private ExecutorService executor;
  private String api;
  private volatile String path;
  private volatile String received;
  private volatile int status = 200;
  private volatile String reply = "{\"ok\":true,\"result\":{\"message_id\":1}}";
  private volatile boolean stall;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    server.createContext("/", this::respond);
    server.start();
    api = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    server.stop(0);
    executor.close();
  }

  private void respond(HttpExchange exchange) throws IOException {
    calls.incrementAndGet();
    path = exchange.getRequestURI().getRawPath();
    received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    if (stall) {
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    byte[] body = reply.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  private TelegramNotifier notifier() {
    return notifier(api, TOKEN, CHAT);
  }

  private TelegramNotifier notifier(String apiUrl, String token, String chat) {
    return new TelegramNotifier(
        RestClient.builder(),
        new AlertProperties.Telegram(apiUrl, token, chat, Duration.ofMillis(500)));
  }

  @Test
  void sendsPlainTextToTheConfiguredChat() {
    notifier().send("Заявка №7\nтребует внимания");
    assertThat(path).isEqualTo("/bot" + TOKEN + "/sendMessage");
    JsonNode body = mapper.readTree(received);
    assertThat(body.get("chat_id").stringValue()).isEqualTo(CHAT);
    assertThat(body.get("text").stringValue()).isEqualTo("Заявка №7\nтребует внимания");
    assertThat(body.has("parse_mode")).isFalse();
    assertThat(calls).hasValue(1);
  }

  @Test
  void unconfirmedMessageFails() {
    reply = "{\"ok\":false}";
    assertThatThrownBy(() -> notifier().send("text"))
        .isInstanceOf(AlertException.class)
        .hasMessageContaining("did not confirm");
  }

  @Test
  void rejectionKeepsTheBotApiExplanation() {
    status = 400;
    reply = "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}";
    assertThatThrownBy(() -> notifier().send("text"))
        .isInstanceOf(AlertException.class)
        .hasMessage("Telegram answered HTTP 400: Bad Request: chat not found");
  }

  @Test
  void explanationEchoingTheTokenIsDropped() {
    status = 401;
    reply = "{\"ok\":false,\"description\":\"bad token " + TOKEN + "\"}";
    assertThatThrownBy(() -> notifier().send("text")).hasMessage("Telegram answered HTTP 401");
  }

  @Test
  void transportFailuresNeverQuoteTheTokenOrUrl() {
    stall = true;
    assertThatThrownBy(() -> notifier().send("text"))
        .isInstanceOf(AlertException.class)
        .hasMessageStartingWith("Telegram request failed: ")
        .hasNoCause()
        .message()
        .doesNotContain(TOKEN)
        .doesNotContain("127.0.0.1");
    server.stop(0);
    assertThatThrownBy(() -> notifier().send("text"))
        .isInstanceOf(AlertException.class)
        .message()
        .doesNotContain(TOKEN);
  }

  @Test
  void textLengthIsBounded() {
    assertThatThrownBy(() -> notifier().send(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> notifier().send("x".repeat(TelegramNotifier.MAX_TEXT + 1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(calls).hasValue(0);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://api.telegram.org",
        "https://api.telegram.org.example.com",
        "https://example.com",
        "https://user@api.telegram.org",
        "https://api.telegram.org/bot",
      })
  void onlyTheOfficialApiOrLoopbackIsAccepted(String url) {
    assertThatThrownBy(() -> notifier(url, TOKEN, CHAT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "plainword", "123:short", "123456789:with/slash-AAAAAAAAAAAAAAAAAAAAAA"})
  void malformedTokensFailFast(String token) {
    assertThatThrownBy(() -> notifier(api, token, CHAT))
        .isInstanceOf(IllegalArgumentException.class)
        .message()
        .doesNotContain(token.isEmpty() ? "\u0000" : token);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "chat", "-12a", "@abc"})
  void malformedChatsFailFast(String chat) {
    assertThatThrownBy(() -> notifier(api, TOKEN, chat))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void diagnosticsHideTheToken() {
    assertThat(notifier().toString()).doesNotContain(TOKEN).contains(CHAT);
    assertThat(new AlertProperties.Telegram(api, TOKEN, CHAT, Duration.ofSeconds(1)).toString())
        .doesNotContain(TOKEN);
  }
}
