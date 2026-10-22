package kz.mbmmotors.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kz.mbmmotors.catalog.ProductTags.Kind;
import kz.mbmmotors.catalog.ProductTags.Tag;
import org.junit.jupiter.api.Test;

class ProductTagsTest {
  @Test
  void readsRolesInImportOrder() {
    assertThat(
            ProductTags.classify(
                List.of(
                    "Ходовая часть",
                    "Амортизаторы и опоры",
                    "Hyundai",
                    "Sonata",
                    "Kia",
                    "Optima",
                    "Бренд HPT")))
        .containsExactly(
            new Tag("Ходовая часть", Kind.SECTION, null),
            new Tag("Амортизаторы и опоры", Kind.SUBSECTION, "Ходовая часть"),
            new Tag("Hyundai", Kind.MAKE, null),
            new Tag("Sonata", Kind.MODEL, "Hyundai"),
            new Tag("Kia", Kind.MAKE, null),
            new Tag("Optima", Kind.MODEL, "Kia"),
            new Tag("HPT", Kind.BRAND, null));
  }

  @Test
  void productsWithoutSectionKeepMakeAndBrand() {
    assertThat(ProductTags.classify(List.of("KIA", "Бренд NGP", "Бренд Mobis")))
        .containsExactly(
            new Tag("KIA", Kind.MAKE, null),
            new Tag("NGP", Kind.BRAND, null),
            new Tag("Mobis", Kind.BRAND, null));
  }

  @Test
  void extraManualTagsDoNotBecomeFilters() {
    assertThat(
            ProductTags.classify(List.of("Двигатель", "Поршневая группа", "Распродажа", "Бренд ")))
        .extracting(Tag::kind)
        .containsExactly(Kind.SECTION, Kind.SUBSECTION, Kind.OTHER, Kind.OTHER);
  }
}
