package kz.mbmmotors.delivery;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Explicitly constructed internal processor; never a Spring bean or an automatic worker. */
public final class DeliveryProcessor {
  private final DeliveryRepository repository;
  private final DeliveryGateway gateway;
  private final Clock clock;

  public DeliveryProcessor(DeliveryRepository repository, DeliveryGateway gateway, Clock clock) {
    this.repository = Objects.requireNonNull(repository);
    this.gateway = Objects.requireNonNull(gateway);
    this.clock = Objects.requireNonNull(clock);
  }

  public boolean prepare(UUID id) {
    return repository.transition(id, "WAITING_CONFIGURATION", "READY", clock.instant());
  }

  public void process(UUID id) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("External delivery cannot run inside a transaction");
    if (repository.transition(id, "READY", "CUSTOMER_IN_FLIGHT", clock.instant())) {
      try {
        var snapshot = repository.snapshot(id);
        snapshot.validate();
        var result = gateway.createCustomer(snapshot);
        DeliverySnapshot.validateId(result == null ? null : result.id());
        if (!repository.customerConfirmed(id, result.id(), clock.instant())) return;
      } catch (RuntimeException e) {
        repository.transition(id, "CUSTOMER_IN_FLIGHT", "UNKNOWN", clock.instant());
        return;
      }
    }
    if (repository.transition(id, "CUSTOMER_CREATED", "SALE_IN_FLIGHT", clock.instant())) {
      try {
        var snapshot = repository.snapshot(id);
        snapshot.validate();
        var command = new SaleCommand(snapshot, repository.customerId(id), clock.instant());
        var result = gateway.createSale(command);
        DeliverySnapshot.validateId(result == null ? null : result.id());
        if (result.number() <= 0) throw new IllegalArgumentException("Invalid sale number");
        repository.saleConfirmed(id, result, clock.instant());
      } catch (RuntimeException e) {
        repository.transition(id, "SALE_IN_FLIGHT", "UNKNOWN", clock.instant());
      }
    }
  }

  public int staleInFlight(Instant before) {
    if (before.isAfter(clock.instant()))
      throw new IllegalArgumentException("Future recovery boundary");
    return repository.recover(before, clock.instant());
  }
}
