package kz.mbmmotors.cloudshop;

/** CloudShop answered 4xx: the request was refused and nothing was created. */
public class CloudShopRejectedException extends CloudShopException {
  public CloudShopRejectedException(String message) {
    super(message);
  }
}
