package kz.mbmmotors.cart.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.math.BigDecimal;
import java.util.List;
import kz.mbmmotors.cart.CartService;
import kz.mbmmotors.config.WebSecurityConfiguration;
import kz.mbmmotors.config.web.CsrfController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest({CartApiController.class, CsrfController.class})
@Import(WebSecurityConfiguration.class)
// The portal is partner-only; access rules themselves are covered by PartnerSecurityTest.
@WithMockUser(roles = "PARTNER")
class CartApiControllerTest {
  @Autowired MockMvcTester mvc;
  @Autowired JsonMapper json;
  @MockitoBean CartService service;

  @Test
  void csrfEndpointTokenWorksAsHeaderInSameSessionAndSessionCartIsShared() throws Exception {
    var session = new MockHttpSession();
    when(service.view(any())).thenReturn(empty());
    var tokenResult = mvc.get().uri("/api/csrf").session(session).exchange();
    assertThat(tokenResult).hasStatusOk();
    assertThat(tokenResult.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    var token = json.readTree(tokenResult.getResponse().getContentAsString());
    assertThat(token.path("headerName").asString()).isEqualTo("X-CSRF-TOKEN");
    var result =
        mvc.post()
            .uri("/api/cart/add")
            .session(session)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .header(token.path("headerName").asString(), token.path("token").asString())
            .param("productId", "product")
            .param("storeId", "store")
            .exchange();
    assertThat(result).hasStatusOk();
    verify(service)
        .add(same(CartController.sessionCart(session)), eq("product"), eq("store"), eq(1));
    var body = json.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("message").asString()).isEqualTo("Товар добавлен в корзину.");
    assertThat(CartController.sessionCart(new MockHttpSession()))
        .isNotSameAs(CartController.sessionCart(session));
  }

  @Test
  void getSerializesNullableFieldsAndExactDecimalAsString() throws Exception {
    when(service.view(any()))
        .thenReturn(
            new CartService.CartView(
                "store",
                "Car City",
                List.of(
                    new CartService.CartItem(
                        "product",
                        "Товар",
                        null,
                        2,
                        null,
                        "Цена",
                        "Сумма",
                        null,
                        true,
                        new BigDecimal("9007199254740993.0100")),
                    new CartService.CartItem(
                        "gone",
                        "Недоступен",
                        null,
                        1,
                        null,
                        "Цена уточняется",
                        "—",
                        "Недоступен",
                        false,
                        null)),
                "0 ₸",
                true));
    var result = mvc.get().uri("/api/cart").exchange();
    assertThat(result).hasStatusOk();
    assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    var body = json.readTree(result.getResponse().getContentAsString());
    assertThat(body.path("message").isNull()).isTrue();
    var item = body.path("cart").path("items").get(0);
    assertThat(item.path("currentPrice").asString()).isEqualTo("9007199254740993.0100");
    assertThat(item.path("currentPrice").isString()).isTrue();
    assertThat(item.path("sku").isNull()).isTrue();
    assertThat(body.path("cart").path("items").get(1).path("currentPrice").isNull()).isTrue();
  }

  @Test
  void allActionsUseExistingService() {
    var session = new MockHttpSession();
    when(service.view(any())).thenReturn(empty());
    var cart = CartController.sessionCart(session);
    assertThat(
            mvc.post()
                .uri("/api/cart/update")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("productId", "p")
                .param("quantity", "2"))
        .hasStatusOk();
    assertThat(
            mvc.post()
                .uri("/api/cart/confirm-price")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("productId", "p")
                .param("expectedPrice", "1.2500"))
        .hasStatusOk();
    assertThat(
            mvc.post()
                .uri("/api/cart/remove")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("productId", "p"))
        .hasStatusOk();
    assertThat(
            mvc.post()
                .uri("/api/cart/clear")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .hasStatusOk();
    verify(service).update(cart, "p", 2);
    verify(service).confirmPrice(cart, "p", new BigDecimal("1.2500"));
    verify(service).remove(cart, "p");
    verify(service).clear(cart);
  }

  @Test
  void missingOrInvalidParametersAre400AndBusinessRules409() throws Exception {
    for (String quantity : List.of("missing", "1.5")) {
      var request =
          mvc.post()
              .uri("/api/cart/update")
              .with(csrf())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .param("productId", "p");
      if (!quantity.equals("missing")) request.param("quantity", quantity);
      var result = request.exchange();
      assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
      assertProblem(result.getResponse().getContentAsString(), 400);
    }
    verifyNoInteractions(service);
    doThrow(new CartService.CartProblem("Остаток изменился."))
        .when(service)
        .add(any(), any(), any(), anyInt());
    var result =
        mvc.post()
            .uri("/api/cart/add")
            .with(csrf())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("productId", "p")
            .param("storeId", "s")
            .exchange();
    assertThat(result).hasStatus(HttpStatus.CONFLICT);
    assertProblem(result.getResponse().getContentAsString(), 409);
    verify(service, never()).view(any());
  }

  @Test
  void csrfAndUnlistedApiRoutesAreForbiddenWithProblemJson() throws Exception {
    for (String path : List.of("/api/cart/add", "/api/cart/unknown", "/api/csrf")) {
      var result =
          mvc.post().uri(path).contentType(MediaType.APPLICATION_FORM_URLENCODED).exchange();
      assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
      assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
      assertProblem(result.getResponse().getContentAsString(), 403);
    }
    assertThat(mvc.post().uri("/api/cart/unknown").with(csrf())).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(mvc.get().uri("/api/cart/add")).hasStatus(HttpStatus.FORBIDDEN);
    var legacy = mvc.post().uri("/cart/add").exchange();
    assertThat(legacy.getResponse().getContentType()).startsWith("text/plain");
    verifyNoInteractions(service);
  }

  private void assertProblem(String content, int status) throws Exception {
    var body = json.readTree(content);
    assertThat(body.path("status").asInt()).isEqualTo(status);
    assertThat(body.path("type").asString()).isNotBlank();
    assertThat(body.path("detail").asString()).isNotBlank();
  }

  private CartService.CartView empty() {
    return new CartService.CartView(null, null, List.of(), "0 ₸", false);
  }
}
