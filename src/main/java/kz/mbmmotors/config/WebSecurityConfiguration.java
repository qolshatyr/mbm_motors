package kz.mbmmotors.config;

import kz.mbmmotors.admin.AdminProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebSecurityConfiguration {
  @Bean
  SecurityFilterChain publicCatalogSecurity(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        HttpMethod.GET,
                        "/api/catalog/stores",
                        "/api/catalog/products",
                        "/api/catalog/products/{id}",
                        "/api/cart",
                        "/api/csrf",
                        "/frontend/**",
                        "/basket",
                        "/products/{id}",
                        "/checkout",
                        "/checkout/success",
                        "/api/checkout",
                        "/api/orders/by-key/{key}")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/cart/add",
                        "/api/cart/update",
                        "/api/cart/remove",
                        "/api/cart/clear",
                        "/api/cart/confirm-price",
                        "/api/orders")
                    .permitAll()
                    .requestMatchers(
                        "/",
                        "/catalog/**",
                        "/cart/**",
                        "/css/**",
                        "/images/**",
                        "/webjars/**",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .requestCache(cache -> cache.disable())
        .logout(logout -> logout.disable())
        .headers(
            headers ->
                headers.contentSecurityPolicy(
                    csp ->
                        csp.policyDirectives(
                            "default-src 'self'; img-src 'self' https:; style-src 'self'; script-src 'self'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")))
        .exceptionHandling(
            errors ->
                errors.accessDeniedHandler(
                    (request, response, error) -> {
                      response.setStatus(403);
                      if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                        response.setContentType("application/problem+json;charset=UTF-8");
                        response
                            .getWriter()
                            .write(
                                "{\"type\":\"urn:mbm:security:forbidden\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"Запрос отклонён. Обновите страницу и повторите действие.\"}");
                        return;
                      }
                      response.setContentType("text/plain;charset=UTF-8");
                      response
                          .getWriter()
                          .write("Запрос отклонён. Обновите страницу и повторите действие.");
                    }))
        .build();
  }

  @Bean
  PasswordEncoder staffPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService accounts(AdminProperties properties) {
    properties.validate();
    if (properties.isEnabled()) {
      return new InMemoryUserDetailsManager(
          User.withUsername(properties.getUsername())
              .password(properties.getPasswordHash())
              .roles("STAFF")
              .build());
    }
    return username -> {
      throw new UsernameNotFoundException("Authentication is not configured");
    };
  }
}
