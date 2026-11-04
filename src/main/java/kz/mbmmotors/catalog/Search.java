package kz.mbmmotors.catalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Text condition of the catalog search. Every word of the query must be found in the name, article
 * or code, in any order, so «колодки sonata» finds «Колодки передние HY SONATA». Russian spellings
 * of common models and makes also match their Latin names. With {@code fuzzy}, a word of four or
 * more letters may also match a similar word of the name (pg_trgm), which catches typos.
 */
final class Search {
  static final int MAX_WORDS = 8;

  /** Lower-case word → spellings used in CloudShop names. */
  private static final Map<String, List<String>> SYNONYMS =
      Map.ofEntries(
          Map.entry("хендай", List.of("hyundai", "hy ")),
          Map.entry("хендэ", List.of("hyundai", "hy ")),
          Map.entry("хундай", List.of("hyundai", "hy ")),
          Map.entry("хюндай", List.of("hyundai", "hy ")),
          Map.entry("хёндэ", List.of("hyundai", "hy ")),
          Map.entry("киа", List.of("kia")),
          Map.entry("солярис", List.of("solaris")),
          Map.entry("соната", List.of("sonata")),
          Map.entry("элантра", List.of("elantra")),
          Map.entry("акцент", List.of("accent")),
          Map.entry("туксон", List.of("tucson")),
          Map.entry("тусон", List.of("tucson")),
          Map.entry("туссан", List.of("tucson")),
          Map.entry("сантафе", List.of("santa fe", "santafe")),
          Map.entry("санта", List.of("santa")),
          Map.entry("спортейдж", List.of("sportage")),
          Map.entry("спортедж", List.of("sportage")),
          Map.entry("рио", List.of("rio")),
          Map.entry("церато", List.of("cerato")),
          Map.entry("серато", List.of("cerato")),
          Map.entry("соренто", List.of("sorento")),
          Map.entry("оптима", List.of("optima")),
          Map.entry("пиканто", List.of("picanto")),
          Map.entry("крета", List.of("creta")),
          Map.entry("гетц", List.of("getz")),
          Map.entry("старекс", List.of("starex")),
          Map.entry("портер", List.of("porter")),
          Map.entry("бонго", List.of("bongo")),
          Map.entry("карнивал", List.of("carnival")),
          Map.entry("грандер", List.of("grandeur")),
          Map.entry("грандеур", List.of("grandeur")),
          Map.entry("азера", List.of("azera")),
          Map.entry("палисад", List.of("palisade")),
          Map.entry("мохав", List.of("mohave")),
          Map.entry("соул", List.of("soul")),
          Map.entry("сид", List.of("ceed")),
          Map.entry("генезис", List.of("genesis")),
          Map.entry("санйонг", List.of("ssangyong")),
          Map.entry("саньенг", List.of("ssangyong")));

  private Search() {}

  record Condition(String sql, List<Object> params) {}

  static List<String> words(String query) {
    return Arrays.stream(query.strip().split("\\s+"))
        .filter(word -> !word.isEmpty())
        .limit(MAX_WORDS)
        .toList();
  }

  static String compact(String text) {
    return text.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
  }

  /** Typo tolerance is tried only when a word has enough letters to compare. */
  static boolean fuzzyWorthy(String query) {
    return words(query).stream().anyMatch(Search::fuzzyWord);
  }

  private static boolean fuzzyWord(String word) {
    return word.codePoints().filter(Character::isLetter).count() >= 4;
  }

  static Condition condition(String query, boolean fuzzy) {
    List<Object> params = new ArrayList<>();
    List<String> perWord = new ArrayList<>();
    for (String word : words(query)) {
      List<String> any = new ArrayList<>();
      String pattern = "%" + CatalogViewService.escapeLike(word) + "%";
      any.add("p.name ilike ? escape '!'");
      any.add("coalesce(p.sku,'') ilike ? escape '!'");
      any.add("coalesce(p.code,'') ilike ? escape '!'");
      params.add(pattern);
      params.add(pattern);
      params.add(pattern);
      String compact = compact(word);
      if (!compact.isEmpty()) {
        any.add(COMPACT_SKU + " like ? escape '!'");
        params.add("%" + CatalogViewService.escapeLike(compact) + "%");
      }
      for (String synonym : SYNONYMS.getOrDefault(word.toLowerCase(Locale.ROOT), List.of())) {
        any.add("(' ' || p.name || ' ') ilike ? escape '!'");
        params.add("%" + CatalogViewService.escapeLike(synonym) + "%");
      }
      if (fuzzy && fuzzyWord(word)) {
        any.add("word_similarity(?, lower(p.name)) >= 0.5");
        params.add(word.toLowerCase(Locale.ROOT));
      }
      perWord.add("(" + String.join(" or ", any) + ")");
    }
    String sql = "(" + String.join(" and ", perWord) + ")";
    String whole = compact(query);
    if (!whole.isEmpty()) {
      // An article typed with spaces or dashes in other places, such as «54610 2T000».
      sql = "(" + sql + " or " + COMPACT_SKU + " like ? escape '!')";
      params.add("%" + CatalogViewService.escapeLike(whole) + "%");
    }
    return new Condition(sql, params);
  }

  private static final String COMPACT_SKU =
      "regexp_replace(lower(coalesce(p.sku,'')), '[^[:alnum:]]', '', 'g')";
}
