package kz.mbmmotors.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import java.util.concurrent.*;
import kz.mbmmotors.cart.Cart;
import kz.mbmmotors.cart.CartService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@EnabledIfEnvironmentVariable(named = "MBM_TEST_DATABASE_URL", matches = ".*/mbm_test")
@SpringBootTest(
    properties = {
      "spring.datasource.url=${MBM_TEST_DATABASE_URL}",
      "spring.datasource.username=${MBM_TEST_DATABASE_USER:mbm}",
      "spring.datasource.password=${MBM_TEST_DATABASE_PASSWORD:}"
    })
@AutoConfigureMockMvc
class OrderIntegrationTest {
  static final String STORE = "6967a39f62918c7e760f3747";
  static final String PRODUCT = "aaaaaaaaaaaaaaaaaaaaaaaa";
  static final UUID PARTNER = UUID.fromString("00000000-0000-4000-8000-000000000001");
  static final org.springframework.test.web.servlet.request.RequestPostProcessor SIGNED_IN =
      user("turbo").roles("PARTNER");
  @Autowired OrderService orders;
  @Autowired CartService carts;
  @Autowired CheckoutProperties properties;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;

  @BeforeEach
  void setup() {
    jdbc.execute(
        "delete from local_order_items; delete from local_orders; delete from partners; delete from product_images; delete from product_offers; delete from products; delete from sync_runs");
    jdbc.update(
        "insert into partners(id,login,password_hash,company,contact_name,phone,created_at) values (?,'turbo',?,'Synthetic Partner','Synthetic Buyer','+77011234567',now())",
        PARTNER,
        "$2a$10$" + "a".repeat(53));
    jdbc.update(
        "insert into products(id,name,sku,unit,imported_at) values (?,'Test item','TEST','шт',now())",
        PRODUCT);
    jdbc.update(
        "insert into product_offers(product_id,store_id,quantity,price) values (?,?,10,2500.01)",
        PRODUCT,
        STORE);
    jdbc.update(
        "insert into sync_runs(id,mode,status,started_at,finished_at) values (?,'FULL','SUCCESS',now(),now())",
        UUID.randomUUID());
    properties.setPickupStoreIds(java.util.List.of(STORE));
  }

  Cart cart() {
    var cart = new Cart();
    carts.add(cart, PRODUCT, STORE, 2);
    return cart;
  }

  SubmitOrder command(OrderService.Preview preview) {
    return SubmitOrder.validate(
        preview.idempotencyKey(),
        preview.revision(),
        "Synthetic Buyer",
        "87011234567",
        "Synthetic fixture",
        "true");
  }

  @Test
  void transactionPersistsExactSnapshotAndPrivateReplay() {
    var cart = cart();
    var state = new CheckoutSession();
    var preview = orders.preview(cart, state, null);
    var cmd = command(preview);
    var result = orders.submit(cart, state, cmd, PARTNER);
    assertTrue(result.created());
    assertEquals(
        "5000.02", jdbc.queryForObject("select total::text from local_orders", String.class));
    assertEquals(1, jdbc.queryForObject("select count(*) from local_order_items", Integer.class));
    assertEquals(
        "+77011234567", jdbc.queryForObject("select phone from local_orders", String.class));
    carts.clear(cart);
    assertFalse(orders.submit(cart, state, cmd, PARTNER).created());
    assertEquals(1, jdbc.queryForObject("select count(*) from delivery_outbox", Integer.class));
    assertEquals(
        "WAITING_CONFIGURATION",
        jdbc.queryForObject("select state from delivery_outbox", String.class));
    assertEquals(result.receipt(), orders.receipt(state.ownerId(), cmd.key()));
    assertEquals(
        404,
        assertThrows(OrderProblem.class, () -> orders.receipt(UUID.randomUUID(), cmd.key()))
            .status());
    var different =
        new SubmitOrder(cmd.key(), cmd.revision(), cmd.customerName(), cmd.phone(), "Changed");
    assertEquals(
        409,
        assertThrows(OrderProblem.class, () -> orders.submit(cart, state, different, PARTNER))
            .status());
    jdbc.update("delete from product_offers; delete from products");
    assertEquals(1, jdbc.queryForObject("select count(*) from local_order_items", Integer.class));
  }

