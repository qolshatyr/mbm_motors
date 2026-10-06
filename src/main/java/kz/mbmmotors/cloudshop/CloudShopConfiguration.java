package kz.mbmmotors.cloudshop;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CloudShopProperties.class)
class CloudShopConfiguration {
  @Bean
  CloudShopGateway cloudShopGateway(RestClient.Builder builder, CloudShopProperties properties) {
    return new RestClientCloudShopGateway(builder, properties);
  }
}
