package ocr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import ocr.model.OcrCandidate;
import util.RegexTerms;

public class OcrDiagnosticsExporter {

  private static final String ENABLED_PROPERTY = "ll.ocrDiagnostics";
  private static final String FILE_NAME = "ocr_diagnostics.csv";

  private static boolean initialized = false;

  public static void reset() {
    if (!enabled()) {
      return;
    }

    try {
      Path path = path();
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(
        path,
        "page,region,rotation,candidateIndex,candidateName,tierUsed,score,confidence,englishWordRatio,readabilityScore,artifactPenalty,garbageRatio,speakerLikeLines,dialogueLines,stageDirectionLines,uppercaseLines,garbageLines,mirroredArtifactLines,suspiciousTokenCount,readableWordCount,totalLines,textLength,selectedWinner,preview\n",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
      );
      initialized = true;
    } catch (IOException e) {
      System.out.println("Could not reset OCR diagnostics: " + e.getMessage());
    }
  }

  public static void log(
    int page,
    int region,
    int index,
    OcrCandidate candidate
  ) {
    log(page, region, index, candidate, false);
  }

  public static void log(
    int page,
    int region,
    int index,
    OcrCandidate candidate,
    boolean selected
  ) {
    if (!enabled() || candidate == null) {
      return;
    }

    if (!initialized) {
      reset();
    }

    String row =
      page +
      "," +
      region +
      "," +
      candidate.rotation +
      "," +
      index +
      "," +
      csv(candidate.name) +
      "," +
      csv(candidate.tier) +
      "," +
      format(candidate.score) +
      "," +
      format(candidate.confidence) +
      "," +
      format(candidate.englishRatio) +
      "," +
      format(candidate.readability) +
      "," +
      format(candidate.artifacts) +
      "," +
      format(candidate.junkRatio) +
      "," +
      candidate.speakerLines +
      "," +
      candidate.dialogue +
      "," +
      candidate.stage +
      "," +
      candidate.caps +
      "," +
      candidate.junk +
      "," +
      candidate.mirrored +
      "," +
      candidate.tokens +
      "," +
      candidate.words +
      "," +
      candidate.lines +
      "," +
      candidate.text.length() +
      "," +
      selected +
      "," +
      csv(preview(candidate.text)) +
      "\n";

    try {
      Files.writeString(
        path(),
        row,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND
      );
    } catch (IOException e) {
      System.out.println("Could not write OCR diagnostics: " + e.getMessage());
    }
  }

  private static String format(double value) {
    return String.format(java.util.Locale.US, "%.4f", value);
  }

  private static String preview(String text) {
    if (text == null) {
      return "";
    }

    String cleaned = text.replace('\n', ' ').replace('\r', ' ').trim();

    if (cleaned.length() <= 120) {
      return cleaned;
    }

    return cleaned.substring(0, 120);
  }

  private static String csv(String value) {
    if (value == null) {
      value = "";
    }

    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  public static void printLocation() {
    if (!enabled()) {
      return;
    }

    System.out.println("OCR diagnostics exported to: " + path().toAbsolutePath());
  }

  private static boolean enabled() {
    return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
  }

  private static Path path() {
    String root = property("ll.sessionRoot", "parser_sessions");
    String id = property("ll.sessionId", "");
    if (id.isEmpty()) {
      return Path.of(FILE_NAME);
    }
    return Path.of(root, clean(id), FILE_NAME);
  }

  private static String property(String key, String fallback) {
    String value = System.getProperty(key);
    return value == null ? fallback : value.trim();
  }

  private static String clean(String name) {
    return name.replaceAll(RegexTerms.NON_SESSION_NAME_CHAR, "_");
  }
}
