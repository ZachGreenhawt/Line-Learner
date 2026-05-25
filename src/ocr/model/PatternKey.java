package ocr.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PatternKey {

  public enum Parity {
    ODD,
    EVEN,
    UNKNOWN,
  }

  public enum Mode {
    UNKNOWN,
    FRONT_MATTER,
    TITLE_OR_CAST,
    PROSE,
    DIALOGUE,
    STAGE_HEAVY,
    MIXED,
  }

  public enum Band {
    EARLY,
    BODY,
    LATE,
    UNKNOWN,
  }

  public final int region;
  public final Parity parity;
  public final Mode mode;
  public final Band band;
  public final boolean regionSpecific;
  public final boolean paritySpecific;
  public final boolean modeSpecific;
  public final boolean bandSpecific;

  private PatternKey(
    int region,
    Parity parity,
    Mode mode,
    Band band,
    boolean regionSpecific,
    boolean paritySpecific,
    boolean modeSpecific,
    boolean bandSpecific
  ) {
    this.region = region;
    this.parity = parity == null ? Parity.UNKNOWN : parity;
    this.mode = mode == null ? Mode.UNKNOWN : mode;
    this.band = band == null ? Band.UNKNOWN : band;
    this.regionSpecific = regionSpecific;
    this.paritySpecific = paritySpecific;
    this.modeSpecific = modeSpecific;
    this.bandSpecific = bandSpecific;
  }

  public static PatternKey exact(int page, int region, Mode mode) {
    return new PatternKey(
      region,
      parity(page),
      mode,
      band(page),
      true,
      true,
      mode != null && mode != Mode.UNKNOWN,
      page > 0
    );
  }

  public static PatternKey regionAndParity(int page, int region) {
    return new PatternKey(
      region,
      parity(page),
      Mode.UNKNOWN,
      band(page),
      true,
      true,
      false,
      page > 0
    );
  }

  public static PatternKey regionOnly(int region) {
    return new PatternKey(
      region,
      Parity.UNKNOWN,
      Mode.UNKNOWN,
      Band.UNKNOWN,
      true,
      false,
      false,
      false
    );
  }

  public static PatternKey modeOnly(Mode mode) {
    return new PatternKey(
      -1,
      Parity.UNKNOWN,
      mode,
      Band.UNKNOWN,
      false,
      false,
      mode != null && mode != Mode.UNKNOWN,
      false
    );
  }

  public static PatternKey bandOnly(int page) {
    return new PatternKey(
      -1,
      Parity.UNKNOWN,
      Mode.UNKNOWN,
      band(page),
      false,
      false,
      false,
      page > 0
    );
  }

  public static PatternKey global() {
    return new PatternKey(
      -1,
      Parity.UNKNOWN,
      Mode.UNKNOWN,
      Band.UNKNOWN,
      false,
      false,
      false,
      false
    );
  }

  public static Parity parity(int page) {
    if (page <= 0) {
      return Parity.UNKNOWN;
    }

    return page % 2 == 0 ? Parity.EVEN : Parity.ODD;
  }

  public static Band band(int page) {
    if (page <= 0) {
      return Band.UNKNOWN;
    }

    if (page <= 8) {
      return Band.EARLY;
    }

    if (page >= 70) {
      return Band.LATE;
    }

    return Band.BODY;
  }

  public static Mode infer(String text) {
    if (text == null || text.isBlank()) {
      return Mode.UNKNOWN;
    }

    String lower = text.toLowerCase();
    String upper = text.toUpperCase();

    int speakers = 0;
    int prose = 0;
    int stage = 0;
    int caps = 0;
    int lines = 0;

    for (String raw : text.split("\\R")) {
      String line = raw == null ? "" : raw.trim();

      if (line.isEmpty()) {
        continue;
      }

      lines++;

      boolean shortCaps =
        line.length() <= 35 &&
        line.matches(".*[A-Z].*") &&
        line.equals(line.toUpperCase());

      if (shortCaps && line.matches("^[A-Z0-9 ./'\\-]+$")) {
        caps++;
      }

      if (shortCaps) {
        speakers++;
      }

      boolean proseLine =
        line.length() >= 60 &&
        line.matches(".*[a-z].*") &&
        line.matches(
          ".*\\b(the|and|that|with|this|was|for|you|she|he|they)\\b.*"
        );

      if (proseLine) {
        prose++;
      }

      if (stageLine(line)) {
        stage++;
      }
    }

    if (
      lower.contains("characters") ||
      lower.contains("dramatis personae") ||
      lower.contains("cast")
    ) {
      return Mode.TITLE_OR_CAST;
    }

    if (
      lower.contains("preface") ||
      lower.contains("copyright") ||
      lower.contains("published") ||
      lower.contains("all rights reserved") ||
      lower.contains("isbn")
    ) {
      return Mode.FRONT_MATTER;
    }

    if (speakers >= 4 && prose >= 2) {
      return Mode.MIXED;
    }

    if (stage >= 4 && speakers >= 2) {
      return Mode.STAGE_HEAVY;
    }

    if (speakers >= 4 || caps >= 6) {
      return Mode.DIALOGUE;
    }

    if (stage >= 5) {
      return Mode.STAGE_HEAVY;
    }

    if (prose >= 3 || lower.length() > 600) {
      return Mode.PROSE;
    }

    if (lines <= 5 && upper.contains("PLAY")) {
      return Mode.TITLE_OR_CAST;
    }

    return Mode.UNKNOWN;
  }

  private static boolean stageLine(String line) {
    String t = line == null ? "" : line.trim();
    if (t.isEmpty()) {
      return false;
    }

    if (t.startsWith("(") && t.endsWith(")")) {
      return true;
    }

    String lower = t.toLowerCase();
    return (
      lower.startsWith("enter ") ||
      lower.startsWith("exit ") ||
      lower.startsWith("exeunt") ||
      lower.contains(" enters") ||
      lower.contains(" exits") ||
      lower.contains(" crosses") ||
      lower.contains(" sits") ||
      lower.contains(" stands") ||
      lower.contains(" turns") ||
      lower.contains(" pauses") ||
      lower.contains(" blackout") ||
      lower.contains("lights up") ||
      lower.contains("lights down") ||
      lower.contains("at rise") ||
      lower.contains("curtain")
    );
  }

  public List<PatternKey> broaderKeys() {
    List<PatternKey> keys = new ArrayList<>();

    if (regionSpecific && paritySpecific && modeSpecific && bandSpecific) {
      keys.add(
        new PatternKey(
          region,
          parity,
          mode,
          Band.UNKNOWN,
          true,
          true,
          true,
          false
        )
      );
    }

    if (regionSpecific && paritySpecific && bandSpecific) {
      keys.add(
        new PatternKey(
          region,
          parity,
          Mode.UNKNOWN,
          band,
          true,
          true,
          false,
          true
        )
      );
    }

    if (regionSpecific && paritySpecific) {
      keys.add(
        new PatternKey(
          region,
          parity,
          Mode.UNKNOWN,
          Band.UNKNOWN,
          true,
          true,
          false,
          false
        )
      );
    }

    if (regionSpecific && modeSpecific) {
      keys.add(
        new PatternKey(
          region,
          Parity.UNKNOWN,
          mode,
          Band.UNKNOWN,
          true,
          false,
          true,
          false
        )
      );
    }

    if (regionSpecific) {
      keys.add(regionOnly(region));
    }

    if (modeSpecific) {
      keys.add(modeOnly(mode));
    }

    if (bandSpecific) {
      keys.add(
        new PatternKey(
          -1,
          Parity.UNKNOWN,
          Mode.UNKNOWN,
          band,
          false,
          false,
          false,
          true
        )
      );
    }

    keys.add(global());

    return keys;
  }

  public boolean isGlobal() {
    return !regionSpecific && !paritySpecific && !modeSpecific && !bandSpecific;
  }

  public String summary() {
    return String.format(
      "PatternKey{region=%s, parity=%s, mode=%s, band=%s}",
      regionSpecific ? String.valueOf(region) : "ANY",
      paritySpecific ? parity.name() : "ANY",
      modeSpecific ? mode.name() : "ANY",
      bandSpecific ? band.name() : "ANY"
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

    if (!(other instanceof PatternKey)) {
      return false;
    }

    PatternKey that = (PatternKey) other;

    return (
      region == that.region &&
      regionSpecific == that.regionSpecific &&
      paritySpecific == that.paritySpecific &&
      modeSpecific == that.modeSpecific &&
      bandSpecific == that.bandSpecific &&
      parity == that.parity &&
      mode == that.mode &&
      band == that.band
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      region,
      parity,
      mode,
      band,
      regionSpecific,
      paritySpecific,
      modeSpecific,
      bandSpecific
    );
  }
}
