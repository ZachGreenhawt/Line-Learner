package parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import parser.detect.*;
import util.RegexTerms;
import util.TextNormalizer;

public class LogicalLineBuilder {

  public static List<String> build(String scriptText, Set<String> chars) {
    List<String> out = new ArrayList<>();
    if (scriptText == null) {
      return out;
    }

    List<String> rawLines = normalizedNonEmptyLines(scriptText);
    Set<String> hintKeys = ScriptLoader.stageHintKeys();

    for (int i = 0; i < rawLines.size(); i++) {
      String line = rawLines.get(i);

      if (shouldDropLogicalLine(line, chars)) {
        continue;
      }

      if (!hintKeys.isEmpty() && hintKeys.contains(StageHints.key(line))) {
        out.add(line);
        continue;
      }

      if (i + 1 < rawLines.size()) {
        String[] moved = splitTrailingHeadingFragment(
          line,
          rawLines.get(i + 1),
          chars
        );
        if (moved != null) {
          line = moved[0];
          rawLines.set(i + 1, moved[1]);
        }
      }

      if (SpeakerDetector.heading(line, chars)) {
        if (hasEmbeddedTurnAfterStart(line, chars)) {
          out.addAll(explodeEmbeddedTurns(line, chars));
          continue;
        }
        out.add(line);
        continue;
      }

      if (i + 1 < rawLines.size()) {
        String nextLine = rawLines.get(i + 1);

        if (shouldDropLogicalLine(nextLine, chars)) {
          out.addAll(explodeEmbeddedTurns(line, chars));
          continue;
        }

        String numberedHeading = joinSplitNumberedHeading(
          line,
          nextLine,
          chars
        );
        if (!numberedHeading.isEmpty()) {
          out.add(numberedHeading);
          i++;
          continue;
        }

        String joinedBrokenSpeaker = joinBrokenSpeaker(line, nextLine, chars);
        if (!joinedBrokenSpeaker.isEmpty()) {
          out.add(joinedBrokenSpeaker);
          i++;
          continue;
        }

        String joinedSplitMultiWordSpeaker = joinSplitMultiWordSpeaker(
          line,
          nextLine,
          chars
        );
        if (!joinedSplitMultiWordSpeaker.isEmpty()) {
          out.addAll(explodeEmbeddedTurns(joinedSplitMultiWordSpeaker, chars));
          i++;
          continue;
        }

        if (SpeakerDetector.heading(nextLine, chars)) {
          out.add(line);
          continue;
        }

        String merged = mergeSplitSpeakerLine(line, nextLine, chars);
        if (!merged.isEmpty()) {
          out.addAll(explodeEmbeddedTurns(merged, chars));
          i++;
          continue;
        }
      }

      String[] trailing = splitTrailingHeading(line, chars);
      if (trailing != null) {
        out.addAll(explodeEmbeddedTurns(trailing[0], chars));
        out.add(trailing[1]);
        continue;
      }

      out.addAll(explodeEmbeddedTurns(line, chars));
    }

    return out;
  }

  private static String[] splitTrailingHeadingFragment(
    String line,
    String next,
    Set<String> chars
  ) {
    if (chars == null || chars.isEmpty()) {
      return null;
    }

    String t = TextNormalizer.norm(line);
    String n = TextNormalizer.norm(next);
    if (t.length() < 12 || n.isEmpty()) {
      return null;
    }

    for (String name : CharacterExtractor.sortedNamesByLength(chars)) {
      String clean = TextNormalizer.cleanName(name);
      String[] words = clean.split(RegexTerms.WHITESPACE);
      if (words.length < 2) {
        continue;
      }

      for (int cut = 1; cut < words.length; cut++) {
        String prefix = String.join(
          " ",
          Arrays.copyOfRange(words, 0, cut)
        );
        String remaining = String.join(
          " ",
          Arrays.copyOfRange(words, cut, words.length)
        );

        if (
          prefix.length() < 4 ||
          !t.endsWith(" " + prefix) ||
          !n.regionMatches(true, 0, remaining, 0, remaining.length()) ||
          !SpeakerDetector.allCapsAt(t, t.length() - prefix.length(), prefix) ||
          !SpeakerDetector.allCapsAt(n, 0, remaining)
        ) {
          continue;
        }

        if (
          n.length() > remaining.length() &&
          !validCharacterBoundary(n, remaining.length())
        ) {
          continue;
        }

        String before = t
          .substring(0, t.length() - prefix.length())
          .strip();
        if (before.length() < 8) {
          continue;
        }
        char last = before.charAt(before.length() - 1);
        if (last != '.' && last != '!' && last != '?' && last != '"' &&
            last != '”' && last != ')') {
          continue;
        }

        return new String[] { before, prefix + " " + n };
      }
    }

    return null;
  }

