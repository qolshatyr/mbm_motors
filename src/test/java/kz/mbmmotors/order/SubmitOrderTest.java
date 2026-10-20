package kz.mbmmotors.order;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubmitOrderTest {
  @Test
  void normalizesKazakhAndInternationalPhonesWithoutAcceptingJunk() {
    assertEquals("+77011234567", SubmitOrder.normalizePhone("8 (701) 123-45-67"));
    assertEquals("+77011234567", SubmitOrder.normalizePhone("7011234567"));
    assertEquals("+442071234567", SubmitOrder.normalizePhone("+44 20 7123 4567"));
    assertNull(SubmitOrder.normalizePhone("123"));
    assertNull(SubmitOrder.normalizePhone("+7abc7011234567"));
  }

  @Test
  void returnsFieldErrorsAndNormalizesPayload() {
    var error =
        assertThrows(
            OrderProblem.class,
            () -> SubmitOrder.validate("bad", "", "", null, "x".repeat(1001), "false"));
    assertEquals(400, error.status());
    assertEquals(6, error.errors().size());
    var cmd =
        SubmitOrder.validate(
            UUID.randomUUID().toString(), "a".repeat(64), " Test ", "87011234567", null, "true");
    assertEquals("Test", cmd.customerName());
    assertEquals("", cmd.comment());
    assertEquals("+77011234567", cmd.phone());
  }
}
