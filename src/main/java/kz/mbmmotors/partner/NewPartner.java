package kz.mbmmotors.partner;

import java.util.Locale;

/** Validated partner details entered by staff. */
public record NewPartner(String login, String company, String contactName, String phone) {
  public static NewPartner validate(
      String login, String company, String contactName, String phone) {
    login = login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
    company = company == null ? "" : company.strip();
    contactName = contactName == null ? "" : contactName.strip();
    if (!login.matches("[a-z0-9._-]{3,64}"))
      throw new IllegalArgumentException("Логин: 3–64 символа, латиница, цифры, точка, дефис.");
    if (company.isEmpty() || company.length() > 120 || hasControl(company))
      throw new IllegalArgumentException("Компания: от 1 до 120 символов.");
    if (contactName.isEmpty() || contactName.length() > 80 || hasControl(contactName))
      throw new IllegalArgumentException("Контактное лицо: от 1 до 80 символов.");
    String normalized = normalizePhone(phone);
    if (normalized == null)
      throw new IllegalArgumentException("Телефон: укажите номер с кодом страны.");
    return new NewPartner(login, company, contactName, normalized);
  }

  /** Same rules as the checkout form, so a partner's phone always passes order validation. */
  static String normalizePhone(String value) {
    if (value == null || value.length() > 32 || !value.matches("[+0-9 ()-]+")) return null;
    String digits = value.replaceAll("[ ()-]", "");
    if (digits.matches("[0-9]{10}")) return "+7" + digits;
    if (digits.matches("[78][0-9]{10}")) return "+7" + digits.substring(1);
    if (digits.matches("\\+[1-9][0-9]{9,14}")) return digits;
    return null;
  }

  public static void validatePassword(String password) {
    // BCrypt ignores input beyond 72 bytes; Cyrillic characters take two bytes each.
    if (password == null
        || password.length() < 10
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("Пароль: не короче 10 символов и не длиннее 72 байт.");
  }

  private static boolean hasControl(String value) {
    return value.chars().anyMatch(Character::isISOControl);
  }
}
