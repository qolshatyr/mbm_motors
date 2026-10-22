package kz.mbmmotors.cloudshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CloudShopProductParserTest {
  private final CloudShopProductParser parser = new CloudShopProductParser();
  private final String product =
      """
        {"id":"6a6e12abb0b00726916dde8b","options":{"name":"Опора"},"sku":"54610-2T000",
        "code":"105427","unit":null,"price":null,"stock":[],"store_prices":[],
        "updated_at":"2026-10-04 10:15:46","img":[]}
        """;

  @Test
  void normalizesObservedEmptyMapsAndInterpretsLegacyDateAsUtc() {
    CloudShopProduct result = parser.parse("[" + product + "]").getFirst();
    assertThat(result.stock()).isEmpty();
    assertThat(result.storePrices()).isEmpty();
    assertThat(result.price()).isNull();
    assertThat(result.unit()).isEqualTo("шт");
    assertThat(result.name()).isEqualTo("Опора");
    assertThat(result.updatedAt()).isEqualTo(Instant.parse("2026-10-04T10:15:46Z"));
  }

  @Test
  void preservesZeroPricesNegativeStockAndDuplicateSkus() {
    String priced =
        product
            .replace("\"price\":null", "\"price\":0")
            .replace("\"stock\":[]", "\"stock\":{\"store\":-1}")
            .replace("\"store_prices\":[]", "\"store_prices\":{\"store\":\"0.00\"}");
    var products =
        parser.parse(
            "["
                + priced
                + ","
                + priced.replace("6a6e12abb0b00726916dde8b", "6a6e12abb0b00726916dde8c")
                + "]");
    assertThat(products).hasSize(2);
    assertThat(products.getFirst().price()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(products.getFirst().storePrices().get("store"))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(products.getFirst().stock().get("store"))
        .isEqualByComparingTo(BigDecimal.ONE.negate());
  }

  @Test
  void nullStorePriceMeansNoPriceForThatPlaceButNullStockIsRejected() {
    String partial =
        product.replace("\"store_prices\":[]", "\"store_prices\":{\"a\":null,\"b\":\"5.00\"}");
    var prices = parser.parse("[" + partial + "]").getFirst().storePrices();
    assertThat(prices).containsOnlyKeys("b");
    assertThatThrownBy(
            () ->
                parser.parse("[" + product.replace("\"stock\":[]", "\"stock\":{\"a\":null}") + "]"))
        .isInstanceOf(CloudShopException.class);
  }

  @Test
  void rejectsNonemptyArraysAndInvalidNumbersWithoutExposingResponse() {
    for (String invalid :
        new String[] {
          product.replace("\"stock\":[]", "\"stock\":[1]"),
          product.replace("\"store_prices\":[]", "\"store_prices\":[1]"),
          product.replace("\"price\":null", "\"price\":\"secret-token\"")
        }) {
      assertThatThrownBy(() -> parser.parse("[" + invalid + "]"))
          .isInstanceOf(CloudShopException.class)
          .hasMessage("CloudShop returned an unsupported product response")
          .hasNoCause();
    }
  }

  @Test
  void rejectsPartialMalformedPagesAndWrongResponseRoot() {
    assertThatThrownBy(() -> parser.parse("[" + product + ",{}]"))
        .isInstanceOf(CloudShopException.class);
    assertThatThrownBy(() -> parser.parse("{\"status\":false}"))
        .isInstanceOf(CloudShopException.class);
  }

  @Test
  void blankUnitMeansPieceAndExplicitUnitIsKept() {
    assertThat(CloudShopProductParser.unit(null)).isEqualTo("шт");
    assertThat(CloudShopProductParser.unit("  ")).isEqualTo("шт");
    assertThat(CloudShopProductParser.unit(" шт. ")).isEqualTo("шт.");
    assertThat(CloudShopProductParser.unit("л")).isEqualTo("л");
  }

  @Test
  void keepsTagsInOrderAndRejectsNonTextTags() {
    assertThat(parser.parse("[" + product + "]").getFirst().tags()).isEmpty();
    String tagged =
        product.replace(
            "\"img\":[]", "\"img\":[],\"tags\":[\"Двигатель\",\" Hyundai \",\"\",\"Hyundai\"]");
    assertThat(parser.parse("[" + tagged + "]").getFirst().tags())
        .containsExactly("Двигатель", "Hyundai");
    String broken = product.replace("\"img\":[]", "\"img\":[],\"tags\":[1]");
    assertThatThrownBy(() -> parser.parse("[" + broken + "]"))
        .isInstanceOf(CloudShopException.class);
  }
}
