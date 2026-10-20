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
        select o.number,o.status,o.created_at,o.store_name,o.item_count,o.total_text,d.state delivery_state
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
                    delivery(rs.getString("delivery_state"))),
            page * 20L);
    return new Page(items, page, 20, count, (int) Math.min(Integer.MAX_VALUE, (count + 19) / 20));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Optional<Detail> detail(long number) {
    return jdbc
        .query(
            """
        select o.number,o.status,o.created_at,o.store_name,o.item_count,o.total_text,
        o.customer_name,o.phone,o.comment,d.state delivery_state
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
                    delivery(rs.getString("delivery_state"))),
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
                  detail.delivery());
            });
  }

  static Delivery delivery(String state) {
    if (state == null) {
      return new Delivery(
          "NOT_PREPARED", "Передача ещё не подготовлена. Обратитесь к администратору.");
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
    return new Delivery(state, message);
  }

  public record Delivery(String state, String message) {}

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
      Delivery delivery) {}

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
      Delivery delivery) {}

  public record Page(
      List<Summary> items, int page, int pageSize, long totalItems, int totalPages) {}
}
