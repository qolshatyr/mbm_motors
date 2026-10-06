package kz.mbmmotors.cart.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import kz.mbmmotors.cart.Cart;
import kz.mbmmotors.cart.CartService;
import kz.mbmmotors.config.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CartController.class)
@Import(WebSecurityConfiguration.class)
class CartControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean CartService service;

  @Test
  void continueSearchKeepsCartStoreAndEmptyCartUsesDefault() throws Exception {
    when(service.view(any()))
        .thenReturn(new CartService.CartView(null, null, List.of(), "0 ₸", false));
    mvc.perform(get("/cart"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "href=\"/catalog\">Продолжить поиск</a>")));
    var item =
        new CartService.CartItem(
            "000000000000000000000001",
            "Запчасть",
            "SKU",
            1,
            "шт",
            "3000 ₸",
            "3000 ₸",
            null,
            false,
            new java.math.BigDecimal("3000"));
    when(service.view(any()))
        .thenReturn(
            new CartService.CartView(
                "698c479d4bb8342bd30e3c33", "БАКОРДА", List.of(item), "3000 ₸", false));
    mvc.perform(get("/cart"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "href=\"/catalog?store=698c479d4bb8342bd30e3c33\">Продолжить поиск</a>")));
  }

  @Test
  void formIsCsrfProtectedAndQuantityCannotBeFractional() throws Exception {
    mvc.perform(post("/cart/add").param("productId", "id").param("storeId", "store"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
    mvc.perform(
            post("/cart/add")
                .with(csrf())
                .param("productId", "id")
                .param("storeId", "store")
                .param("quantity", "1.5"))
        .andExpect(status().isFound())
        .andExpect(flash().attributeExists("problem"));
    verifyNoInteractions(service);
  }

  @Test
  void cartBelongsToSessionAndOnlyServerServiceChangesIt() throws Exception {
    var first = new MockHttpSession();
    var second = new MockHttpSession();
    mvc.perform(
            post("/cart/add")
                .session(first)
                .with(csrf())
                .param("productId", "id")
                .param("storeId", "store")
                .param("quantity", "2")
                .param("price", "1"))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/cart"));
    verify(service).add(same(CartController.sessionCart(first)), eq("id"), eq("store"), eq(2));
    assertThat(CartController.sessionCart(first)).isNotSameAs(CartController.sessionCart(second));
    doThrow(new CartService.CartProblem("Нет в наличии"))
        .when(service)
        .update(any(Cart.class), eq("id"), eq(999));
    mvc.perform(
            post("/cart/update")
                .session(first)
                .with(csrf())
                .param("productId", "id")
                .param("quantity", "999"))
        .andExpect(status().isFound())
        .andExpect(flash().attribute("problem", "Нет в наличии"));
  }

  @Test
  void emptyCartAndUntrustedItemTextRenderSafely() throws Exception {
    when(service.view(any()))
        .thenReturn(new CartService.CartView(null, null, List.of(), "0 ₸", false));
    mvc.perform(get("/cart"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Корзина пока пуста")));
    var item =
        new CartService.CartItem(
            "000000000000000000000001",
            "<script>unsafe</script>",
            "SKU",
            1,
            "шт",
            "3000 ₸",
            "3000 ₸",
            null,
            true,
            new java.math.BigDecimal("3000"));
    when(service.view(any()))
        .thenReturn(new CartService.CartView("store", "Car City", List.of(item), "3000 ₸", true));
    mvc.perform(get("/cart"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
  }
}
