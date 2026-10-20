package kz.mbmmotors.config.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
  @GetMapping("/api/csrf")
  ResponseEntity<Token> token(CsrfToken token) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new Token(token.getHeaderName(), token.getToken()));
  }

  public record Token(String headerName, String token) {}
}
