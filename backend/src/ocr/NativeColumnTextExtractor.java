package ocr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

class NativeColumnTextExtractor {

  private static final double GUTTER_SCAN_START = 0.3;
  private static final double GUTTER_SCAN_END = 0.7;
  private static final double GUTTER_HALF_WIDTH_RATIO = 0.012;
  private static final double GLYPH_JUMP_RATIO = 0.03;
  private static final int MIN_BAND_LINES = 3;
  private static final float BAND_LINE_GAP = 40f;
  private static final float MIN_CORRIDOR_WIDTH = 8f;

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
        Extracted columns = columnBands(document, pageNumber);

        if (
          columns != null &&
          structure(columns.quality) > structure(best.quality)
        ) {
          System.out.println(
            "Native column bands adopted on page " + pageNumber
          );
          best = columns;
        }

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

  private static final java.util.regex.Pattern CAST_PAGE =
    java.util.regex.Pattern.compile(
      "(?im)^\\s*(characters|cast of characters|cast|dramatis personae)\\b.{0,20}$"
    );

  private static Extracted columnBands(PDDocument document, int pageNumber) {
    try {
      float pageWidth = document
        .getPage(pageNumber - 1)
        .getMediaBox()
        .getWidth();

      List<Span> spans = collectSpans(document, pageNumber, pageWidth);
      List<Line> lines = groupLines(spans);
      Float gutterX = findGutter(spans, lines);
      if (gutterX == null) {
        return null;
      }

      List<Band> bands = findBands(lines, gutterX, spans);
      if (bands.isEmpty()) {
        return null;
      }

      String text = rebuild(lines, bands);
      if (CAST_PAGE.matcher(text).find()) {
        return null;
      }
      String cleaned = TextExtractionQualityScorer.cleanText(text);
      return new Extracted(cleaned, TextExtractionQualityScorer.score(cleaned));
    } catch (Exception e) {
      return null;
    }
  }

  private static String rebuild(List<Line> lines, List<Band> bands) {
    StringBuilder out = new StringBuilder();
    int bandIndex = 0;

    for (int i = 0; i < lines.size(); i++) {
      Band band = bandIndex < bands.size() ? bands.get(bandIndex) : null;

      if (band != null && i == band.firstLine) {
        for (int j = band.firstLine; j <= band.lastLine; j++) {
          appendSpans(out, lines.get(j), true, band.cutX);
        }
        for (int j = band.firstLine; j <= band.lastLine; j++) {
          appendSpans(out, lines.get(j), false, band.cutX);
        }
        i = band.lastLine;
        bandIndex++;
        continue;
      }

      appendLine(out, lines.get(i));
    }

    return out.toString();
  }

  private static void appendSpans(
    StringBuilder out,
    Line line,
    boolean leftSide,
    float cutX
  ) {
    StringBuilder lineText = new StringBuilder();
    for (Span span : line.spans) {
      boolean isLeft = span.x1 <= cutX;
      if (isLeft != leftSide) {
        continue;
      }
      if (!lineText.isEmpty()) {
        lineText.append(' ');
      }
      lineText.append(span.text);
    }
    if (!lineText.isEmpty()) {
      out.append(lineText).append('\n');
    }
  }

  private static void appendLine(StringBuilder out, Line line) {
    StringBuilder lineText = new StringBuilder();
    for (Span span : line.spans) {
      if (!lineText.isEmpty()) {
        lineText.append(' ');
      }
      lineText.append(span.text);
    }
    if (!lineText.isEmpty()) {
      out.append(lineText).append('\n');
    }
  }

  private static List<Span> collectSpans(
    PDDocument document,
    int pageNumber,
    float pageWidth
  ) throws IOException {
    List<Span> spans = new ArrayList<>();
    float jump = pageWidth * (float) GLYPH_JUMP_RATIO;

    PDFTextStripper collector = new PDFTextStripper() {
      @Override
      protected void writeString(
        String text,
        List<TextPosition> positions
      ) {
        if (positions == null || positions.isEmpty()) {
          return;
        }
        int start = 0;
        for (int i = 1; i <= positions.size(); i++) {
          boolean breakHere =
            i == positions.size() ||
            positions.get(i).getXDirAdj() -
              (positions.get(i - 1).getXDirAdj() +
                positions.get(i - 1).getWidthDirAdj()) >
              jump;
          if (breakHere) {
            spans.add(makeSpan(positions, start, i));
            start = i;
          }
        }
      }
    };
    collector.setSortByPosition(true);
    collector.setStartPage(pageNumber);
    collector.setEndPage(pageNumber);
    collector.getText(document);

    return spans;
  }

  private static Span makeSpan(
    List<TextPosition> positions,
    int from,
    int to
  ) {
    TextPosition first = positions.get(from);
    TextPosition last = positions.get(to - 1);

    StringBuilder text = new StringBuilder();
    for (int i = from; i < to; i++) {
      TextPosition glyph = positions.get(i);
      if (i > from) {
        TextPosition prev = positions.get(i - 1);
        float gap =
          glyph.getXDirAdj() - (prev.getXDirAdj() + prev.getWidthDirAdj());
        float spaceWidth = Math.max(glyph.getWidthOfSpace(), 1f);
        boolean alreadySpaced =
          endsWithSpace(text) || glyph.getUnicode().startsWith(" ");
        if (gap > spaceWidth * 0.5f && !alreadySpaced) {
          text.append(' ');
        }
      }
      text.append(glyph.getUnicode());
    }

    return new Span(
      first.getXDirAdj(),
      last.getXDirAdj() + last.getWidthDirAdj(),
      first.getYDirAdj(),
      text.toString().trim()
    );
  }

