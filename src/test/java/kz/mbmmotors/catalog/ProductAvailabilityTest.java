package kz.mbmmotors.catalog;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductAvailabilityTest {
  @Test
  void explainsMissingUnitWithoutInventingPieces() {
    for (String unit : new String[] {null, "", "   ", "кг"})
      assertThat(ProductAvailability.problem(BigDecimal.TEN, BigDecimal.TEN, unit, 1, false))
          .contains("единицу измерения");
    assertThat(ProductAvailability.problem(BigDecimal.TEN, BigDecimal.ONE, " шт. ", 1, false))
        .isNull();
  }

  @Test
  void explainsFreshnessPriceAndStockAndMatchesButtonEligibility() {
    assertThat(ProductAvailability.problem(BigDecimal.TEN, BigDecimal.TEN, "шт", 1, true))
        .contains("устарели");
    assertThat(ProductAvailability.problem(BigDecimal.ZERO, BigDecimal.TEN, "шт", 1, false))
        .contains("Цену");
    assertThat(ProductAvailability.problem(BigDecimal.TEN, BigDecimal.ZERO, "шт", 1, false))
        .contains("нет в наличии");
    assertThat(ProductAvailability.problem(BigDecimal.TEN, new BigDecimal("0.5"), "шт", 1, false))
        .contains("превышает");
    assertThat(ProductAvailability.problem(BigDecimal.TEN, BigDecimal.ONE, "шт", 2, false))
        .contains("превышает");
    assertThat(CatalogViewService.canAdd(BigDecimal.TEN, BigDecimal.ONE, "шт", false)).isTrue();
    assertThat(CatalogViewService.canAdd(BigDecimal.TEN, BigDecimal.ONE, "", false)).isFalse();
  }
}
