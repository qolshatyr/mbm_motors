package kz.mbmmotors.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AdminSecurityConfiguration {
  @Bean
  @Order(1)
  SecurityFilterChain staffSecurity(HttpSecurity http, AdminProperties properties)
      throws Exception {
    http.securityMatcher("/admin", "/admin/**")
        .requestCache(cache -> cache.disable())
        .headers(
            headers ->
                headers
                    .addHeaderWriter(
                        (request, response) -> response.setHeader("Cache-Control", "no-store"))
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; img-src 'self' https:; style-src 'self'; script-src 'self'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, exception) -> {
                          if (!properties.isEnabled()) problem(response, 403);
                          else if (isApi(request)) problem(response, 401);
                          else response.sendRedirect(request.getContextPath() + "/admin/login");
                        })
                    .accessDeniedHandler((request, response, exception) -> problem(response, 403)));
    if (!properties.isEnabled()) {
      return http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
          .logout(logout -> logout.disable())
          .build();
    }
    return http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/admin/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/admin/login")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/admin/requests",
                        "/admin/requests/{number}",
                        "/admin/api/requests",
                        "/admin/api/requests/{number}")
                    .hasRole("STAFF")
                    .anyRequest()
                    .denyAll())
        .formLogin(
            login ->
                login
                    .loginPage("/admin/login")
                    .loginProcessingUrl("/admin/login")
                    .defaultSuccessUrl("/admin/requests", true)
                    .failureUrl("/admin/login?error"))
        .logout(logout -> logout.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login?logout"))
        .build();
  }

  private static boolean isApi(HttpServletRequest request) {
    return request.getRequestURI().startsWith(request.getContextPath() + "/admin/api/");
  }

  private static void problem(HttpServletResponse response, int status) throws IOException {
    response.setStatus(status);
    response.setHeader("Cache-Control", "no-store");
    response.setContentType("application/problem+json;charset=UTF-8");
    response
        .getWriter()
        .write(
            "{\"type\":\"urn:mbm:staff:access\",\"title\":\""
                + (status == 401 ? "Unauthorized" : "Forbidden")
                + "\",\"status\":"
                + status
                + ",\"detail\":\""
                + (status == 401
                    ? "Войдите в служебный раздел."
                    : "Доступ к служебному разделу запрещён.")
                + "\"}");
  }
}
