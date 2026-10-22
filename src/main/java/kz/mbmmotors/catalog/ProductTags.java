package kz.mbmmotors.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the roles of CloudShop tags. The catalogue import (tools/catalog/fill_categories.py) writes
 * them in a fixed order: section, subsection, each car make followed by its models, then «Бренд …»
 * for every part brand. Makes are recognised by name, brands by the prefix; a tag after a make is
 * its model, and the first two other tags before any make are the section and the subsection.
 */
final class ProductTags {
  static final String BRAND_PREFIX = "Бренд ";

  /** Display names of tools/catalog/catalog_rules.py MAKE_TOKENS. */
  private static final Set<String> MAKES =
      Set.of(
          "hyundai",
          "kia",
          "genesis",
          "toyota",
          "lexus",
          "nissan",
          "honda",
          "mitsubishi",
          "daewoo",
          "chevrolet",
          "ssangyong");

  enum Kind {
    SECTION,
    SUBSECTION,
    MAKE,
    MODEL,
    BRAND,
    OTHER
  }

  /** {@code parent}: the section of a subsection or the make of a model. */
  record Tag(String name, Kind kind, String parent) {}

  private ProductTags() {}

  static List<Tag> classify(List<String> tags) {
    List<Tag> result = new ArrayList<>();
    String section = null;
    String subsection = null;
    String make = null;
    for (String tag : tags) {
      if (tag.startsWith(BRAND_PREFIX) && tag.length() > BRAND_PREFIX.length()) {
        result.add(new Tag(tag.substring(BRAND_PREFIX.length()).strip(), Kind.BRAND, null));
      } else if (MAKES.contains(tag.toLowerCase(Locale.ROOT))) {
        make = tag;
        result.add(new Tag(tag, Kind.MAKE, null));
      } else if (make != null) {
        result.add(new Tag(tag, Kind.MODEL, make));
      } else if (section == null) {
        section = tag;
        result.add(new Tag(tag, Kind.SECTION, null));
      } else if (subsection == null) {
        subsection = tag;
        result.add(new Tag(tag, Kind.SUBSECTION, section));
      } else {
        result.add(new Tag(tag, Kind.OTHER, null));
      }
    }
    return result;
  }
}
