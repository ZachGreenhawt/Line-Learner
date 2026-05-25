package ocr;

import java.util.HashSet;
import java.util.Set;
import ocr.model.OcrCandidate;

public class OcrCandidateScorer {

  private static final Set<String> COMMON_ENGLISH_WORDS = new HashSet<>();
  private static final double HARD_REJECT_SCORE = -1_000_000.0;

  public enum Failure {
    GOOD_ENOUGH,
    BAD_ROTATION,
    PAGE_FURNITURE,
    CLIPPED_TEXT,
    LOW_TEXT,
    GARBAGE_TEXT,
    SPLIT_SPEAKER,
    UNKNOWN,
  }

  static {
    String[] commonWords = {
      "the",
      "and",
      "you",
      "that",
      "with",
      "this",
      "was",
      "for",
      "not",
      "have",
      "love",
      "what",
      "when",
      "where",
      "will",
      "your",
      "are",
      "but",
      "all",
      "she",
      "him",
      "her",
      "his",
      "they",
      "them",
      "there",
      "here",
      "like",
      "from",
      "time",
      "one",
      "two",
      "how",
      "why",
      "who",
      "into",
      "about",
      "would",
      "could",
      "should",
      "remember",
      "morning",
      "father",
      "mother",
      "family",
      "home",
      "life",
      "day",
      "night",
      "good",
      "yes",
      "no",
      "don",
      "can",
      "cant",
      "hello",
      "please",
      "enter",
      "exit",
      "exits",
      "enters",
      "crosses",
      "sits",
      "stands",
      "turns",
      "pause",
      "beat",
      "silence",
      "lights",
      "curtain",
      "act",
      "scene",
      "voice",
      "door",
      "room",
      "stage",
      "offstage",
      "upstage",
      "downstage",
      "left",
      "right",
      "a",
      "an",
      "of",
      "to",
      "in",
      "on",
      "it",
      "is",
      "be",
      "as",
      "or",
      "if",
      "we",
      "me",
      "my",
      "our",
      "their",
      "then",
      "than",
      "out",
      "up",
      "down",
      "little",
      "long",
      "man",
      "woman",
      "girl",
      "boy",
      "hand",
      "hands",
      "eyes",
      "head",
      "house",
      "office",
      "business",
      "company",
      "letter",
      "line",
      "book",
      "play",
      "by",
      "new",
      "york",
      "london",
      "chicago",
      "american",
      "published",
      "copyright",
      "rights",
      "reserved",
      "performance",
      "produced",
      "directed",
    };

    for (String word : commonWords) {
      COMMON_ENGLISH_WORDS.add(word);
    }
  }

