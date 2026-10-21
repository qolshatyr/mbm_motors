package kz.mbmmotors.config.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Explicit SPA routes; API paths and unknown URLs never fall back to HTML. */
@Controller
@ConditionalOnProperty(name = "frontend.enabled", havingValue = "true")
public class FrontendController {
  // Keep in sync with WebSecurityConfiguration.SPA_ROUTES; "/" is served by CatalogController.
  @GetMapping({
    "/login",
    "/search",
    "/products/{id}",
    "/quick-order",
    "/basket",
    "/checkout",
    "/checkout/success",
    "/account",
    "/admin/requests",
    "/admin/requests/{number}",
    "/admin/clients"
  })
  String page() {
    return "forward:/frontend/index.html";
  }
}
