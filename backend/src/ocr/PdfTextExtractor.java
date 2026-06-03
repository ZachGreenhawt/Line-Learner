package ocr;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import ocr.model.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import parser.detect.PageFurnitureDetector;
import util.RegexTerms;

public class PdfTextExtractor {

  private static final int OCR_DPI = 300;

  public static String extract(File pdf) throws Exception {
    DocumentLearningCache cache = new DocumentLearningCache();

    HybridTextExtraction.Result result = HybridTextExtraction.extract(
      pdf,
      file -> ocr(file, cache)
    );

    cache.remember(pdf, result.source, result.selectedQuality.score);

    System.out.println(result.summary());

    return result.text;
  }

  private static String ocr(File pdf, DocumentLearningCache cache)
    throws Exception {
    List<String> pages = new ArrayList<>();
    OcrDiagnosticsExporter.reset();
    ImagePreprocessor.reset();

    DocumentLearningCache store =
      cache == null ? new DocumentLearningCache() : cache;
    OcrRunProfile profile = new OcrRunProfile(store.preferredTrial(pdf));

    try (PDDocument document = Loader.loadPDF(pdf)) {
      PDFRenderer renderer = new PDFRenderer(document);
      TesseractCli tesseract = makeTesseract();

      for (
        int pageIndex = 0;
        pageIndex < document.getNumberOfPages();
        pageIndex++
      ) {
        int pageNumber = pageIndex + 1;
        System.out.println("Processing page " + pageNumber);

        BufferedImage pageImage = renderer.renderImageWithDPI(
          pageIndex,
          OCR_DPI,
          ImageType.RGB
        );

        List<BufferedImage> regions = PageRegionExtractor.regions(pageImage);

        List<OcrResult> results = new ArrayList<>();

        for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
          OcrResult result = OrientationResolver.result(
            regions.get(regionIndex),
            tesseract,
            pageNumber,
            regionIndex + 1,
            profile
          );

          rememberConfidentOcrTrial(pdf, store, result);

          results.add(result);
        }

        List<ReadingOrderResolver.Region> ordered =
          ReadingOrderResolver.fromOcr(pageNumber, results);

        String pageText = cleanText(ReadingOrderResolver.assemble(ordered));
        if (!pageText.isBlank()) {
          pages.add(pageText);
        }
      }
    }

    OcrDiagnosticsExporter.printLocation();
    ImagePreprocessor.printSummary();
    System.out.println(profile.summary());

    return cleanPages(pages);
  }

  private static String cleanPages(List<String> pages) {
    if (pages == null || pages.isEmpty()) {
      return "";
    }

    PageFurnitureDetector.DetectionModel model = PageFurnitureDetector.learn(
      pages
    );

    List<String> cleaned = new ArrayList<>();

    for (String page : pages) {
      String text = cleanText(PageFurnitureDetector.remove(page, model));
      if (!text.isBlank()) {
        cleaned.add(text);
      }
    }

    return join(cleaned);
  }

  private static String join(List<String> pages) {
    if (pages == null || pages.isEmpty()) {
      return "";
    }

    StringBuilder out = new StringBuilder();

    for (String page : pages) {
      if (page == null || page.isBlank()) {
        continue;
      }

      if (!out.isEmpty()) {
        out.append("\n\n");
      }

      out.append(page.trim());
    }

    return collapse(out.toString()).trim();
  }

  private static void rememberConfidentOcrTrial(
    File pdf,
    DocumentLearningCache cache,
    OcrResult result
  ) {
    if (pdf == null || cache == null || result == null) {
      return;
    }

    if (result.best == null || !result.isConfident()) {
      return;
    }

    cache.rememberTrial(
      pdf,
      result.rotation,
      result.index,
      result.name,
      result.confidence,
      result.score
    );
  }

  private static String cleanText(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }

    String cleaned = text
      .replace('\uFFFD', ' ')
      .replace('￾', ' ')
      .replace('￿', ' ');

    StringBuilder out = new StringBuilder();
    for (String line : cleaned.split(RegexTerms.LINE_BREAK)) {
      String lineText = line == null ? "" : line.trim();
      if (lineText.isEmpty()) {
        blank(out);
        continue;
      }

      out.append(lineText).append('\n');
    }

    return collapse(out.toString()).trim();
  }

  private static void blank(StringBuilder out) {
    int length = out.length();
    if (length == 0) {
      return;
    }

    if (
      length >= 2 &&
      out.charAt(length - 1) == '\n' &&
      out.charAt(length - 2) == '\n'
    ) {
      return;
    }

    out.append('\n');
  }

  private static String collapse(String text) {
    return text.replaceAll(RegexTerms.NEWLINE_RUN, "\\n\\n");
  }

  private static TesseractCli makeTesseract() {
    return new TesseractCli(tessdataPath(), "eng", 1, 3);
  }

  private static String tessdataPath() {
    File tessdata = new File("backend/lib/tessdata");

    if (!tessdata.exists()) {
      tessdata = new File("../backend/lib/tessdata");
    }

    System.out.println("Tessdata path: " + tessdata.getAbsolutePath());
    System.out.println("Tessdata exists: " + tessdata.exists());

    return tessdata.getAbsolutePath();
  }
}