  public static OcrCandidate rank(String text, int rotation) {
    if (text == null) {
      text = "";
    }

    double score = 0;

    int speakerLines = 0;
    int dialogue = 0;
    int junk = 0;
    int readableLines = 0;
    int artifactLines = 0;
    int edgeNoiseLines = 0;
    int pageFurnitureLines = 0;
    int clippedLines = 0;
    int stage = 0;
    int splitSpeakerLines = 0;

    String[] lines = text.split("\\R");

    int nonEmptyLines = 0;

    for (String rawLine : lines) {
      String line = rawLine.trim();

      if (line.isEmpty()) {
        continue;
      }

      nonEmptyLines++;

      boolean readable = readable(line);
      boolean speakerLike = speakerHeading(line);
      boolean dialogueLike = dialogue(line);
      boolean artifact = upsideDown(line);
      boolean edgeNoise = edgeNoise(line);
      boolean pageFurniture = furniture(line);
      boolean clippedLine = clipped(line);
      boolean stageDirection = stageDirection(line);
      boolean splitSpeaker = splitSpeaker(line);
      boolean garbage = junk(line) || artifact || edgeNoise;

      if (readable) {
        readableLines++;
        score += 5;
      }

      if (speakerLike) {
        speakerLines++;

        if (readable || english(line) >= 0.20) {
          score += 5;
        } else {
          score -= 10;
        }
      }

      if (dialogueLike) {
        dialogue++;
        score += readable ? 4 : 1;
      }

      if (stageDirection) {
        stage++;
        score += readable ? 3 : 1;
      }

      if (pageFurniture) {
        pageFurnitureLines++;
        score -= 28;
      }

      if (clippedLine) {
        clippedLines++;
        score -= 18;
      }

      if (splitSpeaker) {
        splitSpeakerLines++;
        score -= 10;
      }

      if (artifact) {
        artifactLines++;
        score -= 45;
      }

      if (edgeNoise) {
        edgeNoiseLines++;
        score -= 25;
      }

      if (garbage) {
        junk++;
        score -= 18;
      }
    }

    double englishRatio = english(text);
    double readableLineRatio =
      nonEmptyLines == 0 ? 0.0 : readableLines / (double) nonEmptyLines;

    int words = words(text);
    int tokens = tokens(text);
    double junkRatio = nonEmptyLines == 0 ? 0.0 : junk / (double) nonEmptyLines;
    double artifacts =
      (artifactLines * 45.0) +
      (pageFurnitureLines * 18.0) +
      (clippedLines * 10.0);

    HardReject hardReject = hardReject(
      text,
      nonEmptyLines,
      englishRatio,
      readableLineRatio,
      words,
      tokens,
      junkRatio,
      artifactLines,
      pageFurnitureLines,
      clippedLines,
      splitSpeakerLines
    );

    if (hardReject.rejected) {
      return new OcrCandidate(
        text,
        rotation,
        HARD_REJECT_SCORE + hardReject.penalty,
        speakerLines,
        dialogue,
        junk,
        caps(lines),
        stageLines(lines),
        words,
        tokens,
        artifactLines,
        -1,
        nonEmptyLines,
        hardReject.reason,
        "hard-reject",
        junkRatio,
        englishRatio,
        artifacts,
        readableLineRatio,
        0.0
      );
    }

    score += englishRatio * 520.0;
    score += readableLineRatio * 180.0;
    score += Math.min(words, 90) * 3.0;

    if (
      readableEnough(
        text,
        nonEmptyLines,
        englishRatio,
        readableLineRatio,
        words,
        junkRatio,
        tokens
      )
    ) {
      score += Math.min(dialogue, 40) * 1.5;
      score += Math.min(stage, 20) * 1.2;
    }

    if (speakerLines >= 3 && englishRatio >= 0.08 && words >= 5) {
      score += 12;
    }

    if (speakerLines >= 8 && englishRatio < 0.05) {
      score -= 140;
    }

    if (artifactLines >= 2) {
      score -= artifactLines * 35;
    }

    if (edgeNoiseLines >= 2) {
      score -= edgeNoiseLines * 15;
    }

    if (pageFurnitureLines >= 2) {
      score -= pageFurnitureLines * 20;
    }

    if (clippedLines >= 2) {
      score -= clippedLines * 12;
    }

    if (splitSpeakerLines >= 2) {
      score -= splitSpeakerLines * 8;
    }

    if (text.length() < 80 && dialogue == 0 && speakerLines == 0) {
      score -= 20;
    }

    return new OcrCandidate(
      text,
      rotation,
      score,
      speakerLines,
      dialogue,
      junk,
      caps(lines),
      stageLines(lines),
      words,
      tokens,
      artifactLines,
      -1,
      nonEmptyLines,
      "unknown",
      "full-search",
      junkRatio,
      englishRatio,
      artifacts,
      readableLineRatio,
      confidence(
        score,
        englishRatio,
        readableLineRatio,
        junk,
        artifactLines,
        pageFurnitureLines,
        clippedLines,
        splitSpeakerLines,
        nonEmptyLines
      )
    );
  }

