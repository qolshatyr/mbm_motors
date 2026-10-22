package kz.mbmmotors.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/** Partner portal: everything except the sign-in surface requires a partner account. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebSecurityConfiguration {
  private static final String LOGIN = "/api/auth/login";

  /** Password version the session signed in with; a newer password ends the session. */
  private static final String CREDENTIALS = "mbm.partner.credentials";

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
    // Blocked accounts and sessions opened with a replaced password lose access immediately.
    // Every portal sign-in records the password version; sessions live in memory, so none
    // without it survive a restart (only test-injected authentications lack it).
    jakarta.servlet.Filter current =
        (request, response, chain) -> {
          var session = ((HttpServletRequest) request).getSession(false);
          Object version = session == null ? null : session.getAttribute(CREDENTIALS);
          var authentication = SecurityContextHolder.getContext().getAuthentication();
          if (version != null
              && authentication != null
              && authentication.getAuthorities().stream()
                  .anyMatch(a -> ("ROLE_" + Partner.ROLE).equals(a.getAuthority()))) {
            boolean valid =
                partners
                    .getObject()
                    .find(authentication.getName())
                    .filter(
                        p -> p.enabled() && Integer.valueOf(p.credentialsVersion()).equals(version))
                    .isPresent();
            if (!valid) {
              session.invalidate();
              SecurityContextHolder.clearContext();
            }
          }
          chain.doFilter(request, response);
        };
    return http.authenticationManager(new ProviderManager(provider))
        .addFilterBefore(guard, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(current, AuthorizationFilter.class)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, SPA_ROUTES)
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/api/csrf", "/api/auth/me", "/frontend/**", "/css/**")
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
                          var partner =
                              partners.getObject().find(authentication.getName()).orElseThrow();
                          request
                              .getSession()
                              .setAttribute(CREDENTIALS, partner.credentialsVersion());
                          partners.getObject().recordLogin(partner.login(), Instant.now());
                          response.setStatus(200);
                          response.setHeader("Cache-Control", "no-store");
                          response.setContentType("application/json;charset=UTF-8");
                          json.writeValue(response.getOutputStream(), partner.profile());
                        })
                    .failureHandler(
                        (request, response, error) -> {
                          throttle.failure(
                              request.getRemoteAddr(), request.getParameter("username"));
                          // The block is named only to someone who knows the password.
                          if (error instanceof DisabledException
                              && blockedWithPassword(
                                  partners.getObject(),
                                  encoder,
                                  request.getParameter("username"),
                                  request.getParameter("password")))
                            problem(
                                response,
                                403,
                                "Доступ к этой учётной записи приостановлен. Свяжитесь с менеджером.");
                          else problem(response, 401, "Неверный логин или пароль.");
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

  private static boolean blockedWithPassword(
      PartnerRepository partners, PasswordEncoder encoder, String login, String password) {
    if (login == null || password == null) return false;
    return partners
        .find(login.strip().toLowerCase(java.util.Locale.ROOT))
        .filter(p -> !p.enabled() && encoder.matches(password, p.passwordHash()))
        .isPresent();
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
