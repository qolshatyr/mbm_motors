package kz.mbmmotors.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebHeadersConfiguration implements WebMvcConfigurer {
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new HandlerInterceptor() {
          public boolean preHandle(
              HttpServletRequest request, HttpServletResponse response, Object handler) {
            response.setHeader(
                "Content-Security-Policy",
                "default-src 'self'; img-src 'self' https:; style-src 'self'; script-src 'self'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            return true;
          }
        });
  }
}