  private static HardReject hardReject(
    String text,
    int nonEmptyLines,
    double englishRatio,
    double readableLineRatio,
    int words,
    int tokens,
    double junkRatio,
    int artifactLines,
    int pageFurnitureLines,
    int clippedLines,
    int splitSpeakerLines
  ) {
    if (text == null || text.isBlank() || nonEmptyLines == 0) {
      return HardReject.reject("hard-reject-low-text", -5000.0);
    }

    int textLength = text.trim().length();
    boolean tinyPage = textLength < 120 || nonEmptyLines <= 3;

    if (tinyPage) {
      if (cleanTitlePage(text)) {
        return HardReject.keep();
      }

      if (words < 2 && englishRatio < 0.04) {
        return HardReject.reject("hard-reject-tiny-unreadable", -4200.0);
      }
    }

    if (junkRatio >= 0.38 && englishRatio < 0.08) {
      return HardReject.reject("hard-reject-garbage-ratio", -3600.0);
    }

    if (tokens >= 5 && englishRatio < 0.10) {
      return HardReject.reject("hard-reject-suspicious-tokens", -3400.0);
    }

    if (words <= 1 && englishRatio < 0.03 && nonEmptyLines >= 3) {
      return HardReject.reject("hard-reject-no-readable-words", -3200.0);
    }

    if (readableLineRatio < 0.08 && englishRatio < 0.06 && nonEmptyLines >= 5) {
      return HardReject.reject("hard-reject-low-language-quality", -3000.0);
    }

    if (mirroredBlock(text) && englishRatio < 0.12) {
      return HardReject.reject("hard-reject-mirrored-block", -3900.0);
    }

    if (splitSpeakerLines >= 3 && englishRatio < 0.05 && words < 4) {
      return HardReject.reject("hard-reject-split-speaker-garbage", -2600.0);
    }

    return HardReject.keep();
  }

  private static boolean readableEnough(
    String text,
    int nonEmptyLines,
    double englishRatio,
    double readableLineRatio,
    int words,
    double junkRatio,
    int tokens
  ) {
    if (text == null || text.isBlank() || nonEmptyLines == 0) {
      return false;
    }

    if (cleanTitlePage(text)) {
      return true;
    }

    return (
      englishRatio >= 0.07 &&
      readableLineRatio >= 0.18 &&
      words >= 4 &&
      junkRatio < 0.30 &&
      tokens < 5
    );
  }

  private static boolean cleanTitlePage(String text) {
    String normalized = text == null ? "" : text.toLowerCase();
    if (normalized.isBlank()) {
      return false;
    }

    int hits = 0;
    if (normalized.contains("a play by")) hits++;
    if (normalized.contains("new york")) hits++;
    if (normalized.contains("london")) hits++;
    if (normalized.contains("published")) hits++;
    if (normalized.contains("copyright")) hits++;
    if (normalized.contains("all rights")) hits++;
    if (normalized.contains("isbn")) hits++;
    if (normalized.contains("directed by")) hits++;
    if (normalized.contains("produced")) hits++;
    if (normalized.contains("performance")) hits++;

    return hits >= 2 && !mirroredBlock(text);
  }

  private static boolean mirroredBlock(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }

    String[] tokens = text.toUpperCase().split("[^A-Z]+");
    int usable = 0;
    int suspicious = 0;

    for (String token : tokens) {
      if (token.length() < 5) {
        continue;
      }

      usable++;
      if (mirroredToken(token)) {
        suspicious++;
      }
    }

