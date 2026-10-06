package kz.mbmmotors.cart.web;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import kz.mbmmotors.cart.Cart;
import kz.mbmmotors.cart.CartService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cart")
public class CartController {
  private static final String SESSION_KEY = "mbm.cart";
  private final CartService service;

  public CartController(CartService service) {
    this.service = service;
  }

  public static Cart sessionCart(HttpSession session) {
    synchronized (session) {
      Cart cart = (Cart) session.getAttribute(SESSION_KEY);
      if (cart == null) {
        cart = new Cart();
        session.setAttribute(SESSION_KEY, cart);
      }
      return cart;
    }
  }

  @GetMapping
  String view(HttpSession session, Model model) {
    Cart cart = sessionCart(session);
    synchronized (cart) {
      model.addAttribute("cart", service.view(cart));
    }
    return "cart/view";
  }

  @PostMapping("/add")
  String add(
      @RequestParam String productId,
      @RequestParam String storeId,
      @RequestParam(defaultValue = "1") int quantity,
      HttpSession session,
      RedirectAttributes flash) {
    Cart cart = sessionCart(session);
    synchronized (cart) {
      service.add(cart, productId, storeId, quantity);
    }
    flash.addFlashAttribute("notice", "Товар добавлен в корзину.");
    return "redirect:/cart";
  }

  @PostMapping("/update")
  String update(
      @RequestParam String productId,
      @RequestParam int quantity,
      HttpSession session,
      RedirectAttributes flash) {
    Cart cart = sessionCart(session);
    synchronized (cart) {
      service.update(cart, productId, quantity);
    }
    flash.addFlashAttribute("notice", "Количество обновлено.");
    return "redirect:/cart";
  }

  @PostMapping("/confirm-price")
  String confirmPrice(
      @RequestParam String productId,
      @RequestParam BigDecimal expectedPrice,
      HttpSession session,
      RedirectAttributes flash) {
    Cart cart = sessionCart(session);
    synchronized (cart) {
      service.confirmPrice(cart, productId, expectedPrice);
    }
    flash.addFlashAttribute("notice", "Цена подтверждена.");
    return "redirect:/cart";
  }

  @PostMapping("/remove")
  String remove(@RequestParam String productId, HttpSession session) {
    Cart cart = sessionCart(session);
    synchronized (cart) {
      service.remove(cart, productId);
    }
    return "redirect:/cart";
  }

  @PostMapping("/clear")
  String clear(HttpSession session) {
    Cart cart = sessionCart(session);
    synchronized (cart) {
      service.clear(cart);
    }
    return "redirect:/cart";
  }

  @ExceptionHandler(CartService.CartProblem.class)
  String problem(CartService.CartProblem problem, RedirectAttributes flash) {
    flash.addFlashAttribute("problem", problem.getMessage());
    return "redirect:/cart";
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  String invalidQuantity(RedirectAttributes flash) {
    flash.addFlashAttribute(
        "problem",
        "Проверьте числовые параметры: количество должно быть целым от 1 до 999, цена — числом.");
    return "redirect:/cart";
  }
}
