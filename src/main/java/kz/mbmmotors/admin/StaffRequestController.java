package kz.mbmmotors.admin;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaffRequestController {
  private final StaffRequestService service;

  public StaffRequestController(StaffRequestService service) {
    this.service = service;
  }

  @GetMapping("/admin/api/requests")
  StaffRequestService.Page list(@RequestParam(defaultValue = "0") String page) {
    if (!page.matches("[0-9]{1,6}"))
      throw problem(HttpStatus.BAD_REQUEST, "Некорректная страница.");
    int parsed = Integer.parseInt(page);
    if (parsed > 100000) throw problem(HttpStatus.BAD_REQUEST, "Некорректная страница.");
    return service.list(parsed);
  }

  @GetMapping("/admin/api/requests/{number}")
  StaffRequestService.Detail detail(@PathVariable String number) {
    if (!number.matches("[1-9][0-9]{0,18}"))
      throw problem(HttpStatus.BAD_REQUEST, "Некорректный номер заявки.");
    long parsed;
    try {
      parsed = Long.parseLong(number);
    } catch (NumberFormatException exception) {
      throw problem(HttpStatus.BAD_REQUEST, "Некорректный номер заявки.");
    }
    return service
        .detail(parsed)
        .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Заявка не найдена."));
  }

  @ExceptionHandler(ErrorResponseException.class)
  ResponseEntity<ProblemDetail> error(ErrorResponseException exception) {
    return ResponseEntity.status(exception.getStatusCode()).body(exception.getBody());
  }

  private static ErrorResponseException problem(HttpStatus status, String detail) {
    var body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setType(URI.create("urn:mbm:staff:request"));
    return new ErrorResponseException(status, body, null);
  }
}
