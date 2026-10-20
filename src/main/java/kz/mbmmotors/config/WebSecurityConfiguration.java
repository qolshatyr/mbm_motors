package kz.mbmmotors.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import kz.mbmmotors.admin.AdminProperties;
import kz.mbmmotors.partner.LoginThrottle;
import kz.mbmmotors.partner.Partner;
import kz.mbmmotors.partner.PartnerRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/** Partner portal: everything except the sign-in surface requires a partner account. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebSecurityConfiguration {
  private static final String LOGIN = "/api/auth/login";
  static final String CSP =
      "default-src 'self'; img-src 'self' https:; style-src 'self'; script-src 'self'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'";

  /** Single-page routes only render the interface; their data comes from protected APIs. */
  public static final String[] SPA_ROUTES = {
    "/",
    "/login",
    "/search",
    "/products/{id}",
    "/quick-order",
    "/basket",
    "/checkout",
    "/checkout/success",
    "/account"
  };

  @Bean
  SecurityFilterChain partnerSecurity(
      HttpSecurity http,
      ObjectProvider<PartnerRepository> partners,
      PasswordEncoder encoder,
      JsonMapper json)
      throws Exception {
    // A dedicated manager without the global parent: staff credentials never open the portal.
    UserDetailsService details =
        login ->
            partners
                .getObject()
                .find(login)
                .map(Partner::userDetails)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown partner"));
    var provider = new DaoAuthenticationProvider(details);
    provider.setPasswordEncoder(encoder);
    var throttle = new LoginThrottle(10, Duration.ofMinutes(15), Clock.systemUTC());
    jakarta.servlet.Filter guard =
        (request, response, chain) -> {
          var login = (HttpServletRequest) request;
          if ("POST".equals(login.getMethod())
              && LOGIN.equals(login.getRequestURI().substring(login.getContextPath().length()))
              && throttle.blocked(login.getRemoteAddr(), login.getParameter("username"))) {
            problem(
                (HttpServletResponse) response,
                429,
                "Слишком много попыток входа. Повторите через 15 минут.");
            return;
          }
          chain.doFilter(request, response);
        };
    return http.authenticationManager(new ProviderManager(provider))
        .addFilterBefore(guard, UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, SPA_ROUTES)
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/csrf",
                        "/api/auth/me",
                        "/frontend/**",
                        "/css/**",
                        "/images/**",
                        "/webjars/**")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    // Deny by default: only listed partner operations are reachable.
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/catalog/stores",
                        "/api/catalog/products",
                        "/api/catalog/products/{id}",
                        "/api/cart",
                        "/api/checkout",
                        "/api/orders/by-key/{key}",
                        "/api/account/orders",
                        "/api/account/orders/{number}")
                    .hasRole(Partner.ROLE)
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/cart/add",
                        "/api/cart/update",
                        "/api/cart/remove",
                        "/api/cart/clear",
                        "/api/cart/confirm-price",
                        "/api/orders")
                    .hasRole(Partner.ROLE)
                    .requestMatchers("/catalog/**", "/cart/**")
                    .hasRole(Partner.ROLE)
                    .anyRequest()
                    .denyAll())
        .formLogin(
            login ->
                login
                    .loginPage("/login")
                    .loginProcessingUrl(LOGIN)
                    .successHandler(
                        (request, response, authentication) -> {
                          throttle.success(request.getRemoteAddr(), authentication.getName());
                          var partner = partners.getObject().find(authentication.getName());
                          response.setStatus(200);
                          response.setHeader("Cache-Control", "no-store");
                          response.setContentType("application/json;charset=UTF-8");
                          json.writeValue(
                              response.getOutputStream(), partner.orElseThrow().profile());
                        })
                    .failureHandler(
                        (request, response, error) -> {
                          throttle.failure(
                              request.getRemoteAddr(), request.getParameter("username"));
                          problem(response, 401, "Неверный логин или пароль.");
                        }))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler(
                        (request, response, authentication) -> response.setStatus(204)))
        .requestCache(cache -> cache.disable())
        .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CSP)))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, error) -> {
                          if (isApi(request)) problem(response, 401, "Войдите в кабинет партнёра.");
                          else response.sendRedirect(request.getContextPath() + "/login");
                        })
                    .accessDeniedHandler(
                        (request, response, error) -> {
                          if (isApi(request))
                            problem(
                                response,
                                403,
                                "Запрос отклонён. Обновите страницу и повторите действие.");
                          else {
                            response.setStatus(403);
                            response.setContentType("text/plain;charset=UTF-8");
                            response
                                .getWriter()
                                .write("Запрос отклонён. Обновите страницу и повторите действие.");
                          }
                        }))
        .build();
  }

  @Bean
  PasswordEncoder staffPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** Staff accounts for /admin; partners are resolved only by the portal chain above. */
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

  private static boolean isApi(HttpServletRequest request) {
    return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
  }

  private static void problem(HttpServletResponse response, int status, String detail)
      throws IOException {
    response.setStatus(status);
    response.setHeader("Cache-Control", "no-store");
    response.setContentType("application/problem+json;charset=UTF-8");
    response
        .getWriter()
        .write(
            "{\"type\":\"urn:mbm:security:"
                + (status == 401 ? "unauthorized" : status == 429 ? "throttled" : "forbidden")
                + "\",\"title\":\""
                + (status == 401
                    ? "Unauthorized"
                    : status == 429 ? "Too Many Requests" : "Forbidden")
                + "\",\"status\":"
                + status
                + ",\"detail\":\""
                + detail
                + "\"}");
  }
}
