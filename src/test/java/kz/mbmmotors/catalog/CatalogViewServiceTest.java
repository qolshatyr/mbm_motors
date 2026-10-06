package kz.mbmmotors.catalog;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CatalogViewServiceTest {
  @Test
  void addButtonRequiresAnEntirePieceAndValidOffer() {
    assertThat(CatalogViewService.canAdd(BigDecimal.TEN, BigDecimal.ONE, "шт.", false)).isTrue();
    assertThat(CatalogViewService.canAdd(BigDecimal.TEN, new BigDecimal("0.5"), "шт", false))
        .isFalse();
    assertThat(CatalogViewService.canAdd(BigDecimal.ZERO, BigDecimal.ONE, "шт", false)).isFalse();
    assertThat(CatalogViewService.canAdd(BigDecimal.TEN, BigDecimal.ONE, null, false)).isFalse();
    assertThat(CatalogViewService.canAdd(BigDecimal.TEN, BigDecimal.ONE, "шт", true)).isFalse();
  }

  @Test
  void pricesDoNotTurnMissingOrZeroIntoFreeProduct() {
    assertThat(CatalogViewService.priceText(null)).isEqualTo("Цена уточняется");
    assertThat(CatalogViewService.priceText(BigDecimal.ZERO)).isEqualTo("Цена уточняется");
    assertThat(CatalogViewService.priceText(new BigDecimal("3000"))).endsWith(" ₸").contains("3");
  }

  @Test
  void stockDoesNotInventUnitsOrExposeNegativeCounts() {
    assertThat(CatalogViewService.stockText(new BigDecimal("4"), null, false))
        .isEqualTo("В наличии: 4");
    assertThat(CatalogViewService.stockText(new BigDecimal("-1"), "шт", false))
        .isEqualTo("Нет в наличии");
    assertThat(CatalogViewService.stockText(new BigDecimal("4"), "шт", true))
        .isEqualTo("Наличие уточняется");
  }

  @Test
  void userPatternsAreLiteralAndImagesRequireSafeHttps() {
    assertThat(CatalogViewService.escapeLike("50%_!")).isEqualTo("50!%!_!!");
    assertThat(CatalogViewService.safeImage("javascript:alert(1)")).isNull();
    assertThat(CatalogViewService.safeImage("https://user:secret@example.com/img.png")).isNull();
    assertThat(CatalogViewService.safeImage("https://example.com/img.png"))
        .isEqualTo("https://example.com/img.png");
  }
}
