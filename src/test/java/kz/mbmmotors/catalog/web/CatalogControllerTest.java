package kz.mbmmotors.catalog.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

@WebMvcTest(CatalogController.class)
@Import(WebSecurityConfiguration.class)
class CatalogControllerTest {
  @Autowired MockMvcTester mvc;
  @MockitoBean CatalogViewService service;

  @Test
  void routesRenderAndEscapeUntrustedProductText() throws Exception {
    var product =
        new CatalogViewService.ProductView(
            "000000000000000000000001",
            "<script>alert('unsafe')</script>",
            "SKU",
            "1",
            null,
            "3 000 ₸",
            "В наличии: 4",
            null);
    when(service.list("", null, false, 0))
        .thenReturn(
            new CatalogViewService.CatalogPage(
                List.of(product),
                "",
                CatalogViewService.DEFAULT_STORE,
                "Car City",
                List.of(
                    new CatalogViewService.StoreView(CatalogViewService.DEFAULT_STORE, "Car City")),
                0,
                1,
                1,
                "05.10.2026 12:00",
                false));
    var result = mvc.get().uri("/catalog").exchange();
    assertThat(result).hasStatusOk().hasViewName("catalog/list");
    assertThat(result.getResponse().getContentAsString())
        .contains("&lt;script&gt;")
        .doesNotContain("<script>alert");
    assertThat(mvc.get().uri("/")).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/catalog");
  }

  @Test
  void badPageAndMissingProductReturnFriendlyErrorStatus() {
    assertThat(mvc.get().uri("/catalog").param("page", "nonsense"))
        .hasStatus(HttpStatus.BAD_REQUEST);
    when(service.detail(anyString(), any())).thenThrow(new CatalogViewService.CatalogNotFound());
    assertThat(mvc.get().uri("/catalog/missing"))
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasViewName("error");
  }
}
