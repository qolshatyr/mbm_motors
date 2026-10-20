package kz.mbmmotors.delivery;

import java.time.Instant;
import java.util.Objects;

/** Unposted sale intent; intentionally has no payment fields. Instant represents UTC. */
public record SaleCommand(DeliverySnapshot snapshot, String customerId, Instant date) {
  public SaleCommand {
    Objects.requireNonNull(snapshot);
    Objects.requireNonNull(date);
    DeliverySnapshot.validateId(customerId);
  }

  public boolean status() {
    return false;
  }
}
