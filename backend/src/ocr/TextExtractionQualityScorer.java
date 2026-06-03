package ocr;

import java.util.Locale;
import util.RegexTerms;

public class TextExtractionQualityScorer {

  private static final double MIN_USABLE_NATIVE_SCORE = 0.62;
  private static final int MIN_USABLE_NATIVE_CHARS = 500;
  private static final int MIN_USABLE_NATIVE_LINES = 12;

  public static TextQuality score(String text) {
    if (text == null || text.isBlank()) {
      return TextQuality.empty();
    }

    String normalized = cleanText(text);
    String[] lines = normalized.split(RegexTerms.LINE_BREAK);

    int nonBlankLines = 0;
    int speakerLikeLines = 0;
    int dialogueLikeLines = 0;
    int stageLikeLines = 0;
    int garbageLines = 0;
    int pageFurnitureLines = 0;
    int letters = 0;
    int digits = 0;
    int whitespace = 0;
    int weird = 0;
    int replacement = 0;

    for (int i = 0; i < normalized.length(); i++) {
      char ch = normalized.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
      } else if (Character.isDigit(ch)) {
        digits++;
      } else if (Character.isWhitespace(ch)) {
        whitespace++;
      } else if (ch == '\uFFFD' || ch == '￾' || ch == '￿') {
        replacement++;
      } else if (!isCommonTextPunctuation(ch)) {
        weird++;
      }
    }

    for (String rawLine : lines) {
      String line = rawLine == null ? "" : rawLine.trim();
      if (line.isEmpty()) {
        continue;
      }

      nonBlankLines++;

      if (looksLikeSpeakerHeading(line)) {
        speakerLikeLines++;
      }

      if (looksLikeDialogue(line)) {
        dialogueLikeLines++;
      }

      if (looksLikeStageDirection(line)) {
        stageLikeLines++;
      }

      if (looksLikePageFurniture(line)) {
        pageFurnitureLines++;
      }

      if (looksLikeGarbage(line)) {
        garbageLines++;
      }
    }

    int length = normalized.length();
    double letterRatio = length == 0 ? 0.0 : letters / (double) length;
    double digitRatio = length == 0 ? 0.0 : digits / (double) length;
    double whitespaceRatio = length == 0 ? 0.0 : whitespace / (double) length;
    double weirdRatio = length == 0 ? 0.0 : weird / (double) length;
    double replacementRatio = length == 0 ? 0.0 : replacement / (double) length;
    double garbageLineRatio =
      nonBlankLines == 0 ? 1.0 : garbageLines / (double) nonBlankLines;
    double furnitureRatio =
      nonBlankLines == 0 ? 0.0 : pageFurnitureLines / (double) nonBlankLines;
    double averageLineLength =
      nonBlankLines == 0 ? 0.0 : length / (double) nonBlankLines;
    double structureSignal = Math.min(
      1.0,
      (speakerLikeLines + stageLikeLines + dialogueLikeLines) / 20.0
    );

    double score = 0.0;
    score += Math.min(0.28, letterRatio * 0.42);
    score += Math.min(0.18, nonBlankLines / 140.0);
    score += Math.min(0.18, length / 12000.0);
    score += structureSignal * 0.20;

    if (averageLineLength >= 12 && averageLineLength <= 95) {
      score += 0.10;
    }

    if (speakerLikeLines >= 3) {
      score += 0.08;
    }

    if (stageLikeLines >= 3) {
      score += 0.04;
    }

    score -= Math.min(0.30, weirdRatio * 4.0);
    score -= Math.min(0.30, replacementRatio * 8.0);
    score -= Math.min(0.25, garbageLineRatio * 0.40);
    score -= Math.min(0.15, furnitureRatio * 0.20);

    score = clamp01(score);

    boolean usable =
      score >= MIN_USABLE_NATIVE_SCORE &&
      length >= MIN_USABLE_NATIVE_CHARS &&
      nonBlankLines >= MIN_USABLE_NATIVE_LINES &&
      letterRatio >= 0.45 &&
      weirdRatio <= 0.035 &&
      replacementRatio <= 0.01 &&
      garbageLineRatio <= 0.22;

