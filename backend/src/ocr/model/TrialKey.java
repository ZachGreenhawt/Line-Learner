package ocr.model;

import java.util.Objects;

public class TrialKey {

  public enum CandidateCategory {
    SAFE,
    AGGRESSIVE,
    UNKNOWN,
  }

  public final int rotation;
  public final int candidateIndex;
  public final String candidateName;
  public final CandidateCategory category;

  public TrialKey(int rotation, int candidateIndex, String candidateName) {
    this.rotation = normalizeRotation(rotation);
    this.candidateIndex = candidateIndex;
    this.candidateName = candidateName == null ? "unknown" : candidateName;
    this.category = categoryForCandidate(
      this.candidateIndex,
      this.candidateName
    );
  }

  public static TrialKey fromCandidate(OcrCandidate candidate) {
    if (candidate == null) {
      return new TrialKey(0, -1, "unknown");
    }

    return new TrialKey(candidate.rotation, candidate.index, candidate.name);
  }

  public TrialKey withRotation(int newRotation) {
    return new TrialKey(newRotation, candidateIndex, candidateName);
  }

  public TrialKey withCandidate(
    int newCandidateIndex,
    String newCandidateName
  ) {
    return new TrialKey(rotation, newCandidateIndex, newCandidateName);
  }

  public boolean sameRotation(TrialKey other) {
    return other != null && rotation == other.rotation;
  }

  public boolean sameCandidate(TrialKey other) {
    return other != null && candidateIndex == other.candidateIndex;
  }

  public boolean isSafe() {
    return category == CandidateCategory.SAFE;
  }

  public boolean isAggressive() {
    return category == CandidateCategory.AGGRESSIVE;
  }

  public int preprocessingPriority() {
    return candidateIndex >= 0 && candidateIndex <= 4 ? candidateIndex : 99;
  }

  public String summary() {
    return (
      "TrialKey" +
      "{rotation=" +
      rotation +
      ", candidateIndex=" +
      candidateIndex +
      ", candidateName='" +
      candidateName +
      "'" +
      ", category=" +
      category +
      "}"
    );
  }

  @Override
  public String toString() {
    return summary();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof TrialKey)) {
      return false;
    }

    TrialKey that = (TrialKey) other;

    return rotation == that.rotation && candidateIndex == that.candidateIndex;
  }

  @Override
  public int hashCode() {
    return Objects.hash(rotation, candidateIndex);
  }

  private static int normalizeRotation(int rotation) {
    int normalized = rotation % 360;

    if (normalized < 0) {
      normalized += 360;
    }

    if (normalized == 90 || normalized == 270) {
      return normalized;
    }

    return normalized == 180 ? 180 : 0;
  }

  private static CandidateCategory categoryForCandidate(
    int candidateIndex,
    String candidateName
  ) {
    if (candidateIndex >= 0 && candidateIndex <= 2) {
      return CandidateCategory.SAFE;
    }

    if (candidateIndex == 3 || candidateIndex == 4) {
      return CandidateCategory.AGGRESSIVE;
    }

    String name = candidateName == null ? "" : candidateName.toLowerCase();

    if (
      name.contains("threshold") ||
      name.contains("otsu") ||
      name.contains("denoise")
    ) {
      return CandidateCategory.AGGRESSIVE;
    }

    if (
      name.contains("original") ||
      name.contains("gray") ||
      name.contains("contrast")
    ) {
      return CandidateCategory.SAFE;
    }

    return CandidateCategory.UNKNOWN;
  }
}
