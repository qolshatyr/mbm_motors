package kz.mbmmotors.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("admin")
public class AdminProperties {
  private boolean enabled;
  private String username = "";
  private String passwordHash = "";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public void validate() {
    if (!enabled) return;
    if (username == null
        || !username.matches("[A-Za-z0-9._-]{1,64}")
        || passwordHash == null
        || !passwordHash.matches("\\$2[aby]\\$(1[0-6])\\$[./A-Za-z0-9]{53}")) {
      throw new IllegalStateException(
          "Enabled staff access requires a valid username and BCrypt password hash");
    }
  }
}
