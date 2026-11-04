package kz.mbmmotors.config.web;

import java.net.URI;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Files browsers and crawlers ask for at fixed addresses; public, without signing in. */
@RestController
public class SiteFilesController {
  /** The portal is closed to the public, so nothing is to be indexed. */
  @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
  ResponseEntity<String> robots() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
        .body("User-agent: *\nDisallow: /\n");
  }

  @GetMapping("/favicon.ico")
  ResponseEntity<Void> favicon() {
    return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
        .location(URI.create("/frontend/favicon.svg"))
        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
        .build();
  }
}
