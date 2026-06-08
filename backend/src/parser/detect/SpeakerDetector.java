package parser.detect;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import parser.CharacterExtractor;
import util.RegexTerms;
import util.TextNormalizer;

public class SpeakerDetector {

  public static String name(String line, Set<String> chars) {
    if (chars == null || chars.isEmpty()) {
      return "";
    }

    line = TextNormalizer.norm(line);
    if (line.isEmpty()) {
      return "";
    }

    String speaker = namePlain(line, chars);
    if (!speaker.isEmpty()) {
      return speaker;
    }

    String recovered = TextNormalizer.stripLeadingParentheticals(line);
    if (!recovered.equals(line)) {
      return namePlain(recovered, chars);
    }

    return "";
  }

  public static boolean is(String line, Set<String> chars) {
    return chars != null && chars.contains(TextNormalizer.cleanName(line));
  }

  public static boolean has(String line, Set<String> chars) {
    return !name(line, chars).isEmpty();
  }

  public static boolean starts(String line, String speaker) {
    line = TextNormalizer.norm(line);
    speaker = TextNormalizer.rawHeadingName(speaker);

    if (line.length() < speaker.length()) {
      return false;
    }
    if (!line.regionMatches(true, 0, speaker, 0, speaker.length())) {
      return false;
    }
    if (!allCapsAt(line, 0, speaker)) {
      return false;
    }
    if (line.length() == speaker.length()) {
      return true;
    }

    char next = line.charAt(speaker.length());
    if (headingPunctuation(next)) {
      return true;
    }
    if (!Character.isWhitespace(next) && next != '(' && next != '[') {
      return false;
    }

    String rest = line.substring(speaker.length()).trim();
    if (rest.isEmpty()) {
      return true;
    }

    if (StageDetector.actionStart(rest) && !bareTurn(rest)) {
      return false;
    }

    if (rest.startsWith("(") || rest.startsWith("[")) {
      String stripped = TextNormalizer.stripLeadingParentheticals(rest);
      return (
        stripped.isEmpty() ||
        startsWithHeadingPunctuation(stripped)
      );
    }

    return bareTurn(rest);
  }

  public static int inside(String line, int from, Set<String> chars) {
    if (line == null || chars == null || chars.isEmpty()) {
      return -1;
    }

    int best = -1;
    String upperLine = line.toUpperCase();

    for (String name : CharacterExtractor.sortedNamesByLength(chars)) {
      String clean = TextNormalizer.cleanName(name);
      if (
        clean.isEmpty() ||
        clean.length() < 2 ||
        CharacterExtractor.BAD_SHORT_LINES.contains(clean) ||
        CharacterExtractor.BAD_HEADINGS.contains(clean)
      ) {
        continue;
      }

      int index = Math.max(0, from);
      while (index < line.length()) {
        index = upperLine.indexOf(clean, index);
        if (index < 0) {
          break;
        }

        if (index >= from && match(line, index, clean, chars)) {
          best = best < 0 ? index : Math.min(best, index);
          break;
        }

        index += Math.max(1, clean.length());
      }
    }

    return best;
  }

  public static boolean match(
    String line,
    int start,
    String name,
    Set<String> chars
  ) {
    if (!beforeBoundary(line, start)) {
      return false;
    }
    if (afterEntranceCue(line, start)) {
      return false;
    }
    if (insideStage(line, start)) {
      return false;
    }
    if (!allCapsAt(line, start, name)) {
      return false;
    }

    int end = start + name.length();
    if (!validInsideEnd(line, end)) {
      return false;
    }

    String rest = line.substring(Math.min(line.length(), end)).strip();
    if (rest.startsWith("(") || rest.startsWith("[")) {
      String stripped = TextNormalizer.stripLeadingParentheticals(rest);
      if (
        !stripped.isEmpty() &&
        (startsWithHeadingPunctuation(stripped) || bareTurn(stripped))
      ) {
        rest = stripped;
      }
    }

    if (start > 0) {
      return startsWithHeadingPunctuation(rest);
    }

    if (rest.isEmpty() || startsWithHeadingPunctuation(rest)) {
      return true;
    }

    if (rest.startsWith("(") || rest.startsWith("[")) {
      return false;
    }

    return bareTurn(rest);
  }

