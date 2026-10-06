package kz.mbmmotors.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebSecurityConfiguration {
  @Bean
  SecurityFilterChain publicCatalogSecurity(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/catalog/stores", "/api/catalog/products")
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
                      response.setContentType("text/plain;charset=UTF-8");
                      response
                          .getWriter()
                          .write("Запрос отклонён. Обновите страницу и повторите действие.");
                    }))
        .build();
  }

  // No accounts or login endpoints exist yet; avoid Boot's generated development user.
  @Bean
  UserDetailsService noAccountsYet() {
    return username -> {
      throw new UsernameNotFoundException("Authentication is not configured");
    };
  }
}
