package kz.mbmmotors.cart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps a copy of each partner's cart in the database, so it survives logout and session expiry.
 * The session cart stays the working copy; it is saved after every change and restored when a new
 * session of the partner has none. With several sessions of one partner, the last change wins.
 */
@Repository
public class CartStore {
  private final JdbcTemplate jdbc;

  public CartStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The saved cart of the partner with this login; empty when none is saved. */
  @Transactional(readOnly = true)
  public Cart load(String login) {
    Cart cart = new Cart();
    jdbc.query(
        """
        select c.product_id,c.store_id,c.quantity,c.accepted_price from partner_cart_items c
        join partners p on p.id=c.partner_id where p.login=? order by c.position
        """,
        rs -> {
          cart.storeId = rs.getString("store_id");
          cart.entries.put(
              rs.getString("product_id"),
              new Cart.Entry(rs.getInt("quantity"), rs.getBigDecimal("accepted_price")));
        },
        login);
    return cart;
  }

  /** Replaces the saved cart of the partner with this login; unknown logins are ignored. */
  @Transactional
  public void save(String login, Cart cart) {
    String storeId;
    Map<String, Cart.Entry> entries;
    synchronized (cart) {
      storeId = cart.storeId;
      entries = new LinkedHashMap<>(cart.entries);
    }
    jdbc.update(
        "delete from partner_cart_items where partner_id=(select id from partners where login=?)",
        login);
    if (entries.isEmpty()) return;
    var rows = new ArrayList<Object[]>();
    int position = 0;
    for (var entry : entries.entrySet()) {
      rows.add(
          new Object[] {
            entry.getKey(),
            storeId,
            position++,
            entry.getValue().quantity(),
            entry.getValue().acceptedPrice(),
            login
          });
    }
    jdbc.batchUpdate(
        """
        insert into partner_cart_items(partner_id,product_id,store_id,position,quantity,accepted_price)
        select id,?,?,?,?,? from partners where login=?
        """,
        rows);
  }
}
