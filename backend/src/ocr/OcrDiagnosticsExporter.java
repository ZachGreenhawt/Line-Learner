package ocr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import ocr.model.OcrCandidate;

public class OcrDiagnosticsExporter {

  private static final boolean ENABLED = true;
  private static final Path PATH = Path.of("ocr_diagnostics.csv");

  private static boolean initialized = false;

  public static void reset() {
    if (!ENABLED) {
      return;
    }

    try {
      Files.writeString(
        PATH,
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
    if (!ENABLED || candidate == null) {
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
        PATH,
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
    if (!ENABLED) {
      return;
    }

    System.out.println("OCR diagnostics exported to: " + PATH.toAbsolutePath());
  }
}
