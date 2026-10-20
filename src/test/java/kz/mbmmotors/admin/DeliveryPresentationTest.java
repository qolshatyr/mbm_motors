package kz.mbmmotors.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryPresentationTest {
  @Test
  void uncertainResultExplainsManualReconciliation() {
    var result = StaffRequestService.delivery("UNKNOWN");
    assertThat(result.state()).isEqualTo("UNKNOWN");
    assertThat(result.message()).contains("ручная сверка", "повтор запрещён");
  }

  @Test
  void deliveryDoesNotPromiseReservationOrPayment() {
    assertThat(StaffRequestService.delivery("SENT").message())
        .contains("непроведённой", "не подтверждает резерв или оплату");
  }

  @Test
  void unrecognizedStateDoesNotPretendToBeWaiting() {
    assertThat(StaffRequestService.delivery("FUTURE_STATE").message())
        .contains("Неизвестное состояние");
    assertThat(StaffRequestService.delivery(null).state()).isEqualTo("NOT_PREPARED");
  }
}
