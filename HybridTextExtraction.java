import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class HybridTextExtraction {

  @FunctionalInterface
  public interface Ocr {
    String extract(File pdf) throws Exception;
  }

  public enum Source {
    NATIVE_TEXT,
    OCR_TEXT,
    EMPTY,
  }

  public static Result extract(File pdf, Ocr ocr) throws Exception {
    Native nativeResult = nativeText(pdf);
    if (TextExtractionQualityScorer.shouldTrustNativeText(nativeResult.text)) {
      return new Result(
        nativeResult.text,
        Source.NATIVE_TEXT,
        nativeResult.quality,
        null
      );
    }

    if (ocr == null) {
      return new Result(
        nativeResult.text,
        sourceFor(nativeResult.text),
        nativeResult.quality,
        null
      );
    }

    String ocrText = ocr.extract(pdf);
    TextExtractionQualityScorer.TextQuality ocrQuality =
      TextExtractionQualityScorer.score(ocrText);

    if (ocrText != null && !ocrText.isBlank()) {
      return new Result(
        ocrText,
        Source.OCR_TEXT,
        ocrQuality,
        nativeResult.quality
      );
    }

    return new Result(
      nativeResult.text,
      sourceFor(nativeResult.text),
      nativeResult.quality,
      ocrQuality
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
    if (pdf == null || !pdf.exists()) {
      return Collections.emptyList();
    }

    List<Page> pages = new ArrayList<>();

    try (PDDocument document = Loader.loadPDF(pdf)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);

      int pageCount = document.getNumberOfPages();

      for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);

        String text = TextExtractionQualityScorer.cleanText(
          stripper.getText(document)
        );
        pages.add(
          new Page(pageNumber, text, TextExtractionQualityScorer.score(text))
        );
      }
    }

    return pages;
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
