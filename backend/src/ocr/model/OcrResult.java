package ocr.model;

import ocr.OcrCandidateScorer;
import util.RegexTerms;

public class OcrResult {

  public final String text;
  public final OcrCandidate best;

  public final int page;
  public final int region;

  public final int rotation;
  public final int index;
  public final String name;

  public final double score;
  public final double confidence;

  public final int printedPage;
  public final boolean hasPrintedPage;

  public final OcrCandidateScorer.Failure failure;
  public final String reason;
  public final boolean goodEnough;
  public final boolean likelyBadRotation;
  public final boolean likelyPageFurniture;
  public final boolean likelyClippedText;
  public final boolean likelyLowText;
  public final boolean likelyGarbageText;
  public final boolean likelySplitSpeaker;

  public OcrResult(String text, OcrCandidate best, int page, int region) {
    this.text = text == null ? "" : text;
    this.best = best;

    this.page = page;
    this.region = region;

    if (best == null) {
      this.rotation = 0;
      this.index = -1;
      this.name = "unknown";
      this.score = 0.0;
      this.confidence = 0.0;
      this.failure = OcrCandidateScorer.Failure.LOW_TEXT;
    } else {
      this.rotation = best.rotation;
      this.index = best.index;
      this.name = best.name;
      this.score = best.score;
      this.confidence = best.confidence;
      this.failure = OcrCandidateScorer.failure(best);
    }

    this.printedPage = detectPrintedPage(this.text);
    this.hasPrintedPage = this.printedPage >= 0;
    this.goodEnough = this.failure == OcrCandidateScorer.Failure.GOOD_ENOUGH;
    this.likelyBadRotation =
      this.failure == OcrCandidateScorer.Failure.BAD_ROTATION;
    this.likelyPageFurniture =
      this.failure == OcrCandidateScorer.Failure.PAGE_FURNITURE;
    this.likelyClippedText =
      this.failure == OcrCandidateScorer.Failure.CLIPPED_TEXT;
    this.likelyLowText = this.failure == OcrCandidateScorer.Failure.LOW_TEXT;
    this.likelyGarbageText =
      this.failure == OcrCandidateScorer.Failure.GARBAGE_TEXT;
    this.likelySplitSpeaker =
      this.failure == OcrCandidateScorer.Failure.SPLIT_SPEAKER;
    this.reason = buildReason();
  }

  public boolean isConfident() {
    return (
      best != null &&
      confidence >= 0.55 &&
      best.junkRatio <= 0.20 &&
      !likelyBadRotation &&
      !likelyLowText &&
      !likelyGarbageText
    );
  }

  public boolean review() {
    return !isConfident();
  }

  public boolean rescue() {
    return (
      best == null ||
      likelyBadRotation ||
      likelyClippedText ||
      likelyLowText ||
      likelyGarbageText ||
      likelySplitSpeaker
    );
  }

  public boolean suppressLearning() {
    return likelyBadRotation || likelyLowText || likelyGarbageText;
  }

  public boolean usableText() {
    return text != null && !text.isBlank() && !likelyLowText;
  }

  public boolean reliablePage() {
    return (hasPrintedPage && printedPage >= 0 && printedPage <= 2000);
  }

  public String rescueHint() {
    switch (failure) {
      case BAD_ROTATION:
        return "try alternate rotation before changing preprocessing";
      case CLIPPED_TEXT:
        return "try expanded crop or overlap region before OCR variants";
      case PAGE_FURNITURE:
        return "suppress repeated page furniture before parser learning";
      case LOW_TEXT:
        return "try crop expansion or lower threshold preprocessing";
      case GARBAGE_TEXT:
        return "try safer preprocessing or alternate rotation";
      case SPLIT_SPEAKER:
        return "try line-merge/speaker repair before full OCR rescue";
      case GOOD_ENOUGH:
        return "no rescue needed";
      case UNKNOWN:
      default:
        return "review manually or use conservative rescue";
    }
  }

  public String summary() {
    return String.format(
      "OcrResult | page=%d region=%d rotation=%d index=%d name=%s score=%.2f confidence=%.2f printedPage=%s failure=%s reason=%s review=%s",
      page,
      region,
      rotation,
      index,
      name,
      score,
      confidence,
      hasPrintedPage ? Integer.toString(printedPage) : "unknown",
      failure,
      reason,
      review()
    );
  }

  private String buildReason() {
    if (failure == null) {
      return "unknown";
    }

    switch (failure) {
      case GOOD_ENOUGH:
        return "candidate passed OCR quality thresholds";
      case BAD_ROTATION:
        return "low orientation confidence with high garbage or suspicious-token signal";
      case PAGE_FURNITURE:
        return "candidate appears dominated by repeated headers, footers, page numbers, or publisher artifacts";
      case CLIPPED_TEXT:
        return "candidate has too little readable text for the number of detected lines";
      case LOW_TEXT:
        return "candidate has empty or near-empty OCR text";
      case GARBAGE_TEXT:
        return "candidate has high garbage ratio or many suspicious OCR tokens";
      case SPLIT_SPEAKER:
        return "candidate looks like speaker headings were fragmented or not followed by dialogue";
      case UNKNOWN:
      default:
        return "candidate quality problem is not specific enough to classify";
    }
  }

  private static int detectPrintedPage(String text) {
    if (text == null || text.isBlank()) {
      return -1;
    }

    String[] lines = text.split(RegexTerms.LINE_BREAK);

    int maxLinesToCheck = Math.min(lines.length, 10);

    for (int i = 0; i < maxLinesToCheck; i++) {
      int found = pageNumberIn(lines[i]);

      if (found >= 0) {
        return found;
      }
    }

    int start = Math.max(0, lines.length - 10);

    for (int i = start; i < lines.length; i++) {
      int found = pageNumberIn(lines[i]);

      if (found >= 0) {
        return found;
      }
    }

    return -1;
  }

  private static int pageNumberIn(String line) {
    if (line == null) {
      return -1;
    }

    String trimmed = line.trim();

    if (trimmed.isEmpty()) {
      return -1;
    }

    if (trimmed.matches(RegexTerms.PAGE_NUMBER_ONLY)) {
      return parsePage(trimmed);
    }

    if (trimmed.matches(RegexTerms.PAGE_LABEL)) {
      return parsePage(trimmed.replaceFirst(RegexTerms.PAGE_PREFIX_CI, ""));
    }

    if (trimmed.matches(RegexTerms.OCR_NUMBER_THEN_CAPS)) {
      return parsePage(trimmed.replaceFirst(RegexTerms.AFTER_FIRST_SPACE_TO_END, ""));
    }

    if (trimmed.matches(RegexTerms.OCR_CAPS_THEN_NUMBER)) {
      return parsePage(trimmed.replaceFirst(RegexTerms.BEFORE_LAST_SPACE_GREEDY, ""));
    }

    return -1;
  }

  private static int parsePage(String value) {
    try {
      int parsed = Integer.parseInt(value);

      if (parsed < 0 || parsed > 2000) {
        return -1;
      }

      return parsed;
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
