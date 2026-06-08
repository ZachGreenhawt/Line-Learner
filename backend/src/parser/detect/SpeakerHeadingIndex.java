package parser.detect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import util.RegexTerms;
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

    HeadingRecord explicitDialogue = explicitKnownDialogueHeading(
      lineIndex,
      line,
      names,
      aliases
    );
    if (explicitDialogue != null) {
      return explicitDialogue;
    }

    if (FrontMatterDetector.blockHeading(line, chars)) {
      return null;
    }

    if (
      looksLikeStandaloneFurnitureHeading(line) &&
      !knownBare(line, names) &&
      !nearKnownBare(line, names)
    ) {
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

      HeadingRecord fuzzy = fuzzyHeading(lineIndex, line, candidate, canonical);
      if (fuzzy != null) {
        return fuzzy;
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

  private static boolean nearKnownBare(String line, List<String> names) {
    if (names == null || names.isEmpty() || !simpleHeadingWord(line)) {
      return false;
    }

    for (String name : names) {
      if (simpleName(name) && closeHeading(line, name)) {
        return true;
      }
    }
    return false;
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

    String[] pieces = heading.split(RegexTerms.SLASH);
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
    int comma = line.indexOf(',');

    int best = -1;
    for (int index : new int[] { dot, colon, comma }) {
      if (index >= 0 && (best < 0 || index < best)) {
        best = index;
      }
    }
    return best;
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
      upper.equals(aliasUpper + ":") ||
      upper.equals(aliasUpper + ",")
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

  private static HeadingRecord explicitKnownDialogueHeading(
    int lineIndex,
    String line,
    List<String> names,
    Map<String, String> aliases
  ) {
    String upper = TextNormalizer.norm(line).toUpperCase();

    for (String candidate : names) {
      String alias = TextNormalizer.cleanName(candidate);
      if (alias.isEmpty()) {
        continue;
      }
      if (!startsWithHeadingPunctuation(upper, alias)) {
        continue;
      }
      if (!safeBoundaryAfterAlias(line, alias.length())) {
        continue;
      }

      String after = cleanRemainingAfterHeading(
        line.substring(Math.min(line.length(), alias.length() + 1)).trim()
      );
      if (after.isEmpty() || looksLikeStageOnlyContinuation(after)) {
        return new HeadingRecord(
          lineIndex,
          line,
          alias,
          canonicalName(alias, aliases),
          "",
          HeadingConfidence.HIGH,
          false,
          false
        );
      }
      if (!looksLikeDialogueRemainder(after)) continue;
      if (
        looksLikeCharacterActionOrNarration(after) ||
        looksLikePublicationOrFurnitureRemainder(after)
      ) continue;

      return new HeadingRecord(
        lineIndex,
        line,
        alias,
        canonicalName(alias, aliases),
        after,
        HeadingConfidence.HIGH,
        true,
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

    String after = TextNormalizer.stripLeadingParentheticals(afterAlias);
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
    if (startsWithHeadingPunctuation(upper, aliasUpper)) {
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

  private static HeadingRecord fuzzyHeading(
    int lineIndex,
    String line,
    String alias,
    String canonical
  ) {
    String cleanAlias = TextNormalizer.cleanName(alias);
    if (!simpleName(cleanAlias)) {
      return null;
    }

    String normalized = TextNormalizer.norm(line);
    int cut = firstHeadingPunctuationIndex(normalized);
    String head = cut >= 0 ? normalized.substring(0, cut).trim() : normalized;
    String remaining = cut >= 0 ? normalized.substring(cut + 1).trim() : "";
    boolean missingPunctuation = false;

    if (cut < 0) {
      int space = normalized.indexOf(' ');
      if (space > 0) {
        head = normalized.substring(0, space).trim();
        remaining = normalized.substring(space + 1).trim();
        missingPunctuation = true;
      }
    }

    if (!simpleHeadingWord(head) || !closeHeading(head, cleanAlias)) {
      return null;
    }

    remaining = cleanRemainingAfterHeading(remaining);
    if (!remaining.isEmpty() && looksLikeStageOnlyContinuation(remaining)) {
      remaining = "";
    }
    if (!remaining.isEmpty() && !looksLikeDialogueRemainder(remaining)) {
      return null;
    }
    if (missingPunctuation && !remaining.isEmpty() && !SpeakerDetector.bareTurn(remaining)) {
      return null;
    }

    return new HeadingRecord(
      lineIndex,
      line,
      alias,
      canonical,
      remaining,
      HeadingConfidence.MEDIUM,
      !remaining.isEmpty(),
      false
    );
  }

  private static boolean simpleName(String name) {
    String clean = TextNormalizer.cleanName(name);
    return (
      clean.length() >= 4 &&
      clean.length() <= 14 &&
      !clean.contains(" ") &&
      !clean.contains("/")
    );
  }

  private static boolean simpleHeadingWord(String text) {
    String clean = TextNormalizer.cleanName(text);
    if (!simpleName(clean)) {
      return false;
    }

    int letters = 0;
    int caps = 0;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
        if (Character.isUpperCase(ch)) {
          caps++;
        }
      } else if (!Character.isDigit(ch) && ch != '\'' && ch != '’') {
        return false;
      }
    }

    return letters > 0 && caps * 10 >= letters * 8;
  }

  private static boolean closeHeading(String raw, String alias) {
    String left = foldOcr(TextNormalizer.cleanName(raw));
    String right = foldOcr(TextNormalizer.cleanName(alias));
    if (left.equals(right)) {
      String cleanRaw = TextNormalizer.cleanName(raw);
      String cleanAlias = TextNormalizer.cleanName(alias);
      return !cleanRaw.equals(cleanAlias);
    }
    if (Math.abs(left.length() - right.length()) > 1) {
      return false;
    }
    return editDistanceAtMostOne(left, right);
  }

  private static String foldOcr(String text) {
    return text
      .replace('0', 'O')
      .replace('1', 'I')
      .replace('5', 'S')
      .replace('8', 'B');
  }

  private static boolean editDistanceAtMostOne(String left, String right) {
    if (left.equals(right)) {
      return true;
    }

    int i = 0;
    int j = 0;
    int edits = 0;

    while (i < left.length() && j < right.length()) {
      if (left.charAt(i) == right.charAt(j)) {
        i++;
        j++;
        continue;
      }

      edits++;
      if (edits > 1) {
        return false;
      }

      if (left.length() > right.length()) {
        i++;
      } else if (right.length() > left.length()) {
        j++;
      } else {
        i++;
        j++;
      }
    }

    return edits + (left.length() - i) + (right.length() - j) <= 1;
  }

  private static String cleanRemainingAfterHeading(String remaining) {
    String out = TextNormalizer.norm(remaining);

    out = TextNormalizer.stripLeadingParentheticals(out);

    while (startsWithHeadingPunctuation(out)) {
      out = TextNormalizer.norm(out.substring(1));
      out = TextNormalizer.stripLeadingParentheticals(out);
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
        RegexTerms.containsAnyWord(RegexTerms.HEADING_DIALOGUE_REMAINDER)
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

    if (startsWithHeadingPunctuation(after)) {
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

    if (explicitDialogueHeading(normalized, cleanAlias, after)) {
      return false;
    }

    return FrontMatterDetector.blockHeading(normalized, chars);
  }

  private static boolean explicitDialogueHeading(
    String line,
    String alias,
    String after
  ) {
    String upperLine = TextNormalizer.norm(line).toUpperCase();
    String upperAlias = TextNormalizer.cleanName(alias);
    String remainder = TextNormalizer.norm(after);

    return (
      startsWithHeadingPunctuation(upperLine, upperAlias) &&
      !remainder.isEmpty() &&
      looksLikeDialogueRemainder(remainder) &&
      !looksLikeStageOnlyContinuation(remainder)
    );
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
    if (next == ':' || next == '.' || next == ',') {
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
      next == ',' ||
      next == '/' ||
      next == '(' ||
      next == '[' ||
      next == '{'
    );
  }

  private static boolean startsWithHeadingPunctuation(
    String text,
    String alias
  ) {
    return (
      text.startsWith(alias + ".") ||
      text.startsWith(alias + ":") ||
      text.startsWith(alias + ",")
    );
  }

  private static boolean startsWithHeadingPunctuation(String text) {
    String cleaned = TextNormalizer.norm(text);
    return (
      cleaned.startsWith(".") ||
      cleaned.startsWith(":") ||
      cleaned.startsWith(",")
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
        RegexTerms.containsAnyWord(RegexTerms.HEADING_CAST_CREDIT_REMAINDER)
      )
    ) {
      return true;
    }

    return looksLikePersonNameRemainder(cleaned);
  }

  private static boolean looksLikeCharacterDescriptionRemainder(String after) {
    String lower = TextNormalizer.norm(after).toLowerCase();
    if (lower.isEmpty()) {
      return false;
    }

    return lower.matches(
      RegexTerms.containsAnyWord(RegexTerms.HEADING_DESCRIPTION_REMAINDER)
    );
  }

  private static boolean looksLikeCharacterActionOrNarration(String after) {
    String cleaned = TextNormalizer.norm(after);
    if (cleaned.isEmpty()) {
      return false;
    }

    if (
      cleaned.contains("?") || cleaned.contains("!") || cleaned.contains("\"")
    ) {
      return false;
    }

    String lower = cleaned.toLowerCase();
    return lower.matches(
      RegexTerms.startsWithAnyWord(RegexTerms.HEADING_ACTION_NARRATION)
    );
  }

  private static boolean looksLikePublicationOrFurnitureRemainder(String text) {
    String lower = TextNormalizer.norm(text).toLowerCase();
    if (lower.isEmpty()) {
      return false;
    }

    return RegexTerms.containsPublicationOrFurniture(lower);
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

    String[] words = cleaned.split(RegexTerms.WHITESPACE);
    if (words.length < 2 || words.length > 4) {
      return false;
    }

    int personLike = 0;
    for (String word : words) {
      if (word.matches(RegexTerms.TITLE_CASE_WORD)) {
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

    if (upper.matches(RegexTerms.CONTAINS_PAGE_NUMBER)) {
      return true;
    }

    String[] words = upper.split(RegexTerms.WHITESPACE);
    if (words.length < 1 || words.length > 3) {
      return false;
    }

    for (String word : words) {
      if (!word.matches(RegexTerms.ALL_CAPS_WORD)) {
        return false;
      }
    }

    return words.length >= 2 || upper.length() > 10;
  }
}
