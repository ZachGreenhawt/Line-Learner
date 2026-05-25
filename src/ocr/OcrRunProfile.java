import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OcrRunProfile {

  private static final int DEFAULT_ROTATION = 0;
  private static final int MAX_RECENT_SCORES = 8;
  private static final int MIN_GOOD_RESULTS_FOR_PATTERN = 6;
  private static final int MIN_TOTAL_GOOD_RESULTS_BEFORE_SPEEDUP = 3;

  private static final int MAX_ADAPTIVE_TRIALS = 3;
  private static final int MAX_TENTATIVE_TRIALS = 2;

  private static final double MIN_GOOD_CONFIDENCE = 0.55;
  private static final double MAX_GOOD_GARBAGE_RATIO = 0.20;
  private final TrialKey cachedPreferredTrial;
  private final Map<PatternKey, RegionProfile> patternProfiles =
    new HashMap<>();

  private final Map<Integer, Integer> globalRotationWins = new HashMap<>();

  private int totalGoodResults;
  private int totalResults;
  private int recentGlobalFailures;
  private OcrCandidateScorer.Failure lastFailure =
    OcrCandidateScorer.Failure.UNKNOWN;

  public OcrRunProfile() {
    this(null);
  }

  public OcrRunProfile(TrialKey cachedPreferredTrial) {
    this.cachedPreferredTrial = cachedPreferredTrial;
  }

  public List<OcrSearchTier> plan(int region) {
    return plan(-1, region, PatternKey.Mode.UNKNOWN);
  }

  public List<OcrSearchTier> plan(int page, int region) {
    return plan(page, region, PatternKey.Mode.UNKNOWN);
  }

  public List<OcrSearchTier> plan(int page, int region, PatternKey.Mode mode) {
    if (stillWarmingUp()) {
      return warmup();
    }

    PatternKey exactKey = PatternKey.exact(page, region, mode);

    RegionProfile exactProfile = patternProfiles.get(exactKey);

    if (exactProfile != null && exactProfile.hasUsefulTrialStats()) {
      return adaptive("exact-pattern", exactProfile, exactProfile.isTrusted());
    }

    PatternKey regionParityKey = PatternKey.regionAndParity(page, region);

    RegionProfile regionParityProfile = patternProfiles.get(regionParityKey);

    if (
      regionParityProfile != null && regionParityProfile.hasUsefulTrialStats()
    ) {
      return adaptive(
        "region-parity-pattern",
        regionParityProfile,
        regionParityProfile.isTrusted()
      );
    }

    PatternKey regionOnlyKey = PatternKey.regionOnly(region);
    RegionProfile regionProfile = patternProfiles.get(regionOnlyKey);

    if (regionProfile != null && regionProfile.isTrusted()) {
      return adaptive("region-pattern", regionProfile, true);
    }

    return warmup();
  }

  public void learn(int region, OcrResult result) {
    learn(result == null ? -1 : result.page, region, result);
  }

  public void learn(int page, int region, OcrResult result) {
    totalResults++;

    if (result == null || result.best == null) {
      lastFailure = OcrCandidateScorer.Failure.LOW_TEXT;
      recordFailureForKeys(page, region, PatternKey.Mode.UNKNOWN, null);
      return;
    }

    lastFailure = result.failure;

    PatternKey.Mode inferredMode = PatternKey.infer(result.text);

    if (
      inferredMode == PatternKey.Mode.FRONT_MATTER ||
      inferredMode == PatternKey.Mode.TITLE_OR_CAST
    ) {
      return;
    }

    if (result.suppressLearning()) {
      recordFailureForKeys(page, region, inferredMode, result);
      return;
    }

    if (!isGoodResult(result)) {
      recordFailureForKeys(page, region, inferredMode, result);
      return;
    }

    totalGoodResults++;
    recentGlobalFailures = 0;
    increment(globalRotationWins, result.rotation);
    lastFailure = OcrCandidateScorer.Failure.GOOD_ENOUGH;

    for (PatternKey key : learningKeys(page, region, inferredMode)) {
      profileForKey(key).learn(result);
    }
  }

  public boolean shouldAccept(OcrResult result, OcrSearchTier tier) {
    if (result == null || result.best == null || tier == null) {
      return false;
    }

    OcrCandidate candidate = result.best;

    if (result.goodEnough) {
      return true;
    }

    if (
      result.likelyBadRotation ||
      result.likelyLowText ||
      result.likelyGarbageText
    ) {
      return false;
    }

    if (strongAccept(candidate)) {
      return true;
    }

    if (adaptiveAccept(candidate, tier)) {
      return true;
    }

    if (candidate.score < tier.minScore) {
      return false;
    }

    if (candidate.junkRatio > MAX_GOOD_GARBAGE_RATIO) {
      return false;
    }

    if (candidate.confidence < MIN_GOOD_CONFIDENCE) {
      return false;
    }

    if (candidate.tokens >= 4) {
      return false;
    }

    return true;
  }

  private boolean strongAccept(OcrCandidate candidate) {
    return (
      candidate.confidence >= 0.85 &&
      candidate.junkRatio <= 0.05 &&
      candidate.englishRatio >= 0.15 &&
      candidate.tokens <= 2
    );
  }

  private boolean adaptiveAccept(OcrCandidate candidate, OcrSearchTier tier) {
    if (tier.name == null) {
      return false;
    }

    boolean adaptiveOrLogicTier =
      tier.name.contains("adaptive-trial") ||
      tier.name.contains("warmup-logic") ||
      tier.name.contains("rescue-same-rotation");

    if (!adaptiveOrLogicTier) {
      return false;
    }

    return (
      candidate.confidence >= 0.78 &&
      candidate.junkRatio <= 0.06 &&
      candidate.englishRatio >= 0.18 &&
      candidate.tokens <= 2 &&
      candidate.score >= tier.minScore
    );
  }

  public void reset() {
    patternProfiles.clear();
    globalRotationWins.clear();
    totalGoodResults = 0;
    totalResults = 0;
    recentGlobalFailures = 0;
    lastFailure = OcrCandidateScorer.Failure.UNKNOWN;
  }

  public String summary() {
    StringBuilder builder = new StringBuilder();
    builder.append("OcrRunProfile summary:\n");
    builder
      .append("  totalResults=")
      .append(totalResults)
      .append(" totalGoodResults=")
      .append(totalGoodResults)
      .append(" recentGlobalFailures=")
      .append(recentGlobalFailures)
      .append(" lastFailure=")
      .append(lastFailure)
      .append(" warmingUp=")
      .append(stillWarmingUp())
      .append("\n");

    if (patternProfiles.isEmpty()) {
      builder.append("  No patterns learned yet.\n");
      return builder.toString();
    }

    for (Map.Entry<
      PatternKey,
      RegionProfile
    > entry : patternProfiles.entrySet()) {
      builder
        .append("  ")
        .append(entry.getKey().summary())
        .append(" -> ")
        .append(entry.getValue().summary())
        .append("\n");
    }

    return builder.toString();
  }

  private boolean stillWarmingUp() {
    return totalGoodResults < MIN_TOTAL_GOOD_RESULTS_BEFORE_SPEEDUP;
  }

  private RegionProfile profileForKey(PatternKey key) {
    return patternProfiles.computeIfAbsent(key, RegionProfile::new);
  }

  private List<PatternKey> learningKeys(
    int page,
    int region,
    PatternKey.Mode mode
  ) {
    List<PatternKey> keys = new ArrayList<>();

    PatternKey exact = PatternKey.exact(page, region, mode);

    keys.add(exact);
    keys.addAll(exact.broaderKeys());

    return keys;
  }

  private void recordFailureForKeys(
    int page,
    int region,
    PatternKey.Mode mode,
    OcrResult result
  ) {
    recentGlobalFailures++;

    for (PatternKey key : learningKeys(page, region, mode)) {
      RegionProfile profile = patternProfiles.get(key);

      if (profile != null) {
        profile.recordFailure(result);
      }
    }
  }

  private List<OcrSearchTier> warmup() {
    List<OcrSearchTier> tiers = new ArrayList<>();

    if (cachedPreferredTrial != null) {
      tiers.add(
        new OcrSearchTier(
          "warmup-cache-preferred-r" +
            cachedPreferredTrial.rotation +
            "-c" +
            cachedPreferredTrial.candidateIndex,
          List.of(cachedPreferredTrial.rotation),
          List.of(cachedPreferredTrial.candidateIndex),
          Double.NEGATIVE_INFINITY,
          true
        )
      );
    }

    tiers.add(
      new OcrSearchTier(
        "warmup-logic-primary-original",
        List.of(warmupRotation()),
        List.of(0),
        Double.NEGATIVE_INFINITY,
        true
      )
    );

    tiers.add(
      new OcrSearchTier(
        "warmup-logic-primary-grayscale",
        List.of(warmupRotation()),
        List.of(1),
        Double.NEGATIVE_INFINITY,
        true
      )
    );

    tiers.add(
      new OcrSearchTier(
        "warmup-logic-primary-contrast",
        List.of(warmupRotation()),
        List.of(2),
        Double.NEGATIVE_INFINITY,
        true
      )
    );

    tiers.add(
      new OcrSearchTier(
        "warmup-logic-alternate-original",
        List.of(alternate(warmupRotation())),
        List.of(0),
        Double.NEGATIVE_INFINITY,
        true
      )
    );

    tiers.add(
      new OcrSearchTier(
        "warmup-logic-threshold-rescue",
        List.of(warmupRotation(), alternate(warmupRotation())),
        List.of(3),
        Double.NEGATIVE_INFINITY,
        false
      )
    );

    tiers.add(
      new OcrSearchTier(
        "warmup-logic-denoise-rescue",
        List.of(warmupRotation(), alternate(warmupRotation())),
        List.of(4),
        Double.NEGATIVE_INFINITY,
        false
      )
    );

    return tiers;
  }

  private int warmupRotation() {
    return mostCommonKey(globalRotationWins, DEFAULT_ROTATION);
  }

  private static int alternate(int rotation) {
    return rotation == 0 ? 180 : 0;
  }

  private List<OcrSearchTier> adaptive(
    String prefix,
    RegionProfile profile,
    boolean fullyTrusted
  ) {
    List<OcrSearchTier> tiers = new ArrayList<>();
    List<TrialKey> rankedTrials = profile.rankedTrials();

    if (rankedTrials.isEmpty()) {
      return warmup();
    }

    rankedTrials = prioritizeCachedTrial(rankedTrials);

    int trialLimit = fullyTrusted
      ? MAX_ADAPTIVE_TRIALS
      : Math.min(MAX_TENTATIVE_TRIALS, rankedTrials.size());

    for (int i = 0; i < rankedTrials.size() && tiers.size() < trialLimit; i++) {
      TrialKey trial = rankedTrials.get(i);
      TrialStats stats = profile.statsFor(trial);

      if (!fullyTrusted && trial.isAggressive()) {
        continue;
      }

      if (!fullyTrusted && (stats == null || stats.getSuccesses() == 0)) {
        continue;
      }

      if (stats != null && stats.isActivelyBad()) {
        continue;
      }

      tiers.add(
        new OcrSearchTier(
          prefix +
            "-adaptive-trial-" +
            tiers.size() +
            "-r" +
            trial.rotation +
            "-c" +
            trial.candidateIndex,
          List.of(trial.rotation),
          List.of(trial.candidateIndex),
          profile.minimumAcceptableScoreForTrial(trial, fullyTrusted),
          true
        )
      );
    }

    if (tiers.isEmpty()) {
      return warmup();
    }

    tiers.addAll(rescuePlan(prefix, profile));

    return tiers;
  }

  private List<TrialKey> prioritizeCachedTrial(List<TrialKey> rankedTrials) {
    if (
      cachedPreferredTrial == null ||
      rankedTrials == null ||
      rankedTrials.isEmpty()
    ) {
      return rankedTrials;
    }

    List<TrialKey> out = new ArrayList<>();
    out.add(cachedPreferredTrial);

    for (TrialKey trial : rankedTrials) {
      if (!sameTrial(trial, cachedPreferredTrial)) {
        out.add(trial);
      }
    }

    return out;
  }

  private static boolean sameTrial(TrialKey first, TrialKey second) {
    if (first == null || second == null) {
      return false;
    }

    return (
      first.rotation == second.rotation &&
      first.candidateIndex == second.candidateIndex
    );
  }

  private List<OcrSearchTier> rescuePlan(String prefix, RegionProfile profile) {
    List<OcrSearchTier> tiers = new ArrayList<>();

    int preferredRotation = profile.preferredRotationOrDefault();
    int alternateRotation = alternate(preferredRotation);

    switch (lastFailure) {
      case BAD_ROTATION:
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-bad-rotation-original",
            List.of(alternateRotation),
            List.of(0),
            Double.NEGATIVE_INFINITY,
            true
          )
        );
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-bad-rotation-grayscale",
            List.of(alternateRotation),
            List.of(1),
            Double.NEGATIVE_INFINITY,
            false
          )
        );
        return tiers;
      case LOW_TEXT:
      case CLIPPED_TEXT:
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-low-text-same-rotation-contrast",
            List.of(preferredRotation),
            List.of(2),
            Double.NEGATIVE_INFINITY,
            true
          )
        );
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-low-text-same-rotation-threshold",
            List.of(preferredRotation),
            List.of(3),
            Double.NEGATIVE_INFINITY,
            false
          )
        );
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-low-text-alternate-original",
            List.of(alternateRotation),
            List.of(0),
            Double.NEGATIVE_INFINITY,
            false
          )
        );
        return tiers;
      case PAGE_FURNITURE:
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-page-furniture-same-original",
            List.of(preferredRotation),
            List.of(0),
            Double.NEGATIVE_INFINITY,
            true
          )
        );
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-page-furniture-same-grayscale",
            List.of(preferredRotation),
            List.of(1),
            Double.NEGATIVE_INFINITY,
            true
          )
        );
        return tiers;
      case GARBAGE_TEXT:
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-garbage-same-grayscale",
            List.of(preferredRotation),
            List.of(1),
            Double.NEGATIVE_INFINITY,
            true
          )
        );
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-garbage-alternate-original",
            List.of(alternateRotation),
            List.of(0),
            Double.NEGATIVE_INFINITY,
            false
          )
        );
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-garbage-same-denoise",
            List.of(preferredRotation),
            List.of(4),
            Double.NEGATIVE_INFINITY,
            false
          )
        );
        return tiers;
      case SPLIT_SPEAKER:
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-split-speaker-same-original",
            List.of(preferredRotation),
            List.of(0),
            Double.NEGATIVE_INFINITY,
            true
          )
        );
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-split-speaker-same-contrast",
            List.of(preferredRotation),
            List.of(2),
            Double.NEGATIVE_INFINITY,
            true
          )
        );
        return tiers;
      case GOOD_ENOUGH:
      case UNKNOWN:
      default:
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-same-rotation-grayscale",
            List.of(preferredRotation),
            List.of(1),
            Double.NEGATIVE_INFINITY,
            true
          )
        );
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-same-rotation-contrast",
            List.of(preferredRotation),
            List.of(2),
            Double.NEGATIVE_INFINITY,
            true
          )
        );
        tiers.add(
          new OcrSearchTier(
            prefix + "-rescue-alternate-rotation-original",
            List.of(alternateRotation),
            List.of(0),
            Double.NEGATIVE_INFINITY,
            false
          )
        );
        return tiers;
    }
  }

  private static boolean isGoodResult(OcrResult result) {
    return (
      result != null &&
      result.best != null &&
      !result.suppressLearning() &&
      result.confidence >= MIN_GOOD_CONFIDENCE &&
      result.best.junkRatio <= MAX_GOOD_GARBAGE_RATIO &&
      result.best.tokens < 4
    );
  }

  private static List<TrialKey> defaultTrialOrder() {
    List<TrialKey> trials = new ArrayList<>();

    trials.add(new TrialKey(0, 0, "original"));
    trials.add(new TrialKey(0, 1, "grayscale"));
    trials.add(new TrialKey(0, 2, "contrast-1.35"));
    trials.add(new TrialKey(180, 0, "original"));
    trials.add(new TrialKey(180, 1, "grayscale"));
    trials.add(new TrialKey(180, 2, "contrast-1.35"));
    trials.add(new TrialKey(0, 3, "otsu-threshold"));
    trials.add(new TrialKey(0, 4, "otsu-denoised"));
    trials.add(new TrialKey(180, 3, "otsu-threshold"));
    trials.add(new TrialKey(180, 4, "otsu-denoised"));

    return trials;
  }

  private static void increment(Map<Integer, Integer> map, int key) {
    map.put(key, map.getOrDefault(key, 0) + 1);
  }

  private static int mostCommonKey(
    Map<Integer, Integer> map,
    int defaultValue
  ) {
    int bestKey = defaultValue;
    int bestCount = -1;

    for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
      if (entry.getValue() > bestCount) {
        bestKey = entry.getKey();
        bestCount = entry.getValue();
      }
    }

    return bestKey;
  }

  private static double rotationDominance(
    Map<Integer, Integer> wins,
    int rotation,
    int goodResults
  ) {
    if (goodResults == 0) {
      return 0.0;
    }

    return wins.getOrDefault(rotation, 0) / (double) goodResults;
  }

  public static class RegionProfile {

    public final PatternKey key;

    private final Map<Integer, Integer> rotationWins = new HashMap<>();
    private final Map<Integer, Integer> candidateWins = new HashMap<>();
    private final List<Double> recentGoodScores = new ArrayList<>();
    private final Map<TrialKey, TrialStats> trialStats = new HashMap<>();

    private int totalResults;
    private int goodResults;
    private int recentFailures;
    private int stablePatternCount;

    RegionProfile(PatternKey key) {
      this.key = key;
    }

    void learn(OcrResult result) {
      totalResults++;

      if (!isGoodResult(result)) {
        recordFailure(result);
        return;
      }

      goodResults++;
      recentFailures = 0;
      stablePatternCount++;

      increment(rotationWins, result.rotation);
      increment(candidateWins, result.index);

      TrialKey trial = TrialKey.fromCandidate(result.best);
      statsForOrCreate(trial).recordSuccess(result.score, result.confidence);

      recentGoodScores.add(result.score);

      while (recentGoodScores.size() > MAX_RECENT_SCORES) {
        recentGoodScores.remove(0);
      }
    }

    void recordFailure(OcrResult result) {
      totalResults++;
      recentFailures++;
      if (result != null && result.suppressLearning()) {
        stablePatternCount = 0;
      }
      stablePatternCount = Math.max(0, stablePatternCount - 1);

      if (result != null && result.best != null) {
        TrialKey trial = TrialKey.fromCandidate(result.best);
        statsForOrCreate(trial).recordFailure(result.score, result.confidence);
      }
    }

    boolean isTrusted() {
      return (
        goodResults >= MIN_GOOD_RESULTS_FOR_PATTERN &&
        stablePatternCount >= 3 &&
        recentFailures == 0 &&
        hasDominantRotation()
      );
    }

    boolean hasDominantRotation() {
      return goodResults >= 3 && rotationDominance() >= 0.70;
    }

    double rotationDominance() {
      return OcrRunProfile.rotationDominance(
        rotationWins,
        preferredRotationOrDefault(),
        goodResults
      );
    }

    boolean hasUsefulTrialStats() {
      return goodResults >= 3 && hasAtLeastOneSuccessfulTrial();
    }

    boolean hasAtLeastOneSuccessfulTrial() {
      for (TrialStats stats : trialStats.values()) {
        if (stats.getSuccesses() > 0) {
          return true;
        }
      }

      return false;
    }

    TrialStats statsFor(TrialKey trial) {
      return trialStats.get(trial);
    }

    TrialStats statsForOrCreate(TrialKey trial) {
      return trialStats.computeIfAbsent(trial, ignored -> new TrialStats());
    }

    List<TrialKey> rankedTrials() {
      Map<TrialKey, Double> weights = new LinkedHashMap<>();

      for (TrialKey trial : defaultTrialOrder()) {
        TrialStats stats = trialStats.get(trial);
        double weight = baselineWeight(trial);

        if (stats != null) {
          weight += stats.getAdaptiveWeight();
          if (stats.isReliable()) {
            weight += 0.45;
          }
          if (stats.isActivelyBad()) {
            weight -= 0.80;
          }
          if (stats.shouldSuppress()) {
            weight -= 1.20;
          }
          weight -= stats.getPenaltyWeight() * 0.60;
        }

        if (rotationWins.containsKey(trial.rotation)) {
          weight += rotationWins.get(trial.rotation) * 0.35;
        }

        if (candidateWins.containsKey(trial.candidateIndex)) {
          weight += candidateWins.get(trial.candidateIndex) * 0.12;
        }

        if (
          hasDominantRotation() &&
          trial.rotation != preferredRotationOrDefault()
        ) {
          weight -= 0.90;
        }

        weights.put(trial, weight);
      }

      List<TrialKey> ranked = new ArrayList<>(weights.keySet());

      ranked.sort((a, b) -> {
        int weightCompare = Double.compare(weights.get(b), weights.get(a));

        if (weightCompare != 0) {
          return weightCompare;
        }

        int categoryCompare = Boolean.compare(
          a.isAggressive(),
          b.isAggressive()
        );

        if (categoryCompare != 0) {
          return categoryCompare;
        }

        return Integer.compare(
          a.preprocessingPriority(),
          b.preprocessingPriority()
        );
      });

      return ranked;
    }

    int preferredRotationOrDefault() {
      return mostCommonKey(rotationWins, DEFAULT_ROTATION);
    }

    double averageGoodScore() {
      if (recentGoodScores.isEmpty()) {
        return 75.0;
      }

      double sum = 0.0;
      for (double score : recentGoodScores) {
        sum += score;
      }

      return sum / recentGoodScores.size();
    }

    double minimumAcceptableScoreForTrial(
      TrialKey trial,
      boolean fullyTrusted
    ) {
      TrialStats stats = trialStats.get(trial);

      if (stats != null && stats.isStronglyReliable()) {
        return Math.max(0.0, stats.getRecentAverageScore() * 0.70);
      }

      if (stats != null && stats.isReliable()) {
        return Math.max(0.0, stats.getAverageScore() * 0.65);
      }

      double multiplier = fullyTrusted ? 0.65 : 0.50;
      return averageGoodScore() * multiplier;
    }

    String summary() {
      return (
        "trusted=" +
        isTrusted() +
        " preferredRotation=" +
        preferredRotationOrDefault() +
        " avgScore=" +
        String.format("%.2f", averageGoodScore()) +
        " totalResults=" +
        totalResults +
        " goodResults=" +
        goodResults +
        " recentFailures=" +
        recentFailures +
        " rankedTrials=" +
        rankedTrials().stream().limit(3).toList()
      );
    }

    private static double baselineWeight(TrialKey trial) {
      double weight = 0.0;

      if (trial.rotation == DEFAULT_ROTATION) {
        weight += 0.20;
      } else {
        weight -= 0.10;
      }

      if (trial.isSafe()) {
        weight += 0.25;
      }

      weight -= trial.preprocessingPriority() * 0.03;

      if (trial.isAggressive()) {
        weight -= 0.25;
      }

      return weight;
    }
  }
}
