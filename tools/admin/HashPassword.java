import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCrypt;

/** Called only by the local launcher. Password is received on stdin, never an argument. */
class HashPassword {
  public static void main(String[] args) throws Exception {
    String password = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    System.out.print(BCrypt.hashpw(password, BCrypt.gensalt(12)));
  }
}
