package kz.mbmmotors.alert;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Tells staff in Telegram about orders that need attention: an UNKNOWN delivery to reconcile, or a
 * partner order that has not started delivery. Each order is reported once, after Telegram accepts
 * the message; a failed send is retried on the next run. Off while the bot token is empty.
 */
@Component
@ConditionalOnExpression("!'${alerts.telegram.bot-token:}'.isBlank()")
class AlertWorker {
  private static final Logger LOG = LoggerFactory.getLogger(AlertWorker.class);
  static final int BATCH = 15;
  private static final int MAX_REASON = 160;
  private final AlertRepository repository;
  private final TelegramNotifier notifier;
  private final Clock clock;
  private final Duration waitingAfter;
  private final boolean deliveryEnabled;
  private final String requestsUrl;

  @Autowired
  AlertWorker(
      AlertRepository repository,
      RestClient.Builder builder,
      AlertProperties properties,
      Clock clock,
      @Value("${delivery.enabled:false}") boolean deliveryEnabled) {
    // Fails startup on a malformed token, chat or site address rather than at the first alert.
    this(
        repository,
        new TelegramNotifier(builder, properties.telegram()),
        clock,
        properties.waitingAfter(),
        deliveryEnabled,
        properties.siteUrl());
  }

  AlertWorker(
      AlertRepository repository,
      TelegramNotifier notifier,
      Clock clock,
      Duration waitingAfter,
      boolean deliveryEnabled,
      String siteUrl) {
    if (waitingAfter == null || waitingAfter.compareTo(Duration.ofMinutes(1)) < 0)
      throw new IllegalArgumentException("alerts.waiting-after must be at least 1 minute");
    this.repository = repository;
    this.notifier = notifier;
    this.clock = clock;
    this.waitingAfter = waitingAfter;
    this.deliveryEnabled = deliveryEnabled;
    this.requestsUrl = requestsUrl(siteUrl);
    LOG.info("Staff alerts enabled: Telegram {}", notifier);
  }

  private static String requestsUrl(String siteUrl) {
    if (siteUrl == null || siteUrl.isBlank()) return null;
    URI site = URI.create(siteUrl.strip());
    if (!("https".equals(site.getScheme()) || "http".equals(site.getScheme()))
        || site.getHost() == null
        || site.getRawUserInfo() != null
        || site.getRawQuery() != null)
      throw new IllegalArgumentException("alerts.site-url must be an http(s) site address");
    return siteUrl.strip().replaceAll("/+$", "") + "/admin/requests";
  }

  @Scheduled(fixedDelayString = "${alerts.interval:1m}", initialDelayString = "30s")
  void tick() {
    // Without delivery every order waits by design; staff then work from the request list.
    var pending =
        repository.pending(deliveryEnabled ? clock.instant().minus(waitingAfter) : null, BATCH);
    if (pending.isEmpty()) return;
    try {
      notifier.send(message(pending));
    } catch (AlertException failure) {
      LOG.warn(
          "Staff alert not sent, retrying next run: orders={}, reason={}",
          pending.size(),
          failure.getMessage());
      return;
    }
    repository.markAlerted(
        pending.stream().map(AlertRepository.Pending::orderId).toList(), clock.instant());
    LOG.info("Staff alerted about orders: count={}", pending.size());
  }

  String message(List<AlertRepository.Pending> pending) {
    var text = new StringBuilder("MBM Motors: заявки требуют внимания (" + pending.size() + ")\n");
    for (var order : pending) {
      String reason = order.reason().replaceAll("\\p{Cntrl}", " ").strip();
      if (reason.length() > MAX_REASON) reason = reason.substring(0, MAX_REASON) + "…";
      text.append("\n№")
          .append(order.number())
          .append(
              "UNKNOWN".equals(order.state())
                  ? " — неясен результат передачи, сверьте с CloudShop: "
                  : " — не передана в CloudShop: ")
          .append(reason);
    }
    if (requestsUrl != null) text.append("\n\nЗаявки: ").append(requestsUrl);
    return text.toString();
  }
}
