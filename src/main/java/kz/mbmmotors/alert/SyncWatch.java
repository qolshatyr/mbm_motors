package kz.mbmmotors.alert;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tells staff once when the catalog has not synchronized successfully for too long, and once when
 * it recovers. Runs only with alerts and catalog sync both on. The episode is kept in memory: a
 * restart during an outage reports it again after the same delay.
 */
@Component
@ConditionalOnExpression(AlertConfiguration.ENABLED + " and ${catalog.sync.enabled:false}")
class SyncWatch {
  private static final Logger LOG = LoggerFactory.getLogger(SyncWatch.class);
  private static final DateTimeFormatter LOCAL =
      DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm").withZone(ZoneId.of("Asia/Almaty"));
  private final JdbcTemplate jdbc;
  private final TelegramNotifier notifier;
  private final Clock clock;
  private final Duration staleAfter;
  private final Instant started;
  private boolean reported;

  @Autowired
  SyncWatch(JdbcTemplate jdbc, TelegramNotifier notifier, AlertProperties properties, Clock clock) {
    this(jdbc, notifier, clock, properties.syncStaleAfter());
  }

  SyncWatch(JdbcTemplate jdbc, TelegramNotifier notifier, Clock clock, Duration staleAfter) {
    if (staleAfter == null || staleAfter.compareTo(Duration.ofMinutes(10)) < 0)
      throw new IllegalArgumentException("alerts.sync-stale-after must be at least 10 minutes");
    this.jdbc = jdbc;
    this.notifier = notifier;
    this.clock = clock;
    this.staleAfter = staleAfter;
    // A fresh database has no successful run yet; give the first sync the same time.
    this.started = clock.instant();
  }

  @Scheduled(fixedDelayString = "${alerts.interval:1m}", initialDelayString = "1m")
  void tick() {
    Timestamp success =
        jdbc.queryForObject(
            "select max(finished_at) from sync_runs where status='SUCCESS'", Timestamp.class);
    Instant last = success == null ? null : success.toInstant();
    Instant reference = last == null || last.isBefore(started) ? started : last;
    boolean stale = reference.plus(staleAfter).isBefore(clock.instant());
    if (stale == reported) return;
    String text = stale ? staleMessage(last) : "MBM Motors: каталог снова обновляется.";
    try {
      notifier.send(text);
    } catch (AlertException failure) {
      LOG.warn("Catalog sync alert not sent, retrying next run: reason={}", failure.getMessage());
      return;
    }
    reported = stale;
    LOG.info("Staff told that catalog sync is {}", stale ? "stale" : "back");
  }

  static String staleMessage(Instant last) {
    return "MBM Motors: каталог не обновляется"
        + (last == null
            ? " — успешной синхронизации ещё не было."
            : " с " + LOCAL.format(last) + " (Алматы).")
        + " Цены и остатки на сайте могут быть устаревшими. Проверьте доступ к CloudShop и токен;"
        + " подробности — в журнале приложения.";
  }
}
