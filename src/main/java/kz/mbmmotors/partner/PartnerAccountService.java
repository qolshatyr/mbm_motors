package kz.mbmmotors.partner;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerAccountService {
  private final PartnerRepository partners;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public PartnerAccountService(PartnerRepository partners, JdbcTemplate jdbc, Clock clock) {
    this.partners = partners;
    this.jdbc = jdbc;
    this.clock = clock;
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
            select o.number,o.created_at,o.item_count,o.total_text,d.state,
            o.cancel_requested_at,o.cancelled_at
            from local_orders o left join delivery_outbox d on d.order_id=o.id
            where o.partner_id=? order by o.created_at desc, o.number desc limit 100
            """,
            (rs, row) -> {
              String status = status(rs);
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
  public OrderDetail detail(Partner partner, String number) {
    long value = number(number);
    var orders =
        jdbc.query(
            """
            select o.id,o.created_at,o.item_count,o.total_text,d.state,
            o.cancel_requested_at,o.cancel_reason,o.cancelled_at
            from local_orders o left join delivery_outbox d on d.order_id=o.id
            where o.partner_id=? and o.number=?
            """,
            (rs, row) -> {
              String status = status(rs);
              return new OrderDetail(
                  number,
                  rs.getTimestamp("created_at").toInstant(),
                  rs.getInt("item_count"),
                  rs.getString("total_text"),
                  status,
                  statusText(status),
                  rs.getString("cancel_reason"),
                  rs.getTimestamp("cancel_requested_at") == null
                      && rs.getTimestamp("cancelled_at") == null,
                  items(rs.getObject("id", UUID.class)));
            },
            partner.id(),
            value);
    // Another partner's number is indistinguishable from a missing one.
    if (orders.isEmpty()) throw new AccountProblem(404, "Заявка не найдена.");
    return orders.getFirst();
  }

  private List<OrderItem> items(UUID orderId) {
    return jdbc.query(
        """
        select product_id,product_name,sku,unit,quantity,price,line_total
        from local_order_items where order_id=? order by position
        """,
        (rs, row) ->
            new OrderItem(
                rs.getString("product_id"),
                rs.getString("product_name"),
                rs.getString("sku"),
                rs.getString("unit"),
                rs.getInt("quantity"),
                money(rs.getBigDecimal("price")),
                money(rs.getBigDecimal("line_total"))),
        orderId);
  }

  /**
   * Records the partner's request to cancel; staff remove the CloudShop sale by hand. A second
   * request cannot replace the first reason.
   */
  @Transactional
  public OrderDetail requestCancel(Partner partner, String number, String reason) {
    long value = number(number);
    String text = reason == null ? "" : reason.replaceAll("\\p{Cntrl}", " ").strip();
    if (text.length() > 500) throw new AccountProblem(400, "Причина — не больше 500 символов.");
    int updated =
        jdbc.update(
            """
            update local_orders set cancel_requested_at=?,cancel_reason=?
            where partner_id=? and number=? and cancel_requested_at is null and cancelled_at is null
            """,
            Timestamp.from(clock.instant()),
            text.isEmpty() ? null : text,
            partner.id(),
            value);
    var detail = detail(partner, number);
    if (updated == 0)
      throw new AccountProblem(
          409,
          "CANCELLED".equals(detail.status())
              ? "Заявка уже отменена."
              : "Отмена по этой заявке уже запрошена.");
    return detail;
  }

  private static long number(String number) {
    if (number == null || !number.matches("MBM-[1-9][0-9]{0,17}"))
      throw new AccountProblem(404, "Заявка не найдена.");
    return Long.parseLong(number.substring(4));
  }

  private static String status(ResultSet rs) throws SQLException {
    if (rs.getTimestamp("cancelled_at") != null) return "CANCELLED";
    if (rs.getTimestamp("cancel_requested_at") != null) return "CANCEL_REQUESTED";
    return status(rs.getString("state"));
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
      case "CANCEL_REQUESTED" -> "Запрошена отмена";
      case "CANCELLED" -> "Отменена";
      default -> "Принята";
    };
  }

  static String money(BigDecimal value) {
    var format = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));
    format.setMinimumFractionDigits(0);
    format.setMaximumFractionDigits(2);
    return format.format(value) + " ₸";
  }

  public record Orders(List<Order> orders) {}

  public record Order(
      String number,
      Instant createdAt,
      int itemCount,
      String totalText,
      String status,
      String statusText) {}

  /** {@code cancellable}: the partner has not asked to cancel and staff have not cancelled. */
  public record OrderDetail(
      String number,
      Instant createdAt,
      int itemCount,
      String totalText,
      String status,
      String statusText,
      String cancelReason,
      boolean cancellable,
      List<OrderItem> items) {}

  public record OrderItem(
      String productId,
      String name,
      String sku,
      String unit,
      int quantity,
      String priceText,
      String lineTotalText) {}

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
