package ocr;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

class NativeColumnTextExtractor {

  static List<HybridTextExtraction.Page> pages(File pdf) throws Exception {
    if (pdf == null || !pdf.exists()) {
      return List.of();
    }

    List<HybridTextExtraction.Page> pages = new ArrayList<>();

    try (PDDocument document = Loader.loadPDF(pdf)) {
      int pageCount = document.getNumberOfPages();

      for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
        Extracted sorted = extract(document, pageNumber, true);
        Extracted stream = extract(document, pageNumber, false);
        Extracted best = best(sorted, stream);

        pages.add(
          new HybridTextExtraction.Page(
            pageNumber,
            best.text,
            best.quality
          )
        );
      }
    }

    return pages;
  }

  private static Extracted extract(
    PDDocument document,
    int pageNumber,
    boolean sortByPosition
  ) throws Exception {
    PDFTextStripper stripper = new PDFTextStripper();
    stripper.setSortByPosition(sortByPosition);
    stripper.setStartPage(pageNumber);
    stripper.setEndPage(pageNumber);

    String text = TextExtractionQualityScorer.cleanText(
      stripper.getText(document)
    );

    return new Extracted(text, TextExtractionQualityScorer.score(text));
  }

  private static Extracted best(Extracted sorted, Extracted stream) {
    if (stream.text.isBlank()) {
      return sorted;
    }
    if (sorted.text.isBlank()) {
      return stream;
    }

    if (stream.quality.score > sorted.quality.score + 0.01) {
      return stream;
    }
    if (sorted.quality.score > stream.quality.score + 0.01) {
      return sorted;
    }

    return structure(stream.quality) >= structure(sorted.quality)
      ? stream
      : sorted;
  }

  private static int structure(TextExtractionQualityScorer.TextQuality quality) {
    if (quality == null) {
      return 0;
    }

    return (
      quality.speakerLikeLines * 3 +
      quality.stageLikeLines * 2 +
      quality.dialogueLikeLines
    );
  }

  private static class Extracted {

    final String text;
    final TextExtractionQualityScorer.TextQuality quality;

    Extracted(String text, TextExtractionQualityScorer.TextQuality quality) {
      this.text = text == null ? "" : text;
      this.quality =
        quality == null
          ? TextExtractionQualityScorer.TextQuality.empty()
          : quality;
    }
  }
}
