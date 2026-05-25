package parser.detect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import util.TextNormalizer;

public class SpeakerHeadingIndex {

  public enum HeadingConfidence {
    HIGH,
    MEDIUM,
    LOW,
  }

  public static class HeadingRecord {

    public final int lineIndex;
    public final String rawLine;
    public final String matchedAlias;
    public final String canonicalSpeaker;
    public final String remainingText;
    public final HeadingConfidence confidence;
    public final boolean inlineDialogue;
    public final boolean parentheticalHeading;

    public HeadingRecord(
      int lineIndex,
      String rawLine,
      String matchedAlias,
      String canonicalSpeaker,
      String remainingText,
      HeadingConfidence confidence,
      boolean inlineDialogue,
      boolean parentheticalHeading
    ) {
      this.lineIndex = lineIndex;
      this.rawLine = rawLine == null ? "" : rawLine;
      this.matchedAlias = matchedAlias == null ? "" : matchedAlias;
      this.canonicalSpeaker = canonicalSpeaker == null ? "" : canonicalSpeaker;
      this.remainingText = remainingText == null ? "" : remainingText;
      this.confidence = confidence == null ? HeadingConfidence.LOW : confidence;
      this.inlineDialogue = inlineDialogue;
      this.parentheticalHeading = parentheticalHeading;
    }

    public boolean hasDialogue() {
      return !TextNormalizer.norm(remainingText).isEmpty();
    }
  }

  public static Map<Integer, HeadingRecord> build(
    List<String> lines,
    Set<String> chars,
    Map<String, String> aliases
  ) {
    Map<Integer, HeadingRecord> index = new LinkedHashMap<>();

    if (lines == null || lines.isEmpty()) {
      return index;
    }

    List<String> names = buildNameList(chars, aliases);

    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      HeadingRecord record = detect(i, line, names, aliases, chars);
      if (record != null) {
        index.put(i, record);
      }
    }

