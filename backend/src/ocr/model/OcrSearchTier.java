package ocr.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OcrSearchTier {

  public final String name;
  public final List<Integer> rotations;
  public final List<Integer> candidates;
  public final double minScore;
  public final boolean allowFallback;

  public OcrSearchTier(
    String name,
    List<Integer> rotations,
    List<Integer> candidates,
    double minScore,
    boolean allowFallback
  ) {
    this.name = name == null || name.isBlank() ? "unnamed-tier" : name;

    this.rotations = copy(rotations, List.of(0, 180));

    this.candidates = copy(candidates, List.of(0, 1, 2, 3, 4));

    this.minScore = minScore;
    this.allowFallback = allowFallback;
  }

  public boolean hasRotation(int rotation) {
    return rotations.contains(rotation);
  }

  public boolean hasCandidate(int index) {
    return candidates.contains(index);
  }

  public boolean fullSearch() {
    return (
      rotations.contains(0) &&
      rotations.contains(180) &&
      candidates.contains(0) &&
      candidates.contains(1) &&
      candidates.contains(2) &&
      candidates.contains(3) &&
      candidates.contains(4)
    );
  }

  public String summary() {
    return String.format(
      "%s | rotations=%s candidates=%s minScore=%.2f allowFallback=%s",
      name,
      rotations,
      candidates,
      minScore,
      allowFallback
    );
  }

  @Override
  public String toString() {
    return summary();
  }

  private static List<Integer> copy(
    List<Integer> values,
    List<Integer> fallback
  ) {
    List<Integer> list = values == null || values.isEmpty() ? fallback : values;

    return Collections.unmodifiableList(new ArrayList<>(list));
  }
}
