import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.sourceforge.tess4j.Tesseract;

public class OrientationResolver {

  public static String text(
    BufferedImage image,
    Tesseract tesseract,
    int page,
    int region
  ) throws Exception {
    return result(image, tesseract, page, region).text;
  }

  public static OcrResult result(
    BufferedImage image,
    Tesseract tesseract,
    int page,
    int region
  ) throws Exception {
    return result(image, tesseract, page, region, new OcrRunProfile());
  }

  public static OcrResult result(
    BufferedImage image,
    Tesseract tesseract,
    int page,
    int region,
    OcrRunProfile profile
  ) throws Exception {
    if (image == null || tesseract == null) {
      return new OcrResult("", null, page, region);
    }

    if (profile == null) {
      profile = new OcrRunProfile();
    }

    OcrCandidate best = null;
    OcrSearchTier bestTier = null;
    Set<String> tried = new HashSet<>();

    List<OcrSearchTier> tiers = profile.plan(page, region);

    for (OcrSearchTier tier : tiers) {
      OcrCandidate tierBest = null;

      for (int rotation : rotations(tier.rotations)) {
        BufferedImage rotated = rotate(image, rotation);
        if (rotated == null) {
          continue;
        }

        List<ImagePreprocessor.Candidate> images = ImagePreprocessor.candidates(
          rotated,
          tier.candidates
        );

        for (ImagePreprocessor.Candidate imageOption : images) {
          BufferedImage processed = imageOption.image;
          if (processed == null) {
            continue;
          }

          String comboKey = rotation + ":" + imageOption.index;
          if (!tried.add(comboKey)) {
            continue;
          }

          String text = tesseract.doOCR(processed);

          OcrCandidate candidate = withInfo(
            OcrCandidateScorer.rank(text, rotation),
            imageOption.index,
            imageOption.name,
            tier.name
          );

          if (candidate == null) {
            continue;
          }

          OcrDiagnosticsExporter.log(
            page,
            region,
            imageOption.index,
            candidate
          );

          OcrCandidate newTierBest = better(tierBest, candidate);
          if (newTierBest != tierBest) {
            tierBest = newTierBest;
          }

          OcrCandidate newBest = better(best, candidate);
          if (newBest != best) {
            best = newBest;
            bestTier = tier;
          }

          OcrResult immediateResult = new OcrResult(
            candidate.text,
            candidate,
            page,
            region
          );

          if (acceptNow(immediateResult, tier, profile)) {
            return finish(immediateResult, tier, profile, page, region, false);
          }
        }
      }

      if (tierBest != null) {
        OcrResult tierResult = new OcrResult(
          tierBest.text,
          tierBest,
          page,
          region
        );

        if (profile.shouldAccept(tierResult, tier)) {
          return finish(tierResult, tier, profile, page, region, false);
        }

        if (!tier.allowFallback) {
          break;
        }
      }
    }

    if (best == null) {
      OcrResult emptyResult = new OcrResult("", null, page, region);
      profile.learn(page, region, emptyResult);
      return emptyResult;
    }

    OcrResult fallbackResult = new OcrResult(best.text, best, page, region);

    return finish(fallbackResult, bestTier, profile, page, region, true);
  }

  private static boolean acceptNow(
    OcrResult result,
    OcrSearchTier tier,
    OcrRunProfile profile
  ) {
    if (
      result == null || result.best == null || tier == null || profile == null
    ) {
      return false;
    }

    if (!tier.allowFallback) {
      return false;
    }

    if (tier.name == null) {
      return false;
    }

    boolean logicTier =
      tier.name.contains("adaptive-trial") ||
      tier.name.contains("warmup-logic") ||
      tier.name.contains("rescue-same-rotation");

    if (!logicTier) {
      return false;
    }

    if (wrongRotation(result.best)) {
      return false;
    }

    return (profile.shouldAccept(result, tier) && strong(result.best));
  }

