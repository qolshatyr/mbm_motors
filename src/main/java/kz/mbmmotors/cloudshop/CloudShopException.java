package kz.mbmmotors.cloudshop;

/** Deliberately excludes response bodies, request headers and underlying HTTP exceptions. */
public class CloudShopException extends RuntimeException {
  public CloudShopException(String message) {
    super(message);
  }
}
