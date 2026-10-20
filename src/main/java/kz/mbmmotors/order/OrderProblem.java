package kz.mbmmotors.order;

import java.util.Map;

public class OrderProblem extends RuntimeException {
  private final int status;
  private final Map<String, String> errors;

  public OrderProblem(int status, String message) {
    this(status, message, Map.of());
  }

  public OrderProblem(int status, String message, Map<String, String> errors) {
    super(message);
    this.status = status;
    this.errors = Map.copyOf(errors);
  }

  public int status() {
    return status;
  }

  public Map<String, String> errors() {
    return errors;
  }
}
