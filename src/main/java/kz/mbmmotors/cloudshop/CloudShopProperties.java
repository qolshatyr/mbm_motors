package kz.mbmmotors.cloudshop;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudshop")
public record CloudShopProperties(String baseUrl, String token, Duration timeout) {
  // Configuration must never leak the token through generated record diagnostics.
  @Override
  public String toString() {
    return "CloudShopProperties[token=REDACTED]";
  }
}
