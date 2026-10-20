package kz.mbmmotors.partner;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerAccountService {
  private final PartnerRepository partners;
  private final JdbcTemplate jdbc;

  public PartnerAccountService(PartnerRepository partners, JdbcTemplate jdbc) {
    this.partners = partners;
    this.jdbc = jdbc;
  }

  /** Resolves the signed-in partner on every request, so a disabled account loses access. */
  public Partner current(Authentication authentication) {
    if (authentication == null
        || authentication instanceof AnonymousAuthenticationToken
        || !authentication.isAuthenticated()
        || authentication.getAuthorities().stream()
            .noneMatch(a -> ("ROLE_" + Partner.ROLE).equals(a.getAuthority())))
      throw new AccountProblem(401, "Войдите в кабинет партнёра.");
    return partners
        .find(authentication.getName())
        .filter(Partner::enabled)
        .orElseThrow(() -> new AccountProblem(401, "Учётная запись недоступна. Войдите снова."));
  }

  @Transactional(readOnly = true)
  public Orders orders(Partner partner) {
    return new Orders(
        jdbc.query(
            """
            select o.number,o.created_at,o.item_count,o.total_text,d.state
            from local_orders o left join delivery_outbox d on d.order_id=o.id
            where o.partner_id=? order by o.created_at desc, o.number desc limit 100
            """,
            (rs, row) -> {
              String status = status(rs.getString("state"));
              return new Order(
                  "MBM-" + rs.getLong("number"),
                  rs.getTimestamp("created_at").toInstant(),
                  rs.getInt("item_count"),
                  rs.getString("total_text"),
                  status,
                  statusText(status));
            },
            partner.id()));
  }

  @Transactional(readOnly = true)
  public OrderItems items(Partner partner, String number) {
    if (number == null || !number.matches("MBM-[1-9][0-9]{0,17}"))
      throw new AccountProblem(404, "Заявка не найдена.");
    long value = Long.parseLong(number.substring(4));
    var items =
        jdbc.query(
            """
            select i.product_id,i.product_name,i.sku,i.quantity
            from local_orders o join local_order_items i on i.order_id=o.id
            where o.partner_id=? and o.number=? order by i.position
            """,
            (rs, row) ->
                new OrderItem(
                    rs.getString("product_id"),
                    rs.getString("product_name"),
                    rs.getString("sku"),
                    rs.getInt("quantity")),
            partner.id(),
            value);
    // Another partner's number is indistinguishable from a missing one.
    if (items.isEmpty()) throw new AccountProblem(404, "Заявка не найдена.");
    return new OrderItems(number, items);
  }

  static String status(String deliveryState) {
    if ("SENT".equals(deliveryState)) return "SENT";
    if ("UNKNOWN".equals(deliveryState)) return "UNKNOWN";
    return "RECEIVED";
  }

  static String statusText(String status) {
    return switch (status) {
      case "SENT" -> "Передана в магазин";
      case "UNKNOWN" -> "Проверяется менеджером";
      default -> "Принята";
    };
  }

  public record Orders(List<Order> orders) {}

  public record Order(
      String number,
      Instant createdAt,
      int itemCount,
      String totalText,
      String status,
      String statusText) {}

  public record OrderItems(String number, List<OrderItem> items) {}

  public record OrderItem(String productId, String name, String sku, int quantity) {}

  public static class AccountProblem extends RuntimeException {
    private final int status;

    public AccountProblem(int status, String message) {
      super(message);
      this.status = status;
    }

    public int status() {
      return status;
    }
  }
}