  @Test
  void blocksUnconfiguredPickupStalePriceAndChangedRevisionWithoutClearingCart() {
    var cart = cart();
    var state = new CheckoutSession();
    var p = orders.preview(cart, state, null);
    var cmd = command(p);
    properties.setPickupStoreIds(java.util.List.of());
    assertFalse(orders.preview(cart, state, null).canSubmit());
    assertThrows(OrderProblem.class, () -> orders.submit(cart, state, cmd, PARTNER));
    properties.setPickupStoreIds(java.util.List.of(STORE));
    jdbc.update("update product_offers set price=2600 where product_id=?", PRODUCT);
    assertThrows(OrderProblem.class, () -> orders.submit(cart, state, cmd, PARTNER));
    carts.confirmPrice(cart, PRODUCT, new java.math.BigDecimal("2600"));
    assertThrows(OrderProblem.class, () -> orders.submit(cart, state, cmd, PARTNER));
    var fresh = command(orders.preview(cart, state, null));
    jdbc.update("update sync_runs set finished_at=now()-interval '16 minutes'");
    assertThrows(OrderProblem.class, () -> orders.submit(cart, state, fresh, PARTNER));
    assertEquals(2, cart.itemCount());
    assertEquals(0, jdbc.queryForObject("select count(*) from local_orders", Integer.class));
  }

  @Test
  void rollbackWhenItemInsertFailsLeavesNoOrderAndKeepsCart() {
    var cart = cart();
    var state = new CheckoutSession();
    var cmd = command(orders.preview(cart, state, null));
    jdbc.execute(
        "alter table local_order_items add constraint test_reject_items check (quantity < 1)");
    try {
      assertThrows(
          org.springframework.dao.DataIntegrityViolationException.class,
          () -> orders.submit(cart, state, cmd, PARTNER));
      assertEquals(0, jdbc.queryForObject("select count(*) from local_orders", Integer.class));
      assertEquals(2, cart.itemCount());
    } finally {
      jdbc.execute("alter table local_order_items drop constraint test_reject_items");
    }
  }

  @Test
  void rollbackWhenOutboxInsertFailsLeavesNoOrderItemsOrJobAndKeepsCart() {
    var cart = cart();
    var state = new CheckoutSession();
    var cmd = command(orders.preview(cart, state, null));
    jdbc.execute(
        "alter table delivery_outbox add constraint test_reject_outbox check (state <> 'WAITING_CONFIGURATION')");
    try {
      assertThrows(
          org.springframework.dao.DataIntegrityViolationException.class,
          () -> orders.submit(cart, state, cmd, PARTNER));
      assertEquals(0, jdbc.queryForObject("select count(*) from local_orders", Integer.class));
      assertEquals(0, jdbc.queryForObject("select count(*) from local_order_items", Integer.class));
      var session = new MockHttpSession();
      session.setAttribute("mbm.cart", cart);
      session.setAttribute("mbm.checkout", state);
      var failure =
          assertThrows(
              jakarta.servlet.ServletException.class,
              () ->
                  mvc.perform(
                      post("/api/orders")
                          .session(session)
                          .with(SIGNED_IN)
                          .with(csrf())
                          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                          .param("idempotencyKey", cmd.key().toString())
                          .param("revision", cmd.revision())
                          .param("customerName", cmd.customerName())
                          .param("phone", cmd.phone())
                          .param("comment", cmd.comment())
                          .param("consent", "true")));
      assertInstanceOf(
          org.springframework.dao.DataIntegrityViolationException.class, failure.getCause());
      assertEquals(0, jdbc.queryForObject("select count(*) from local_orders", Integer.class));
      assertEquals(0, jdbc.queryForObject("select count(*) from local_order_items", Integer.class));
      assertEquals(0, jdbc.queryForObject("select count(*) from delivery_outbox", Integer.class));
      assertEquals(cmd.key(), state.key());
      assertEquals(2, cart.itemCount());
    } finally {
      jdbc.execute("alter table delivery_outbox drop constraint test_reject_outbox");
    }
  }

