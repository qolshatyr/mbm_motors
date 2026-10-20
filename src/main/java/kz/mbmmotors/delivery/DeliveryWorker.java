package kz.mbmmotors.delivery;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import kz.mbmmotors.cloudshop.CloudShopProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Live delivery of partner orders to CloudShop. Off unless {@code delivery.enabled=true}; each
 * order is attempted once, and an uncertain result is left as UNKNOWN for manual reconciliation.
 */
@Component
@ConditionalOnProperty(prefix = "delivery", name = "enabled", havingValue = "true")
class DeliveryWorker {
  private static final Logger LOG = LoggerFactory.getLogger(DeliveryWorker.class);
  private static final int BATCH = 10;
  private final DeliveryProcessor processor;
  private final Clock clock;
  private final Duration staleAfter;

  DeliveryWorker(
      DeliveryRepository repository,
      RestClient.Builder builder,
      CloudShopProperties cloudshop,
      Clock clock,
      @Value("${delivery.stale-after:5m}") Duration staleAfter) {
    // Fails startup on a missing token or a non-CloudShop URL rather than at the first order.
    var gateway =
        new CloudShopDeliveryGateway(
            builder, URI.create(cloudshop.baseUrl()), cloudshop.token(), cloudshop.timeout());
    this.processor = new DeliveryProcessor(repository, gateway, clock);
    this.clock = clock;
    this.staleAfter = staleAfter;
  }

  @Scheduled(fixedDelayString = "${delivery.interval:30s}", initialDelayString = "10s")
  void tick() {
    int recovered = processor.staleInFlight(clock.instant().minus(staleAfter));
    if (recovered > 0)
      LOG.warn("Delivery attempts interrupted and marked UNKNOWN: count={}", recovered);
    for (var id : processor.deliverable(BATCH)) {
      try {
        processor.deliverPartnerOrder(id);
      } catch (RuntimeException failure) {
        // The processor records UNKNOWN itself; details may contain customer data.
        LOG.warn("Delivery run stopped on an unexpected error");
        return;
      }
    }
  }
}
