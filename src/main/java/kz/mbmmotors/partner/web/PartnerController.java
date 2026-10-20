package kz.mbmmotors.partner.web;

import java.net.URI;
import kz.mbmmotors.partner.Partner;
import kz.mbmmotors.partner.PartnerAccountService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PartnerController {
  private final PartnerAccountService accounts;

  public PartnerController(PartnerAccountService accounts) {
    this.accounts = accounts;
  }

  /** Public endpoint: lets the interface decide between the login screen and the portal. */
  @GetMapping("/api/auth/me")
  ResponseEntity<Partner.Profile> me(Authentication authentication) {
    return noStore(accounts.current(authentication).profile());
  }

  @GetMapping("/api/account/orders")
  ResponseEntity<PartnerAccountService.Orders> orders(Authentication authentication) {
    return noStore(accounts.orders(accounts.current(authentication)));
  }

  @GetMapping("/api/account/orders/{number}")
  ResponseEntity<PartnerAccountService.OrderItems> order(
      @PathVariable String number, Authentication authentication) {
    return noStore(accounts.items(accounts.current(authentication), number));
  }

  @ExceptionHandler(PartnerAccountService.AccountProblem.class)
  ResponseEntity<ProblemDetail> problem(PartnerAccountService.AccountProblem error) {
    var detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(error.status()), error.getMessage());
    detail.setType(URI.create("urn:mbm:account:" + error.status()));
    return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore()).body(detail);
  }

  private static <T> ResponseEntity<T> noStore(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
