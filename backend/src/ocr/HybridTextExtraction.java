package ocr;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import parser.detect.PageFurnitureDetector;

public class HybridTextExtraction {

  @FunctionalInterface
  public interface Ocr {
    Map<Integer, String> ocrPages(File pdf, Set<Integer> pageNumbers)
      throws Exception;
  }

  public enum Source {
    NATIVE_TEXT,
    OCR_TEXT,
    EMPTY,
  }

  private static final double MIN_COMMON_WORD_RATIO = 0.08;
  private static final int MIN_TOKENS_TO_JUDGE = 30;

  private static final Set<String> COMMON_WORDS = Set.of(
    "the",
    "and",
    "a",
    "to",
    "of",
    "in",
    "is",
    "it",
    "you",
    "i",
    "that",
    "was",
    "for",
    "on",
    "with",
    "he",
    "she",
    "as",
    "his",
    "her",
    "at",
    "be",
    "this",
    "have",
    "from",
    "or",
    "by",
    "not",
    "but",
    "what",
    "all",
    "are",
    "we",
    "when",
    "your",
    "can",
    "there",
    "do",
    "my",
    "me",
    "no",
    "so",
    "him",
    "them",
    "then",
    "out",
    "up",
    "now",
    "get",
    "like",
    "just",
    "know",
    "here",
    "come",
    "go",
    "has",
    "had",
    "will",
    "would",
    "been",
    "about",
    "into",
    "time",
    "if",
    "they",
    "an",
    "who",
    "she's",
    "i'm"
  );

  public static java.util.Set<String> stageHintKeys() {
    return NativeColumnTextExtractor.stageHintKeys();
  }

  public static Result extract(File pdf, Ocr ocr) throws Exception {
    List<Page> pages = nativePages(pdf);

    PageFurnitureDetector.DetectionModel furniture =
      PageFurnitureDetector.learn(texts(pages));
    String nativeJoined = join(cleanPages(pages, furniture));
    TextExtractionQualityScorer.TextQuality nativeQuality =
      TextExtractionQualityScorer.score(nativeJoined);

    Set<Integer> garbled = new TreeSet<>();
    for (Page page : pages) {
      if (page != null && looksLikeGarbledNative(page.text)) {
        garbled.add(page.pageNumber);
      }
    }

    boolean nativeTrusted = TextExtractionQualityScorer.shouldTrustNativeText(
      nativeJoined
    );

    if (garbled.isEmpty() && nativeTrusted) {
      return new Result(nativeJoined, Source.NATIVE_TEXT, nativeQuality, null);
    }

    if (ocr == null) {
      return new Result(
        nativeJoined,
        sourceFor(nativeJoined),
        nativeQuality,
        null
      );
    }

    boolean mostlyBad = !pages.isEmpty() && garbled.size() * 2 >= pages.size();

    if (nativeTrusted && !mostlyBad && !garbled.isEmpty()) {
      Map<Integer, String> ocrText = ocr.ocrPages(pdf, garbled);
      String mixed = assemble(pages, ocrText, garbled);
      System.out.println(
        "Hybrid extraction: OCR'd " +
          garbled.size() +
          " garbled page(s) of " +
          pages.size() +
          ", kept native text for the rest; pages=" +
          garbled
      );
      if (mixed != null && !mixed.isBlank()) {
        return new Result(
          mixed,
          Source.OCR_TEXT,
          TextExtractionQualityScorer.score(mixed),
          nativeQuality
        );
      }
    }

    Set<Integer> allPages = new TreeSet<>();
    for (Page page : pages) {
      allPages.add(page.pageNumber);
    }
    Map<Integer, String> ocrText = ocr.ocrPages(pdf, allPages);
    String ocrJoined = assemble(pages, ocrText, allPages);

    if (ocrJoined != null && !ocrJoined.isBlank()) {
      return new Result(
        ocrJoined,
        Source.OCR_TEXT,
        TextExtractionQualityScorer.score(ocrJoined),
        nativeQuality
      );
    }

    return new Result(
      nativeJoined,
      sourceFor(nativeJoined),
      nativeQuality,
      TextExtractionQualityScorer.score(ocrJoined)
    );
  }

  private static String assemble(
    List<Page> nativePages,
    Map<Integer, String> ocrText,
    Set<Integer> ocrPages
  ) {
    if (nativePages == null || nativePages.isEmpty()) {
      return "";
    }

    List<Page> rebuilt = new ArrayList<>();
    for (Page page : nativePages) {
      if (page == null) {
        continue;
      }
      String text = ocrPages.contains(page.pageNumber)
        ? ocrText.getOrDefault(page.pageNumber, "")
        : page.text;
      if (text == null) {
        text = "";
      }
      rebuilt.add(
        new Page(page.pageNumber, text, TextExtractionQualityScorer.score(text))
      );
    }

    PageFurnitureDetector.DetectionModel furniture =
      PageFurnitureDetector.learn(texts(rebuilt));
    return join(cleanPages(rebuilt, furniture));
  }

