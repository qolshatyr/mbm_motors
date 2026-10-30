package kz.mbmmotors.partner;

import java.net.URI;
import kz.mbmmotors.cloudshop.CloudShopProperties;
import kz.mbmmotors.delivery.CloudShopDeliveryGateway;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Creates a CloudShop customer card for a partner company; one POST /customers, never retried. */
@Component
public class CustomerCards {
  private final RestClient.Builder builder;
  private final CloudShopProperties cloudshop;

  CustomerCards(RestClient.Builder builder, CloudShopProperties cloudshop) {
    this.builder = builder;
    this.cloudshop = cloudshop;
  }

  /**
   * Returns the new card ID. {@link kz.mbmmotors.cloudshop.CloudShopRejectedException} means
   * CloudShop refused and nothing was created; any other failure means the outcome is unknown.
   */
  public String create(String company, String phone) {
    var gateway =
        new CloudShopDeliveryGateway(
            builder, URI.create(cloudshop.baseUrl()), cloudshop.token(), cloudshop.timeout());
    return gateway.createCompany(company, phone).id();
  }
}
