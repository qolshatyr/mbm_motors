package kz.mbmmotors.partner;

import java.net.URI;
import kz.mbmmotors.cloudshop.CloudShopProperties;
import kz.mbmmotors.delivery.CloudShopDeliveryGateway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Staff commands that give a partner a CloudShop customer card, required before their orders can be
 * sent. {@code --partner-link-customer} stores an existing card ID from {@code
 * MBM_CLOUDSHOP_CUSTOMER_ID}; {@code --partner-create-customer} creates the card once through the
 * API. The partner is chosen by {@code MBM_PARTNER_LOGIN}. Creation is never retried: after an
 * uncertain response staff check CloudShop and link the card manually.
 */
@Component
class PartnerCustomerCommand implements ApplicationRunner {
  private final PartnerRepository partners;
  private final RestClient.Builder builder;
  private final CloudShopProperties cloudshop;
  private final ConfigurableApplicationContext context;

  PartnerCustomerCommand(
      PartnerRepository partners,
      RestClient.Builder builder,
      CloudShopProperties cloudshop,
      ConfigurableApplicationContext context) {
    this.partners = partners;
    this.builder = builder;
    this.cloudshop = cloudshop;
    this.context = context;
  }

  public void run(ApplicationArguments args) {
    boolean link = args.containsOption("partner-link-customer");
    boolean create = args.containsOption("partner-create-customer");
    if (!link && !create) return;
    try {
      if (link && create) throw new IllegalArgumentException("Выберите одно действие.");
      String login = System.getenv("MBM_PARTNER_LOGIN");
      var partner =
          partners
              .find(login == null ? null : login.strip().toLowerCase(java.util.Locale.ROOT))
              .orElseThrow(() -> new IllegalArgumentException("Партнёр не найден."));
      if (partner.cloudshopCustomerId() != null)
        throw new IllegalArgumentException(
            "У партнёра уже есть карточка CloudShop: " + partner.cloudshopCustomerId());
      String customerId;
      if (link) {
        customerId = System.getenv("MBM_CLOUDSHOP_CUSTOMER_ID");
        if (customerId == null || !customerId.strip().matches("[a-f0-9]{24}"))
          throw new IllegalArgumentException("ID карточки CloudShop: 24 символа 0-9, a-f.");
        customerId = customerId.strip();
      } else {
        var gateway =
            new CloudShopDeliveryGateway(
                builder, URI.create(cloudshop.baseUrl()), cloudshop.token(), cloudshop.timeout());
        try {
          customerId = gateway.createCompany(partner.company(), partner.phone()).id();
        } catch (RuntimeException uncertain) {
          throw new IllegalStateException(
              "Ответ CloudShop не получен или неясен. Не повторяйте команду: проверьте покупателей в CloudShop и привяжите карточку через --partner-link-customer.");
        }
      }
      if (!partners.linkCustomer(partner.login(), customerId))
        throw new IllegalStateException(
            "Карточка " + customerId + " не привязана: проверьте партнёра и привяжите её вручную.");
      System.out.println("Партнёр " + partner.login() + " привязан к карточке " + customerId);
    } finally {
      context.close();
    }
  }
}
