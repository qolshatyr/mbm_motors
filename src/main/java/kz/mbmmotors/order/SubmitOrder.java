package kz.mbmmotors.order;

import java.util.LinkedHashMap;
import java.util.UUID;

public record SubmitOrder(
    UUID key, String revision, String customerName, String phone, String comment) {
  public static SubmitOrder validate(
      String key, String revision, String name, String phone, String comment, String consent) {
    var errors = new LinkedHashMap<String, String>();
    UUID id = null;
    try {
      id = UUID.fromString(key == null ? "" : key);
      if (!id.toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
    } catch (IllegalArgumentException e) {
      errors.put("idempotencyKey", "Обновите страницу оформления.");
    }
    if (revision == null || !revision.matches("[a-f0-9]{64}"))
      errors.put("revision", "Обновите страницу оформления.");
    name = name == null ? "" : name.strip();
    comment = comment == null ? "" : comment.strip();
    if (name.isEmpty() || name.length() > 80 || name.chars().anyMatch(Character::isISOControl))
      errors.put("customerName", "Укажите имя, не более 80 символов.");
    if (comment.length() > 1000
        || comment
            .chars()
            .anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
      errors.put("comment", "Комментарий должен быть не длиннее 1000 символов.");
    String normalized = normalizePhone(phone);
    if (normalized == null)
      errors.put("phone", "Укажите корректный номер телефона с кодом страны.");
    if (!"true".equals(consent))
      errors.put("consent", "Подтвердите согласие на обработку данных заявки.");
    if (!errors.isEmpty()) throw new OrderProblem(400, "Проверьте поля формы.", errors);
    return new SubmitOrder(id, revision, name, normalized, comment);
  }

  static String normalizePhone(String value) {
    if (value == null || value.length() > 32 || !value.matches("[+0-9 ()-]+")) return null;
    String digits = value.replaceAll("[ ()-]", "");
    if (digits.matches("[0-9]{10}")) return "+7" + digits;
    if (digits.matches("[78][0-9]{10}")) return "+7" + digits.substring(1);
    if (digits.matches("\\+[1-9][0-9]{9,14}")) return digits;
    return null;
  }
}
