package kz.mbmmotors.sync;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SyncConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
