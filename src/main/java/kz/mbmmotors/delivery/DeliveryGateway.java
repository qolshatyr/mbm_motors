package kz.mbmmotors.delivery;

/** Internal port. No production adapter is registered or invoked by the application. */
public interface DeliveryGateway {
  CustomerResult createCustomer(DeliverySnapshot snapshot);

  SaleResult createSale(SaleCommand command);

  record CustomerResult(String id) {}

  record SaleResult(String id, long number) {}
}
