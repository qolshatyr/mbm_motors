package kz.mbmmotors.catalog.web;

import java.net.URI;
import java.util.List;
import kz.mbmmotors.catalog.CatalogViewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/catalog")
public class CatalogApiController {
  private final CatalogViewService catalog;

  public CatalogApiController(CatalogViewService catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/stores")
  StoresResponse stores() {
    return new StoresResponse(catalog.stores(), CatalogViewService.DEFAULT_STORE);
  }

  @GetMapping("/products")
  CatalogViewService.CatalogPage products(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) String store,
      @RequestParam(defaultValue = "false") boolean available,
      @RequestParam(defaultValue = "false") boolean orderable,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String section,
      @RequestParam(required = false) String subsection,
      @RequestParam(required = false) String make,
      @RequestParam(required = false) String model,
      @RequestParam(required = false) List<String> brand) {
    return catalog.list(
        q,
        store,
        available,
        orderable,
        page,
        sort,
        new CatalogViewService.CatalogFilter(section, subsection, make, model, brand));
  }

  @GetMapping("/products/{id}")
  CatalogViewService.Detail product(
      @PathVariable String id, @RequestParam(required = false) String store) {
    return catalog.detail(id, store);
  }

  @ExceptionHandler(CatalogViewService.BadCatalogQuery.class)
  ProblemDetail badQuery(CatalogViewService.BadCatalogQuery error) {
    return invalid(error.getMessage());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail badParameter(MethodArgumentTypeMismatchException error) {
    return invalid(
        switch (error.getName()) {
          case "page" -> "Неверный номер страницы.";
          case "available", "orderable" -> "Неверное значение фильтра наличия.";
          default -> "Проверьте параметры поиска.";
        });
  }

  private static ProblemDetail invalid(String detail) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    problem.setType(URI.create("urn:mbm:catalog:invalid-query"));
    return problem;
  }

  @ExceptionHandler(CatalogViewService.CatalogNotFound.class)
  ProblemDetail notFound() {
    var problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "Товар или страница не найдены. Вернитесь в каталог и повторите поиск.");
    problem.setType(URI.create("urn:mbm:catalog:page-not-found"));
    return problem;
  }

  public record StoresResponse(List<CatalogViewService.StoreView> stores, String defaultStoreId) {}
}
