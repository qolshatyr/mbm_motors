package kz.mbmmotors.admin;

import static org.assertj.core.api.Assertions.assertThat;

import kz.mbmmotors.config.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

class AdminNonWebConfigurationTest {
  @Test
  void oneShotSyncNonWebContextDoesNotRequireHttpSecurity() {
    new ApplicationContextRunner()
        .withUserConfiguration(AdminSecurityConfiguration.class, WebSecurityConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
            });
  }
}
