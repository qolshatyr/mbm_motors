package kz.mbmmotors.cloudshop;

import java.time.Instant;
import java.util.List;

/** GET-only boundary; document creation is intentionally not exposed. */
public interface CloudShopGateway {
  List<CloudShopProduct> products(int limit, int offset);

  List<CloudShopProduct> updated(Instant since);
}
