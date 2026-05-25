import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ReadingOrderResolver {

  public static List<Region> order(List<Region> regions) {
    if (regions == null || regions.isEmpty()) {
      return List.of();
    }

    List<Region> ordered = new ArrayList<>();
    for (Region region : regions) {
      if (region != null) {
        ordered.add(region);
      }
    }

    if (ordered.size() < 2) {
      return ordered;
    }

    if (allPrinted(ordered)) {
      ordered.sort(
        Comparator.comparingInt((Region region) ->
          region.printedPage
        ).thenComparingInt(region -> region.index)
      );
      return ordered;
    }

    ordered.sort(
      Comparator.comparingInt((Region region) -> region.page)
        .thenComparingInt(region -> region.order)
        .thenComparingInt(region -> region.index)
    );

    return ordered;
  }

  public static String assemble(List<Region> regions) {
    List<Region> ordered = order(regions);
    StringBuilder out = new StringBuilder();

    for (Region region : ordered) {
      if (region == null || region.text == null || region.text.isBlank()) {
        continue;
      }

      String cleaned = region.text.trim();
      if (!out.isEmpty()) {
        out.append("\n\n");
      }

      out.append(cleaned);
    }

    return collapse(out.toString()).trim();
  }

  public static List<Region> fromOcr(int page, List<OcrResult> results) {
    if (results == null || results.isEmpty()) {
      return List.of();
    }

    List<Region> regions = new ArrayList<>();

    for (int i = 0; i < results.size(); i++) {
      OcrResult result = results.get(i);
      if (result == null) {
        continue;
      }

      regions.add(
        new Region(
          page,
          i,
          i,
          result.text,
          result.reliablePage() ? result.printedPage : -1,
          result.confidence,
          result.failure,
          Source.OCR
        )
      );
    }

    return regions;
  }

  public static List<Region> fromNative(List<HybridTextExtraction.Page> pages) {
    if (pages == null || pages.isEmpty()) {
      return List.of();
    }

    List<Region> regions = new ArrayList<>();

    for (HybridTextExtraction.Page page : pages) {
      if (page == null) {
        continue;
      }

      regions.add(
        new Region(
          page.pageNumber,
          0,
          0,
          page.text,
          -1,
          quality(page),
          OcrCandidateScorer.Failure.UNKNOWN,
          Source.NATIVE
        )
      );
    }

    return regions;
  }

  private static boolean allPrinted(List<Region> regions) {
    if (regions == null || regions.size() < 2) {
      return false;
    }

    for (Region region : regions) {
      if (region == null || !region.reliablePage()) {
        return false;
      }
    }

    return true;
  }

  private static double quality(HybridTextExtraction.Page page) {
    return page == null || page.quality == null ? 0.0 : page.quality.score;
  }

  private static String collapse(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    return text.replaceAll("\n{3,}", "\n\n");
  }

  public enum Source {
    NATIVE,
    OCR,
    MIXED,
    UNKNOWN,
  }

  public static class Region {

    public final int page;
    public final int index;
    public final int order;
    public final String text;
    public final int printedPage;
    public final double confidence;
    public final OcrCandidateScorer.Failure failure;
    public final Source source;

    public Region(
      int page,
      int index,
      int order,
      String text,
      int printedPage,
      double confidence,
      OcrCandidateScorer.Failure failure,
      Source source
    ) {
      this.page = page;
      this.index = Math.max(0, index);
      this.order = Math.max(0, order);
      this.text = text == null ? "" : text;
      this.printedPage = printedPage;
      this.confidence = confidence;
      this.failure =
        failure == null ? OcrCandidateScorer.Failure.UNKNOWN : failure;
      this.source = source == null ? Source.UNKNOWN : source;
    }

    public boolean reliablePage() {
      return printedPage >= 0 && printedPage <= 2000;
    }

    public boolean usableText() {
      return text != null && !text.isBlank();
    }

    public String summary() {
      return String.format(
        "Region{page=%d, index=%d, order=%d, printedPage=%s, confidence=%.2f, failure=%s, source=%s}",
        page,
        index,
        order,
        reliablePage() ? String.valueOf(printedPage) : "unknown",
        confidence,
        failure,
        source
      );
    }
  }
}