  private static String[] splitTrailingHeading(
    String line,
    Set<String> chars
  ) {
    if (chars == null || chars.isEmpty()) {
      return null;
    }

    String t = TextNormalizer.norm(line);
    if (t.length() < 12) {
      return null;
    }

    for (String name : CharacterExtractor.sortedNamesByLength(chars)) {
      String clean = TextNormalizer.cleanName(name);
      if (clean.length() < 4 || !t.endsWith(clean)) {
        continue;
      }

      int start = t.length() - clean.length();
      if (!SpeakerDetector.allCapsAt(t, start, clean)) {
        continue;
      }

      String before = t.substring(0, start).strip();
      if (before.length() < 8) {
        return null;
      }

      char last = before.charAt(before.length() - 1);
      if (last != '.' && last != '!' && last != '?' && last != '"' &&
          last != '”' && last != ')') {
        return null;
      }

      return new String[] { before, clean };
    }

    return null;
  }

  private static List<String> normalizedNonEmptyLines(String scriptText) {
    List<String> lines = new ArrayList<>();

    for (String raw : scriptText.split(RegexTerms.NEWLINE)) {
      String line = normalizeLogicalInputLine(raw);
      if (!line.isEmpty()) {
        lines.add(line);
      }
    }

    return lines;
  }

  private static String normalizeLogicalInputLine(String raw) {
    String line = TextNormalizer.norm(raw);
    if (line.isEmpty()) {
      return "";
    }

    line = line.replaceAll(RegexTerms.REPLACEMENT_CHAR, "");
    line = line.replaceAll(RegexTerms.BROKEN_FFFE_CHARS, "");

    if (pageMarkerTag(line)) {
      return "";
    }

    line = line.replaceAll(
      RegexTerms.HEADING_WITH_TRAILING_PAGE_NUMBER,
      "$1"
    );

    return TextNormalizer.norm(line);
  }

  private static boolean pageMarkerTag(String line) {
    return (
      line.matches(RegexTerms.PAGE_MARKER_PARSED_TEXT) ||
      line.matches(RegexTerms.PAGE_MARKER_REGION) ||
      line.matches(RegexTerms.PAGE_MARKER_IMAGE)
    );
  }