    return index;
  }

  public static HeadingRecord detect(
    int lineIndex,
    String rawLine,
    List<String> names,
    Map<String, String> aliases
  ) {
    return detect(lineIndex, rawLine, names, aliases, null);
  }

  private static HeadingRecord detect(
    int lineIndex,
    String rawLine,
    List<String> names,
    Map<String, String> aliases,
    Set<String> chars
  ) {
    String line = rawLine == null ? "" : rawLine.trim();
    if (line.isEmpty()) {
      return null;
    }
    if (FrontMatterDetector.blockHeading(line, chars)) {
      return null;
    }

    if (looksLikeStandaloneFurnitureHeading(line) && !knownBare(line, names)) {
      return null;
    }

    HeadingRecord slash = slashHeading(lineIndex, line, names, aliases);
    if (slash != null) {
      return slash;
    }

    for (String candidate : names) {
      String canonical = canonicalName(candidate, aliases);
      if (shouldRejectCandidateHeading(line, candidate, chars)) {
        continue;
      }

      HeadingRecord exact = exactHeading(lineIndex, line, candidate, canonical);
      if (exact != null) {
        return exact;
      }

      HeadingRecord parenthetical = parentheticalHeading(
        lineIndex,
        line,
        candidate,
        canonical
      );
      if (parenthetical != null) {
        return parenthetical;
      }

      HeadingRecord inline = inlineDialogueHeading(
        lineIndex,
        line,
        candidate,
        canonical
      );
      if (inline != null) {
        return inline;
      }

      HeadingRecord missingPunctuation = missingPunctuationHeading(
        lineIndex,
        line,
        candidate,
        canonical
      );
      if (missingPunctuation != null) {
        return missingPunctuation;
      }
    }

    return null;
  }

  private static boolean knownBare(String line, List<String> names) {
    String name = TextNormalizer.cleanName(line);
    return !name.isEmpty() && names != null && names.contains(name);
  }

  private static HeadingRecord slashHeading(
    int lineIndex,
    String line,
    List<String> names,
    Map<String, String> aliases
  ) {
    String normalized = TextNormalizer.norm(line);
    if (
      FrontMatterDetector.blockHeading(normalized, null) ||
      looksLikeStandaloneFurnitureHeading(normalized)
    ) {
      return null;
    }
    if (!normalized.contains("/")) {
      return null;
    }

    int cut = firstHeadingPunctuationIndex(normalized);
    String heading =
      cut >= 0 ? normalized.substring(0, cut).trim() : normalized.trim();
    String remaining = cut >= 0 ? normalized.substring(cut + 1).trim() : "";

    if (cut < 0) {
      int paren = firstParentheticalIndex(normalized);
      if (paren > 0) {
        heading = normalized.substring(0, paren).trim();
        remaining = normalized.substring(paren).trim();
      }
    }

    if (!heading.contains("/")) {
      return null;
    }

    String[] pieces = heading.split("/");
    List<String> cleanPieces = new ArrayList<>();

    for (String piece : pieces) {
      String cleaned = TextNormalizer.cleanName(piece);
      if (cleaned.isEmpty() || !names.contains(cleaned)) {
        return null;
      }
      cleanPieces.add(cleaned);
    }

    if (cleanPieces.size() < 2) {
      return null;
    }

    remaining = cleanRemainingAfterHeading(remaining);

    String comboAlias = String.join(" / ", cleanPieces);
    String comboCanonical = canonicalSlashCombo(cleanPieces, aliases);

    return new HeadingRecord(
      lineIndex,
      line,
      comboAlias,
      comboCanonical,
      remaining,
      HeadingConfidence.HIGH,
      !remaining.isEmpty(),
      false
    );
  }

  private static int firstHeadingPunctuationIndex(String line) {
    int dot = line.indexOf('.');
    int colon = line.indexOf(':');

    if (dot < 0) {
      return colon;
    }
    if (colon < 0) {
      return dot;
    }
    return Math.min(dot, colon);
  }

  private static int firstParentheticalIndex(String line) {
    int best = -1;
    for (char ch : new char[] { '(', '[', '{' }) {
      int index = line.indexOf(ch);
      if (index >= 0 && (best < 0 || index < best)) {
        best = index;
      }
    }
    return best;
  }

  private static String canonicalSlashCombo(
    List<String> cleanPieces,
    Map<String, String> aliases
  ) {
    List<String> canonicalPieces = new ArrayList<>();
    for (String piece : cleanPieces) {
      String canonical = canonicalName(piece, aliases);
      if (!canonical.isEmpty() && !canonicalPieces.contains(canonical)) {
        canonicalPieces.add(canonical);
      }
    }
    return String.join(" / ", canonicalPieces);
  }

  private static HeadingRecord exactHeading(
    int lineIndex,
    String line,
    String alias,
    String canonical
  ) {
    String upper = line.toUpperCase();
    String aliasUpper = alias.toUpperCase();

    if (
      upper.equals(aliasUpper) ||
      upper.equals(aliasUpper + ".") ||
      upper.equals(aliasUpper + ":")
    ) {
      return new HeadingRecord(
        lineIndex,
        line,
        alias,
        canonical,
        "",
        HeadingConfidence.HIGH,
        false,
        false
      );
    }

    return null;
  }

  private static HeadingRecord parentheticalHeading(
    int lineIndex,
    String line,
    String alias,
    String canonical
  ) {
    String upper = line.toUpperCase();
    String aliasUpper = alias.toUpperCase();

    if (!upper.startsWith(aliasUpper)) {
      return null;
    }

    if (line.length() <= alias.length()) {
      return null;
    }

    String afterAlias = line.substring(alias.length()).trim();
    if (!safeBoundaryAfterAlias(line, alias.length())) {
      return null;
    }
    if (
      !(afterAlias.startsWith("(") ||
        afterAlias.startsWith("[") ||
        afterAlias.startsWith("{"))
    ) {
      return null;
    }

    String after = stripLeadingParentheticals(afterAlias);
    boolean inlineDialogue =
      !after.isEmpty() && !after.equals(".") && !after.equals(":");
    after = cleanRemainingAfterHeading(after);

    if (!after.isEmpty() && looksLikeStageOnlyContinuation(after)) {
      after = "";
      inlineDialogue = false;
    }

    return new HeadingRecord(
      lineIndex,
      line,
      alias,
      canonical,
      after,
      HeadingConfidence.HIGH,
      inlineDialogue && !after.isEmpty(),
      true
    );
  }

  private static HeadingRecord inlineDialogueHeading(
    int lineIndex,
    String line,
    String alias,
    String canonical
  ) {
    String aliasUpper = alias.toUpperCase();
    String upper = line.toUpperCase();

    int end = alias.length();
    if (
      upper.startsWith(aliasUpper + ".") || upper.startsWith(aliasUpper + ":")
    ) {
      end = alias.length() + 1;
    } else if (upper.startsWith(aliasUpper + " ")) {
      end = alias.length();
    } else {
      return null;
    }

    String after = line.substring(end).trim();
    if (!safeBoundaryAfterAlias(line, alias.length())) {
      return null;
    }

    if (startsWithTitleCaseAliasButNotHeading(line, alias)) {
      return null;
    }
    after = cleanRemainingAfterHeading(after);
    if (looksLikeCharacterActionOrNarration(after)) {
      return null;
    }
    if (
      looksLikeCastOrCreditRemainder(after) ||
      looksLikeCharacterDescriptionRemainder(after) ||
      looksLikePublicationOrFurnitureRemainder(after)
    ) {
      return null;
    }
    if (after.isEmpty()) {
      return null;
    }
    if (looksLikeStageOnlyContinuation(after)) {
      return null;
    }

    return new HeadingRecord(
      lineIndex,
      line,
      alias,
      canonical,
      after,
      HeadingConfidence.HIGH,
      true,
      false
    );
  }

  private static HeadingRecord missingPunctuationHeading(
    int lineIndex,
    String line,
    String alias,
    String canonical
  ) {
    String aliasUpper = alias.toUpperCase();
    String upper = line.toUpperCase();

    if (!upper.startsWith(aliasUpper + " ")) {
      return null;
    }

    if (!isAllCapsAt(line, 0, alias.length())) {
      return null;
    }

    String after = line.substring(alias.length()).trim();
    if (looksLikeCharacterActionOrNarration(after)) {
      return null;
    }

    if (looksLikeCastOrCreditRemainder(after)) {
      return null;
    }
    if (
      looksLikeCharacterDescriptionRemainder(after) ||
      looksLikePublicationOrFurnitureRemainder(after)
    ) {
      return null;
    }

    after = cleanRemainingAfterHeading(after);

    if (after.isEmpty()) {
      return null;
    }

    if (looksLikeStageOnlyContinuation(after)) {
      return null;
    }

    if (StageDetector.startsLikeWrappedContinuation(after)) {
      return null;
    }

    if (!SpeakerDetector.bareTurn(after)) {
      return null;
    }

    if (!looksLikeDialogueRemainder(after)) {
      return null;
    }

    if (after.length() > 240) {
      return null;
    }

    return new HeadingRecord(
      lineIndex,
      line,
      alias,
      canonical,
      after,
      HeadingConfidence.MEDIUM,
      true,
      false
    );
  }

  private static String cleanRemainingAfterHeading(String remaining) {
    String out = TextNormalizer.norm(remaining);

    out = stripLeadingParentheticals(out);

    while (out.startsWith(".") || out.startsWith(":")) {
      out = TextNormalizer.norm(out.substring(1));
      out = stripLeadingParentheticals(out);
    }

    return out;
  }

  private static String stripLeadingParentheticals(String text) {
    String out = TextNormalizer.norm(text);
    boolean changed = true;

    while (changed && !out.isEmpty()) {
      changed = false;

      if (out.startsWith("(")) {
        int close = out.indexOf(")");
        if (close >= 0) {
          out = TextNormalizer.norm(out.substring(close + 1));
          changed = true;
        }
      } else if (out.startsWith("[")) {
        int close = out.indexOf("]");
        if (close >= 0) {
          out = TextNormalizer.norm(out.substring(close + 1));
          changed = true;
        }
      } else if (out.startsWith("{")) {
        int close = out.indexOf("}");
        if (close >= 0) {
          out = TextNormalizer.norm(out.substring(close + 1));
          changed = true;
        }
      }
    }

    return out;
  }

  private static boolean looksLikeStageOnlyContinuation(String text) {
    String normalized = TextNormalizer.norm(text);
    if (normalized.isEmpty()) {
      return true;
    }

    return (
      StageDetector.whole(normalized) ||
      StageDetector.is(normalized, null) ||
      StageDetector.strong(normalized, null) ||
      StageDetector.prose(normalized, null)
    );
  }

  private static boolean isAllCapsAt(String line, int start, int length) {
    if (
      line == null || start < 0 || length <= 0 || start + length > line.length()
    ) {
      return false;
    }

    String slice = line.substring(start, start + length);
    int letters = 0;
    int upper = 0;

    for (int i = 0; i < slice.length(); i++) {
      char ch = slice.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
        if (Character.isUpperCase(ch)) {
          upper++;
        }
      }
    }

    return letters > 0 && upper == letters;
  }

  private static String canonicalName(
    String candidate,
    Map<String, String> aliases
  ) {
    if (aliases == null) {
      return candidate;
    }

    String mapped = aliases.get(TextNormalizer.cleanName(candidate));
    if (mapped == null || mapped.trim().isEmpty()) {
      return TextNormalizer.cleanName(candidate);
    }
    return TextNormalizer.cleanName(mapped);
  }

  private static List<String> buildNameList(
    Set<String> chars,
    Map<String, String> aliases
  ) {
    List<String> names = new ArrayList<>();

    if (chars != null) {
      for (String ch : chars) {
        String cleaned = TextNormalizer.cleanName(ch);
        if (!cleaned.isEmpty() && !names.contains(cleaned)) {
          names.add(cleaned);
        }
      }
    }

    if (aliases != null) {
      for (String alias : aliases.keySet()) {
        String cleaned = TextNormalizer.cleanName(alias);
        if (!cleaned.isEmpty() && !names.contains(cleaned)) {
          names.add(cleaned);
        }
      }
    }

    names.sort(
      Comparator.comparingInt(String::length)
        .reversed()
        .thenComparing(Comparator.naturalOrder())
    );
    return names;
  }

  private static boolean looksLikeDialogueRemainder(String after) {
    String cleaned = TextNormalizer.norm(after);
    if (cleaned.isEmpty()) {
      return false;
    }

    if (StageDetector.is(cleaned, null) || StageDetector.prose(cleaned, null)) {
      return false;
    }

    String lower = cleaned.toLowerCase();

    return (
      cleaned.contains("?") ||
      cleaned.contains("!") ||
      cleaned.contains("\"") ||
      cleaned.contains("'") ||
      lower.matches(
        ".*\\b(i|i'm|i'll|i've|you|you're|we|we're|they|he|she|it|don't|can't|won't|would|could|should|want|know|think|feel|love|hate|need|mean|remember|please|yes|no)\\b.*"
      ) ||
      cleaned.length() <= 80
    );
  }

  private static boolean shouldRejectCandidateHeading(
    String line,
    String alias,
    Set<String> chars
  ) {
    String normalized = TextNormalizer.norm(line);
    String cleanAlias = TextNormalizer.cleanName(alias);

    if (normalized.isEmpty() || cleanAlias.isEmpty()) {
      return true;
    }

    if (!startsWithAlias(normalized, cleanAlias)) {
      return false;
    }

    String after =
      normalized.length() > cleanAlias.length()
        ? TextNormalizer.norm(normalized.substring(cleanAlias.length()))
        : "";

    if (after.startsWith(":") || after.startsWith(".")) {
      after = TextNormalizer.norm(after.substring(1));
    }

    if (startsWithTitleCaseAliasButNotHeading(normalized, cleanAlias)) {
      return true;
    }

    if (after.isEmpty()) {
      return false;
    }

    if (
      looksLikeCastOrCreditRemainder(after) ||
      looksLikeCharacterDescriptionRemainder(after) ||
      looksLikeCharacterActionOrNarration(after) ||
      looksLikePublicationOrFurnitureRemainder(after)
    ) {
      return true;
    }

    return FrontMatterDetector.blockHeading(normalized, chars);
  }

  private static boolean startsWithTitleCaseAliasButNotHeading(
    String line,
    String alias
  ) {
    String cleaned = TextNormalizer.norm(line);
    String cleanAlias = TextNormalizer.cleanName(alias);

    if (cleaned.isEmpty() || cleanAlias.isEmpty()) {
      return false;
    }

    if (cleaned.length() <= cleanAlias.length()) {
      return false;
    }

    if (!cleaned.regionMatches(true, 0, cleanAlias, 0, cleanAlias.length())) {
      return false;
    }

    if (!safeBoundaryAfterAlias(cleaned, cleanAlias.length())) {
      return false;
    }

    char next = cleaned.charAt(cleanAlias.length());
    if (next == ':' || next == '.') {
      return false;
    }

    String visiblePrefix = cleaned.substring(0, cleanAlias.length());
    boolean hasLetter = false;
    boolean hasLower = false;

    for (int i = 0; i < visiblePrefix.length(); i++) {
      char ch = visiblePrefix.charAt(i);
      if (Character.isLetter(ch)) {
        hasLetter = true;
        if (Character.isLowerCase(ch)) {
          hasLower = true;
        }
      }
    }

    return hasLetter && hasLower;
  }

  private static boolean startsWithAlias(String line, String alias) {
    if (line == null || alias == null) {
      return false;
    }

    String normalized = TextNormalizer.norm(line);
    if (normalized.length() < alias.length()) {
      return false;
    }

    if (!normalized.regionMatches(true, 0, alias, 0, alias.length())) {
      return false;
    }

    return safeBoundaryAfterAlias(normalized, alias.length());
  }

  private static boolean safeBoundaryAfterAlias(String line, int aliasLength) {
    if (line == null || aliasLength < 0 || aliasLength > line.length()) {
      return false;
    }

    if (aliasLength == line.length()) {
      return true;
    }

    char next = line.charAt(aliasLength);
    return (
      Character.isWhitespace(next) ||
      next == ':' ||
      next == '.' ||
      next == '/' ||
      next == '(' ||
      next == '[' ||
      next == '{'
    );
  }

  private static boolean looksLikeCastOrCreditRemainder(String after) {
    String cleaned = TextNormalizer.norm(after);
    if (cleaned.isEmpty()) {
      return false;
    }

    String lower = cleaned.toLowerCase();

    if (
      lower.matches(
        ".*\\b(actor|actress|played by|understudy|director|directed by|voice of|original cast|premiere|production|company|artistic director|executive director)\\b.*"
      )
    ) {
      return true;
    }

    if (looksLikePersonNameRemainder(cleaned)) {
      return true;
    }

    return false;
  }

  private static boolean looksLikeCharacterDescriptionRemainder(String after) {
    String lower = TextNormalizer.norm(after).toLowerCase();
    if (lower.isEmpty()) {
      return false;
    }

    return lower.matches(
      ".*\\b(husband|wife|daughter|son|father|mother|brother|sister|child|children|narrator|voice|pregnant|doesn't speak|does not speak|years old|year old|inside|grown up|played by|appears as|also plays|described as|counterpart|adolescence|elderly|young|old)\\b.*"
    );
  }

  private static boolean looksLikeCharacterActionOrNarration(String after) {
    String lower = TextNormalizer.norm(after).toLowerCase();
    if (lower.isEmpty()) {
      return false;
    }

    return lower.matches(
      "^(is|are|was|were|talks|speaks|enters|exits|attaches|straightens|shines|makes|stopped|grew|goes|comes|looks|watches|follows|sits|stands|nods|waves|shrugs|smiles|kisses|touches|helps|reads|puts|gets|takes|thumbs|runs|twirls|consoles|sips|eats|whispers|demonstrates|opens|closes|crosses|pulls|pushes|lays|carries|holds|reports|remembers|forgets|cries|laughs|points|turns|moves|walks|kneels|rises|falls|leans|stares|waits|listens|searches|throws|picks|drops|hands|gives|receives|places|sets|covers|uncovers|wipes|combs|brushes|dances|sings|hums)\\b.*"
    );
  }

  private static boolean looksLikePublicationOrFurnitureRemainder(String text) {
    String lower = TextNormalizer.norm(text).toLowerCase();
    if (lower.isEmpty()) {
      return false;
    }

    return lower.matches(
      ".*\\b(isbn|copyright|all rights|permission|publisher|published|publishing|press|catalogue|cataloging|manufactured|book design|cover art|cover design|directed by|produced by|commissioned by|premiere|licensed|license|licence|royalty|royalties|street|avenue|road|lane|drive|boulevard|suite|floor|building|city|state|country|website|www\\.|\\.com|\\.org|\\.net)\\b.*"
    );
  }

  private static boolean looksLikePersonNameRemainder(String text) {
    String cleaned = TextNormalizer.norm(text);
    if (cleaned.isEmpty() || cleaned.length() > 70) {
      return false;
    }

    if (
      cleaned.contains("?") || cleaned.contains("!") || cleaned.contains("\"")
    ) {
      return false;
    }

    String[] words = cleaned.split("\\s+");
    if (words.length < 2 || words.length > 4) {
      return false;
    }

    int personLike = 0;
    for (String word : words) {
      if (word.matches("[A-Z][a-zA-Z'’.-]+")) {
        personLike++;
      }
    }

    return personLike == words.length;
  }

  private static boolean looksLikeStandaloneFurnitureHeading(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty() || cleaned.length() > 55) {
      return false;
    }

    if (
      cleaned.contains("/") || cleaned.contains("(") || cleaned.contains("[")
    ) {
      return false;
    }
    String upper = cleaned.toUpperCase();
    if (!upper.equals(cleaned)) {
      return false;
    }

    if (upper.matches(".*\\d{1,4}.*")) {
      return true;
    }

    String[] words = upper.split("\\s+");
    if (words.length < 1 || words.length > 3) {
      return false;
    }

    for (String word : words) {
      if (!word.matches("[A-Z][A-Z'’\\-]{1,}")) {
        return false;
      }
    }

    return words.length >= 2 || upper.length() > 10;
  }
}
