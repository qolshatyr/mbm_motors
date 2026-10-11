package kz.mbmmotors.catalog.web;

import kz.mbmmotors.catalog.CatalogViewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Controller
public class CatalogController {
  private final CatalogViewService catalog;

  @Value("${frontend.enabled:false}")
  private boolean frontendEnabled;

  public CatalogController(CatalogViewService catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/")
  String home() {
    return frontendEnabled ? "forward:/frontend/index.html" : "redirect:/catalog";
  }

  @GetMapping("/catalog")
  String list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) String store,
      @RequestParam(defaultValue = "false") boolean available,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    model.addAttribute("catalog", catalog.list(q, store, available, page));
    model.addAttribute("available", available);
    return "catalog/list";
  }

  @GetMapping("/catalog/{id}")
  String detail(
      @PathVariable String id, @RequestParam(required = false) String store, Model model) {
    var detail = catalog.detail(id, store);
    model.addAttribute("product", detail.product());
    model.addAttribute("offers", detail.offers());
    model.addAttribute("selectedStore", detail.selectedStore());
    model.addAttribute("lastSyncText", detail.lastSyncText());
    model.addAttribute("stale", detail.stale());
    return "catalog/detail";
  }

  @ExceptionHandler(CatalogViewService.CatalogNotFound.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  String notFound(Model model) {
    model.addAttribute(
        "message", "Товар или страница не найдены. Вернитесь в каталог и повторите поиск.");
    return "error";
  }

  @ExceptionHandler({
    CatalogViewService.BadCatalogQuery.class,
    MethodArgumentTypeMismatchException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  String badQuery(Model model) {
    model.addAttribute(
        "message",
        "Проверьте параметры поиска. Запрос должен быть не длиннее 120 символов; выберите точку из списка.");
    return "error";
  }
}