  private static boolean shouldDropLogicalLine(String line, Set<String> chars) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty()) {
      return true;
    }

    if (SpeakerDetector.heading(t, chars)) {
      return false;
    }

    if (StageDetector.entranceExit(t)) {
      return false;
    }

    if (StageDetector.is(t, chars) || StageDetector.strong(t, chars)) {
      return false;
    }

    if (t.matches(RegexTerms.PAGE_NUMBER_ONLY)) {
      return true;
    }

    if (PageFurnitureDetector.is(t)) {
      return true;
    }

    if (pageMarkerTag(t)) {
      return true;
    }

    return (
      t.matches(RegexTerms.PUNCTUATION_RUN_ONLY) ||
      looksLikeIsolatedPageFurniture(t, chars)
    );
  }

  private static boolean looksLikeIsolatedPageFurniture(
    String line,
    Set<String> chars
  ) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty()) {
      return false;
    }

    if (SpeakerDetector.heading(t, chars)) {
      return false;
    }

    if (StageDetector.is(t, chars) || StageDetector.strong(t, chars)) {
      return false;
    }

    if (!TextNormalizer.hasLetter(t) || !isMostlyUppercaseText(t)) {
      return false;
    }

    String[] words = t.split(RegexTerms.WHITESPACE);
    if (words.length > 4 || t.length() > 40) {
      return false;
    }

    return t.matches(RegexTerms.CONTAINS_DIGIT) || words.length <= 2;
  }

  private static boolean isMostlyUppercaseText(String line) {
    int letters = 0;
    int caps = 0;

    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
        if (Character.isUpperCase(ch)) {
          caps++;
        }
      }
    }

    return letters > 0 && caps * 10 >= letters * 8;
  }

  private static List<String> explodeEmbeddedTurns(
    String raw,
    Set<String> chars
  ) {
    List<String> out = new ArrayList<>();
    String line = TextNormalizer.norm(raw);

    if (line.isEmpty()) {
      return out;
    }

    if (shouldKeepWholeLine(line, chars)) {
      out.add(line);
      return out;
    }

    int start = 0;
    while (start < line.length()) {
      int next = SpeakerDetector.inside(line, start + 1, chars);

      if (next < 0) {
        addIfMeaningful(out, line.substring(start), chars);
        break;
      }

      addIfMeaningful(out, line.substring(start, next), chars);
      start = next;
    }

    return out;
  }

  private static boolean shouldKeepWholeLine(String line, Set<String> chars) {
    if (
      SpeakerDetector.heading(line, chars) ||
      !SpeakerDetector.name(line, chars).isEmpty()
    ) {
      return !hasEmbeddedTurnAfterStart(line, chars);
    }

    return (
      StageDetector.entranceExit(line) ||
      StageDetector.is(line, chars) ||
      StageDetector.strong(line, chars) ||
      looksLikeStandaloneStageDirection(line)
    );
  }

  private static boolean hasEmbeddedTurnAfterStart(
    String line,
    Set<String> chars
  ) {
    return SpeakerDetector.inside(line, 1, chars) > 0;
  }

  private static boolean looksLikeStandaloneStageDirection(String line) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty()) {
      return false;
    }

    return (
      StageDetector.whole(t) ||
      StageDetector.entranceExit(t) ||
      StageDetector.location(t) ||
      StageDetector.actionStart(t) ||
      StageDetector.is(t, null) ||
      StageDetector.strong(t, null)
    );
  }

  private static void addIfMeaningful(
    List<String> out,
    String raw,
    Set<String> chars
  ) {
    String cleaned = TextNormalizer.norm(raw);
    if (cleaned.isEmpty()) {
      return;
    }

    if (!shouldDropLogicalLine(cleaned, chars)) {
      out.add(cleaned);
    }
  }

  private static boolean uppercaseLineFragment(String line) {
    String cleaned = TextNormalizer.rawHeadingName(line);

    if (cleaned.isEmpty() || !TextNormalizer.hasLetter(cleaned)) {
      return false;
    }
    if (cleaned.length() > 55 || cleaned.matches(RegexTerms.ENDS_WITH_SENTENCE_PUNCTUATION)) {
      return false;
    }

    int letters = 0;
    int caps = 0;

    for (int i = 0; i < cleaned.length(); i++) {
      char ch = cleaned.charAt(i);

      if (Character.isLetter(ch)) {
        letters++;
        if (Character.isUpperCase(ch)) {
          caps++;
        }
      } else if (!allowedHeadingCharacter(ch)) {
        return false;
      }
    }

    return letters > 0 && caps * 10 >= letters * 8;
  }

  private static boolean allowedHeadingCharacter(char ch) {
    return (
      Character.isWhitespace(ch) ||
      Character.isDigit(ch) ||
      ch == '-' ||
      ch == '\'' ||
      ch == '’' ||
      ch == '/' ||
      ch == '&' ||
      ch == '.'
    );
  }

  private static String joinSplitNumberedHeading(
    String line,
    String next,
    Set<String> chars
  ) {
    if (chars == null || chars.isEmpty()) {
      return "";
    }

    String first = TextNormalizer.cleanName(line);
    String second = TextNormalizer.norm(next);

    if (first.isEmpty() || second.isEmpty()) {
      return "";
    }
    if (!uppercaseLineFragment(first)) {
      return "";
    }
    if (!second.matches(RegexTerms.NUMBER_SUFFIX_FRAGMENT)) {
      return "";
    }

    String merged = TextNormalizer.cleanName(first + " " + second);
    return chars.contains(merged) ? merged : "";
  }

  private static String mergeSplitSpeakerLine(
    String firstLine,
    String secondLine,
    Set<String> chars
  ) {
    if (chars == null || chars.isEmpty()) {
      return "";
    }

    String first = TextNormalizer.norm(firstLine);
    String second = TextNormalizer.norm(secondLine);

    String slashContinuation = mergeSlashSpeakerContinuation(
      first,
      second,
      chars
    );
    if (!slashContinuation.isEmpty()) {
      return slashContinuation;
    }

    if (shouldProtectSeparateLines(first, second, chars)) {
      return "";
    }
    if (!uppercaseLineFragment(first) || second.isEmpty()) {
      return "";
    }

    String byKnownMultiWordName = mergeByKnownMultiWordName(
      first,
      second,
      chars
    );
    if (!byKnownMultiWordName.isEmpty()) {
      return byKnownMultiWordName;
    }

    String simpleMerge = TextNormalizer.cleanName(first + " " + second);
    if (chars.contains(simpleMerge)) {
      return simpleMerge;
    }

    return "";
  }

  private static String mergeSlashSpeakerContinuation(
    String first,
    String second,
    Set<String> chars
  ) {
    String left = TextNormalizer.norm(first);
    String right = TextNormalizer.norm(second);

    if (!left.endsWith("/") || right.isEmpty()) {
      return "";
    }

    if (!uppercaseLineFragment(left) || !uppercaseLineFragment(right)) {
      return "";
    }

    String merged = TextNormalizer.norm(left + " " + right);
    String speaker = SpeakerDetector.name(merged, chars);
    if (!speaker.isEmpty()) {
      return merged;
    }

    if (merged.matches(RegexTerms.SLASH_SPEAKER_LINE)) {
      return merged;
    }

    return "";
  }

  private static boolean shouldProtectSeparateLines(
    String first,
    String second,
    Set<String> chars
  ) {
    if (first != null && TextNormalizer.norm(first).endsWith("/")) {
      return false;
    }

    if (!joinSplitNumberedHeading(first, second, chars).isEmpty()) {
      return false;
    }

    if (
      SpeakerDetector.heading(first, chars) ||
      SpeakerDetector.heading(second, chars)
    ) {
      return true;
    }

    if (StageDetector.is(first, chars) || StageDetector.is(second, chars)) {
      return true;
    }

    return (
      SpeakerDetector.name(first, chars).isEmpty() &&
      !SpeakerDetector.name(second, chars).isEmpty()
    );
  }

  private static String mergeByKnownMultiWordName(
    String first,
    String second,
    Set<String> chars
  ) {
    for (String ch : CharacterExtractor.sortedNamesByLength(chars)) {
      String clean = TextNormalizer.cleanName(ch);
      String[] parts = clean.split(RegexTerms.WHITESPACE);

      if (parts.length < 2) {
        continue;
      }

      String firstPart = parts[0];
      String remaining = String.join(
        " ",
        Arrays.copyOfRange(parts, 1, parts.length)
      );

      if (!TextNormalizer.cleanName(first).equals(firstPart)) {
        continue;
      }
      if (!startsWithRemainingSpeakerName(second, remaining)) {
        continue;
      }

      String rest = second.substring(remaining.length()).strip();
      String cleanedRest = cleanRestAfterSplitSpeaker(rest);
      if (cleanedRest == null) {
        continue;
      }

      return cleanedRest.isEmpty() ? clean : clean + ": " + cleanedRest;
    }

    return "";
  }

  private static boolean startsWithRemainingSpeakerName(
    String line,
    String remaining
  ) {
    return (
      line.regionMatches(true, 0, remaining, 0, remaining.length()) &&
      SpeakerDetector.allCapsAt(line, 0, remaining)
    );
  }

  private static String cleanRestAfterSplitSpeaker(String rest) {
    if (rest.startsWith(":") || rest.startsWith(".")) {
      return rest.substring(1).strip();
    }

    if (rest.isEmpty()) {
      return "";
    }

    String cleaned = rest.strip();

    if (cleaned.startsWith("(") || cleaned.startsWith("[")) {
      String stripped = TextNormalizer.stripLeadingParentheticals(cleaned);
      if (stripped.startsWith(".") || stripped.startsWith(":")) {
        return cleaned;
      }
      return null;
    }

    if (stageOrParentheticalStart(cleaned)) {
      return null;
    }

    return cleaned;
  }

  private static boolean stageOrParentheticalStart(String line) {
    return (
      StageDetector.actionStart(line) ||
      StageDetector.location(line) ||
      StageDetector.is(line, null) ||
      StageDetector.strong(line, null) ||
      line.startsWith("(") ||
      line.startsWith("[") ||
      line.startsWith("{")
    );
  }

  public static String joinBrokenSpeaker(
    String line,
    String next,
    Set<String> chars
  ) {
    String one = TextNormalizer.cleanName(line);
    String two = TextNormalizer.cleanName(next);

    if (one.isEmpty() || two.isEmpty() || chars == null || chars.isEmpty()) {
      return "";
    }

    if (!uppercaseLineFragment(line) || !uppercaseLineFragment(next)) {
      return "";
    }
    if (StageDetector.is(line, chars) || StageDetector.is(next, chars)) {
      return "";
    }

    String joinedNoSpace = one + two;
    String joinedSpace = one + " " + two;

    if (chars.contains(joinedNoSpace)) {
      return joinedNoSpace;
    }
    if (chars.contains(joinedSpace)) {
      return joinedSpace;
    }
    return "";
  }

  public static String joinSplitMultiWordSpeaker(
    String line,
    String next,
    Set<String> chars
  ) {
    String first = TextNormalizer.cleanName(line);
    String secondLine = TextNormalizer.norm(next);
    if (StageDetector.is(line, chars) || StageDetector.is(secondLine, chars)) {
      return "";
    }

    if (first.isEmpty() || secondLine.isEmpty() || chars == null) {
      return "";
    }

    for (String ch : CharacterExtractor.sortedNamesByLength(chars)) {
      String clean = TextNormalizer.cleanName(ch);
      String[] parts = clean.split(RegexTerms.WHITESPACE);

      if (parts.length < 2 || !parts[0].equals(first)) {
        continue;
      }

      String remaining = String.join(
        " ",
        Arrays.copyOfRange(parts, 1, parts.length)
      );
      if (
        !secondLine.regionMatches(true, 0, remaining, 0, remaining.length())
      ) {
        continue;
      }
      if (!uppercaseLineFragment(line)) {
        continue;
      }
      if (!SpeakerDetector.allCapsAt(secondLine, 0, remaining)) {
        continue;
      }
      if (!validCharacterBoundary(secondLine, remaining.length())) {
        continue;
      }

      String rest = secondLine.substring(remaining.length()).strip();
      String cleanedRest = cleanRestAfterSplitSpeaker(rest);
      if (cleanedRest == null) {
        return "";
      }

      return cleanedRest.isEmpty() ? clean : clean + ": " + cleanedRest;
    }

    return "";
  }

  private static boolean validCharacterBoundary(String line, int index) {
    if (line.length() <= index) {
      return true;
    }

    char next = line.charAt(index);
    return Character.isWhitespace(next) || ":.([{ /".indexOf(next) >= 0;
  }
}