    return new TextQuality(
      score,
      usable,
      length,
      nonBlankLines,
      speakerLikeLines,
      dialogueLikeLines,
      stageLikeLines,
      garbageLines,
      pageFurnitureLines,
      letterRatio,
      digitRatio,
      whitespaceRatio,
      weirdRatio,
      replacementRatio,
      garbageLineRatio,
      furnitureRatio,
      averageLineLength
    );
  }

  public static boolean isUsable(String text) {
    return score(text).usable;
  }

  public static boolean shouldTrustNativeText(String text) {
    TextQuality quality = score(text);

    if (!quality.usable) {
      return false;
    }

    if (
      quality.score >= 0.85 &&
      quality.length >= 5000 &&
      quality.nonBlankLines >= 80 &&
      quality.garbageLineRatio <= 0.08 &&
      quality.weirdRatio <= 0.02 &&
      quality.replacementRatio <= 0.002
    ) {
      return true;
    }

    return !looksLikeBadHiddenOcrLayer(text);
  }

  public static boolean looksLikeBadHiddenOcrLayer(String text) {
    if (text == null || text.isBlank()) {
      return true;
    }

    String cleaned = cleanText(text);
    String[] lines = cleaned.split(RegexTerms.LINE_BREAK);

    int nonBlank = 0;
    int suspicious = 0;
    int veryShortNoise = 0;
    int pipeOrControlArtifacts = 0;
    int mashedWords = 0;
    int consonantChunks = 0;

    for (String rawLine : lines) {
      String line = rawLine == null ? "" : rawLine.trim();
      if (line.isEmpty()) {
        continue;
      }

      nonBlank++;

      if (
        line.matches(RegexTerms.SHORT_LINE_1_8) &&
        line.matches(RegexTerms.CONTAINS_PUNCT_DIGIT)
      ) {
        veryShortNoise++;
        suspicious++;
        continue;
      }

      if (line.contains("|") || line.contains("￾") || line.contains("￿")) {
        pipeOrControlArtifacts++;
        suspicious++;
        continue;
      }

      if (line.matches(RegexTerms.MIXED_CASE_GARBLE)) {
        mashedWords++;
        suspicious++;
        continue;
      }

      String lettersOnly = line.replaceAll(RegexTerms.NON_LETTER, "");
      if (
        lettersOnly.length() >= 10 &&
        lettersOnly.matches(
          RegexTerms.CONSONANT_RUN_5_MIXED
        )
      ) {
        consonantChunks++;
        suspicious++;
      }
    }

    if (nonBlank == 0) {
      return true;
    }

    double suspiciousRatio = suspicious / (double) nonBlank;
    double artifactRatio =
      (veryShortNoise +
        pipeOrControlArtifacts +
        mashedWords +
        consonantChunks) /
      (double) nonBlank;

    return (
      suspicious >= 10 || suspiciousRatio >= 0.008 || artifactRatio >= 0.006
    );
  }

  public static boolean nativeTextBeatsOcr(String nativeText, String ocrText) {
    TextQuality nativeQuality = score(nativeText);
    TextQuality ocrQuality = score(ocrText);

    if (nativeQuality.usable && !ocrQuality.usable) {
      return true;
    }

    if (!nativeQuality.usable && ocrQuality.usable) {
      return false;
    }

    return nativeQuality.score >= ocrQuality.score;
  }

  public static String cleanText(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }

    String cleaned = text
      .replace('\u00A0', ' ')
      .replace('\uFFFD', ' ')
      .replace('￾', ' ')
      .replace('￿', ' ');

    StringBuilder out = new StringBuilder();
    String[] lines = cleaned.split(RegexTerms.LINE_BREAK);

    for (String rawLine : lines) {
      String line = rawLine == null ? "" : rawLine.trim();
      if (line.isEmpty()) {
        appendBlankLine(out);
        continue;
      }

      out.append(line.replaceAll(RegexTerms.DOUBLE_SPACE, " ")).append('\n');
    }

    return collapseExcessBlankLines(out.toString()).trim();
  }

  private static boolean looksLikeSpeakerHeading(String line) {
    if (line == null) {
      return false;
    }

    String t = line.trim();
    if (t.length() < 2 || t.length() > 55) {
      return false;
    }

    if (looksLikePageFurniture(t)) {
      return false;
    }

    String lettersOnly = t.replaceAll(RegexTerms.NON_LETTER, "");
    if (lettersOnly.length() < 2) {
      return false;
    }

    return (
      t.matches(RegexTerms.CAPS_ALNUM_APOS_LINE) &&
      t.equals(t.toUpperCase(Locale.ROOT)) &&
      t.matches(RegexTerms.CONTAINS_UPPERCASE)
    );
  }

  private static boolean looksLikeDialogue(String line) {
    if (line == null) {
      return false;
    }

    String t = line.trim();
    return (
      t.length() > 20 &&
      t.matches(RegexTerms.CONTAINS_LOWERCASE) &&
      t.matches(RegexTerms.CONTAINS_VOWEL) &&
      !looksLikePageFurniture(t) &&
      !looksLikeGarbage(t)
    );
  }

  private static boolean looksLikeStageDirection(String line) {
    if (line == null) {
      return false;
    }

    String t = line.trim();
    String lower = t.toLowerCase(Locale.ROOT);

    return (
      (t.startsWith("(") && t.endsWith(")")) ||
      lower.startsWith("enter ") ||
      lower.startsWith("exit ") ||
      lower.startsWith("exeunt") ||
      lower.startsWith("at rise") ||
      lower.startsWith("lights ") ||
      lower.contains(" enters") ||
      lower.contains(" exits") ||
      lower.contains(" crosses") ||
      lower.contains(" sits") ||
      lower.contains(" stands") ||
      lower.contains(" turns") ||
      lower.contains(" pauses") ||
      lower.contains(" blackout") ||
      lower.contains("curtain")
    );
  }

  private static boolean looksLikePageFurniture(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }

    String upper = line.trim().toUpperCase(Locale.ROOT);

    return (
      upper.matches(RegexTerms.PAGE_NUMBER_ONLY) ||
      upper.matches(RegexTerms.PAGE_LABEL) ||
      upper.matches(
        RegexTerms.CAPS_WORDS_THEN_NUMBER
      ) ||
      upper.matches(
        RegexTerms.NUMBER_THEN_CAPS_WORDS
      ) ||
      upper.matches(RegexTerms.CONTAINS_ISBN_CI) ||
      upper.matches(RegexTerms.CONTAINS_WWW_CI) ||
      upper.matches(RegexTerms.CONTAINS_WEB_TLD_CI)
    );
  }

  private static boolean looksLikeGarbage(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }

    String t = line.trim();
    if (looksLikePageFurniture(t)) {
      return false;
    }

    String lettersOnly = t.replaceAll(RegexTerms.NON_LETTER, "");
    if (lettersOnly.length() >= 8 && countVowels(lettersOnly) == 0) {
      return true;
    }

    if (lettersOnly.length() >= 12) {
      double vowelRatio =
        countVowels(lettersOnly) / (double) lettersOnly.length();
      if (vowelRatio < 0.18 || vowelRatio > 0.70) {
        return true;
      }
    }

    int weird = 0;
    for (int i = 0; i < t.length(); i++) {
      char ch = t.charAt(i);
      if (
        !Character.isLetterOrDigit(ch) &&
        !Character.isWhitespace(ch) &&
        !isCommonTextPunctuation(ch)
      ) {
        weird++;
      }
    }

    return weird > Math.max(4, t.length() / 7);
  }

  private static int countVowels(String text) {
    int count = 0;
    for (int i = 0; i < text.length(); i++) {
      char ch = Character.toLowerCase(text.charAt(i));
      if (ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u') {
        count++;
      }
    }
    return count;
  }

  private static boolean isCommonTextPunctuation(char ch) {
    return ".,;:!?()[]{}'\"-/–—&…“. ”’‘".indexOf(ch) >= 0;
  }

  private static void appendBlankLine(StringBuilder out) {
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

  private static String collapseExcessBlankLines(String text) {
    return text.replaceAll(RegexTerms.NEWLINE_RUN, "\\n\\n");
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  public static class TextQuality {

    public final double score;
    public final boolean usable;
    public final int length;
    public final int nonBlankLines;
    public final int speakerLikeLines;
    public final int dialogueLikeLines;
    public final int stageLikeLines;
    public final int garbageLines;
    public final int pageFurnitureLines;
    public final double letterRatio;
    public final double digitRatio;
    public final double whitespaceRatio;
    public final double weirdRatio;
    public final double replacementRatio;
    public final double garbageLineRatio;
    public final double furnitureRatio;
    public final double averageLineLength;

    public TextQuality(
      double score,
      boolean usable,
      int length,
      int nonBlankLines,
      int speakerLikeLines,
      int dialogueLikeLines,
      int stageLikeLines,
      int garbageLines,
      int pageFurnitureLines,
      double letterRatio,
      double digitRatio,
      double whitespaceRatio,
      double weirdRatio,
      double replacementRatio,
      double garbageLineRatio,
      double furnitureRatio,
      double averageLineLength
    ) {
      this.score = score;
      this.usable = usable;
      this.length = length;
      this.nonBlankLines = nonBlankLines;
      this.speakerLikeLines = speakerLikeLines;
      this.dialogueLikeLines = dialogueLikeLines;
      this.stageLikeLines = stageLikeLines;
      this.garbageLines = garbageLines;
      this.pageFurnitureLines = pageFurnitureLines;
      this.letterRatio = letterRatio;
      this.digitRatio = digitRatio;
      this.whitespaceRatio = whitespaceRatio;
      this.weirdRatio = weirdRatio;
      this.replacementRatio = replacementRatio;
      this.garbageLineRatio = garbageLineRatio;
      this.furnitureRatio = furnitureRatio;
      this.averageLineLength = averageLineLength;
    }

    public static TextQuality empty() {
      return new TextQuality(
        0.0,
        false,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0.0,
        0.0,
        0.0,
        1.0,
        1.0,
        1.0,
        0.0,
        0.0
      );
    }

    public String summary() {
      return (
        "TextQuality{" +
        "score=" +
        String.format(Locale.ROOT, "%.2f", score) +
        ", usable=" +
        usable +
        ", length=" +
        length +
        ", lines=" +
        nonBlankLines +
        ", speakers=" +
        speakerLikeLines +
        ", dialogue=" +
        dialogueLikeLines +
        ", stage=" +
        stageLikeLines +
        ", garbage=" +
        garbageLines +
        ", furniture=" +
        pageFurnitureLines +
        ", letterRatio=" +
        String.format(Locale.ROOT, "%.2f", letterRatio) +
        ", weirdRatio=" +
        String.format(Locale.ROOT, "%.3f", weirdRatio) +
        '}'
      );
    }
  }
}
