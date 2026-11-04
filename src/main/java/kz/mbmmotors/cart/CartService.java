package kz.mbmmotors.cart;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kz.mbmmotors.catalog.CatalogViewService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CartService {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public CartService(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public void add(Cart cart, String productId, String storeId, int quantity) {
    synchronized (cart) {
      validateQuantity(quantity);
      if (!CatalogViewService.DEFAULT_STORE.equals(storeId))
        throw new CartProblem("Товары можно заказать только со склада MBM.");
      if (!cart.isEmpty() && !cart.storeId.equals(storeId))
        throw new CartProblem("В корзине товары другого магазина. Сначала очистите корзину.");
      if (!cart.entries.containsKey(productId) && cart.entries.size() >= 50)
        throw new CartProblem("В корзину можно добавить не более 50 разных товаров.");
      Cart.Entry old = cart.entries.get(productId);
      int total = quantity + (old == null ? 0 : old.quantity());
      if (total > 999) throw new CartProblem("В одной строке корзины может быть не больше 999 шт.");
      Data product = load(productId, storeId);
      requireValid(product, total, stale());
      cart.storeId = storeId;
      cart.entries.put(
          productId, new Cart.Entry(total, old == null ? product.price() : old.acceptedPrice()));
    }
  }

  /**
   * Adds as much of {@code quantity} as the store can supply now, for repeating an earlier order.
   * Never throws for an unavailable product: the outcome says what was added and why not all.
   */
  public Added addAvailable(Cart cart, String productId, String storeId, int quantity) {
    synchronized (cart) {
      if (quantity < 1) return new Added(0, "Количество в заявке некорректно.");
      if (!cart.isEmpty() && !cart.storeId.equals(storeId))
        return new Added(0, "В корзине товары другого магазина.");
      if (!cart.entries.containsKey(productId) && cart.entries.size() >= 50)
        return new Added(0, "В корзине уже 50 разных товаров.");
      Data product;
      try {
        product = load(productId, storeId);
      } catch (CartProblem invalid) {
        return new Added(0, invalid.getMessage());
      }
      Cart.Entry old = cart.entries.get(productId);
      int existing = old == null ? 0 : old.quantity();
      int wanted = Math.min(quantity, 999 - existing);
      if (wanted < 1) return new Added(0, "В корзине уже 999 шт. этого товара.");
      boolean stale = stale();
      String problem = problem(product, existing + wanted, stale);
      int added = wanted;
      if (problem != null && product != null && product.stock() != null) {
        int available = product.stock().intValue() - existing;
        if (available >= 1
            && available < wanted
            && problem(product, existing + available, stale) == null) {
          added = available;
          problem = "Остаток меньше, чем в заявке.";
        }
      }
      if (added == wanted && problem != null) return new Added(0, problem);
      cart.storeId = storeId;
      cart.entries.put(
          productId,
          new Cart.Entry(existing + added, old == null ? product.price() : old.acceptedPrice()));
      return new Added(added, problem);
    }
  }

  /** {@code problem} is null when the whole quantity was added. */
  public record Added(int quantity, String problem) {}

  public void update(Cart cart, String productId, int quantity) {
    synchronized (cart) {
      validateQuantity(quantity);
      if (!cart.entries.containsKey(productId))
        throw new CartProblem("Товар отсутствует в корзине.");
      Data product = load(productId, cart.storeId);
      requireValid(product, quantity, stale());
      cart.entries.put(
          productId, new Cart.Entry(quantity, cart.entries.get(productId).acceptedPrice()));
    }
  }

  public void confirmPrice(Cart cart, String productId, BigDecimal expectedPrice) {
    synchronized (cart) {
      Cart.Entry entry = cart.entries.get(productId);
      if (entry == null) throw new CartProblem("Товар отсутствует в корзине.");
      Data product = load(productId, cart.storeId);
      requireValid(product, entry.quantity(), stale());
      if (expectedPrice == null || product.price().compareTo(expectedPrice) != 0)
        throw new CartProblem("Цена снова изменилась. Обновите страницу и подтвердите новую цену.");
      cart.entries.put(productId, new Cart.Entry(entry.quantity(), product.price()));
    }
  }

  public void remove(Cart cart, String productId) {
    synchronized (cart) {
      cart.entries.remove(productId);
      if (cart.isEmpty()) cart.storeId = null;
    }
  }

  public void clear(Cart cart) {
    synchronized (cart) {
      cart.entries.clear();
      cart.storeId = null;
    }
  }

  public CartView view(Cart cart) {
    synchronized (cart) {
      if (cart.isEmpty()) return new CartView(null, null, List.of(), money(BigDecimal.ZERO), false);
      boolean stale = stale();
      List<CartItem> items = new ArrayList<>();
      BigDecimal total = BigDecimal.ZERO;
      String storeName = null;
      for (var entry : cart.entries.entrySet()) {
        Data product = load(entry.getKey(), cart.storeId);
        int quantity = entry.getValue().quantity();
        String problem = problem(product, quantity, stale);
        boolean changed =
            product != null
                && product.price() != null
                && product.price().compareTo(entry.getValue().acceptedPrice()) != 0;
        BigDecimal lineTotal =
            product == null || product.price() == null
                ? null
                : product.price().multiply(BigDecimal.valueOf(quantity));
        if (problem == null) total = total.add(lineTotal);
        if (product != null) storeName = product.storeName();
        items.add(
            new CartItem(
                entry.getKey(),
                product == null ? "Товар недоступен" : product.name(),
                product == null ? null : product.sku(),
                quantity,
                product == null ? null : product.unit(),
                product == null || product.price() == null || product.price().signum() <= 0
                    ? "Цена уточняется"
                    : money(product.price()),
                lineTotal == null || lineTotal.signum() <= 0 ? "—" : money(lineTotal),
                problem,
                changed,
                product == null ? null : product.price()));
      }
      return new CartView(
          cart.storeId,
          storeName,
          List.copyOf(items),
          money(total),
          items.stream().anyMatch(item -> item.problem() != null || item.priceChanged()));
    }
  }

  private Data load(String productId, String storeId) {
    if (productId == null
        || !productId.matches("[a-fA-F0-9]{24}")
        || storeId == null
        || !storeId.matches("[a-fA-F0-9]{24}"))
      throw new CartProblem("Не удалось найти товар или магазин.");
    List<Data> data =
        jdbc.query(
            """
        select p.name,p.sku,p.unit,o.price,o.quantity,s.name as store_name
        from products p join product_offers o on o.product_id=p.id
        join stores s on s.id=o.store_id where p.id=? and s.id=?
        """,
            (rs, row) ->
                new Data(
                    rs.getString("name"),
                    rs.getString("sku"),
                    rs.getString("unit"),
                    rs.getBigDecimal("price"),
                    rs.getBigDecimal("quantity"),
                    rs.getString("store_name")),
            productId,
            storeId);
    return data.isEmpty() ? null : data.getFirst();
  }

  private boolean stale() {
    Timestamp last =
        jdbc.queryForObject(
            "select max(finished_at) from sync_runs where status='SUCCESS'", Timestamp.class);
    return last == null || last.toInstant().plus(Duration.ofMinutes(15)).isBefore(clock.instant());
  }

  private static void validateQuantity(int quantity) {
    if (quantity < 1 || quantity > 999) throw new InvalidQuantity();
  }

  private static void requireValid(Data product, int quantity, boolean stale) {
    String problem = problem(product, quantity, stale);
    if (problem != null) throw new CartProblem(problem);
  }

  private static String problem(Data product, int quantity, boolean stale) {
    if (product == null) return "Товар недоступен в выбранном магазине.";
    return kz.mbmmotors.catalog.ProductAvailability.problem(
        product.price(), product.stock(), product.unit(), quantity, stale);
  }

  private static String money(BigDecimal value) {
    var format = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));
    format.setMinimumFractionDigits(0);
    format.setMaximumFractionDigits(2);
    return format.format(value) + " ₸";
  }

  private record Data(
      String name, String sku, String unit, BigDecimal price, BigDecimal stock, String storeName) {}

  public record CartView(
      String storeId,
      String storeName,
      List<CartItem> items,
      String totalText,
      boolean hasIssues) {}

  public record CartItem(
      String id,
      String name,
      String sku,
      int quantity,
      String unit,
      String priceText,
      String totalText,
      String problem,
      boolean priceChanged,
      BigDecimal currentPrice) {}

  public static class CartProblem extends RuntimeException {
    public CartProblem(String message) {
      super(message);
    }
  }

  /** A malformed request rather than a conflict with the cart or stock: HTTP 400. */
  public static class InvalidQuantity extends CartProblem {
    public InvalidQuantity() {
      super("Укажите целое количество от 1 до 999.");
    }
  }
}
