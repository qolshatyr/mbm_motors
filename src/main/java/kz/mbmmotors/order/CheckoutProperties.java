package kz.mbmmotors.order;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("checkout")
public class CheckoutProperties {
  private List<String> pickupStoreIds = List.of();

  public List<String> getPickupStoreIds() {
    return pickupStoreIds;
  }

  public void setPickupStoreIds(List<String> ids) {
    pickupStoreIds = List.copyOf(ids);
  }
}
