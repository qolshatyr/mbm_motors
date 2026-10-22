package kz.mbmmotors.partner;

import java.time.Clock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * One-shot staff command {@code --partner-add}. Details come from {@code MBM_PARTNER_LOGIN}, {@code
 * _COMPANY}, {@code _CONTACT}, {@code _PHONE} and {@code _PASSWORD}, so contacts and the password
 * never appear in process arguments or logs. See {@code scripts/partner_add.py}.
 */
@Component
class PartnerCommand implements ApplicationRunner {
  private final PartnerRepository partners;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
  private final Clock clock;
  private final ConfigurableApplicationContext context;

  // Runs without the web context, where the security configuration is absent.
  PartnerCommand(PartnerRepository partners, Clock clock, ConfigurableApplicationContext context) {
    this.partners = partners;
    this.clock = clock;
    this.context = context;
  }

  public void run(ApplicationArguments args) {
    if (!args.containsOption("partner-add")) return;
    try {
      var partner =
          NewPartner.validate(
              System.getenv("MBM_PARTNER_LOGIN"),
              System.getenv("MBM_PARTNER_COMPANY"),
              System.getenv("MBM_PARTNER_CONTACT"),
              System.getenv("MBM_PARTNER_PHONE"));
      String password = System.getenv("MBM_PARTNER_PASSWORD");
      NewPartner.validatePassword(password);
      try {
        partners.create(partner, encoder.encode(password), clock.instant());
      } catch (DuplicateKeyException e) {
        throw new IllegalArgumentException("Партнёр с таким логином уже существует.");
      }
      System.out.println("Партнёр создан: " + partner.login());
    } finally {
      context.close();
    }
  }
}
