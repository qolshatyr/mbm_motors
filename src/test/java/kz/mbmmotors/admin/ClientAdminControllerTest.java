package kz.mbmmotors.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import kz.mbmmotors.config.WebSecurityConfiguration;
import kz.mbmmotors.partner.PartnerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClientAdminController.class)
@Import({WebSecurityConfiguration.class, AdminSecurityConfiguration.class})
class ClientAdminControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean ClientAdminService service;
  @MockitoBean PartnerRepository partners;

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry properties) {
    properties.add("admin.enabled", () -> true);
    properties.add("admin.username", () -> "staff");
    properties.add(
        "admin.password-hash", () -> new BCryptPasswordEncoder().encode("synthetic-test-only"));
  }

  @Test
  void onlyStaffWithCsrfManagesClients() throws Exception {
    mvc.perform(get("/admin/api/clients")).andExpect(status().isUnauthorized());
    mvc.perform(get("/admin/api/clients").with(user("turbo").roles("PARTNER")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/admin/api/clients")
                .with(user("staff").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void createReturnsClientWithoutPasswordAndMapsProblems() throws Exception {
    when(service.create(any()))
        .thenReturn(
            new ClientAdminService.Client(
                "turbo",
                "ТОО «Turbo»",
                "Ерлан",
                "+77015551234",
                null,
                true,
                Instant.parse("2026-10-21T00:00:00Z"),
                null,
                new ClientAdminService.Customer("LINKED", "6ac110842dd87801040fed88")));
    mvc.perform(
            post("/admin/api/clients")
                .with(user("staff").roles("STAFF"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"login\":\"turbo\",\"company\":\"ТОО «Turbo»\",\"contactName\":\"Ерлан\",\"phone\":\"+77015551234\",\"password\":\"synthetic-pass-1\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.login").value("turbo"))
        .andExpect(jsonPath("$.customer.state").value("LINKED"))
        .andExpect(jsonPath("$.password").doesNotExist());
    when(service.list("x")).thenThrow(new ClientAdminService.Problem(400, "Плохой запрос."));
    mvc.perform(get("/admin/api/clients").param("q", "x").with(user("staff").roles("STAFF")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Плохой запрос."));
    when(service.list("")).thenReturn(new ClientAdminService.Clients(List.of(), false));
    mvc.perform(get("/admin/api/clients").with(user("staff").roles("STAFF")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerCreation").value(false));
  }
}
