package kz.mbmmotors.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlertWorkerTest {
  private static final Instant NOW = Instant.parse("2026-10-23T06:00:00Z");
  private final AlertRepository repository = mock(AlertRepository.class);
  private final TelegramNotifier notifier = mock(TelegramNotifier.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private AlertWorker worker(boolean deliveryEnabled, String siteUrl) {
    return new AlertWorker(
        repository, notifier, clock, Duration.ofMinutes(15), deliveryEnabled, siteUrl);
  }

  private static AlertRepository.Pending unknown(long number, String reason) {
    return new AlertRepository.Pending(UUID.randomUUID(), number, "UNKNOWN", reason);
  }

  @Test
  void nothingPendingSendsNothing() {
    when(repository.pending(any(), anyInt())).thenReturn(List.of());
    worker(true, null).tick();
    verify(notifier, never()).send(any());
    verify(repository, never()).markAlerted(anyList(), any());
  }

  @Test
  void sentAlertMarksExactlyTheReportedOrders() {
    var first = unknown(7, "Продажа: HTTP 404");
    var second =
        new AlertRepository.Pending(
            UUID.randomUUID(), 8, "WAITING_CONFIGURATION", "у партнёра нет карточки CloudShop");
    when(repository.pending(NOW.minus(Duration.ofMinutes(15)), AlertWorker.BATCH))
        .thenReturn(List.of(first, second));
    worker(true, "https://shop.example.kz/").tick();
    var text = ArgumentCaptor.forClass(String.class);
    verify(notifier).send(text.capture());
    assertThat(text.getValue())
        .startsWith("MBM Motors: заявки требуют внимания (2)")
        .contains("№7 — неясен результат передачи, сверьте с CloudShop: Продажа: HTTP 404")
        .contains("№8 — не передана в CloudShop: у партнёра нет карточки CloudShop")
        .endsWith("Заявки: https://shop.example.kz/admin/requests");
    verify(repository).markAlerted(List.of(first.orderId(), second.orderId()), NOW);
  }

  @Test
  void failedSendLeavesOrdersForTheNextRun() {
    when(repository.pending(any(), anyInt())).thenReturn(List.of(unknown(7, "reason")));
    doThrow(new AlertException("Telegram answered HTTP 502")).when(notifier).send(any());
    worker(true, null).tick();
    verify(repository, never()).markAlerted(anyList(), any());
  }

  @Test
  void withoutDeliveryOnlyUnknownOrdersAreReported() {
    when(repository.pending(isNull(), anyInt())).thenReturn(List.of());
    worker(false, null).tick();
    verify(repository).pending(null, AlertWorker.BATCH);
  }

  @Test
  void longReasonsAndControlCharactersStayInsideOneMessage() {
    var batch =
        IntStream.range(0, AlertWorker.BATCH)
            .mapToObj(i -> unknown(1000 + i, "line\nbreak " + "x".repeat(400)))
            .toList();
    String text = worker(true, "https://shop.example.kz").message(batch);
    assertThat(text.length()).isLessThanOrEqualTo(TelegramNotifier.MAX_TEXT);
    assertThat(text).doesNotContain("line\nbreak").contains("№1014");
  }

  @Test
  void invalidSettingsFailFast() {
    assertThatThrownBy(() -> worker(true, "javascript:alert(1)"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new AlertWorker(repository, notifier, clock, Duration.ofSeconds(5), true, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
