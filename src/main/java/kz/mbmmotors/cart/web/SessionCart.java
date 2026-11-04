package kz.mbmmotors.cart.web;

import jakarta.servlet.http.HttpSession;
import kz.mbmmotors.cart.Cart;
import kz.mbmmotors.cart.CartStore;
import org.springframework.security.core.Authentication;

/**
 * The partner's cart lives in the server session; callers synchronize on the returned cart. A new
 * session of a signed-in partner starts from the cart saved in {@link CartStore}.
 */
public final class SessionCart {
  private static final String SESSION_KEY = "mbm.cart";

  private SessionCart() {}

  public static Cart of(HttpSession session) {
    return of(session, null, null);
  }

  public static Cart of(HttpSession session, CartStore store, Authentication partner) {
    synchronized (session) {
      Cart cart = (Cart) session.getAttribute(SESSION_KEY);
      if (cart == null) {
        if (store != null && partner != null) cart = store.load(partner.getName());
        if (cart == null) cart = new Cart();
        session.setAttribute(SESSION_KEY, cart);
      }
      return cart;
    }
  }

  /** Saves the cart for the partner's next session; does nothing without a signed-in partner. */
  public static void save(CartStore store, Authentication partner, Cart cart) {
    if (store != null && partner != null) store.save(partner.getName(), cart);
  }
}
