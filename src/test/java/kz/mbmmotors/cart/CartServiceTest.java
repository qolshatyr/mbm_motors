package kz.mbmmotors.cart;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CartServiceTest {
  private static final String PRODUCT = "6a6e12abb0b00726916dde8b";
  private static final String STORE = "6967a39f62918c7e760f3747";
  private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
  private JdbcTemplate jdbc;
  private CartService service;
  private Cart cart;
  private BigDecimal price;
  private BigDecimal stock;
  private String unit;
  private boolean missing;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() throws Exception {
    jdbc = mock(JdbcTemplate.class);
    service = new CartService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    cart = new Cart();
    price = new BigDecimal("3000");
    stock = new BigDecimal("4");
    unit = "шт.";
    when(jdbc.queryForObject(anyString(), eq(Timestamp.class))).thenReturn(Timestamp.from(NOW));
    when(jdbc.query(anyString(), any(RowMapper.class), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              if (missing) return List.of();
              ResultSet rs = mock(ResultSet.class);
              when(rs.getString("name")).thenReturn("Опора амортизатора");
              when(rs.getString("sku")).thenReturn("54610-2T000");
              when(rs.getString("unit")).thenReturn(unit);
              when(rs.getString("store_name")).thenReturn("Car City");
              when(rs.getBigDecimal("price")).thenReturn(price);
              when(rs.getBigDecimal("quantity")).thenReturn(stock);
              RowMapper<?> mapper = invocation.getArgument(1);
              return List.of(mapper.mapRow(rs, 0));
            });
  }

  @Test
  void addAccumulatesAndUpdateSetsQuantityWithoutAcceptingUnseenPrice() {
    service.add(cart, PRODUCT, STORE, 1);
    service.add(cart, PRODUCT, STORE, 2);
    assertThat(cart.itemCount()).isEqualTo(3);
    price = new BigDecimal("3200");
    service.update(cart, PRODUCT, 2);
    var view = service.view(cart);
    assertThat(view.items().getFirst().quantity()).isEqualTo(2);
    assertThat(view.items().getFirst().priceChanged()).isTrue();
    assertThat(view.totalText().replace('\u00a0', ' ')).isEqualTo("6 400 ₸");
  }

  @Test
  void confirmAcceptsOnlyDisplayedCurrentPrice() {
    service.add(cart, PRODUCT, STORE, 1);
    price = new BigDecimal("3200");
    assertThat(service.view(cart).items().getFirst().currentPrice()).isEqualByComparingTo(price);
    assertThatThrownBy(() -> service.confirmPrice(cart, PRODUCT, new BigDecimal("3100")))
        .hasMessageContaining("снова изменилась");
    assertThat(service.view(cart).items().getFirst().priceChanged()).isTrue();
    service.confirmPrice(cart, PRODUCT, new BigDecimal("3200.00"));
    assertThat(service.view(cart).items().getFirst().priceChanged()).isFalse();
    assertThat(cart.itemCount()).isEqualTo(1);
  }

  @Test
  void additionalAddDoesNotAcknowledgeChangedPrice() {
    service.add(cart, PRODUCT, STORE, 1);
    price = new BigDecimal("3300");
    service.add(cart, PRODUCT, STORE, 1);
    assertThat(service.view(cart).items().getFirst().priceChanged()).isTrue();
    assertThat(cart.itemCount()).isEqualTo(2);
  }

  @Test
  void readingRepricesButDoesNotAcknowledgeNewPrice() {
    service.add(cart, PRODUCT, STORE, 1);
    price = new BigDecimal("3500");
    var first = service.view(cart);
    assertThat(first.hasIssues()).isTrue();
    assertThat(first.items().getFirst().priceChanged()).isTrue();
    assertThat(service.view(cart).items().getFirst().priceChanged()).isTrue();
    stock = BigDecimal.ZERO;
    assertThat(service.view(cart).totalText()).isEqualTo("0 ₸");
    assertThat(service.view(cart).items().getFirst().problem()).contains("нет в наличии");
  }

  @Test
  void rejectedUpdateAndDifferentStoreLeaveCartUnchanged() {
    service.add(cart, PRODUCT, STORE, 2);
    assertThatThrownBy(() -> service.update(cart, PRODUCT, 5))
        .isInstanceOf(CartService.CartProblem.class);
    assertThatThrownBy(() -> service.add(cart, PRODUCT, "698c479d4bb8342bd30e3c33", 1))
        .hasMessageContaining("только со склада");
    assertThat(cart.itemCount()).isEqualTo(2);
    assertThat(cart.storeId()).isEqualTo(STORE);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 1000, Integer.MAX_VALUE})
  void rejectsInvalidIntegerQuantityBeforeDatabaseRead(int quantity) {
    assertThatThrownBy(() -> service.add(cart, PRODUCT, STORE, quantity))
        .isInstanceOf(CartService.CartProblem.class);
    verifyNoInteractions(jdbc);
    assertThat(cart.isEmpty()).isTrue();
  }

  @Test
  void rejectsMissingUnitZeroPriceAndFractionalInsufficientStock() {
    unit = null;
    assertThatThrownBy(() -> service.add(cart, PRODUCT, STORE, 1)).hasMessageContaining("единицу");
    unit = "кг";
    assertThatThrownBy(() -> service.add(cart, PRODUCT, STORE, 1)).hasMessageContaining("единицу");
    unit = "шт";
    price = BigDecimal.ZERO;
    assertThatThrownBy(() -> service.add(cart, PRODUCT, STORE, 1)).hasMessageContaining("Цену");
    price = new BigDecimal("3000");
    stock = new BigDecimal("1.9");
    service.add(cart, PRODUCT, STORE, 1);
    assertThatThrownBy(() -> service.add(cart, PRODUCT, STORE, 1)).hasMessageContaining("остаток");
    assertThat(cart.itemCount()).isEqualTo(1);
  }

  @Test
  void staleOrNeverSyncedCatalogBlocksAddAndExistingCart() {
    service.add(cart, PRODUCT, STORE, 1);
    when(jdbc.queryForObject(anyString(), eq(Timestamp.class)))
        .thenReturn(Timestamp.from(NOW.minusSeconds(901)));
    assertThatThrownBy(() -> service.add(cart, PRODUCT, STORE, 1)).hasMessageContaining("устарели");
    assertThat(service.view(cart).hasIssues()).isTrue();
    when(jdbc.queryForObject(anyString(), eq(Timestamp.class))).thenReturn(null);
    assertThatThrownBy(() -> service.update(cart, PRODUCT, 1)).hasMessageContaining("устарели");
    assertThat(cart.itemCount()).isEqualTo(1);
  }

  @Test
  void missingProductBecomesBlockedAndRemoveResetsStore() {
    service.add(cart, PRODUCT, STORE, 1);
    missing = true;
    assertThat(service.view(cart).items().getFirst().problem()).contains("недоступен");
    service.remove(cart, PRODUCT);
    assertThat(cart.storeId()).isNull();
    assertThat(service.view(cart).items()).isEmpty();
  }

  @Test
  void limitsDistinctItemsAndCanClearWithoutDatabaseAccess() {
    for (int i = 0; i < 50; i++) service.add(cart, String.format("%024x", i), STORE, 1);
    assertThatThrownBy(() -> service.add(cart, String.format("%024x", 50), STORE, 1))
        .hasMessageContaining("50");
    service.clear(cart);
    assertThat(cart.itemCount()).isZero();
    assertThat(cart.storeId()).isNull();
  }
}
