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

import java.math.BigDecimal;
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
        repository, notifier, clock, Duration.ofMinutes(15), deliveryEnabled, true, siteUrl);
  }

  private static AlertRepository.NewOrder newOrder(long number, String company, String total) {
    return new AlertRepository.NewOrder(
        UUID.randomUUID(), number, company, 3, new BigDecimal(total));
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
        .startsWith("MBM AutoMotors: заявки требуют внимания (2)")
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
            () ->
                new AlertWorker(
                    repository, notifier, clock, Duration.ofSeconds(5), true, true, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void newOrderIsAnnouncedOnceWithCompanyItemsAndSum() {
    var order = newOrder(12, "ТОО «Автосервис»", "45000.50");
    when(repository.newOrders(AlertWorker.BATCH)).thenReturn(List.of(order));
    worker(true, "https://shop.example.kz").tick();
    var text = ArgumentCaptor.forClass(String.class);
    verify(notifier).send(text.capture());
    assertThat(text.getValue())
        .isEqualTo(
            "MBM AutoMotors: новая заявка\n\n№12 — ТОО «Автосервис», позиций: 3, сумма: 45 000,50 ₸"
                + "\n\nЗаявки: https://shop.example.kz/admin/requests");
    verify(repository).markAnnounced(List.of(order.orderId()), NOW);
  }

  @Test
  void severalNewOrdersShareOneMessage() {
    when(repository.newOrders(anyInt()))
        .thenReturn(List.of(newOrder(12, "Партнёр 1", "1000"), newOrder(13, null, "25")));
    worker(true, null).tick();
    var text = ArgumentCaptor.forClass(String.class);
    verify(notifier).send(text.capture());
    assertThat(text.getValue())
        .startsWith("MBM AutoMotors: новые заявки (2)")
        .contains("№12 — Партнёр 1, позиций: 3, сумма: 1 000 ₸")
        .contains("№13 — без партнёра, позиций: 3, сумма: 25 ₸");
  }

  @Test
  void failedNewOrderAlertIsRetriedAndDoesNotBlockAttentionAlerts() {
    when(repository.newOrders(anyInt())).thenReturn(List.of(newOrder(12, "Партнёр", "10")));
    when(repository.pending(any(), anyInt())).thenReturn(List.of(unknown(7, "reason")));
    doThrow(new AlertException("Telegram answered HTTP 502"))
        .doNothing()
        .when(notifier)
        .send(any());
    worker(true, null).tick();
    verify(repository, never()).markAnnounced(anyList(), any());
    verify(repository).markAlerted(anyList(), any());
  }

  @Test
  void cancelRequestIsAnnouncedOnceWithInstructionAndWithoutReason() {
    var order = newOrder(12, "ТОО «Автосервис»", "45000");
    when(repository.cancelRequests(AlertWorker.BATCH)).thenReturn(List.of(order));
    worker(true, "https://shop.example.kz").tick();
    var text = ArgumentCaptor.forClass(String.class);
    verify(notifier).send(text.capture());
    assertThat(text.getValue())
        .isEqualTo(
            "MBM AutoMotors: клиент просит отменить заявку\n\n№12 — ТОО «Автосервис», позиций: 3,"
                + " сумма: 45 000 ₸\n\nУдалите продажу в CloudShop и отметьте отмену в заявке на"
                + " сайте.\n\nЗаявки: https://shop.example.kz/admin/requests");
    verify(repository).markCancelAnnounced(List.of(order.orderId()), NOW);
  }

  @Test
  void failedCancelRequestAlertIsRetriedAndDoesNotBlockAttentionAlerts() {
    when(repository.cancelRequests(anyInt())).thenReturn(List.of(newOrder(12, "Партнёр", "10")));
    when(repository.pending(any(), anyInt())).thenReturn(List.of(unknown(7, "reason")));
    doThrow(new AlertException("Telegram answered HTTP 502"))
        .doNothing()
        .when(notifier)
        .send(any());
    worker(true, null).tick();
    verify(repository, never()).markCancelAnnounced(anyList(), any());
    verify(repository).markAlerted(anyList(), any());
  }

  @Test
  void newOrderAlertsCanBeSwitchedOff() {
    new AlertWorker(repository, notifier, clock, Duration.ofMinutes(15), true, false, null).tick();
    verify(repository, never()).newOrders(anyInt());
  }

  @Test
  void longCompanyNamesStayInsideOneMessage() {
    var batch =
        IntStream.range(0, AlertWorker.BATCH)
            .mapToObj(i -> newOrder(1000 + i, "Компания\n" + "x".repeat(200), "9999999999.99"))
            .toList();
    String text = worker(true, "https://shop.example.kz").newOrdersMessage(batch);
    assertThat(text.length()).isLessThanOrEqualTo(TelegramNotifier.MAX_TEXT);
    assertThat(text).doesNotContain("Компания\n").contains("№1014");
  }

  @Test
  void moneyUsesSpacesAndKeepsKopecksOnlyWhenPresent() {
    assertThat(AlertWorker.money(new BigDecimal("1234567.00"))).isEqualTo("1 234 567");
    assertThat(AlertWorker.money(new BigDecimal("5000.02"))).isEqualTo("5 000,02");
    assertThat(AlertWorker.money(new BigDecimal("0.5"))).isEqualTo("0,50");
  }
}
