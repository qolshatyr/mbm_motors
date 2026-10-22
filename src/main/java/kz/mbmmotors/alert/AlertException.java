package kz.mbmmotors.alert;

/** An alert was not delivered. The message never contains tokens, URLs or customer data. */
public class AlertException extends RuntimeException {
  public AlertException(String message) {
    super(message);
  }
}
