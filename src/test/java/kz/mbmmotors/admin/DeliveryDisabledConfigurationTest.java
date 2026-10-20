package kz.mbmmotors.admin;

import static org.assertj.core.api.Assertions.assertThat;

import kz.mbmmotors.delivery.DeliveryGateway;
import kz.mbmmotors.delivery.DeliveryProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@EnabledIfEnvironmentVariable(named = "MBM_TEST_DATABASE_URL", matches = ".*/mbm_test")
@SpringBootTest(
    properties = {
      "spring.datasource.url=${MBM_TEST_DATABASE_URL}",
      "spring.datasource.username=${MBM_TEST_DATABASE_USER:mbm}",
      "spring.datasource.password=${MBM_TEST_DATABASE_PASSWORD:}",
      "admin.enabled=false"
    })
class DeliveryDisabledConfigurationTest {
  @Autowired ApplicationContext context;

  @Test
  void normalApplicationHasNoDeliveryAdapterOrProcessor() {
    assertThat(context.getBeansOfType(DeliveryGateway.class)).isEmpty();
    assertThat(context.getBeansOfType(DeliveryProcessor.class)).isEmpty();
  }
}
