package kz.mbmmotors.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SyncWatchTest {
  private static final Instant START = Instant.parse("2026-10-23T06:00:00Z");
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final TelegramNotifier notifier = mock(TelegramNotifier.class);
  private final AtomicReference<Instant> now = new AtomicReference<>(START);
  private final Clock clock =
      new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return now.get();
        }
      };

  private SyncWatch watch() {
    return new SyncWatch(jdbc, notifier, clock, Duration.ofMinutes(30));
  }

  private void lastSuccess(Instant at) {
    when(jdbc.queryForObject(any(String.class), eq(Timestamp.class)))
        .thenReturn(at == null ? null : Timestamp.from(at));
  }

  @Test
  void freshCatalogSendsNothing() {
    lastSuccess(START.minusSeconds(60));
    var watch = watch();
    now.set(START.plus(Duration.ofMinutes(20)));
    watch.tick();
    verify(notifier, never()).send(any());
  }

  @Test
  void staleCatalogIsReportedOnceAndRecoveryOnce() {
    lastSuccess(START.minusSeconds(60));
    var watch = watch();
    now.set(START.plus(Duration.ofMinutes(31)));
    watch.tick();
    watch.tick();
    verify(notifier, times(1))
        .send(startsWith("MBM AutoMotors: каталог не обновляется с 23.10.2026 10:59"));
    lastSuccess(START.plus(Duration.ofMinutes(32)));
    now.set(START.plus(Duration.ofMinutes(33)));
    watch.tick();
    watch.tick();
    verify(notifier, times(1)).send("MBM AutoMotors: каталог снова обновляется.");
  }

  @Test
  void freshDatabaseGetsTheSameDelayFromStartup() {
    lastSuccess(null);
    var watch = watch();
    now.set(START.plus(Duration.ofMinutes(29)));
    watch.tick();
    verify(notifier, never()).send(any());
    now.set(START.plus(Duration.ofMinutes(31)));
    watch.tick();
    verify(notifier)
        .send(
            startsWith(
                "MBM AutoMotors: каталог не обновляется — успешной синхронизации ещё не было."));
  }

  @Test
  void oldSuccessBeforeStartupCountsFromStartup() {
    lastSuccess(START.minus(Duration.ofDays(2)));
    var watch = watch();
    now.set(START.plus(Duration.ofMinutes(10)));
    watch.tick();
    verify(notifier, never()).send(any());
  }

  @Test
  void failedSendIsRetriedNextRun() {
    lastSuccess(START.minusSeconds(60));
    var watch = watch();
    now.set(START.plus(Duration.ofMinutes(31)));
    doThrow(new AlertException("Telegram answered HTTP 502")).when(notifier).send(any());
    watch.tick();
    reset(notifier);
    watch.tick();
    verify(notifier).send(startsWith("MBM AutoMotors: каталог не обновляется"));
  }

  @Test
  void tooShortThresholdFailsFast() {
    assertThatThrownBy(() -> new SyncWatch(jdbc, notifier, clock, Duration.ofMinutes(5)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(SyncWatch.staleMessage(null)).doesNotContain("null");
  }
}
