package kz.mbmmotors.admin;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import kz.mbmmotors.config.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = StaffRequestController.class, properties = "admin.enabled=false")
@Import({WebSecurityConfiguration.class, AdminSecurityConfiguration.class})
class AdminDisabledSecurityTest {
  @Autowired MockMvc mvc;
  @MockitoBean StaffRequestService service;

  @Test
  void disabledDeniesAnonymousAndStaffIncludingLogin() throws Exception {
    for (var path :
        new String[] {
          "/admin",
          "/admin/login",
          "/admin/requests",
          "/admin/api/requests",
          "/admin/api/requests/1"
        }) {
      mvc.perform(get(path))
          .andExpect(status().isForbidden())
          .andExpect(header().string("Cache-Control", "no-store"));
      mvc.perform(get(path).with(user("staff").roles("STAFF"))).andExpect(status().isForbidden());
    }
    mvc.perform(post("/admin/login").with(csrf())).andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }
}
