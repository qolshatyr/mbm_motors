package kz.mbmmotors.partner;

import java.util.UUID;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

public record Partner(
    UUID id,
    String login,
    String passwordHash,
    String company,
    String contactName,
    String phone,
    String cloudshopCustomerId,
    boolean enabled) {
  public static final String ROLE = "PARTNER";

  public UserDetails userDetails() {
    return User.withUsername(login).password(passwordHash).roles(ROLE).disabled(!enabled).build();
  }

  public Profile profile() {
    return new Profile(login, company, contactName, phone);
  }

  /** Public view of the signed-in partner; never includes credentials or CloudShop IDs. */
  public record Profile(String login, String company, String contactName, String phone) {}
}
