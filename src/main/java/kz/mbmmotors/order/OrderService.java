package kz.mbmmotors.order;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import kz.mbmmotors.cart.Cart;
import kz.mbmmotors.cart.CartService;
import kz.mbmmotors.cart.web.CartApiController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private final JdbcTemplate jdbc;
  private final CartService carts;
  private final CheckoutProperties properties;
  private final Clock clock;

  public OrderService(
      JdbcTemplate jdbc, CartService carts, CheckoutProperties properties, Clock clock) {
    this.jdbc = jdbc;
    this.carts = carts;
    this.properties = properties;
    this.clock = clock;
  }

  /** {@code contact} prefills the form from the partner account; the partner may edit it. */
  @Transactional(readOnly = true)
  public Preview preview(Cart cart, CheckoutSession state, Contact contact) {
    var view = carts.view(cart);
    String revision = revision(view);
    state.issue(revision);
    var issues = issues(view);
    return new Preview(
        toView(view), revision, state.key().toString(), issues.isEmpty(), issues, contact);
  }

  /** Caller holds Cart monitor until this proxy returns, including transaction commit. */
  @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
  public Result submit(Cart cart, CheckoutSession state, SubmitOrder command, UUID partnerId) {
    // Transaction-scoped lock also protects against duplicate requests on separate connections.
    jdbc.queryForList(
        "select pg_advisory_xact_lock(?)",
        command.key().getMostSignificantBits() ^ command.key().getLeastSignificantBits());
    String hash =
        hash(
            command.revision(), command.customerName(), command.phone(), command.comment(), "true");
    var saved =
        jdbc.query(
            "select owner_id,request_hash from local_orders where idempotency_key=?",
            (rs, row) -> new Saved(rs.getObject(1, UUID.class), rs.getString(2)),
            command.key());
    if (!saved.isEmpty()) {
      if (!state.ownerId().equals(saved.getFirst().owner())
          || !hash.equals(saved.getFirst().hash()))
        throw new OrderProblem(409, "Этот ключ уже использован. Обновите страницу оформления.");
      return new Result(receipt(state.ownerId(), command.key()), false);
    }
    if (!command.key().equals(state.key()) || !command.revision().equals(state.revision()))
      throw new OrderProblem(409, "Оформление изменилось. Проверьте корзину и обновите страницу.");
    var before = carts.view(cart);
    if (before.items().isEmpty()) throw new OrderProblem(409, "Корзина пуста.");
    // Stable global order prevents lock inversions between overlapping carts and catalog updates.
    jdbc.queryForList("select id from stores where id=? for share", before.storeId());
    for (String id : before.items().stream().map(CartService.CartItem::id).sorted().toList()) {
      jdbc.queryForList("select id from products where id=? for share", id);
      jdbc.queryForList(
          "select product_id from product_offers where product_id=? and store_id=? for share",
          id,
          before.storeId());
    }
    var view = carts.view(cart);
    var issues = issues(view);
    if (!issues.isEmpty()) throw new OrderProblem(409, String.join(" ", issues));
    if (!command.revision().equals(revision(view)))
      throw new OrderProblem(
          409, "Состав или цены корзины изменились. Проверьте корзину перед отправкой.");
    UUID orderId = UUID.randomUUID();
    Instant now = clock.instant();
    BigDecimal total =
        view.items().stream()
            .map(i -> i.currentPrice().multiply(BigDecimal.valueOf(i.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    int count = view.items().stream().mapToInt(CartService.CartItem::quantity).sum();
    jdbc.update(
        """
      insert into local_orders(id,idempotency_key,owner_id,request_hash,revision,status,store_id,store_name,
      customer_name,phone,comment,consent_at,created_at,total,total_text,item_count,partner_id)
      values (?,?,?,?,?,'RECEIVED',?,?,?,?,?,?,?,?,?,?,?)
      """,
        orderId,
        command.key(),
        state.ownerId(),
        hash,
        command.revision(),
        view.storeId(),
        view.storeName(),
        command.customerName(),
        command.phone(),
        command.comment(),
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now),
        total,
        view.totalText(),
        count,
        partnerId);
    int position = 0;
    for (var item : view.items()) {
      jdbc.update(
          """
        insert into local_order_items(order_id,position,product_id,product_name,sku,unit,quantity,price,line_total)
        values (?,?,?,?,?,?,?,?,?)
        """,
          orderId,
          position++,
          item.id(),
          item.name(),
          item.sku(),
          item.unit(),
          item.quantity(),
          item.currentPrice(),
          item.currentPrice().multiply(BigDecimal.valueOf(item.quantity())));
    }
    jdbc.update(
        "insert into delivery_outbox(order_id,state,created_at) values (?,'WAITING_CONFIGURATION',?)",
        orderId,
        java.sql.Timestamp.from(now));
    return new Result(receipt(state.ownerId(), command.key()), true);
  }

  @Transactional(readOnly = true)
  public Receipt receipt(UUID owner, UUID key) {
    var values =
        jdbc.query(
            "select number,store_name,item_count,total_text,created_at from local_orders where owner_id=? and idempotency_key=?",
            (rs, row) ->
                new Receipt(
                    "MBM-" + rs.getLong("number"),
                    "RECEIVED",
                    rs.getString("store_name"),
                    rs.getInt("item_count"),
                    rs.getString("total_text"),
                    rs.getTimestamp("created_at").toInstant(),
                    "Заявка сохранена на сайте. Передача магазину пока не подключена; наличие не зарезервировано."),
            owner,
            key);
    if (values.isEmpty()) throw new OrderProblem(404, "Заявка для этой сессии не найдена.");
    return values.getFirst();
  }

  private List<String> issues(CartService.CartView view) {
    var issues = new ArrayList<String>();
    if (view.items().isEmpty()) issues.add("Добавьте товары в корзину.");
    else {
      if (!properties.getPickupStoreIds().contains(view.storeId()))
        issues.add(
            "Самовывоз из выбранной точки временно недоступен. Уточните возможность получения в магазине.");
      if (view.hasIssues())
        issues.add("Проверьте наличие, количество и подтвердите изменившиеся цены в корзине.");
    }
    return List.copyOf(issues);
  }

  static String revision(CartService.CartView view) {
    var fields = new ArrayList<String>();
    fields.add(view.storeId());
    fields.add(view.storeName());
    for (var item : view.items()) {
      fields.add(item.id());
      fields.add(item.name());
      fields.add(item.sku());
      fields.add(item.unit());
      fields.add(Integer.toString(item.quantity()));
      fields.add(
          item.currentPrice() == null
              ? null
              : item.currentPrice().stripTrailingZeros().toPlainString());
      fields.add(item.problem());
      fields.add(Boolean.toString(item.priceChanged()));
    }
    return hash(fields.toArray(String[]::new));
  }

  static String hash(String... fields) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      for (String field : fields) {
        byte[] bytes = field == null ? new byte[0] : field.getBytes(StandardCharsets.UTF_8);
        digest.update(
            java.nio.ByteBuffer.allocate(4).putInt(field == null ? -1 : bytes.length).array());
        digest.update(bytes);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  static CartApiController.View toView(CartService.CartView view) {
    return new CartApiController.View(
        view.storeId(),
        view.storeName(),
        view.items().stream()
            .map(
                i ->
                    new CartApiController.Item(
                        i.id(),
                        i.name(),
                        i.sku(),
                        i.quantity(),
                        i.unit(),
                        i.priceText(),
                        i.totalText(),
                        i.problem(),
                        i.priceChanged(),
                        i.currentPrice() == null ? null : i.currentPrice().toPlainString()))
            .toList(),
        view.totalText(),
        view.hasIssues());
  }

  private record Saved(UUID owner, String hash) {}

  public record Preview(
      CartApiController.View cart,
      String revision,
      String idempotencyKey,
      boolean canSubmit,
      List<String> issues,
      Contact contact) {}

  public record Contact(String customerName, String phone) {}

  public record Receipt(
      String number,
      String status,
      String storeName,
      int itemCount,
      String totalText,
      Instant createdAt,
      String message) {}

  public record Result(Receipt receipt, boolean created) {}
}