  public static boolean heading(String line, Set<String> chars) {
    return !name(line, chars).isEmpty() || looksLike(line);
  }

  public static boolean looksLike(String line) {
    String raw = TextNormalizer.norm(line);
    if (raw.isEmpty() || raw.length() > 70) {
      return false;
    }

    String name = raw.replaceAll(RegexTerms.TRAILING_DOT_COLON, "").trim();
    if (name.isEmpty() || name.length() > 60) {
      return false;
    }
    if (name.matches(RegexTerms.CONTAINS_SENTENCE_PUNCT)) {
      return false;
    }
    if (name.contains("/") && !slashLooksLike(name)) {
      return false;
    }
    if (!name.matches(RegexTerms.SPEAKER_NAME_SHAPE)) {
      return false;
    }

    int letters = 0;
    int caps = 0;
    for (int i = 0; i < name.length(); i++) {
      char ch = name.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
        if (Character.isUpperCase(ch)) {
          caps++;
        }
      }
    }

    if (letters < 2 || caps * 10 < letters * 7) {
      return false;
    }

    String[] words = name.split(RegexTerms.WHITESPACE);
    if (words.length > 7) {
      return false;
    }

    return (
      raw.endsWith(".") ||
      raw.endsWith(":") ||
      name.matches(RegexTerms.CONTAINS_SMALL_NUMBER) ||
      name.matches(RegexTerms.SPEAKER_ARTICLE_HEADING_PATTERN) ||
      name.matches(RegexTerms.SPEAKER_SLASH_HEADING_PATTERN) ||
      (words.length <= 4 && name.equals(name.toUpperCase()))
    );
  }

  private static boolean slashLooksLike(String line) {
    String[] parts = line.split(RegexTerms.SLASH);
    if (parts.length < 2) {
      return false;
    }

    for (String part : parts) {
      if (!looksLike(part.trim())) {
        return false;
      }
    }
    return true;
  }

  public static int speakerDot(String line) {
    line = TextNormalizer.norm(line);

    for (int i = 0; i < line.length(); i++) {
      if (line.charAt(i) != '.') {
        continue;
      }

      String beforeDot = line.substring(0, i);
      if (CharacterExtractor.heading(beforeDot)) {
        return i;
      }
    }

    return -1;
  }

  private static String namePlain(String line, Set<String> chars) {
    String speaker = bareKnown(line, chars);
    if (!speaker.isEmpty()) {
      return speaker;
    }

    speaker = slashKnown(line, chars);
    if (!speaker.isEmpty()) {
      return speaker;
    }

    speaker = parentheticalKnown(line, chars);
    if (!speaker.isEmpty()) {
      return speaker;
    }

    speaker = beforeColon(line, chars);
    if (!speaker.isEmpty()) {
      return speaker;
    }

    speaker = beforeDot(line, chars);
    if (!speaker.isEmpty()) {
      return speaker;
    }

    speaker = atStart(line, chars);
    if (!speaker.isEmpty()) {
      return speaker;
    }

    return is(line, chars) ? TextNormalizer.cleanName(line) : "";
  }

  private static String bareKnown(String line, Set<String> chars) {
    if (line == null || chars == null || chars.isEmpty()) {
      return "";
    }

    String normalized = TextNormalizer.norm(line);
    if (normalized.isEmpty()) {
      return "";
    }

    String raw = TextNormalizer.rawHeadingName(normalized);
    String cleaned = TextNormalizer.cleanName(raw);
    if (chars.contains(cleaned)) {
      return cleaned;
    }

    if (normalized.endsWith(".") || normalized.endsWith(":")) {
      cleaned = TextNormalizer.cleanName(
        normalized.substring(0, normalized.length() - 1)
      );
      if (chars.contains(cleaned)) {
        return cleaned;
      }
    }

    return "";
  }

  private static String slashKnown(String line, Set<String> chars) {
    if (line == null || chars == null || chars.isEmpty()) {
      return "";
    }

    String normalized = TextNormalizer.norm(line);
    if (!normalized.contains("/")) {
      return "";
    }

    int cut = firstPositiveIndex(
      normalized.indexOf('.'),
      normalized.indexOf(':')
    );
    String heading = cut < 0 ? normalized : normalized.substring(0, cut);

    String[] pieces = heading.split(RegexTerms.SLASH);
    if (pieces.length < 2) {
      return "";
    }

    List<String> cleanPieces = new ArrayList<>();
    for (String piece : pieces) {
      String cleaned = TextNormalizer.cleanName(piece);
      if (cleaned.isEmpty() || !chars.contains(cleaned)) {
        return "";
      }
      cleanPieces.add(cleaned);
    }

    return String.join(" / ", cleanPieces);
  }

  private static String parentheticalKnown(String line, Set<String> chars) {
    if (line == null || chars == null || chars.isEmpty()) {
      return "";
    }

    String normalized = TextNormalizer.norm(line);
    if (normalized.isEmpty()) {
      return "";
    }

    int openParen = firstPositiveIndex(
      normalized.indexOf('('),
      normalized.indexOf('[')
    );
    if (openParen <= 0) {
      return "";
    }

    String speaker = TextNormalizer.cleanName(
      normalized.substring(0, openParen)
    );
    if (!chars.contains(speaker)) {
      return "";
    }

    String rest = normalized.substring(openParen).trim();
    String stripped = TextNormalizer.stripLeadingParentheticals(rest);

    if (
      stripped.isEmpty() ||
      stripped.startsWith(":") ||
      stripped.startsWith(".") ||
      bareTurn(stripped)
    ) {
      return speaker;
    }

    return "";
  }

  private static int firstPositiveIndex(int a, int b) {
    if (a < 0) {
      return b;
    }
    if (b < 0) {
      return a;
    }
    return Math.min(a, b);
  }

  private static String beforeColon(String line, Set<String> chars) {
    int colon = line.indexOf(":");
    if (colon <= 0) {
      return "";
    }

    String name = TextNormalizer.cleanName(line.substring(0, colon));
    if (chars.contains(name)) {
      return name;
    }

    return slashKnown(line.substring(0, colon), chars);
  }

  private static String beforeDot(String line, Set<String> chars) {
    int dot = speakerDot(line);
    if (dot <= 0) {
      return "";
    }

    String name = TextNormalizer.cleanName(line.substring(0, dot));
    if (chars.contains(name)) {
      return name;
    }

    return slashKnown(line.substring(0, dot), chars);
  }

  private static String atStart(String line, Set<String> chars) {
    for (String ch : CharacterExtractor.sortedNamesByLength(chars)) {
      String clean = TextNormalizer.cleanName(ch);
      if (clean.isEmpty()) {
        continue;
      }

      if (starts(line, clean) || startsParenthetical(line, clean)) {
        String rest = afterRaw(line, clean);
        if (
          !rest.isEmpty() && StageDetector.actionStart(rest) && !bareTurn(rest)
        ) {
          continue;
        }
        return clean;
      }
    }

    return "";
  }

  public static String afterSpeaker(String line, String speaker) {
    line = TextNormalizer.norm(line);
    if (line.isEmpty() || speaker == null || speaker.isEmpty()) {
      return "";
    }

    if (speaker.contains("/")) {
      String rest = afterSlash(line, speaker);
      if (!rest.isEmpty()) {
        return rest;
      }
    }

    String recoveredLine = TextNormalizer.stripLeadingParentheticals(line);
    if (
      !recoveredLine.equals(line) &&
      !namePlain(recoveredLine, Set.of(speaker)).isEmpty()
    ) {
      line = recoveredLine;
    }

    String rest = afterColon(line, speaker);
    if (!rest.isEmpty()) {
      return rest;
    }

    rest = afterDot(line, speaker);
    if (!rest.isEmpty()) {
      return rest;
    }

    String rawSpeaker = TextNormalizer.rawHeadingName(speaker);
    if (starts(line, rawSpeaker)) {
      return afterRaw(line, rawSpeaker);
    }

    if (startsParenthetical(line, rawSpeaker)) {
      return afterParenthetical(line, rawSpeaker);
    }

    return "";
  }

  private static String afterSlash(String line, String speaker) {
    String rest = TextNormalizer.norm(line);

    for (String part : speaker.split(RegexTerms.SLASH)) {
      String name = TextNormalizer.cleanName(part);
      if (
        name.isEmpty() || !rest.toUpperCase().startsWith(name.toUpperCase())
      ) {
        return "";
      }

      rest = TextNormalizer.norm(rest.substring(name.length()));
      if (rest.startsWith("/")) {
        rest = TextNormalizer.norm(rest.substring(1));
      }
    }

    if (startsWithHeadingPunctuation(rest)) {
      return stripHeadingPunctuation(rest);
    }

    rest = TextNormalizer.stripLeadingParentheticals(rest);
    if (startsWithHeadingPunctuation(rest)) {
      return stripHeadingPunctuation(rest);
    }

    return "";
  }

  private static String afterColon(String line, String speaker) {
    int colon = line.indexOf(":");
    if (colon <= 0) {
      return "";
    }

    String name = TextNormalizer.cleanName(line.substring(0, colon));
    if (!name.equals(TextNormalizer.cleanName(speaker))) {
      return "";
    }

    return TextNormalizer.norm(line.substring(colon + 1));
  }

  private static String afterDot(String line, String speaker) {
    int dot = speakerDot(line);
    if (dot <= 0) {
      return "";
    }

    String name = TextNormalizer.cleanName(line.substring(0, dot));
    if (!name.equals(TextNormalizer.cleanName(speaker))) {
      return "";
    }

    return TextNormalizer.norm(line.substring(dot + 1));
  }

  private static String afterRaw(String line, String rawSpeaker) {
    String rest = TextNormalizer.norm(line.substring(rawSpeaker.length()));
    rest = TextNormalizer.stripLeadingParentheticals(rest);

    if (startsWithHeadingPunctuation(rest)) {
      rest = stripHeadingPunctuation(rest);
    }

    return rest;
  }

  private static String afterParenthetical(String line, String rawSpeaker) {
    String rest = TextNormalizer.norm(line.substring(rawSpeaker.length()));
    rest = TextNormalizer.stripLeadingParentheticals(rest);

    if (rest.isEmpty()) {
      return "";
    }

    if (startsWithHeadingPunctuation(rest)) {
      return stripHeadingPunctuation(rest);
    }

    return bareTurn(rest) ? rest : "";
  }

  private static boolean startsParenthetical(String line, String speaker) {
    if (line == null || speaker == null || speaker.isEmpty()) {
      return false;
    }

    line = TextNormalizer.norm(line);
    speaker = TextNormalizer.cleanName(speaker);

    if (!line.regionMatches(true, 0, speaker, 0, speaker.length())) {
      return false;
    }
    if (!allCapsAt(line, 0, speaker)) {
      return false;
    }
    if (line.length() <= speaker.length()) {
      return false;
    }

    char next = line.charAt(speaker.length());
    if (Character.isWhitespace(next)) {
      String rest = line.substring(speaker.length()).trim();
      return rest.startsWith("(") || rest.startsWith("[");
    }

    return next == '(' || next == '[';
  }

  public static boolean bareTurn(String rest) {
    rest = TextNormalizer.norm(rest);
    if (rest.isEmpty()) {
      return false;
    }
    if (
      StageDetector.whole(rest) ||
      StageDetector.actionStart(rest) ||
      rest.matches(RegexTerms.SPEAKER_BARE_STAGE_START_PATTERN)
    ) {
      return false;
    }

    char first = rest.charAt(0);
    if (Character.isLowerCase(first)) {
      return false;
    }

    return (
      rest.matches(RegexTerms.CONTAINS_LOWERCASE) ||
      rest.contains("?") ||
      rest.contains("!") ||
      rest.contains("—") ||
      rest.contains("-") ||
      rest.contains("'") ||
      rest.contains("\"")
    );
  }

  public static boolean beforeBoundary(String line, int start) {
    if (start <= 0) {
      return true;
    }

    char previous = line.charAt(start - 1);
    if (
      Character.isLetterOrDigit(previous) || previous == '\'' || previous == '-'
    ) {
      return false;
    }

    return (
      Character.isWhitespace(previous) ||
      previous == '.' ||
      previous == '!' ||
      previous == '?' ||
      previous == ':' ||
      previous == ';' ||
      previous == ')' ||
      previous == ']'
    );
  }

  public static boolean afterBoundary(String line, int end) {
    if (line == null || end >= line.length()) {
      return true;
    }

    char next = line.charAt(end);
    return (
      Character.isWhitespace(next) ||
      next == ':' ||
      next == '.' ||
      next == ',' ||
      next == '(' ||
      next == '[' ||
      next == '/'
    );
  }

  public static boolean afterEntranceCue(String line, int start) {
    if (line == null || start <= 0) {
      return false;
    }

    String before = line
      .substring(0, start)
      .toLowerCase()
      .replaceAll(RegexTerms.NON_LOWER_SPACE_DASH, " ")
      .replaceAll(RegexTerms.WHITESPACE, " ")
      .trim();

    if (before.isEmpty()) {
      return false;
    }

    return before.matches(RegexTerms.SPEAKER_ENTRANCE_CUE_PATTERN);
  }

  private static boolean insideStage(String line, int start) {
    if (line == null || start <= 0) {
      return false;
    }

    String before = line.substring(0, start).trim();
    if (before.isEmpty()) {
      return false;
    }

    if (before.endsWith("(") || before.endsWith("[")) {
      return true;
    }

    String lower = before
      .toLowerCase()
      .replaceAll(RegexTerms.NON_LOWER_SPACE_QUOTE_DASH, " ")
      .replaceAll(RegexTerms.WHITESPACE, " ")
      .trim();
    if (lower.isEmpty()) {
      return false;
    }

    return lower.matches(RegexTerms.SPEAKER_INSIDE_STAGE_PATTERN);
  }

  private static boolean validInsideEnd(String line, int end) {
    return (
      afterBoundary(line, end) ||
      (line.length() > end &&
        (line.charAt(end) == '(' || line.charAt(end) == '['))
    );
  }

  private static boolean startsWithHeadingPunctuation(String text) {
    String cleaned = TextNormalizer.norm(text);
    return (
      cleaned.startsWith(":") ||
      cleaned.startsWith(".") ||
      cleaned.startsWith(",")
    );
  }

  private static String stripHeadingPunctuation(String text) {
    String cleaned = TextNormalizer.norm(text);
    while (!cleaned.isEmpty() && headingPunctuation(cleaned.charAt(0))) {
      cleaned = TextNormalizer.norm(cleaned.substring(1));
    }
    return cleaned;
  }

  private static boolean headingPunctuation(char ch) {
    return ch == ':' || ch == '.' || ch == ',';
  }

  public static boolean allCapsAt(String line, int start, String name) {
    if (line == null || name == null) {
      return false;
    }
    if (start < 0 || start + name.length() > line.length()) {
      return false;
    }

    String actual = line.substring(start, start + name.length());
    if (!actual.equalsIgnoreCase(name)) {
      return false;
    }

    int letters = 0;
    int caps = 0;

    for (int i = 0; i < actual.length(); i++) {
      char ch = actual.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
        if (Character.isUpperCase(ch)) {
          caps++;
        }
      }
    }

    return letters > 0 && caps * 10 >= letters * 8;
  }
}
