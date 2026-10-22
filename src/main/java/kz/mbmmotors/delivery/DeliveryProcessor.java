package kz.mbmmotors.delivery;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import kz.mbmmotors.cloudshop.CloudShopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Explicitly constructed internal processor; only {@link DeliveryWorker} runs it automatically. */
public final class DeliveryProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(DeliveryProcessor.class);
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

  /** Sends one partner order as an unposted sale; an uncertain outcome becomes UNKNOWN. */
  public void deliverPartnerOrder(UUID id) {
    repository.promotePartnerOrder(id, clock.instant());
    process(id);
  }

  public java.util.List<UUID> deliverable(int limit) {
    return repository.deliverable(limit);
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
        String reason = reason(e);
        repository.unknown(id, "CUSTOMER_IN_FLIGHT", "Покупатель: " + reason, clock.instant());
        LOG.warn("Delivery customer step marked UNKNOWN: order={}, reason={}", id, reason);
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
        String reason = reason(e);
        repository.unknown(id, "SALE_IN_FLIGHT", "Продажа: " + reason, clock.instant());
        LOG.warn("Delivery sale step marked UNKNOWN: order={}, reason={}", id, reason);
      }
    }
  }

  /** Messages of these types are fixed texts without customer data; others are named only. */
  private static String reason(RuntimeException e) {
    return (e instanceof CloudShopException || e instanceof IllegalArgumentException)
            && e.getMessage() != null
        ? e.getMessage()
        : e.getClass().getSimpleName();
  }

  public int staleInFlight(Instant before) {
    if (before.isAfter(clock.instant()))
      throw new IllegalArgumentException("Future recovery boundary");
    return repository.recover(before, clock.instant());
  }
}