  @Test
  void concurrentSameKeyCreatesOneOrderAndOneReplay() throws Exception {
    var first = cart();
    var second = cart();
    var state = new CheckoutSession();
    var cmd = command(orders.preview(first, state, null));
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                gate.await();
                return orders.submit(first, state, cmd, PARTNER);
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return orders.submit(second, state, cmd, PARTNER);
              });
      gate.countDown();
      var ar = a.get(20, TimeUnit.SECONDS);
      var br = b.get(20, TimeUnit.SECONDS);
      assertNotEquals(ar.created(), br.created());
      assertEquals(ar.receipt(), br.receipt());
    }
    assertEquals(1, jdbc.queryForObject("select count(*) from local_orders", Integer.class));
    assertEquals(1, jdbc.queryForObject("select count(*) from delivery_outbox", Integer.class));
  }

  @Test
  void mvcValidationSecurityAndCommitBeforeClear() throws Exception {
    var session = new MockHttpSession();
    var cart = cart();
    session.setAttribute("mbm.cart", cart);
    mvc.perform(get("/api/checkout").session(session)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/checkout").session(session).with(SIGNED_IN))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.contact.customerName").value("Synthetic Buyer"))
        .andExpect(jsonPath("$.contact.phone").value("+77011234567"));
    mvc.perform(
            post("/api/orders")
                .session(session)
                .with(SIGNED_IN)
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.phone").exists());
    mvc.perform(
            post("/api/orders")
                .session(session)
                .with(SIGNED_IN)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/orders/private").with(SIGNED_IN)).andExpect(status().isForbidden());
    mvc.perform(get("/api/orders/by-key/not-uuid").with(SIGNED_IN))
        .andExpect(status().isBadRequest());
    var state = (CheckoutSession) session.getAttribute("mbm.checkout");
    var issuedKey = state.key();
    var request =
        post("/api/orders")
            .session(session)
            .with(SIGNED_IN)
            .with(csrf())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("idempotencyKey", state.key().toString())
            .param("revision", state.revision())
            .param("customerName", "Synthetic Buyer")
            .param("phone", "87011234567")
            .param("consent", "true");
    mvc.perform(request)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("RECEIVED"))
        .andExpect(jsonPath("$.phone").doesNotExist());
    assertTrue(cart.isEmpty());
    assertNull(state.key());
    mvc.perform(request).andExpect(status().isOk());
    assertEquals(PARTNER, jdbc.queryForObject("select partner_id from local_orders", UUID.class));
    mvc.perform(get("/api/account/orders").with(SIGNED_IN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orders.length()").value(1))
        .andExpect(jsonPath("$.orders[0].status").value("RECEIVED"));
    String number = jdbc.queryForObject("select 'MBM-'||number from local_orders", String.class);
    mvc.perform(get("/api/account/orders/" + number).with(SIGNED_IN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].productId").value(PRODUCT))
        .andExpect(jsonPath("$.items[0].quantity").value(2));
    mvc.perform(get("/api/orders/by-key/" + issuedKey).session(session).with(SIGNED_IN))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/orders/by-key/" + issuedKey).session(new MockHttpSession()).with(SIGNED_IN))
        .andExpect(status().isNotFound());
    carts.add(cart, PRODUCT, STORE, 2);
    mvc.perform(get("/api/checkout").session(session).with(SIGNED_IN)).andExpect(status().isOk());
    assertNotEquals(issuedKey, state.key());
  }
}