  private static boolean endsWithSpace(StringBuilder text) {
    return text.length() > 0 && text.charAt(text.length() - 1) == ' ';
  }

  private static List<Line> groupLines(List<Span> spans) {
    List<Span> sorted = new ArrayList<>(spans);
    sorted.sort((a, b) -> Float.compare(a.y, b.y));

    List<Line> lines = new ArrayList<>();
    List<Span> current = new ArrayList<>();
    float lastY = Float.NaN;

    for (Span span : sorted) {
      if (!current.isEmpty() && span.y - lastY > 3f) {
        current.sort((a, b) -> Float.compare(a.x0, b.x0));
        lines.add(new Line(current));
        current = new ArrayList<>();
      }
      current.add(span);
      lastY = span.y;
    }
    if (!current.isEmpty()) {
      current.sort((a, b) -> Float.compare(a.x0, b.x0));
      lines.add(new Line(current));
    }

    return lines;
  }

  private static Float findGutter(List<Span> spans, List<Line> lines) {
    if (spans == null || spans.size() < MIN_BAND_LINES) {
      return null;
    }

    float minX = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    for (Span span : spans) {
      minX = Math.min(minX, span.x0);
      maxX = Math.max(maxX, span.x1);
    }
    float textWidth = maxX - minX;
    if (textWidth <= 0 || lines.size() < MIN_BAND_LINES) {
      return null;
    }

    float gap = textWidth * (float) GUTTER_HALF_WIDTH_RATIO;
    Float bestX = null;
    int bestSplit = 0;

    for (double f = GUTTER_SCAN_START; f <= GUTTER_SCAN_END; f += 0.01) {
      float x = minX + textWidth * (float) f;
      int split = 0;
      for (Line line : lines) {
        if (line.kind(x, gap) == Kind.SPLIT) {
          split++;
        }
      }
      if (split >= MIN_BAND_LINES && split > bestSplit) {
        bestSplit = split;
        bestX = x;
      }
    }

    return bestX;
  }

  private static List<Band> findBands(
    List<Line> lines,
    float gutterX,
    List<Span> spans
  ) {
    float minX = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    for (Span span : spans) {
      minX = Math.min(minX, span.x0);
      maxX = Math.max(maxX, span.x1);
    }
    float gap = (maxX - minX) * (float) GUTTER_HALF_WIDTH_RATIO;

    List<Band> bands = new ArrayList<>();
    int runStart = -1;
    int splits = 0;

    for (int i = 0; i <= lines.size(); i++) {
      Kind kind = i == lines.size()
        ? Kind.CROSSING
        : lines.get(i).kind(gutterX, gap);

      boolean gapBreak =
        runStart >= 0 &&
        i > runStart &&
        i < lines.size() &&
        lines.get(i).y - lines.get(i - 1).y > BAND_LINE_GAP;

      if (kind == Kind.CROSSING || gapBreak) {
        if (runStart >= 0 && splits >= MIN_BAND_LINES) {
          Band band = buildBand(lines, runStart, i - 1, gutterX);
          if (band != null) {
            bands.add(band);
          }
        }
        runStart = gapBreak && kind != Kind.CROSSING ? i : -1;
        splits = gapBreak && kind == Kind.SPLIT ? 1 : 0;
        continue;
      }

      if (runStart < 0) {
        runStart = i;
        splits = 0;
      }
      if (kind == Kind.SPLIT) {
        splits++;
      }
    }

    return bands;
  }

  private static Band buildBand(
    List<Line> lines,
    int first,
    int last,
    float gutterX
  ) {
    float leftMax = -Float.MAX_VALUE;
    float rightMin = Float.MAX_VALUE;

    for (int i = first; i <= last; i++) {
      for (Span span : lines.get(i).spans) {
        if (span.x1 < gutterX) {
          leftMax = Math.max(leftMax, span.x1);
        }
        if (span.x0 > gutterX) {
          rightMin = Math.min(rightMin, span.x0);
        }
      }
    }

    if (
      leftMax <= 0 ||
      rightMin == Float.MAX_VALUE ||
      rightMin - leftMax < MIN_CORRIDOR_WIDTH
    ) {
      return null;
    }

    return new Band(first, last, (leftMax + rightMin) / 2f);
  }

  private enum Kind {
    LEFT_ONLY,
    RIGHT_ONLY,
    SPLIT,
    CROSSING,
    EMPTY,
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

  private static class Span {

    final float x0;
    final float x1;
    final float y;
    final String text;

    Span(float x0, float x1, float y, String text) {
      this.x0 = x0;
      this.x1 = x1;
      this.y = y;
      this.text = text == null ? "" : text;
    }
  }

  private static class Line {

    final List<Span> spans;
    final float y;

    Line(List<Span> spans) {
      this.spans = spans;
      float sum = 0;
      for (Span span : spans) {
        sum += span.y;
      }
      this.y = spans.isEmpty() ? 0 : sum / spans.size();
    }

    Kind kind(float x, float gap) {
      boolean left = false;
      boolean right = false;
      boolean crossed = false;
      for (Span span : spans) {
        if (span.x1 < x - gap) {
          left = true;
        } else if (span.x0 > x + gap) {
          right = true;
        } else {
          crossed = true;
        }
      }
      if (crossed) {
        return Kind.CROSSING;
      }
      if (left && right) {
        return Kind.SPLIT;
      }
      if (left) {
        return Kind.LEFT_ONLY;
      }
      if (right) {
        return Kind.RIGHT_ONLY;
      }
      return Kind.EMPTY;
    }
  }

  private static class Band {

    final int firstLine;
    final int lastLine;
    final float cutX;

    Band(int firstLine, int lastLine, float cutX) {
      this.firstLine = firstLine;
      this.lastLine = lastLine;
      this.cutX = cutX;
    }
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
