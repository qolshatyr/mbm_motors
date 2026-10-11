package kz.mbmmotors.config.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Explicit SPA routes; API paths and unknown URLs never fall back to HTML. */
@Controller
@ConditionalOnProperty(name = "frontend.enabled", havingValue = "true")
public class FrontendController {
  @GetMapping({
    "/products/{id}",
    "/basket",
    "/checkout",
    "/checkout/success",
    "/admin/requests",
    "/admin/requests/{number}"
  })
  String page() {
    return "forward:/frontend/index.html";
  }
}