  private static boolean looksLikeGarbledNative(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }

    String lower = text.toLowerCase(Locale.ROOT);
    int total = 0;
    int common = 0;
    for (String token : lower.split(util.RegexTerms.NON_LOWER_APOS_RUN)) {
      if (token.isEmpty()) {
        continue;
      }
      total++;
      if (COMMON_WORDS.contains(token)) {
        common++;
      }
    }

    return (
      total >= MIN_TOKENS_TO_JUDGE &&
      common / (double) total < MIN_COMMON_WORD_RATIO
    );
  }

  private static Source sourceFor(String text) {
    return text == null || text.isBlank() ? Source.EMPTY : Source.NATIVE_TEXT;
  }

  public static Native nativeText(File pdf) throws Exception {
    if (pdf == null || !pdf.exists()) {
      return new Native(
        "",
        TextExtractionQualityScorer.TextQuality.empty(),
        Collections.emptyList()
      );
    }

    List<Page> pages = nativePages(pdf);
    PageFurnitureDetector.DetectionModel furnitureModel =
      PageFurnitureDetector.learn(texts(pages));

    List<Page> cleaned = cleanPages(pages, furnitureModel);
    String text = join(cleaned);

    System.out.println(furnitureModel.summary());

    return new Native(text, TextExtractionQualityScorer.score(text), cleaned);
  }

  public static List<Page> nativePages(File pdf) throws Exception {
    return NativeColumnTextExtractor.pages(pdf);
  }

  private static List<String> texts(List<Page> pages) {
    if (pages == null || pages.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> texts = new ArrayList<>();
    for (Page page : pages) {
      texts.add(page == null ? "" : page.text);
    }
    return texts;
  }

  private static List<Page> cleanPages(
    List<Page> pages,
    PageFurnitureDetector.DetectionModel furnitureModel
  ) {
    if (pages == null || pages.isEmpty()) {
      return Collections.emptyList();
    }

    List<Page> cleaned = new ArrayList<>();

    for (Page page : pages) {
      if (page == null) {
        continue;
      }

      String text = PageFurnitureDetector.remove(page.text, furnitureModel);
      text = TextExtractionQualityScorer.cleanText(text);

      cleaned.add(
        new Page(page.pageNumber, text, TextExtractionQualityScorer.score(text))
      );
    }

    return cleaned;
  }

  private static String join(List<Page> pages) {
    if (pages == null || pages.isEmpty()) {
      return "";
    }

    StringBuilder fullText = new StringBuilder();

    for (Page page : pages) {
      if (page == null || page.text == null || page.text.isBlank()) {
        continue;
      }

      if (!fullText.isEmpty()) {
        fullText.append("\n\n");
      }

      fullText.append(page.text.trim());
    }

    return fullText.toString().trim();
  }

  public static boolean useNative(File pdf) throws Exception {
    return TextExtractionQualityScorer.shouldTrustNativeText(
      nativeText(pdf).text
    );
  }

  public static class Result {

    public final String text;
    public final Source source;
    public final TextExtractionQualityScorer.TextQuality selectedQuality;
    public final TextExtractionQualityScorer.TextQuality rejectedQuality;

    Result(
      String text,
      Source source,
      TextExtractionQualityScorer.TextQuality selectedQuality,
      TextExtractionQualityScorer.TextQuality rejectedQuality
    ) {
      this.text = text == null ? "" : text;
      this.source = source == null ? Source.EMPTY : source;
      this.selectedQuality =
        selectedQuality == null
          ? TextExtractionQualityScorer.TextQuality.empty()
          : selectedQuality;
      this.rejectedQuality = rejectedQuality;
    }

    public boolean nativeUsed() {
      return source == Source.NATIVE_TEXT;
    }

    public boolean ocrUsed() {
      return source == Source.OCR_TEXT;
    }

    public String summary() {
      return (
        "HybridTextExtraction{" +
        "source=" +
        source +
        ", selectedQuality=" +
        selectedQuality.summary() +
        (rejectedQuality == null
          ? ""
          : ", rejectedQuality=" + rejectedQuality.summary()) +
        '}'
      );
    }
  }

  public static class Native {

    public final String text;
    public final TextExtractionQualityScorer.TextQuality quality;
    public final List<Page> pages;

    Native(
      String text,
      TextExtractionQualityScorer.TextQuality quality,
      List<Page> pages
    ) {
      this.text = text == null ? "" : text;
      this.quality =
        quality == null
          ? TextExtractionQualityScorer.TextQuality.empty()
          : quality;
      this.pages = pages == null ? Collections.emptyList() : pages;
    }
  }

  public static class Page {

    public final int pageNumber;
    public final String text;
    public final TextExtractionQualityScorer.TextQuality quality;

    Page(
      int pageNumber,
      String text,
      TextExtractionQualityScorer.TextQuality quality
    ) {
      this.pageNumber = pageNumber;
      this.text = text == null ? "" : text;
      this.quality =
        quality == null
          ? TextExtractionQualityScorer.TextQuality.empty()
          : quality;
    }
  }
}
