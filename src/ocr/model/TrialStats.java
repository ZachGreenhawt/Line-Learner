import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TrialStats {

  private static final double RECENT_DECAY = 0.82;
  private static final double MAX_RECENT_WEIGHT = 12.0;

  private final AtomicInteger attempts = new AtomicInteger();
  private final AtomicInteger successes = new AtomicInteger();
  private final AtomicInteger failures = new AtomicInteger();

  private final AtomicLong cumulativeScore = new AtomicLong();
  private final AtomicLong cumulativeConfidence = new AtomicLong();

  private double recentSuccessWeight = 0.0;
  private double recentFailureWeight = 0.0;
  private double recentScoreWeight = 0.0;
  private double recentConfidenceWeight = 0.0;
  private double recentObservationWeight = 0.0;

  private int currentSuccessStreak = 0;
  private int currentFailureStreak = 0;
  private int bestSuccessStreak = 0;
  private int bestFailureStreak = 0;

  public synchronized void recordSuccess(double score, double confidence) {
    attempts.incrementAndGet();
    successes.incrementAndGet();
    cumulativeScore.addAndGet(scale(score));
    cumulativeConfidence.addAndGet(scale(confidence));

    decayRecentMemory();
    recentSuccessWeight += 1.0;
    recentScoreWeight += normalizeScore(score);
    recentConfidenceWeight += clamp01(confidence);
    recentObservationWeight += 1.0;
    capRecentMemory();

    currentSuccessStreak++;
    currentFailureStreak = 0;
    bestSuccessStreak = Math.max(bestSuccessStreak, currentSuccessStreak);
  }

  public synchronized void recordFailure(double score, double confidence) {
    attempts.incrementAndGet();
    failures.incrementAndGet();
    cumulativeScore.addAndGet(scale(score));
    cumulativeConfidence.addAndGet(scale(confidence));

    decayRecentMemory();
    recentFailureWeight += 1.0;
    recentScoreWeight += normalizeScore(score);
    recentConfidenceWeight += clamp01(confidence);
    recentObservationWeight += 1.0;
    capRecentMemory();

    currentFailureStreak++;
    currentSuccessStreak = 0;
    bestFailureStreak = Math.max(bestFailureStreak, currentFailureStreak);
  }

  public int getAttempts() {
    return attempts.get();
  }

  public int getSuccesses() {
    return successes.get();
  }

  public int getFailures() {
    return failures.get();
  }

  public int getCurrentSuccessStreak() {
    return currentSuccessStreak;
  }

  public int getCurrentFailureStreak() {
    return currentFailureStreak;
  }

  public int getBestSuccessStreak() {
    return bestSuccessStreak;
  }

  public int getBestFailureStreak() {
    return bestFailureStreak;
  }

  public double getSuccessRate() {
    int total = attempts.get();
    if (total == 0) {
      return 0.0;
    }
    return (double) successes.get() / total;
  }

  public double getFailureRate() {
    int total = attempts.get();
    if (total == 0) {
      return 0.0;
    }
    return (double) failures.get() / total;
  }

  public synchronized double getRecentSuccessRate() {
    double total = recentSuccessWeight + recentFailureWeight;
    if (total <= 0.0) {
      return getSuccessRate();
    }
    return recentSuccessWeight / total;
  }

  public synchronized double getRecentFailureRate() {
    double total = recentSuccessWeight + recentFailureWeight;
    if (total <= 0.0) {
      return getFailureRate();
    }
    return recentFailureWeight / total;
  }

  public double getAverageScore() {
    int total = attempts.get();
    if (total == 0) {
      return 0.0;
    }
    return descale(cumulativeScore.get(), total);
  }

  public double getAverageConfidence() {
    int total = attempts.get();
    if (total == 0) {
      return 0.0;
    }
    return descale(cumulativeConfidence.get(), total);
  }

  public synchronized double getRecentAverageScore() {
    if (recentObservationWeight <= 0.0) {
      return normalizeScore(getAverageScore());
    }
    return recentScoreWeight / recentObservationWeight;
  }

  public synchronized double getRecentAverageConfidence() {
    if (recentObservationWeight <= 0.0) {
      return getAverageConfidence();
    }
    return recentConfidenceWeight / recentObservationWeight;
  }

  public synchronized double getAdaptiveWeight() {
    double lifetimeSuccessRate = getSuccessRate();
    double recentSuccessRate = getRecentSuccessRate();
    double lifetimeConfidence = getAverageConfidence();
    double recentConfidence = getRecentAverageConfidence();
    double lifetimeScore = normalizeScore(getAverageScore());
    double recentScore = getRecentAverageScore();

    double attemptBonus = Math.min(1.0, getAttempts() / 25.0);
    double streakBonus = Math.min(0.12, currentSuccessStreak * 0.03);
    double failurePenalty = Math.min(0.25, currentFailureStreak * 0.06);

    double weight =
      (recentSuccessRate * 0.34) +
      (lifetimeSuccessRate * 0.22) +
      (recentConfidence * 0.16) +
      (lifetimeConfidence * 0.10) +
      (recentScore * 0.08) +
      (lifetimeScore * 0.05) +
      (attemptBonus * 0.05) +
      streakBonus -
      failurePenalty;

    return clamp01(weight);
  }

  public synchronized boolean isReliable() {
    if (getAttempts() < 3) {
      return false;
    }

    return (
      getSuccessRate() >= 0.65 &&
      getRecentSuccessRate() >= 0.70 &&
      getRecentAverageConfidence() >= 0.45 &&
      currentFailureStreak == 0
    );
  }

  public synchronized boolean isStronglyReliable() {
    if (getAttempts() < 4) {
      return false;
    }

    return (
      getSuccessRate() >= 0.75 &&
      getRecentSuccessRate() >= 0.80 &&
      getRecentAverageConfidence() >= 0.55 &&
      currentSuccessStreak >= 2
    );
  }

  public synchronized boolean isActivelyBad() {
    if (getAttempts() < 4) {
      return false;
    }

    return (
      getFailureRate() >= 0.75 ||
      getRecentFailureRate() >= 0.80 ||
      currentFailureStreak >= 3
    );
  }

  public synchronized boolean shouldSuppress() {
    return (
      getAttempts() >= 5 &&
      getRecentFailureRate() >= 0.75 &&
      getRecentAverageConfidence() < 0.45
    );
  }

  public synchronized double getPenaltyWeight() {
    if (!isActivelyBad()) {
      return 0.0;
    }

    double recentPenalty = getRecentFailureRate() * 0.65;
    double lifetimePenalty = getFailureRate() * 0.25;
    double streakPenalty = Math.min(0.25, currentFailureStreak * 0.08);

    return clamp01(recentPenalty + lifetimePenalty + streakPenalty);
  }

  private void decayRecentMemory() {
    recentSuccessWeight *= RECENT_DECAY;
    recentFailureWeight *= RECENT_DECAY;
    recentScoreWeight *= RECENT_DECAY;
    recentConfidenceWeight *= RECENT_DECAY;
    recentObservationWeight *= RECENT_DECAY;
  }

  private void capRecentMemory() {
    if (recentObservationWeight <= MAX_RECENT_WEIGHT) {
      return;
    }

    double scale = MAX_RECENT_WEIGHT / recentObservationWeight;
    recentSuccessWeight *= scale;
    recentFailureWeight *= scale;
    recentScoreWeight *= scale;
    recentConfidenceWeight *= scale;
    recentObservationWeight *= scale;
  }

  private long scale(double value) {
    return Math.round(value * 100000.0);
  }

  private double descale(long value, int divisor) {
    return (value / 100000.0) / divisor;
  }

  private double normalizeScore(double score) {
    return Math.max(0.0, Math.min(1.0, (score + 500.0) / 1000.0));
  }

  private double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  @Override
  public synchronized String toString() {
    return (
      "TrialStats{" +
      "attempts=" +
      attempts +
      ", successes=" +
      successes +
      ", failures=" +
      failures +
      ", successRate=" +
      getSuccessRate() +
      ", recentSuccessRate=" +
      getRecentSuccessRate() +
      ", failureRate=" +
      getFailureRate() +
      ", recentFailureRate=" +
      getRecentFailureRate() +
      ", avgScore=" +
      getAverageScore() +
      ", recentAvgScore=" +
      getRecentAverageScore() +
      ", avgConfidence=" +
      getAverageConfidence() +
      ", recentAvgConfidence=" +
      getRecentAverageConfidence() +
      ", currentSuccessStreak=" +
      currentSuccessStreak +
      ", currentFailureStreak=" +
      currentFailureStreak +
      ", adaptiveWeight=" +
      getAdaptiveWeight() +
      '}'
    );
  }
}
