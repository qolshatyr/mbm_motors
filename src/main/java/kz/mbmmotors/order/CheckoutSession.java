package kz.mbmmotors.order;

import java.io.Serializable;
import java.util.UUID;

/** Guarded by the same session Cart monitor as legacy and API cart operations. */
public final class CheckoutSession implements Serializable {
  private static final long serialVersionUID = 1L;
  private final UUID ownerId = UUID.randomUUID();
  private UUID key;
  private String revision;

  public UUID ownerId() {
    return ownerId;
  }

  public UUID key() {
    return key;
  }

  public String revision() {
    return revision;
  }

  public void completed() {
    key = null;
    revision = null;
  }

  public void issue(String value) {
    if (key == null || !value.equals(revision)) {
      key = UUID.randomUUID();
      revision = value;
    }
  }
}