  private static OcrResult finish(
    OcrResult result,
    OcrSearchTier tier,
    OcrRunProfile profile,
    int page,
    int region,
    boolean fallback
  ) {
    OcrCandidate candidate = result == null ? null : result.best;

    if (fallback && wrongRotation(candidate)) {
      System.out.println(
        String.format(
          "Rejected fallback OCR candidate because it still looks badly oriented | rotation=%d score=%s confidence=%.2f",
          candidate.rotation,
          candidate.score,
          candidate.confidence
        )
      );
    }

    if (candidate == null) {
      return result;
    }

    OcrDiagnosticsExporter.log(page, region, candidate.index, candidate, true);

    ImagePreprocessor.win(candidate.name);

    if (profile != null) {
      profile.learn(page, region, result);
    }

    System.out.println(
      String.format(
        "Selected OCR candidate | tier=%s rotation=%d deg index=%d name=%s score=%s confidence=%.2f%s",
        tier == null ? "unknown" : tier.name,
        candidate.rotation,
        candidate.index,
        candidate.name,
        candidate.score,
        candidate.confidence,
        fallback ? " fallback=true" : ""
      )
    );

    return result;
  }

  private static boolean strong(OcrCandidate candidate) {
    if (candidate == null) {
      return false;
    }

    return (
      candidate.confidence >= 0.88 &&
      candidate.junkRatio <= 0.06 &&
      candidate.englishRatio >= 0.15 &&
      candidate.tokens <= 2 &&
      !wrongRotation(candidate)
    );
  }

  private static OcrCandidate better(
    OcrCandidate currentBest,
    OcrCandidate challenger
  ) {
    if (currentBest == null) {
      return challenger;
    }

    if (challenger == null) {
      return currentBest;
    }

    boolean currentSafe = safe(currentBest);
    boolean challengerSafe = safe(challenger);

    if (currentSafe && !challengerSafe) {
      return aggressiveBeatsSafe(challenger, currentBest)
        ? challenger
        : currentBest;
    }

    if (!currentSafe && challengerSafe) {
      return aggressiveBeatsSafe(currentBest, challenger)
        ? currentBest
        : challenger;
    }

    if (close(currentBest, challenger)) {
      int quality = compareQuality(challenger, currentBest);
      if (quality > 0) {
        return challenger;
      }
      if (quality < 0) {
        return currentBest;
      }

      int safety = compareSafety(challenger, currentBest);
      if (safety > 0) {
        return challenger;
      }
      if (safety < 0) {
        return currentBest;
      }

      int priority = Integer.compare(
        priority(challenger),
        priority(currentBest)
      );
      if (priority < 0) {
        return challenger;
      }

      return currentBest;
    }

    return challenger.score > currentBest.score ? challenger : currentBest;
  }

  private static boolean aggressiveBeatsSafe(
    OcrCandidate aggressive,
    OcrCandidate safe
  ) {
    if (aggressive == null || safe == null) {
      return false;
    }

    if (aggressive.score < safe.score * 1.10) {
      return false;
    }

    if (aggressive.confidence < safe.confidence - 0.03) {
      return false;
    }

    if (aggressive.englishRatio < safe.englishRatio - 0.02) {
      return false;
    }

    if (aggressive.words < safe.words * 0.85) {
      return false;
    }

    if (aggressive.text.length() < safe.text.length() * 0.90) {
      return false;
    }

    if (aggressive.tokens > safe.tokens + 1) {
      return false;
    }

    if (aggressive.junkRatio > safe.junkRatio + 0.05) {
      return false;
    }

    return true;
  }

  private static boolean close(OcrCandidate first, OcrCandidate second) {
    double high = Math.max(Math.abs(first.score), Math.abs(second.score));
    double difference = Math.abs(first.score - second.score);

    if (high < 1.0) {
      return difference <= 10.0;
    }

    return difference <= Math.max(12.0, high * 0.15);
  }

  private static int compareQuality(OcrCandidate first, OcrCandidate second) {
    int firstWins = 0;
    int secondWins = 0;

    if (first.englishRatio > second.englishRatio + 0.03) {
      firstWins++;
    } else if (second.englishRatio > first.englishRatio + 0.03) {
      secondWins++;
    }

    if (first.words > second.words + 2) {
      firstWins++;
    } else if (second.words > first.words + 2) {
      secondWins++;
    }

    if (first.junkRatio + 0.03 < second.junkRatio) {
      firstWins++;
    } else if (second.junkRatio + 0.03 < first.junkRatio) {
      secondWins++;
    }

    if (first.tokens + 1 < second.tokens) {
      firstWins++;
    } else if (second.tokens + 1 < first.tokens) {
      secondWins++;
    }

    if (first.confidence > second.confidence + 0.05) {
      firstWins++;
    } else if (second.confidence > first.confidence + 0.05) {
      secondWins++;
    }

    if (first.artifacts + 5.0 < second.artifacts) {
      firstWins++;
    } else if (second.artifacts + 5.0 < first.artifacts) {
      secondWins++;
    }

    return Integer.compare(firstWins, secondWins);
  }