    return usable >= 4 && suspicious / (double) usable >= 0.45;
  }

  private static class HardReject {

    final boolean rejected;
    final String reason;
    final double penalty;

    private HardReject(boolean rejected, String reason, double penalty) {
      this.rejected = rejected;
      this.reason = reason;
      this.penalty = penalty;
    }

    static HardReject reject(String reason, double penalty) {
      return new HardReject(true, reason, penalty);
    }

    static HardReject keep() {
      return new HardReject(false, "", 0.0);
    }
  }

  private static boolean speakerHeading(String line) {
    if (line.length() > 35) {
      return false;
    }

    String lettersOnly = line.replaceAll("[^A-Za-z]", "");

    if (lettersOnly.length() < 2) {
      return false;
    }

    if (upsideDown(line)) {
      return false;
    }

    if (furniture(line)) {
      return false;
    }

    return (
      line.matches("^[A-Z0-9 ./'\\-]+$") &&
      line.equals(line.toUpperCase()) &&
      line.matches(".*[A-Z].*") &&
      vowelRatio(lettersOnly) >= 0.22
    );
  }

  private static boolean dialogue(String line) {
    return (
      line.length() > 20 &&
      line.matches(".*[a-z].*") &&
      line.matches(".*[aeiouAEIOU].*") &&
      !upsideDown(line) &&
      !edgeNoise(line) &&
      !furniture(line)
    );
  }

  private static boolean readable(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }

    if (upsideDown(line) || edgeNoise(line) || furniture(line)) {
      return false;
    }

    String lettersOnly = line.replaceAll("[^A-Za-z]", "");

    if (lettersOnly.length() < 8) {
      return false;
    }

    double vowelRatio = vowelRatio(lettersOnly);

    if (vowelRatio < 0.24 || vowelRatio > 0.60) {
      return false;
    }

    if (tooManyRareLetters(lettersOnly)) {
      return false;
    }

    return (english(line) >= 0.08 || line.matches(".*\\b[A-Z][a-z]{2,}\\b.*"));
  }

  private static boolean junk(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }

    if (furniture(line)) {
      return false;
    }

    String compactLetters = line.replaceAll("[^A-Za-z]", "");

    if (compactLetters.length() >= 8 && vowels(compactLetters) == 0) {
      return true;
    }

    if (compactLetters.length() >= 12 && vowelRatio(compactLetters) < 0.18) {
      return true;
    }

    if (compactLetters.length() >= 12 && vowelRatio(compactLetters) > 0.68) {
      return true;
    }

    if (tooManyRareLetters(compactLetters)) {
      return true;
    }

    int weirdChars = 0;

    for (char c : line.toCharArray()) {
      if (
        !Character.isLetterOrDigit(c) &&
        !Character.isWhitespace(c) &&
        ".,!?;:'\"()-[]{}".indexOf(c) == -1
      ) {
        weirdChars++;
      }
    }

    return weirdChars > Math.max(4, line.length() / 8);
  }

  private static boolean furniture(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }

    String t = line.trim();
    String upper = t.toUpperCase();

    if (upper.matches("^\\d{1,4}$")) {
      return true;
    }

    if (upper.matches("^PAGE\\s+\\d{1,4}$")) {
      return true;
    }

    if (
      upper.matches(
        "^[A-Z][A-Z'’\\-]+(?:\\s+[A-Z][A-Z'’\\-]+){0,4}\\s+\\d{1,4}$"
      )
    ) {
      return true;
    }

    if (
      upper.matches(
        "^\\d{1,4}\\s+[A-Z][A-Z'’\\-]+(?:\\s+[A-Z][A-Z'’\\-]+){0,4}$"
      )
    ) {
      return true;
    }

    if (upper.matches(".*\\bISBN\\b.*")) {
      return true;
    }

    if (upper.matches(".*\\bWWW\\..*")) {
      return true;
    }

    if (upper.matches(".*\\.(COM|ORG|NET|CO\\.UK)\\b.*")) {
      return true;
    }

    return false;
  }

  private static boolean clipped(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }

    String t = line.trim();

    if (furniture(t)) {
      return false;
    }

    if (t.length() < 8) {
      return false;
    }

    boolean startsAbruptly =
      t.matches("^[a-z][a-z]+\\b.*") || t.matches("^[,.;:!?)]\\s*.*");

    boolean endsAbruptly =
      t.matches(".*\\b[a-zA-Z]{1,3}[-–—]$|.*\\b[a-zA-Z]{1,2}$") &&
      !t.matches(".*[.!?\")']$.*");

    boolean hasEnoughLetters = t.replaceAll("[^A-Za-z]", "").length() >= 10;

    return hasEnoughLetters && (startsAbruptly || endsAbruptly);
  }

  private static boolean stageDirection(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }

    String t = line.trim();
    String lower = t.toLowerCase();

    if (t.startsWith("(") && t.endsWith(")")) {
      return true;
    }

    return (
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

  private static boolean splitSpeaker(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }

    String t = line.trim();
    if (t.length() < 3 || t.length() > 18) {
      return false;
    }

    String lettersOnly = t.replaceAll("[^A-Za-z]", "");
    if (lettersOnly.length() < 3) {
      return false;
    }

    if (!lettersOnly.equals(lettersOnly.toUpperCase())) {
      return false;
    }

    if (speakerHeading(t)) {
      return false;
    }

    return (
      vowelRatio(lettersOnly) < 0.20 ||
      lettersOnly.matches(".*[BCDFGHJKLMNPQRSTVWXYZ]{4,}.*")
    );
  }

  private static boolean upsideDown(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }

    String upper = line.toUpperCase();
    String[] tokens = upper.split("[^A-Z]+");

    int suspiciousTokens = 0;
    int usableTokens = 0;

    for (String token : tokens) {
      if (token.length() < 5) {
        continue;
      }

      usableTokens++;

      if (mirroredToken(token)) {
        suspiciousTokens++;
      }
    }

    if (usableTokens == 0) {
      return false;
    }

    double suspiciousRatio = suspiciousTokens / (double) usableTokens;

    return (
      suspiciousTokens >= 2 || (usableTokens >= 3 && suspiciousRatio >= 0.55)
    );
  }

  private static boolean mirroredToken(String token) {
    if (token == null || token.length() < 5) {
      return false;
    }

    double vowelRatio = vowelRatio(token);

    int mirrorBigramHits = mirrorBigrams(token);
    int awkwardEndingHits = awkwardEndings(token);
    boolean hasLongConsonantCluster = consonantCluster(token, 4);
    boolean tooFewVowels = vowelRatio < 0.24;
    boolean tooManyRareLetters = tooManyRareLetters(token);
    boolean alternatingLooksWrong = unnaturalAlternation(token);

    int suspicion = 0;

    if (mirrorBigramHits >= 2) {
      suspicion += 2;
    } else if (mirrorBigramHits == 1) {
      suspicion++;
    }

    if (awkwardEndingHits >= 1) {
      suspicion++;
    }

    if (hasLongConsonantCluster) {
      suspicion++;
    }

    if (tooFewVowels) {
      suspicion++;
    }

    if (tooManyRareLetters) {
      suspicion++;
    }

    if (alternatingLooksWrong) {
      suspicion++;
    }

    return suspicion >= 3;
  }

  private static int mirrorBigrams(String token) {
    String[] suspiciousBigrams = {
      "HN",
      "NH",
      "HT",
      "TH",
      "HL",
      "LH",
      "LN",
      "NL",
      "WV",
      "VW",
      "WY",
      "YW",
      "YY",
      "OO",
      "UU",
      "II",
      "AI",
      "IA",
      "OI",
      "IO",
      "TO",
      "OT",
      "TN",
      "NT",
    };

    int hits = 0;

    for (String bigram : suspiciousBigrams) {
      if (token.contains(bigram)) {
        hits++;
      }
    }

    return hits;
  }

  private static int awkwardEndings(String token) {
    String[] awkwardEndings = {
      "OI",
      "OA",
      "OO",
      "UO",
      "WY",
      "WV",
      "HN",
      "NN",
      "YS",
      "VS",
    };

    int hits = 0;

    for (String ending : awkwardEndings) {
      if (token.endsWith(ending)) {
        hits++;
      }
    }

    return hits;
  }

  private static boolean unnaturalAlternation(String token) {
    if (token.length() < 7) {
      return false;
    }

    int sharpDirectionChanges = 0;

    for (int i = 2; i < token.length(); i++) {
      char a = token.charAt(i - 2);
      char b = token.charAt(i - 1);
      char c = token.charAt(i);

      boolean firstPairRare = rareTransition(a, b);
      boolean secondPairRare = rareTransition(b, c);

      if (firstPairRare && secondPairRare) {
        sharpDirectionChanges++;
      }
    }

    return sharpDirectionChanges >= 2;
  }

  private static boolean rareTransition(char first, char second) {
    String pair = "" + first + second;

    return (
      pair.equals("HN") ||
      pair.equals("NH") ||
      pair.equals("HL") ||
      pair.equals("LH") ||
      pair.equals("WV") ||
      pair.equals("VW") ||
      pair.equals("WY") ||
      pair.equals("YW") ||
      pair.equals("UO") ||
      pair.equals("OI")
    );
  }

  private static boolean edgeNoise(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }

    String compact = line.replaceAll("\\s+", "");

    if (compact.length() < 8) {
      return false;
    }

    int symbolCount = 0;
    int letterCount = 0;

    for (int i = 0; i < compact.length(); i++) {
      char c = compact.charAt(i);

      if (Character.isLetter(c)) {
        letterCount++;
      } else if (!Character.isDigit(c)) {
        symbolCount++;
      }
    }

    if (symbolCount >= 5 && symbolCount > letterCount / 2) {
      return true;
    }

    String upper = compact.toUpperCase();

    return (
      upper.contains("EEEE") ||
      upper.contains("TEE") ||
      upper.contains("EET") ||
      upper.matches(".*[-_=]{4,}.*") ||
      upper.matches(".*[A-Z]{1,3}[-_=]{2,}[A-Z]{1,3}.*")
    );
  }

  private static double english(String text) {
    if (text == null || text.isBlank()) {
      return 0.0;
    }

    String[] tokens = text
      .toLowerCase()
      .replaceAll("[^a-z']", " ")
      .split("\\s+");

    int total = 0;
    int common = 0;

    for (String token : tokens) {
      token = token.replace("'", "");

      if (token.length() < 2) {
        continue;
      }

      total++;

      if (COMMON_ENGLISH_WORDS.contains(token)) {
        common++;
      }
    }

    if (total == 0) {
      return 0.0;
    }

    return common / (double) total;
  }

  private static boolean consonantCluster(String text, int length) {
    int run = 0;

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);

      if (Character.isLetter(c) && !vowel(c)) {
        run++;

        if (run >= length) {
          return true;
        }
      } else {
        run = 0;
      }
    }

    return false;
  }

  private static int vowels(String text) {
    int count = 0;

    for (int i = 0; i < text.length(); i++) {
      if (vowel(text.charAt(i))) {
        count++;
      }
    }

    return count;
  }

  private static double vowelRatio(String text) {
    if (text == null || text.isBlank()) {
      return 0.0;
    }

    return vowels(text) / (double) text.length();
  }

  private static boolean vowel(char c) {
    char lower = Character.toLowerCase(c);

    return (
      lower == 'a' ||
      lower == 'e' ||
      lower == 'i' ||
      lower == 'o' ||
      lower == 'u'
    );
  }

  private static boolean tooManyRareLetters(String text) {
    if (text == null || text.length() < 10) {
      return false;
    }

    int rare = 0;

    for (int i = 0; i < text.length(); i++) {
      char c = Character.toLowerCase(text.charAt(i));

      if (c == 'q' || c == 'x' || c == 'z' || c == 'j') {
        rare++;
      }
    }

    return rare >= 3;
  }

  private static int caps(String[] lines) {
    if (lines == null) {
      return 0;
    }

    int count = 0;

    for (String rawLine : lines) {
      String line = rawLine == null ? "" : rawLine.trim();

      if (line.length() < 2) {
        continue;
      }

      String lettersOnly = line.replaceAll("[^A-Za-z]", "");

      if (
        lettersOnly.length() >= 2 &&
        lettersOnly.equals(lettersOnly.toUpperCase())
      ) {
        count++;
      }
    }

    return count;
  }

  private static int stageLines(String[] lines) {
    if (lines == null) {
      return 0;
    }

    int count = 0;

    for (String rawLine : lines) {
      String line = rawLine == null ? "" : rawLine.trim();
      String lower = line.toLowerCase();

      if (line.isEmpty()) {
        continue;
      }

      if (
        line.startsWith("(") ||
        line.endsWith(")") ||
        lower.matches(
          ".*\\b(enters|exits|crosses|sits|stands|looks|nods|shakes|pause|beat|silence)\\b.*"
        )
      ) {
        count++;
      }
    }

    return count;
  }

  private static int words(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }

    String[] tokens = text
      .toLowerCase()
      .replaceAll("[^a-z']", " ")
      .split("\\s+");

    int count = 0;

    for (String token : tokens) {
      token = token.replace("'", "");

      if (token.length() >= 2 && COMMON_ENGLISH_WORDS.contains(token)) {
        count++;
      }
    }

    return count;
  }

  private static int tokens(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }

    String[] tokens = text.toUpperCase().split("[^A-Z]+");
    int count = 0;

    for (String token : tokens) {
      if (mirroredToken(token)) {
        count++;
      }
    }

    return count;
  }

  private static double confidence(
    double score,
    double englishRatio,
    double readableLineRatio,
    int junk,
    int artifactLines,
    int pageFurnitureLines,
    int clippedLines,
    int splitSpeakerLines,
    int lines
  ) {
    double confidence = 0.0;

    confidence += Math.min(0.45, Math.max(0.0, englishRatio * 2.0));
    confidence += Math.min(0.35, Math.max(0.0, readableLineRatio * 0.7));

    if (score > 100) {
      confidence += 0.15;
    } else if (score > 50) {
      confidence += 0.08;
    }

    if (lines > 0) {
      double badRatio = (junk + artifactLines) / (double) lines;
      double furnitureRatio = pageFurnitureLines / (double) lines;
      double clippedRatio = clippedLines / (double) lines;
      double splitSpeakerRatio = splitSpeakerLines / (double) lines;

      confidence -= Math.min(0.45, badRatio * 0.8);
      confidence -= Math.min(0.20, furnitureRatio * 0.35);
      confidence -= Math.min(0.20, clippedRatio * 0.40);
      confidence -= Math.min(0.15, splitSpeakerRatio * 0.35);
    }

    return Math.max(0.0, Math.min(1.0, confidence));
  }

  public static Failure failure(OcrCandidate candidate) {
    if (candidate == null) {
      return Failure.UNKNOWN;
    }

    if (
      candidate.lines == 0 || candidate.text == null || candidate.text.isBlank()
    ) {
      return Failure.LOW_TEXT;
    }
    if (candidate.score <= HARD_REJECT_SCORE / 2.0) {
      return Failure.GARBAGE_TEXT;
    }

    if (
      candidate.confidence >= 0.82 &&
      candidate.junkRatio <= 0.10 &&
      candidate.englishRatio >= 0.10 &&
      candidate.words >= 5
    ) {
      return Failure.GOOD_ENOUGH;
    }

    if (
      candidate.confidence < 0.35 &&
      (candidate.junkRatio >= 0.25 || candidate.tokens >= 5)
    ) {
      return Failure.BAD_ROTATION;
    }

    if (candidate.artifacts >= 80.0 && candidate.junkRatio < 0.30) {
      return Failure.PAGE_FURNITURE;
    }

    if (candidate.words <= 3 && candidate.lines >= 4) {
      return Failure.CLIPPED_TEXT;
    }

    if (
      candidate.speakerLines >= 2 &&
      candidate.dialogue == 0 &&
      candidate.englishRatio < 0.08
    ) {
      return Failure.SPLIT_SPEAKER;
    }

    if (candidate.junkRatio >= 0.35 || candidate.tokens >= 6) {
      return Failure.GARBAGE_TEXT;
    }

    return Failure.UNKNOWN;
  }
}
