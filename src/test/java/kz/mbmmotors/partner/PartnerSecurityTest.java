package kz.mbmmotors.partner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Optional;
import java.util.UUID;
import kz.mbmmotors.admin.AdminSecurityConfiguration;
import kz.mbmmotors.catalog.CatalogViewService;
import kz.mbmmotors.catalog.web.CatalogApiController;
import kz.mbmmotors.config.WebSecurityConfiguration;
import kz.mbmmotors.config.web.CsrfController;
import kz.mbmmotors.partner.web.PartnerController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({PartnerController.class, CsrfController.class, CatalogApiController.class})
@Import({
  WebSecurityConfiguration.class,
  AdminSecurityConfiguration.class,
  PartnerAccountService.class
})
class PartnerSecurityTest {
  private static final String PASSWORD = "synthetic-test-only";
  private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);

  @Autowired MockMvc mvc;
  @MockitoBean PartnerRepository partners;
  @MockitoBean JdbcTemplate jdbc;
  @MockitoBean CatalogViewService catalog;

  @DynamicPropertySource
  static void staff(DynamicPropertyRegistry properties) {
    properties.add("admin.enabled", () -> true);
    properties.add("admin.username", () -> "staff");
    properties.add("admin.password-hash", () -> HASH);
  }

  @BeforeEach
  void accounts() {
    when(partners.find(anyString())).thenReturn(Optional.empty());
    when(partners.find("turbo")).thenReturn(Optional.of(partner("turbo", true)));
    when(partners.find("closed")).thenReturn(Optional.of(partner("closed", false)));
  }

  @Test
  void anonymousSeesOnlyTheSignInSurface() throws Exception {
    mvc.perform(get("/api/catalog/products"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(401));
    mvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
    mvc.perform(get("/api/csrf")).andExpect(status().isOk());
    mvc.perform(post("/api/cart/add").with(csrf())).andExpect(status().isUnauthorized());
    mvc.perform(get("/catalog")).andExpect(redirectedUrl("/login"));
    // SPA routes are not gated by security; without a built frontend they are simply absent.
    mvc.perform(get("/search")).andExpect(status().isNotFound());
    verifyNoInteractions(catalog);
  }

  @Test
  void loginRequiresCsrfRotatesSessionAndReturnsProfileWithoutSecrets() throws Exception {
    mvc.perform(post("/api/auth/login").param("username", "turbo").param("password", PASSWORD))
        .andExpect(status().isForbidden());
    var session = new MockHttpSession();
    String before = session.getId();
    var login =
        mvc.perform(
                post("/api/auth/login")
                    .session(session)
                    .with(csrf())
                    .param("username", "turbo")
                    .param("password", PASSWORD))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.login").value("turbo"))
            .andExpect(jsonPath("$.company").value("СТО «Turbo»"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.cloudshopCustomerId").doesNotExist())
            .andReturn();
    var signedIn = (MockHttpSession) login.getRequest().getSession(false);
    assertThat(signedIn.getId()).isNotEqualTo(before);
    mvc.perform(get("/api/auth/me").session(signedIn))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").value("+77015551234"));
    mvc.perform(get("/api/unknown").session(signedIn)).andExpect(status().isForbidden());
    mvc.perform(post("/api/auth/logout").session(signedIn).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/auth/me").session(signedIn)).andExpect(status().isUnauthorized());
  }

  @Test
  void wrongPasswordDisabledPartnerAndStaffCredentialsAreRejectedAlike() throws Exception {
    for (var credentials :
        new String[][] {{"turbo", "wrong-password"}, {"closed", PASSWORD}, {"staff", PASSWORD}}) {
      mvc.perform(
              post("/api/auth/login")
                  .with(csrf())
                  .param("username", credentials[0])
                  .param("password", credentials[1]))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.detail").value("Неверный логин или пароль."));
    }
  }

  private static Partner partner(String login, boolean enabled) {
    return new Partner(
        UUID.randomUUID(), login, HASH, "СТО «Turbo»", "Ерлан", "+77015551234", null, enabled);
  }
}
