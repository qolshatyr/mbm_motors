package kz.mbmmotors.order.web;

import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.UUID;
import kz.mbmmotors.cart.CartService;
import kz.mbmmotors.cart.web.SessionCart;
import kz.mbmmotors.order.*;
import kz.mbmmotors.partner.PartnerAccountService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class CheckoutController {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(CheckoutController.class);
  private final OrderService orders;
  private final PartnerAccountService partners;

  public CheckoutController(OrderService orders, PartnerAccountService partners) {
    this.orders = orders;
    this.partners = partners;
  }

  @GetMapping("/api/checkout")
  ResponseEntity<OrderService.Preview> preview(HttpSession session, Authentication authentication) {
    var partner = partners.current(authentication);
    var contact = new OrderService.Contact(partner.contactName(), partner.phone());
    var cart = SessionCart.of(session);
    synchronized (cart) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.noStore())
          .body(orders.preview(cart, state(session), contact));
    }
  }

  @PostMapping(value = "/api/orders", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  ResponseEntity<OrderService.Receipt> submit(
      @RequestParam(required = false) String idempotencyKey,
      @RequestParam(required = false) String revision,
      @RequestParam(required = false) String customerName,
      @RequestParam(required = false) String phone,
      @RequestParam(required = false) String comment,
      @RequestParam(required = false) String consent,
      HttpSession session,
      Authentication authentication) {
    // Contacts are validated before checking business state, so form errors always have HTTP400.
    var command =
        SubmitOrder.validate(idempotencyKey, revision, customerName, phone, comment, consent);
    var partner = partners.current(authentication);
    var cart = SessionCart.of(session);
    synchronized (cart) {
      var checkout = state(session);
      var result = orders.submit(cart, checkout, command, partner.id());
      // OrderService's transaction interceptor has committed before this statement.
      if (result.created()) {
        cart.clearSelection();
        checkout.completed();
        log.info(
            "Local order received: number={}, itemCount={}",
            result.receipt().number(),
            result.receipt().itemCount());
      }
      return ResponseEntity.status(result.created() ? 201 : 200)
          .cacheControl(CacheControl.noStore())
          .body(result.receipt());
    }
  }

  @GetMapping("/api/orders/by-key/{key}")
  ResponseEntity<OrderService.Receipt> recover(@PathVariable String key, HttpSession session) {
    UUID id;
    try {
      id = UUID.fromString(key);
      if (!id.toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
    } catch (IllegalArgumentException e) {
      throw new OrderProblem(400, "Неверный формат ключа заявки.");
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(orders.receipt(state(session).ownerId(), id));
  }

  private static CheckoutSession state(HttpSession session) {
    synchronized (session) {
      Object existing = session.getAttribute("mbm.checkout");
      if (existing instanceof CheckoutSession state) return state;
      var state = new CheckoutSession();
      session.setAttribute("mbm.checkout", state);
      return state;
    }
  }

  @ExceptionHandler(OrderProblem.class)
  ResponseEntity<ProblemDetail> problem(OrderProblem error) {
    var detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(error.status()), error.getMessage());
    detail.setType(URI.create("urn:mbm:checkout:" + error.status()));
    if (!error.errors().isEmpty()) detail.setProperty("errors", error.errors());
    return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore()).body(detail);
  }

  @ExceptionHandler(PartnerAccountService.AccountProblem.class)
  ResponseEntity<ProblemDetail> accountProblem(PartnerAccountService.AccountProblem error) {
    return problem(new OrderProblem(error.status(), error.getMessage()));
  }

  @ExceptionHandler(CartService.CartProblem.class)
  ResponseEntity<ProblemDetail> cartProblem(CartService.CartProblem error) {
    return problem(new OrderProblem(409, error.getMessage()));
  }
}
