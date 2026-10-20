package kz.mbmmotors.partner;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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
        insert into partners(id,login,password_hash,company,contact_name,phone,enabled,created_at)
        values (?,?,?,?,?,?,true,?)
        """,
        id,
        partner.login(),
        passwordHash,
        partner.company(),
        partner.contactName(),
        partner.phone(),
        Timestamp.from(now));
    return find(partner.login()).orElseThrow();
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
        rs.getBoolean("enabled"));
  }
}
