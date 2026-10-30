package kz.mbmmotors.admin;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffRequestService {
  private final JdbcTemplate jdbc;

  public StaffRequestService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page list(int page) {
    long count = jdbc.queryForObject("select count(*) from local_orders", Long.class);
    var items =
        jdbc.query(
            """
        select o.number,o.status,o.created_at,o.store_name,o.item_count,o.total_text,d.state delivery_state,
        d.failure_reason,d.reconciled_result,d.reconciled_sale_number,d.reconciled_by,d.reconciled_at,
        o.cancel_requested_at,o.cancel_reason,o.cancelled_at,o.cancelled_by
        from local_orders o left join delivery_outbox d on d.order_id=o.id
        order by o.created_at desc,o.number desc limit 20 offset ?
        """,
            (rs, row) ->
                new Summary(
                    Long.toString(rs.getLong("number")),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getString("store_name"),
                    rs.getInt("item_count"),
                    rs.getString("total_text"),
                    delivery(rs),
                    cancellation(rs)),
            page * 20L);
    return new Page(items, page, 20, count, (int) Math.min(Integer.MAX_VALUE, (count + 19) / 20));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Optional<Detail> detail(long number) {
    return jdbc
        .query(
            """
        select o.number,o.status,o.created_at,o.store_name,o.item_count,o.total_text,
        o.customer_name,o.phone,o.comment,d.state delivery_state,
        d.failure_reason,d.reconciled_result,d.reconciled_sale_number,d.reconciled_by,d.reconciled_at,
        o.cancel_requested_at,o.cancel_reason,o.cancelled_at,o.cancelled_by
        from local_orders o left join delivery_outbox d on d.order_id=o.id where o.number = ?
        """,
            (rs, row) ->
                new Detail(
                    Long.toString(rs.getLong("number")),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getString("store_name"),
                    rs.getInt("item_count"),
                    rs.getString("total_text"),
                    rs.getString("customer_name"),
                    rs.getString("phone"),
                    rs.getString("comment"),
                    List.of(),
                    delivery(rs),
                    cancellation(rs)),
            number)
        .stream()
        .findFirst()
        .map(
            detail -> {
              var items =
                  jdbc.query(
                      """
              select position,product_name,sku,unit,quantity,price,line_total from local_order_items
              where order_id = (select id from local_orders where number = ?) order by position
              """,
                      (rs, row) ->
                          new Item(
                              rs.getInt("position"),
                              rs.getString("product_name"),
                              rs.getString("sku"),
                              rs.getString("unit"),
                              rs.getInt("quantity"),
                              money(rs.getBigDecimal("price")),
                              money(rs.getBigDecimal("line_total"))),
                      number);
              return new Detail(
                  detail.number(),
                  detail.status(),
                  detail.createdAt(),
                  detail.storeName(),
                  detail.itemCount(),
                  detail.totalText(),
                  detail.customerName(),
                  detail.phone(),
                  detail.comment(),
                  items,
                  detail.delivery(),
                  detail.cancellation());
            });
  }

  /**
   * Records the outcome of a manual check in CloudShop for an UNKNOWN order. The state stays
   * UNKNOWN, so the worker never sends the order again; a second check cannot overwrite the first.
   */
  @Transactional
  public Optional<Detail> reconcile(long number, String result, Long saleNumber, String staff) {
    if (!("FOUND".equals(result) || "NOT_CREATED".equals(result)))
      throw new Problem(400, "Укажите итог сверки.");
    if ("FOUND".equals(result) != (saleNumber != null))
      throw new Problem(400, "Номер продажи указывается, только если она найдена в CloudShop.");
    if (saleNumber != null && (saleNumber <= 0 || saleNumber > 999_999_999_999L))
      throw new Problem(400, "Некорректный номер продажи.");
    if (staff == null || staff.isBlank() || staff.length() > 64)
      throw new Problem(400, "Не удалось определить сотрудника.");
    int updated =
        jdbc.update(
            """
            update delivery_outbox set reconciled_result=?,reconciled_sale_number=?,reconciled_by=?,
            reconciled_at=now() where order_id=(select id from local_orders where number=?)
            and state='UNKNOWN' and reconciled_result is null
            """,
            result,
            saleNumber,
            staff,
            number);
    if (updated == 0) {
      // The guarded update is atomic; this read only explains why nothing changed.
      var state =
          jdbc.query(
              """
              select d.state,d.reconciled_result from local_orders o
              left join delivery_outbox d on d.order_id=o.id where o.number=?
              """,
              (rs, row) -> new String[] {rs.getString(1), rs.getString(2)},
              number);
      if (state.isEmpty()) return Optional.empty();
      if (!"UNKNOWN".equals(state.getFirst()[0]))
        throw new Problem(
            409, "Сверка нужна только для заявок с неизвестным результатом передачи.");
      throw new Problem(409, "Сверка по этой заявке уже записана.");
    }
    return detail(number);
  }

  /**
   * Records that staff cancelled the order after removing its sale in CloudShop by hand. Only for
   * orders whose delivery has finished (SENT or UNKNOWN), so the worker can no longer send them.
   */
  @Transactional
  public Optional<Detail> cancel(long number, String staff) {
    if (staff == null || staff.isBlank() || staff.length() > 64)
      throw new Problem(400, "Не удалось определить сотрудника.");
    int updated =
        jdbc.update(
            """
            update local_orders o set cancelled_at=now(),cancelled_by=?
            where o.number=? and o.cancelled_at is null
              and not exists (select 1 from delivery_outbox d where d.order_id=o.id
                and d.state not in ('SENT','UNKNOWN'))
            """,
            staff,
            number);
    if (updated == 0) {
      // The guarded update is atomic; this read only explains why nothing changed.
      var cancelled =
          jdbc.query(
              "select cancelled_at is not null from local_orders where number=?",
              (rs, row) -> rs.getBoolean(1),
              number);
      if (cancelled.isEmpty()) return Optional.empty();
      if (cancelled.getFirst()) throw new Problem(409, "Заявка уже отмечена отменённой.");
      throw new Problem(
          409,
          "Заявка ещё передаётся в CloudShop. Отметить отмену можно после передачи или сверки.");
    }
    return detail(number);
  }

  public static class Problem extends RuntimeException {
    private final int status;

    Problem(int status, String message) {
      super(message);
      this.status = status;
    }

    public int status() {
      return status;
    }
  }

  private static Delivery delivery(java.sql.ResultSet rs) throws java.sql.SQLException {
    Delivery base = delivery(rs.getString("delivery_state"));
    String result = rs.getString("reconciled_result");
    Reconciliation reconciliation =
        result == null
            ? null
            : new Reconciliation(
                result,
                rs.getObject("reconciled_sale_number", Long.class),
                rs.getString("reconciled_by"),
                rs.getTimestamp("reconciled_at").toInstant());
    String message =
        reconciliation == null
            ? base.message()
            : "FOUND".equals(result)
                ? "Сверено вручную: продажа №"
                    + reconciliation.saleNumber()
                    + " найдена в CloudShop."
                : "Сверено вручную: продажи в CloudShop нет, заявка не передана.";
    return new Delivery(base.state(), message, rs.getString("failure_reason"), reconciliation);
  }

  private static Cancellation cancellation(java.sql.ResultSet rs) throws java.sql.SQLException {
    var requested = rs.getTimestamp("cancel_requested_at");
    var cancelled = rs.getTimestamp("cancelled_at");
    if (requested == null && cancelled == null) return null;
    return new Cancellation(
        requested == null ? null : requested.toInstant(),
        rs.getString("cancel_reason"),
        cancelled == null ? null : cancelled.toInstant(),
        rs.getString("cancelled_by"));
  }

  static Delivery delivery(String state) {
    if (state == null) {
      return new Delivery(
          "NOT_PREPARED", "Передача ещё не подготовлена. Обратитесь к администратору.", null, null);
    }
    String message =
        switch (state) {
          case "WAITING_CONFIGURATION" -> "Передача ещё не подключена. Ожидает настройки.";
          case "READY" -> "Заявка подготовлена к передаче.";
          case "CUSTOMER_IN_FLIGHT" ->
              "Выполняется создание покупателя. Повтор запрещён до проверки результата.";
          case "CUSTOMER_CREATED" -> "Покупатель создан. Ожидается передача продажи.";
          case "SALE_IN_FLIGHT" ->
              "Выполняется создание непроведённой продажи. Повтор запрещён до проверки результата.";
          case "SENT" ->
              "Адаптер подтвердил создание непроведённой продажи. Это не подтверждает резерв или оплату.";
          case "UNKNOWN" ->
              "Результат передачи неизвестен. Нужна ручная сверка; автоматический повтор запрещён.";
          default -> "Неизвестное состояние передачи. Обратитесь к администратору.";
        };
    return new Delivery(state, message, null, null);
  }

  /**
   * {@code reason} is why the attempt became UNKNOWN; {@code reconciliation} is the staff check.
   */
  public record Delivery(
      String state, String message, String reason, Reconciliation reconciliation) {}

  /**
   * {@code requestedAt}/{@code reason}: the partner's request; {@code cancelledAt}/{@code by}: the
   * staff mark after removing the sale in CloudShop.
   */
  public record Cancellation(Instant requestedAt, String reason, Instant cancelledAt, String by) {}

  /** {@code result}: FOUND (sale number given) or NOT_CREATED. */
  public record Reconciliation(String result, Long saleNumber, String by, Instant at) {}

  static String money(BigDecimal value) {
    var format = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));
    format.setMinimumFractionDigits(0);
    format.setMaximumFractionDigits(2);
    return format.format(value) + " ₸";
  }

  public record Summary(
      String number,
      String status,
      Instant createdAt,
      String storeName,
      int itemCount,
      String totalText,
      Delivery delivery,
      Cancellation cancellation) {}

  public record Item(
      int position,
      String productName,
      String sku,
      String unit,
      int quantity,
      String priceText,
      String lineTotalText) {}

  public record Detail(
      String number,
      String status,
      Instant createdAt,
      String storeName,
      int itemCount,
      String totalText,
      String customerName,
      String phone,
      String comment,
      List<Item> items,
      Delivery delivery,
      Cancellation cancellation) {}

  public record Page(
      List<Summary> items, int page, int pageSize, long totalItems, int totalPages) {}
}
