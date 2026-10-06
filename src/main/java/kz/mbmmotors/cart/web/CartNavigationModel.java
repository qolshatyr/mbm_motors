package kz.mbmmotors.cart.web;

import jakarta.servlet.http.HttpServletRequest;
import kz.mbmmotors.catalog.web.CatalogController;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {CatalogController.class, CartController.class})
class CartNavigationModel {
  @ModelAttribute("cartCount")
  int cartCount(HttpServletRequest request) {
    var session = request.getSession(false);
    if (session == null) return 0;
    var cart = CartController.sessionCart(session);
    synchronized (cart) {
      return cart.itemCount();
    }
  }
}