  private static int compareSafety(OcrCandidate first, OcrCandidate second) {
    boolean firstWrong = wrongRotation(first);
    boolean secondWrong = wrongRotation(second);

    if (firstWrong && !secondWrong) {
      return -1;
    }

    if (!firstWrong && secondWrong) {
      return 1;
    }

    if (first.confidence > second.confidence + 0.04) {
      return 1;
    }

    if (second.confidence > first.confidence + 0.04) {
      return -1;
    }

    if (first.rotation == 0 && second.rotation != 0) {
      return 1;
    }

    if (second.rotation == 0 && first.rotation != 0) {
      return -1;
    }

    return 0;
  }

  private static boolean wrongRotation(OcrCandidate candidate) {
    if (candidate == null) {
      return false;
    }

    if (candidate.confidence >= 0.55) {
      return false;
    }

    if (candidate.junkRatio >= 0.28) {
      return true;
    }

    if (candidate.words <= 3 && candidate.tokens >= 4) {
      return true;
    }

    if (candidate.englishRatio < 0.05 && candidate.lines >= 4) {
      return true;
    }

    return false;
  }

  private static boolean safe(OcrCandidate candidate) {
    return priority(candidate) <= 2;
  }

  private static int priority(OcrCandidate candidate) {
    if (candidate == null || candidate.name == null) {
      return 99;
    }

    switch (candidate.name) {
      case "original":
        return 0;
      case "grayscale":
        return 1;
      case "contrast-1.35":
        return 2;
      case "otsu-threshold":
        return 3;
      case "otsu-denoised":
        return 4;
      default:
        return 99;
    }
  }

  private static List<Integer> rotations(List<Integer> rotations) {
    List<Integer> out = new ArrayList<>();
    Set<Integer> seen = new HashSet<>();

    if (rotations == null || rotations.isEmpty()) {
      out.add(0);
      return out;
    }

    for (Integer rotation : rotations) {
      int degrees = rotation(rotation == null ? 0 : rotation);
      if (seen.add(degrees)) {
        out.add(degrees);
      }
    }

    if (out.isEmpty()) {
      out.add(0);
    }

    return out;
  }

  private static int rotation(int rotation) {
    int degrees = rotation % 360;
    if (degrees < 0) {
      degrees += 360;
    }

    return degrees == 360 ? 0 : degrees;
  }

  private static OcrCandidate withInfo(
    OcrCandidate candidate,
    int index,
    String name,
    String tier
  ) {
    if (candidate == null) {
      return null;
    }

    return new OcrCandidate(
      candidate.text,
      candidate.rotation,
      candidate.score,
      candidate.speakerLines,
      candidate.dialogue,
      candidate.junk,
      candidate.caps,
      candidate.stage,
      candidate.words,
      candidate.tokens,
      candidate.mirrored,
      index,
      candidate.lines,
      name,
      tier,
      candidate.junkRatio,
      candidate.englishRatio,
      candidate.artifacts,
      candidate.readability,
      candidate.confidence
    );
  }

  private static BufferedImage rotate(BufferedImage image, int degrees) {
    if (image == null) {
      return null;
    }

    degrees = rotation(degrees);

    if (degrees == 0) {
      return image;
    }

    double radians = Math.toRadians(degrees);

    int oldWidth = image.getWidth();
    int oldHeight = image.getHeight();

    int newWidth = (degrees == 90 || degrees == 270) ? oldHeight : oldWidth;

    int newHeight = (degrees == 90 || degrees == 270) ? oldWidth : oldHeight;

    int imageType =
      image.getType() == BufferedImage.TYPE_CUSTOM
        ? BufferedImage.TYPE_INT_RGB
        : image.getType();

    BufferedImage rotated = new BufferedImage(newWidth, newHeight, imageType);

    Graphics2D graphics = rotated.createGraphics();

    AffineTransform transform = new AffineTransform();

    transform.translate(newWidth / 2.0, newHeight / 2.0);
    transform.rotate(radians);
    transform.translate(-oldWidth / 2.0, -oldHeight / 2.0);

    graphics.drawImage(image, transform, null);
    graphics.dispose();

    return rotated;
  }
}
