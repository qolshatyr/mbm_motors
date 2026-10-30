package kz.mbmmotors.partner;

import kz.mbmmotors.cloudshop.CloudShopException;
import kz.mbmmotors.cloudshop.CloudShopRejectedException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Staff commands that give a partner a CloudShop customer card, required before their orders can be
 * sent. {@code --partner-link-customer} stores an existing card ID from {@code
 * MBM_CLOUDSHOP_CUSTOMER_ID}; {@code --partner-create-customer} creates the card once through the
 * API. The partner is chosen by {@code MBM_PARTNER_LOGIN}. Creation is never retried: after an
 * uncertain response staff check CloudShop and link the card manually. A 4xx refusal creates
 * nothing, so its reason is shown and the command may be run again once the cause is fixed.
 */
@Component
class PartnerCustomerCommand implements ApplicationRunner {
  private final PartnerRepository partners;
  private final CustomerCards cards;
  private final ConfigurableApplicationContext context;

  PartnerCustomerCommand(
      PartnerRepository partners, CustomerCards cards, ConfigurableApplicationContext context) {
    this.partners = partners;
    this.cards = cards;
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
        try {
          customerId = cards.create(partner.company(), partner.phone());
        } catch (CloudShopRejectedException refused) {
          throw new IllegalStateException(
              "CloudShop отказал, карточка не создана: "
                  + refused.getMessage()
                  + ". Устраните причину (например, права токена на /customers) и повторите команду.");
        } catch (RuntimeException uncertain) {
          // Gateway messages are sanitized; other exceptions may carry partner data.
          String reason =
              uncertain instanceof CloudShopException ? " (" + uncertain.getMessage() + ")" : "";
          throw new IllegalStateException(
              "Ответ CloudShop не получен или неясен"
                  + reason
                  + ". Не повторяйте команду: проверьте покупателей в CloudShop и привяжите карточку через --partner-link-customer.");
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
