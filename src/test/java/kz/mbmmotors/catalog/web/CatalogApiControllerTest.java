package kz.mbmmotors.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;
import kz.mbmmotors.catalog.CatalogViewService;
import kz.mbmmotors.config.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(CatalogApiController.class)
@Import(WebSecurityConfiguration.class)
class CatalogApiControllerTest {
  @Autowired MockMvcTester mvc;
  @Autowired JsonMapper json;
  @MockitoBean CatalogViewService service;

  @Test
  void storesArePublicAndIncludeDefaultSelection() throws Exception {
    when(service.stores()).thenReturn(List.of(store()));
    var result = mvc.get().uri("/api/catalog/stores").exchange();
    assertThat(result).hasStatusOk();
    var body = json.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("defaultStoreId").asString()).isEqualTo(CatalogViewService.DEFAULT_STORE);
    assertThat(body.path("stores").get(0).path("name").asString()).isEqualTo("Car City");
  }

  @Test
  void defaultProductsSerializeDisplayValuesFreshnessAndNullableFields() throws Exception {
    when(service.list("", null, false, 0)).thenReturn(page());
    var result = mvc.get().uri("/api/catalog/products").exchange();
    assertThat(result).hasStatusOk();
    var body = json.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("totalProducts").asLong()).isEqualTo(1);
    assertThat(body.path("stale").asBoolean()).isTrue();
    assertThat(body.path("lastSyncText").isNull()).isTrue();
    var product = body.path("products").get(0);
    assertThat(product.path("priceText").asString()).isEqualTo("Цена уточняется");
    assertThat(product.path("stockText").asString()).isEqualTo("Наличие уточняется");
    for (String key : List.of("sku", "code", "unit", "imageUrl")) {
      assertThat(product.path(key).isNull()).as(key).isTrue();
    }
    verify(service).list("", null, false, 0);
  }

  @Test
  void forwardsSearchFiltersAndPageToExistingService() {
    when(service.list("54610-2T000", "warehouse", true, 2)).thenReturn(page());
    assertThat(
            mvc.get()
                .uri("/api/catalog/products")
                .param("q", "54610-2T000")
                .param("store", "warehouse")
                .param("available", "true")
                .param("page", "2"))
        .hasStatusOk();
    verify(service).list("54610-2T000", "warehouse", true, 2);
  }

  @Test
  void invalidBindingAndServiceValidationReturnProblemJson() throws Exception {
    for (String parameter : List.of("page", "available")) {
      var result = mvc.get().uri("/api/catalog/products").param(parameter, "nonsense").exchange();
      assertProblem(result.getResponse().getContentAsString(), 400);
      assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
      assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    }
    verifyNoInteractions(service);
    when(service.list(anyString(), any(), anyBoolean(), anyInt()))
        .thenThrow(new CatalogViewService.BadCatalogQuery());
    var result = mvc.get().uri("/api/catalog/products").param("store", "missing").exchange();
    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertProblem(result.getResponse().getContentAsString(), 400);
  }

  @Test
  void pageOutsideResultsReturnsProblemNotFound() throws Exception {
    when(service.list("", null, false, 99)).thenThrow(new CatalogViewService.CatalogNotFound());
    var result = mvc.get().uri("/api/catalog/products").param("page", "99").exchange();
    assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    assertProblem(result.getResponse().getContentAsString(), 404);
  }

  @Test
  void mutationWithOrWithoutCsrfAndUnlistedApiPathsRemainForbidden() {
    assertThat(mvc.post().uri("/api/catalog/products")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(mvc.post().uri("/api/catalog/products").with(csrf()))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(mvc.get().uri("/api/catalog/products/unknown/extra"))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(mvc.get().uri("/api/admin")).hasStatus(HttpStatus.FORBIDDEN);
    verifyNoInteractions(service);
  }

  @Test
  void detailUsesExistingServiceAndReturnsMissingProductAs404() throws Exception {
    var detail =
        new CatalogViewService.Detail(
            page().products().getFirst(),
            List.of(
                new CatalogViewService.OfferView(
                    store().id(), store().name(), "3000 ₸", "4 шт", true, null)),
            store().id(),
            "06.10.2026",
            false);
    when(service.detail("item", store().id())).thenReturn(detail);
    var result =
        mvc.get().uri("/api/catalog/products/item").param("store", store().id()).exchange();
    assertThat(result).hasStatusOk();
    var body = json.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("offers").get(0).path("canAdd").asBoolean()).isTrue();
    assertThat(body.path("offers").get(0).path("unavailabilityReason").isNull()).isTrue();
    assertThat(body.path("selectedStore").asString()).isEqualTo(store().id());
    when(service.detail("missing", null)).thenThrow(new CatalogViewService.CatalogNotFound());
    assertThat(mvc.get().uri("/api/catalog/products/missing")).hasStatus(HttpStatus.NOT_FOUND);
  }

  private void assertProblem(String content, int status) throws Exception {
    var body = json.readTree(content);
    assertThat(body.path("type").asString())
        .isEqualTo(
            status == 400 ? "urn:mbm:catalog:invalid-query" : "urn:mbm:catalog:page-not-found");
    assertThat(body.path("status").asInt()).isEqualTo(status);
    assertThat(body.path("title").asString()).isNotBlank();
    assertThat(body.path("detail").asString()).isNotBlank();
  }

  private CatalogViewService.StoreView store() {
    return new CatalogViewService.StoreView(CatalogViewService.DEFAULT_STORE, "Car City");
  }

  private CatalogViewService.CatalogPage page() {
    return new CatalogViewService.CatalogPage(
        List.of(
            new CatalogViewService.ProductView(
                "000000000000000000000001",
                "Амортизатор",
                null,
                null,
                null,
                "Цена уточняется",
                "Наличие уточняется",
                null)),
        "",
        store().id(),
        store().name(),
        List.of(store()),
        0,
        1,
        1,
        null,
        true);
  }
}
