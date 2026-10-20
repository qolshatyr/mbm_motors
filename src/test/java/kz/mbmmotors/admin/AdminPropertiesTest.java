package kz.mbmmotors.admin;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminPropertiesTest {
  @Test
  void disabledNeedsNoCredentialsButEnabledFailsClosed() {
    var properties = new AdminProperties();
    properties.validate();
    properties.setEnabled(true);
    assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    properties.setUsername("staff");
    for (var hash :
        new String[] {"", "secret", "$2a$31$" + "a".repeat(53), "$2a$04$" + "a".repeat(53)}) {
      properties.setPasswordHash(hash);
      assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }
    properties.setPasswordHash(new BCryptPasswordEncoder().encode("synthetic-test-only"));
    assertThatCode(properties::validate).doesNotThrowAnyException();
    properties.setUsername(" staff\n");
    assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void monetaryTextUsesSameRussianTengeConventionAsCart() {
    assertThat(StaffRequestService.money(new BigDecimal("3000.25"))).isEqualTo("3\u00a0000,25 ₸");
    assertThat(StaffRequestService.money(new BigDecimal("3000.00"))).isEqualTo("3\u00a0000 ₸");
  }
}
