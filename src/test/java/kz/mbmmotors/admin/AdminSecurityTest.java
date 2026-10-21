package kz.mbmmotors.admin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kz.mbmmotors.config.WebSecurityConfiguration;
import kz.mbmmotors.config.web.CsrfController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({StaffRequestController.class, AdminLoginController.class, CsrfController.class})
@Import({WebSecurityConfiguration.class, AdminSecurityConfiguration.class})
class AdminSecurityTest {
  @Autowired MockMvc mvc;
  @MockitoBean StaffRequestService service;

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry properties) {
    properties.add("admin.enabled", () -> true);
    properties.add("admin.username", () -> "staff");
    properties.add(
        "admin.password-hash", () -> new BCryptPasswordEncoder().encode("synthetic-test-only"));
  }

  @Test
  void anonymousApiIsJson401AndPageRedirectsWithNoStore() throws Exception {
    mvc.perform(get("/admin/api/requests"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(get("/admin/requests"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/login"))
        .andExpect(header().string("Cache-Control", "no-store"));
    verifyNoInteractions(service);
  }

  @Test
  void loginRendersHiddenCsrfAndHasSecurityHeaders() throws Exception {
    mvc.perform(get("/admin/login"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            header()
                .string(
                    "Content-Security-Policy",
                    org.hamcrest.Matchers.containsString("form-action 'self'")));
  }

  @Test
  void actualCredentialsRequireCsrfRotateSessionAndLogoutInvalidates() throws Exception {
    mvc.perform(
            post("/admin/login")
                .param("username", "staff")
                .param("password", "synthetic-test-only"))
        .andExpect(status().isForbidden());
    var session = new MockHttpSession();
    String oldId = session.getId();
    var result =
        mvc.perform(
                post("/admin/login")
                    .session(session)
                    .with(csrf())
                    .param("username", "staff")
                    .param("password", "synthetic-test-only"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/clients"))
            .andReturn();
    assertThat(result.getRequest().getSession().getId()).isNotEqualTo(oldId);
    when(service.list(0)).thenReturn(new StaffRequestService.Page(List.of(), 0, 20, 0, 0));
    mvc.perform(get("/admin/api/requests").session(session)).andExpect(status().isOk());
    mvc.perform(post("/admin/logout").session(session)).andExpect(status().isForbidden());
    assertThat(session.isInvalid()).isFalse();
    mvc.perform(post("/admin/logout").session(session).with(csrf()))
        .andExpect(redirectedUrl("/admin/login?logout"));
    assertThat(session.isInvalid()).isTrue();
    mvc.perform(get("/admin/api/requests")).andExpect(status().isUnauthorized());
  }

  @Test
  void badCredentialsFailAndReturnTargetsCannotChangeSuccessRedirect() throws Exception {
    mvc.perform(
            post("/admin/login").with(csrf()).param("username", "staff").param("password", "wrong"))
        .andExpect(redirectedUrl("/admin/login?error"));
    mvc.perform(
            post("/admin/login")
                .with(csrf())
                .param("username", "staff")
                .param("password", "synthetic-test-only")
                .param("continue", "https://evil.invalid"))
        .andExpect(redirectedUrl("/admin/clients"));
  }

  @Test
  void wrongRoleAndMutationAreDeniedWhilePublicCsrfIsUnchanged() throws Exception {
    mvc.perform(get("/admin/api/requests").with(user("viewer").roles("USER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
    mvc.perform(post("/admin/api/requests").with(user("staff").roles("STAFF")).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/csrf")).andExpect(status().isOk());
    verifyNoInteractions(service);
  }

  @Test
  void boundedPagesAndNumbersRejectBadInputBeforeQuery() throws Exception {
    for (String page : List.of("-1", "100001", "1.5", "word", "999999999999999999999")) {
      mvc.perform(get("/admin/api/requests").param("page", page).with(user("staff").roles("STAFF")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value(400));
    }
    for (String number : List.of("0", "-1", "abc", "9223372036854775808")) {
      mvc.perform(get("/admin/api/requests/" + number).with(user("staff").roles("STAFF")))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value(400));
    }
    verifyNoInteractions(service);
  }

  @Test
  void summaryOmitsContactsAndDetailIncludesOnlySnapshots() throws Exception {
    when(service.list(100000))
        .thenReturn(new StaffRequestService.Page(List.of(), 100000, 20, 0, 0));
    mvc.perform(
            get("/admin/api/requests").param("page", "100000").with(user("staff").roles("STAFF")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    var created = Instant.parse("2026-10-11T00:00:00Z");
    when(service.list(0))
        .thenReturn(
            new StaffRequestService.Page(
                List.of(
                    new StaffRequestService.Summary(
                        "123",
                        "RECEIVED",
                        created,
                        "Test store",
                        1,
                        "3 000 ₸",
                        StaffRequestService.delivery("WAITING_CONFIGURATION"))),
                0,
                20,
                1,
                1));
    mvc.perform(get("/admin/api/requests").with(user("staff").roles("STAFF")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].number").value("123"))
        .andExpect(jsonPath("$.items[0].delivery.state").value("WAITING_CONFIGURATION"))
        .andExpect(jsonPath("$.items[0].phone").doesNotExist())
        .andExpect(jsonPath("$.items[0].customerName").doesNotExist())
        .andExpect(jsonPath("$.items[0].ownerId").doesNotExist())
        .andExpect(jsonPath("$.items[0].idempotencyKey").doesNotExist());
    when(service.detail(123))
        .thenReturn(
            Optional.of(
                new StaffRequestService.Detail(
                    "123",
                    "RECEIVED",
                    created,
                    "Test store",
                    1,
                    "3 000 ₸",
                    "Synthetic customer",
                    "+77000000000",
                    "Test comment",
                    List.of(
                        new StaffRequestService.Item(
                            1, "Snapshot part", "TEST", "шт", 1, "3 000 ₸", "3 000 ₸")),
                    StaffRequestService.delivery("WAITING_CONFIGURATION"))));
    mvc.perform(get("/admin/api/requests/123").with(user("staff").roles("STAFF")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerName").value("Synthetic customer"))
        .andExpect(jsonPath("$.delivery.state").value("WAITING_CONFIGURATION"))
        .andExpect(jsonPath("$.items[0].productName").value("Snapshot part"))
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.requestHash").doesNotExist())
        .andExpect(header().string("Cache-Control", "no-store"));
    when(service.detail(124)).thenReturn(Optional.empty());
    mvc.perform(get("/admin/api/requests/124").with(user("staff").roles("STAFF")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }
}
