package kz.mbmmotors.cart.web;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import kz.mbmmotors.cart.Cart;
import kz.mbmmotors.cart.CartService;
import kz.mbmmotors.cart.CartStore;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/cart")
public class CartApiController {
  private final CartService service;
  private final CartStore store;

  public CartApiController(CartService service, CartStore store) {
    this.service = service;
    this.store = store;
  }

  @GetMapping
  ResponseEntity<CartSnapshot> view(HttpSession session, Authentication partner) {
    return change(session, partner, null, cart -> {});
  }

  @PostMapping(value = "/add", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  ResponseEntity<CartSnapshot> add(
      @RequestParam String productId,
      @RequestParam String storeId,
      @RequestParam(defaultValue = "1") int quantity,
      HttpSession session,
      Authentication partner) {
    return change(
        session,
        partner,
        "Товар добавлен в корзину.",
        cart -> service.add(cart, productId, storeId, quantity));
  }

  @PostMapping(value = "/update", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  ResponseEntity<CartSnapshot> update(
      @RequestParam String productId,
      @RequestParam int quantity,
      HttpSession session,
      Authentication partner) {
    return change(
        session,
        partner,
        "Количество обновлено.",
        cart -> service.update(cart, productId, quantity));
  }

  @PostMapping(value = "/confirm-price", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  ResponseEntity<CartSnapshot> confirmPrice(
      @RequestParam String productId,
      @RequestParam BigDecimal expectedPrice,
      HttpSession session,
      Authentication partner) {
    return change(
        session,
        partner,
        "Цена подтверждена.",
        cart -> service.confirmPrice(cart, productId, expectedPrice));
  }

  @PostMapping(value = "/remove", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  ResponseEntity<CartSnapshot> remove(
      @RequestParam String productId, HttpSession session, Authentication partner) {
    return change(
        session, partner, "Товар удалён из корзины.", cart -> service.remove(cart, productId));
  }

  @PostMapping(value = "/clear", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  ResponseEntity<CartSnapshot> clear(HttpSession session, Authentication partner) {
    return change(session, partner, "Корзина очищена.", service::clear);
  }

  private ResponseEntity<CartSnapshot> change(
      HttpSession session, Authentication partner, String message, Consumer<Cart> action) {
    Cart cart = SessionCart.of(session, store, partner);
    synchronized (cart) {
      action.accept(cart);
      if (message != null) SessionCart.save(store, partner, cart);
      var view = service.view(cart);
      var items = view.items().stream().map(Item::from).toList();
      var snapshot =
          new CartSnapshot(
              new View(view.storeId(), view.storeName(), items, view.totalText(), view.hasIssues()),
              cart.itemCount(),
              message);
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshot);
    }
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class
  })
  ProblemDetail invalidParameters() {
    return problem(
        HttpStatus.BAD_REQUEST,
        "urn:mbm:cart:invalid-parameters",
        "Проверьте параметры: количество должно быть целым от 1 до 999, цена — числом; обязательные поля должны быть заполнены.");
  }

  @ExceptionHandler(CartService.CartProblem.class)
  ProblemDetail cartProblem(CartService.CartProblem error) {
    if (error instanceof CartService.InvalidQuantity)
      return problem(HttpStatus.BAD_REQUEST, "urn:mbm:cart:invalid-parameters", error.getMessage());
    return problem(HttpStatus.CONFLICT, "urn:mbm:cart:conflict", error.getMessage());
  }

  private static ProblemDetail problem(HttpStatus status, String type, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(type));
    return problem;
  }

  public record CartSnapshot(View cart, int itemCount, String message) {}

  public record View(
      String storeId, String storeName, List<Item> items, String totalText, boolean hasIssues) {}

  public record Item(
      String id,
      String name,
      String sku,
      int quantity,
      String unit,
      String priceText,
      String totalText,
      String problem,
      boolean priceChanged,
      String currentPrice) {
    static Item from(CartService.CartItem item) {
      return new Item(
          item.id(),
          item.name(),
          item.sku(),
          item.quantity(),
          item.unit(),
          item.priceText(),
          item.totalText(),
          item.problem(),
          item.priceChanged(),
          item.currentPrice() == null ? null : item.currentPrice().toPlainString());
    }
  }
}
