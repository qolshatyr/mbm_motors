package kz.mbmmotors.partner;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PartnerRepository {
  private final JdbcTemplate jdbc;

  public PartnerRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Partner> find(String login) {
    if (login == null || !login.matches("[a-z0-9._-]{3,64}")) return Optional.empty();
    return jdbc
        .query("select * from partners where login=?", PartnerRepository::map, login)
        .stream()
        .findFirst();
  }

  public Partner create(NewPartner partner, String passwordHash, Instant now) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into partners(id,login,password_hash,company,contact_name,phone,bin,enabled,created_at)
        values (?,?,?,?,?,?,?,true,?)
        """,
        id,
        partner.login(),
        passwordHash,
        partner.company(),
        partner.contactName(),
        partner.phone(),
        partner.bin(),
        Timestamp.from(now));
    return find(partner.login()).orElseThrow();
  }

  private static final String CLIENT_COLUMNS =
      """
      select login,company,contact_name,phone,bin,enabled,created_at,last_login_at,
        cloudshop_customer_id,cloudshop_customer_state
      from partners
      """;

  /** Newest first, at most 500; {@code query} matches company, login or contact name. */
  public List<Client> clients(String query) {
    String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    return jdbc.query(
        CLIENT_COLUMNS
            + """
            where company ilike ? escape '!' or login ilike ? escape '!' or contact_name ilike ? escape '!'
            order by created_at desc, login limit 500
            """,
        PartnerRepository::client,
        pattern,
        pattern,
        pattern);
  }

  public Optional<Client> client(String login) {
    return jdbc.query(CLIENT_COLUMNS + " where login=?", PartnerRepository::client, login).stream()
        .findFirst();
  }

  private static Client client(ResultSet rs, int row) throws SQLException {
    return new Client(
        rs.getString("login"),
        rs.getString("company"),
        rs.getString("contact_name"),
        rs.getString("phone"),
        rs.getString("bin"),
        rs.getBoolean("enabled"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("last_login_at") == null
            ? null
            : rs.getTimestamp("last_login_at").toInstant(),
        rs.getString("cloudshop_customer_id"),
        rs.getString("cloudshop_customer_state"));
  }

  /** A new password ends every session signed in with the previous one. */
  public boolean changePassword(String login, String passwordHash) {
    return jdbc.update(
            "update partners set password_hash=?, credentials_version=credentials_version+1 where login=?",
            passwordHash,
            login)
        == 1;
  }

  public boolean setEnabled(String login, boolean enabled) {
    return jdbc.update("update partners set enabled=? where login=?", enabled, login) == 1;
  }

  public void recordLogin(String login, Instant now) {
    jdbc.update("update partners set last_login_at=? where login=?", Timestamp.from(now), login);
  }

  /**
   * Claims the single CloudShop card request for a partner without a card. A request after an
   * unclear one ({@code UNKNOWN}) needs {@code afterCheck}: staff confirmed no card was created.
   */
  public boolean claimCustomerRequest(String login, boolean afterCheck) {
    return jdbc.update(
            """
            update partners set cloudshop_customer_state='PENDING'
            where login=? and cloudshop_customer_id is null
              and (cloudshop_customer_state is null or (? and cloudshop_customer_state='UNKNOWN'))
            """,
            login,
            afterCheck)
        == 1;
  }

  /** The card request had an unclear outcome: staff check CloudShop before anything else. */
  public void markCustomerUnknown(String login) {
    jdbc.update(
        "update partners set cloudshop_customer_state='UNKNOWN' where login=? and cloudshop_customer_id is null",
        login);
  }

  /** Links a CloudShop card once; an existing link is never overwritten silently. */
  public boolean linkCustomer(String login, String customerId) {
    return jdbc.update(
            "update partners set cloudshop_customer_id=?, cloudshop_customer_state=null where login=? and cloudshop_customer_id is null",
            customerId,
            login)
        == 1;
  }

  private static Partner map(ResultSet rs, int row) throws SQLException {
    return new Partner(
        rs.getObject("id", UUID.class),
        rs.getString("login"),
        rs.getString("password_hash"),
        rs.getString("company"),
        rs.getString("contact_name"),
        rs.getString("phone"),
        rs.getString("cloudshop_customer_id"),
        rs.getBoolean("enabled"),
        rs.getInt("credentials_version"));
  }

  /** Staff view of a partner account; never includes the password hash. */
  public record Client(
      String login,
      String company,
      String contactName,
      String phone,
      String bin,
      boolean enabled,
      Instant createdAt,
      Instant lastLoginAt,
      String cloudshopCustomerId,
      String cloudshopCustomerState) {}
}
