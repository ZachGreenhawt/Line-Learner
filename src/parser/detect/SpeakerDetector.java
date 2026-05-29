package parser.detect;

import java.util.Set;
import parser.CharacterExtractor;
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

    String recovered = stripLeadingParentheticals(line);
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
    if (next == ':' || next == '.') {
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
      String stripped = stripLeadingParentheticals(rest);
      return (
        stripped.isEmpty() ||
        stripped.startsWith(":") ||
        stripped.startsWith(".")
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
      if (ignoreName(clean)) {
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
    rest = normalizeInsideRest(rest);

    if (start > 0) {
      return rest.startsWith(":") || rest.startsWith(".");
    }

    if (rest.isEmpty() || rest.startsWith(":") || rest.startsWith(".")) {
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

    String name = raw.replaceAll("[.:]+$", "").trim();
    if (name.isEmpty() || name.length() > 60) {
      return false;
    }
    if (name.matches(".*[?!;].*")) {
      return false;
    }
    if (name.contains("/") && !slashLooksLike(name)) {
      return false;
    }
    if (!name.matches("[A-Za-z][A-Za-z0-9 .'’\\-/]*")) {
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

    String[] words = name.split("\\s+");
    if (words.length > 7) {
      return false;
    }

    return (
      raw.endsWith(".") ||
      raw.endsWith(":") ||
      name.matches(".*\\b\\d{1,3}\\b.*") ||
      name.matches("(?i)^(A|AN|THE)\\s+[A-Z][A-Z0-9 .'’\\-]{1,50}$") ||
      name.matches(
        "[A-Z][A-Z0-9 .'’\\-]+(?:\\s*/\\s*[A-Z][A-Z0-9 .'’\\-]+)+"
      ) ||
      (words.length <= 4 && name.equals(name.toUpperCase()))
    );
  }

  private static boolean slashLooksLike(String line) {
    String[] parts = line.split("/");
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
      String withoutPunctuation = normalized.substring(
        0,
        normalized.length() - 1
      );
      cleaned = TextNormalizer.cleanName(withoutPunctuation);
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

    String[] pieces = heading.split("/");
    if (pieces.length < 2) {
      return "";
    }

    StringBuilder combo = new StringBuilder();
    for (String piece : pieces) {
      String cleaned = TextNormalizer.cleanName(piece);
      if (cleaned.isEmpty() || !chars.contains(cleaned)) {
        return "";
      }

      if (combo.length() > 0) {
        combo.append(" / ");
      }
      combo.append(cleaned);
    }

    return combo.toString();
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
    String stripped = stripLeadingParentheticals(rest);

    if (
      stripped.isEmpty() || stripped.startsWith(":") || stripped.startsWith(".")
    ) {
      return speaker;
    }

    if (bareTurn(stripped)) {
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
        if (StageDetector.actionStart(rest) && !bareTurn(rest)) continue;
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

    String recoveredLine = stripLeadingParentheticals(line);
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

    for (String part : speaker.split("/")) {
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

    if (rest.startsWith(":") || rest.startsWith(".")) {
      return TextNormalizer.norm(rest.substring(1));
    }

    rest = stripLeadingParentheticals(rest);
    if (rest.startsWith(":") || rest.startsWith(".")) {
      return TextNormalizer.norm(rest.substring(1));
    }

    return "";
  }

  private static String afterColon(String line, String speaker) {
    int colon = line.indexOf(":");
    if (colon <= 0) {
      return "";
    }

    String possibleSpeaker = TextNormalizer.cleanName(line.substring(0, colon));
    if (!possibleSpeaker.equals(TextNormalizer.cleanName(speaker))) {
      return "";
    }

    return TextNormalizer.norm(line.substring(colon + 1));
  }

  private static String afterDot(String line, String speaker) {
    int dot = speakerDot(line);
    if (dot <= 0) {
      return "";
    }

    String possibleSpeaker = TextNormalizer.cleanName(line.substring(0, dot));
    if (!possibleSpeaker.equals(TextNormalizer.cleanName(speaker))) {
      return "";
    }

    return TextNormalizer.norm(line.substring(dot + 1));
  }

  private static String afterRaw(String line, String rawSpeaker) {
    String rest = TextNormalizer.norm(line.substring(rawSpeaker.length()));
    rest = stripLeadingParentheticals(rest);

    if (rest.startsWith(":") || rest.startsWith(".")) {
      rest = TextNormalizer.norm(rest.substring(1));
    }

    return rest;
  }

  private static String afterParenthetical(String line, String rawSpeaker) {
    String rest = TextNormalizer.norm(line.substring(rawSpeaker.length()));
    rest = stripLeadingParentheticals(rest);

    if (rest.isEmpty()) {
      return "";
    }

    if (rest.startsWith(":") || rest.startsWith(".")) {
      return TextNormalizer.norm(rest.substring(1));
    }

    if (bareTurn(rest)) {
      return rest;
    }

    return "";
  }

  private static boolean startsParenthetical(String line, String cleanSpeaker) {
    if (line == null || cleanSpeaker == null || cleanSpeaker.isEmpty()) {
      return false;
    }

    line = TextNormalizer.norm(line);
    cleanSpeaker = TextNormalizer.cleanName(cleanSpeaker);

    if (!line.regionMatches(true, 0, cleanSpeaker, 0, cleanSpeaker.length())) {
      return false;
    }
    if (!allCapsAt(line, 0, cleanSpeaker)) {
      return false;
    }
    if (line.length() <= cleanSpeaker.length()) {
      return false;
    }

    char next = line.charAt(cleanSpeaker.length());
    if (Character.isWhitespace(next)) {
      String rest = line.substring(cleanSpeaker.length()).trim();
      return rest.startsWith("(") || rest.startsWith("[");
    }

    return (
      (next == '(' || next == '[') && afterBoundary(line, cleanSpeaker.length())
    );
  }

  public static boolean bareTurn(String rest) {
    rest = TextNormalizer.norm(rest);
    if (rest.isEmpty()) {
      return false;
    }
    if (
      StageDetector.whole(rest) ||
      StageDetector.actionStart(rest) ||
      rest.matches("^(?i)(enter|exit|exeunt|re-enter|reenter)\\b.*")
    ) {
      return false;
    }

    char first = rest.charAt(0);
    if (Character.isLowerCase(first)) {
      return false;
    }

    return (
      rest.matches(".*[a-z].*") ||
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
      .replaceAll("[^a-z -]", " ")
      .replaceAll("\\s+", " ")
      .trim();

    if (before.isEmpty()) {
      return false;
    }

    return before.matches(
      ".*\\b(enter|enters|exit|exits|re enter|re enters|re-enter|re-enters|reenter|reenters)$"
    );
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
      .replaceAll("[^a-z '-]", " ")
      .replaceAll("\\s+", " ")
      .trim();
    if (lower.isEmpty()) {
      return false;
    }

    return lower.matches(
      ".*\\b(enters?|exits?|crosses|looks at|watches|follows|touches|kisses|helps|leads|brings|takes|puts|lays|hands to|gives to|speaks to|talks to|turns to|goes to|comes to)\\s+$"
    );
  }

  private static boolean validInsideEnd(String line, int end) {
    return (
      afterBoundary(line, end) ||
      (line.length() > end &&
        (line.charAt(end) == '(' || line.charAt(end) == '['))
    );
  }

  private static String normalizeInsideRest(String rest) {
    if (rest.startsWith("(") || rest.startsWith("[")) {
      String stripped = stripLeadingParentheticals(rest);
      if (
        !stripped.isEmpty() &&
        (stripped.startsWith(":") || stripped.startsWith("."))
      ) {
        return stripped;
      }
      if (!stripped.isEmpty() && bareTurn(stripped)) {
        return stripped;
      }
    }
    return rest;
  }

  private static boolean ignoreName(String clean) {
    return (
      clean.isEmpty() ||
      clean.length() < 2 ||
      CharacterExtractor.BAD_SHORT_LINES.contains(clean) ||
      CharacterExtractor.BAD_HEADINGS.contains(clean)
    );
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

  public static String stripLeadingParentheticals(String text) {
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
}
