package kz.mbmmotors.admin;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Staff API for client accounts. Responses carry contacts, so nothing is cached. */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ClientAdminController {
  private final ClientAdminService clients;

  public ClientAdminController(ClientAdminService clients) {
    this.clients = clients;
  }

  @GetMapping("/admin/api/clients")
  ResponseEntity<ClientAdminService.Clients> list(@RequestParam(defaultValue = "") String q) {
    return noStore(HttpStatus.OK, clients.list(q));
  }

  /** The password is chosen in the form and shown to staff once; it is stored only as a hash. */
  @PostMapping("/admin/api/clients")
  ResponseEntity<ClientAdminService.Client> create(
      @RequestBody ClientAdminService.NewClient request) {
    return noStore(HttpStatus.CREATED, clients.create(request));
  }

  @PostMapping("/admin/api/clients/{login}/password")
  ResponseEntity<Void> password(@PathVariable String login, @RequestBody Password request) {
    clients.changePassword(login, request.password());
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @PostMapping("/admin/api/clients/{login}/enabled")
  ResponseEntity<ClientAdminService.Client> enabled(
      @PathVariable String login, @RequestBody Enabled request) {
    return noStore(HttpStatus.OK, clients.setEnabled(login, request.enabled()));
  }

  @PostMapping("/admin/api/clients/{login}/customer")
  ResponseEntity<ClientAdminService.Client> linkCustomer(
      @PathVariable String login, @RequestBody Link request) {
    return noStore(HttpStatus.OK, clients.linkCustomer(login, request.customerId()));
  }

  @PostMapping("/admin/api/clients/{login}/customer/create")
  ResponseEntity<ClientAdminService.Client> createCustomer(
      @PathVariable String login, @RequestBody Create request) {
    return noStore(HttpStatus.OK, clients.createCustomer(login, request.checkedInCloudShop()));
  }

  @ExceptionHandler(ClientAdminService.Problem.class)
  ResponseEntity<ProblemDetail> problem(ClientAdminService.Problem error) {
    var body =
        ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(error.status()), error.getMessage());
    body.setType(URI.create("urn:mbm:staff:client"));
    return ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore()).body(body);
  }

  private static <T> ResponseEntity<T> noStore(HttpStatus status, T body) {
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
  }

  record Password(String password) {
    @Override
    public String toString() {
      return "Password[hidden]";
    }
  }

  record Enabled(boolean enabled) {}

  record Link(String customerId) {}

  record Create(boolean checkedInCloudShop) {}
}
