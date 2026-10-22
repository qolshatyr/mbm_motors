package kz.mbmmotors.cart.web;

import jakarta.servlet.http.HttpSession;
import kz.mbmmotors.cart.Cart;

/** The partner's cart lives in the server session; callers synchronize on the returned cart. */
public final class SessionCart {
  private static final String SESSION_KEY = "mbm.cart";

  private SessionCart() {}

  public static Cart of(HttpSession session) {
    synchronized (session) {
      Cart cart = (Cart) session.getAttribute(SESSION_KEY);
      if (cart == null) {
        cart = new Cart();
        session.setAttribute(SESSION_KEY, cart);
      }
      return cart;
    }
  }
}
