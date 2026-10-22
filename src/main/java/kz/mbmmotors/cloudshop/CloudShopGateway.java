package kz.mbmmotors.cloudshop;

import java.time.Instant;
import java.util.List;

/** GET-only boundary; document creation is intentionally not exposed. */
public interface CloudShopGateway {
  CloudShopBatch products(int limit, int offset);

  CloudShopBatch updated(Instant since);

  /** IDs of products deleted in CloudShop since the given time. */
  List<String> deleted(Instant since);
}
