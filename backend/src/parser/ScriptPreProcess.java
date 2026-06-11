package parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import parser.detect.*;
import util.RegexTerms;

public class ScriptPreProcess {

  private static final Pattern PAGE_NUMBER_ONLY = Pattern.compile(
    RegexTerms.PAGE_NUMBER_ONLY
  );
  private static final Pattern PAGE_TITLE_NUMBER = Pattern.compile(
    RegexTerms.PAGE_TITLE_NUMBER
  );
  private static final Pattern INLINE_PAGE_HEADER = Pattern.compile(
    RegexTerms.INLINE_PAGE_HEADER
  );
  private static final Pattern ZERO_WIDTH = Pattern.compile(
    RegexTerms.ZERO_WIDTH_CHARS
  );
  private static final Pattern PAGE_RANGE_HEADER = Pattern.compile(
    RegexTerms.PAGE_RANGE_HEADER
  );
  private static final Pattern ALL_CAPS_RUN = Pattern.compile(
    RegexTerms.ALL_CAPS_RUN
  );

  public static String clean(String text) {
    if (text == null || text.isEmpty()) return "";
    String normalized = mergeSingleGlyphRuns(raw(text));
    String[] lines = normalized.split(RegexTerms.NEWLINE_CHAR, -1);
    List<String> cleanedLines = new ArrayList<>();

    StringBuilder out = new StringBuilder(normalized.length());

    boolean castRegion = false;
    boolean castEntry = false;
    int castRegionLines = 0;

    for (int i = 0; i < lines.length; i++) {
      String raw = lines[i] == null ? "" : lines[i];
      if (raw.trim().isEmpty()) {
        castEntry = false;
        cleanedLines.add("");
        continue;
      }
      String line = scrub(norm(raw));

      String inner = unwrapFurniture(line);
      if (castRegionHeader(inner)) {
        castRegion = true;
        castEntry = false;
        castRegionLines = 0;
      } else if (
        castRegion && (castRegionEnd(inner) || ++castRegionLines > 200)
      ) {
        castRegion = false;
        castEntry = false;
      }
      if (castRegion && (castDashLine(inner) || castEntry)) {
        castEntry = true;
        cleanedLines.add("");
        continue;
      }

      if (
        !structural(line) &&
        (junk(line) ||
          badExtraction(line) ||
          headerFooter(line) ||
          dottedLeader(line))
      ) {
        cleanedLines.add("");
        continue;
      }
      if (line.endsWith("-") && (i + 1) < lines.length) {
        String nextRaw = lines[i + 1] == null ? "" : lines[i + 1];
        String next = scrub(norm(nextRaw));

        if (joinHyphen(line, next)) {
          String joined = line.substring(0, line.length() - 1) + next;
          joined = joined.replaceAll(RegexTerms.WHITESPACE, " ").trim();
          cleanedLines.add(joined);
          i++;
          continue;
        }
      }

      cleanedLines.add(line);
    }

    for (String cleanedLine : removeRepeated(cleanedLines)) {
      out.append(cleanedLine).append('\n');
    }
    return out.toString().replaceFirst(RegexTerms.TRAILING_WHITESPACE, "");
  }

  private static String unwrapFurniture(String line) {
    return line.replaceAll("</?FURNITURE_CANDIDATE[^>]*>", "").trim();
  }

  private static boolean castRegionHeader(String line) {
    return line.matches(
      "(?i)^(characters|cast of characters|dramatis personae|cast)\\b.{0,30}$"
    );
  }

  private static boolean castRegionEnd(String line) {
    return line.matches(
      "(?i)^(time|place|setting|act|scene|moment|episode|prologue)\\b.*$"
    );
  }

  private static boolean castDashLine(String line) {
    return line.matches(
      "^[A-Z][A-Z0-9 .,'\\-]{1,45}\\s+[-–—~]{1,3}\\s+\\S.{3,}$"
    );
  }

  private static boolean dottedLeader(String line) {
    if (line == null || line.length() < 8) return false;
    for (String token : line.split(" ")) {
      if (token.matches("[A-Za-z']+\\.{1,5}[.,!?;:'\"]*")) continue;
      if (token.matches(".*\\.{6,}.*")) return true;
      if (
        token.length() >= 12 &&
        token.matches("[a-z.]+") &&
        token.chars().filter(c -> c == '.').count() >= 2
      ) return true;
      if (token.matches("[0oO]{2,5}\\.{3,}")) return true;
      if (token.length() >= 20 && token.matches("[a-z.]+")) {
        long distinct = token
          .chars()
          .filter(Character::isLetter)
          .distinct()
          .count();
        if (token.indexOf('.') >= 0 || distinct <= 7) return true;
      }
    }
    return false;
  }

  private static String mergeSingleGlyphRuns(String text) {
    if (text == null || text.isEmpty()) return text;
    String[] lines = text.split(RegexTerms.NEWLINE_CHAR, -1);
    StringBuilder out = new StringBuilder(text.length());
    int i = 0;
    while (i < lines.length) {
      if (singleGlyph(lines[i])) {
        int j = i;
        int letters = 0;
        StringBuilder token = new StringBuilder();
        while (j < lines.length && singleGlyph(lines[j])) {
          char c = lines[j].trim().charAt(0);
          token.append(c);
          if (Character.isLetter(c)) letters++;
          j++;
        }
        if (j - i >= 3 && letters >= 2) {
          out.append(token).append('\n');
          i = j;
          continue;
        }
      }
      out.append(lines[i]).append('\n');
      i++;
    }
    if (out.length() > 0) out.setLength(out.length() - 1);
    return out.toString();
  }

  private static boolean singleGlyph(String line) {
    if (line == null) return false;
    String t = line.trim();
    if (t.length() != 1) return false;
    char c = t.charAt(0);
    return Character.isLetter(c) || ".,:;'".indexOf(c) >= 0;
  }

  public static String removePhrases(String text, List<String> phrases) {
    if (text == null || text.isBlank()) return "";
    if (phrases == null || phrases.isEmpty()) return text;

    String cleaned = text;
    for (String phrase : phrases) {
      cleaned = removePhrase(cleaned, phrase);
    }
    return cleaned;
  }

  private static String removePhrase(String text, String phrase) {
    if (text == null || text.isBlank()) return "";
    if (phrase == null || phrase.isBlank()) return text;

    return text.replaceAll(
      RegexTerms.CASE_INSENSITIVE_FLAG + java.util.regex.Pattern.quote(phrase),
      " "
    );
  }

  private static String raw(String text) {
    if (text == null) return "";

    String t = text.replace("\r\n", "\n").replace("\r", "\n");
    t = ZERO_WIDTH.matcher(t).replaceAll("");
    t = t.replace('\f', '\n');
    t = t.replace('\u2028', '\n');
    t = t.replace('\u2029', '\n');
    return t;
  }

  private static String norm(String line) {
    if (line == null) return "";
    String t = ZERO_WIDTH.matcher(line).replaceAll("");
    return t
      .trim()
      .replace('\u2018', '\'')
      .replace('\u2019', '\'')
      .replace('\u201C', '"')
      .replace('\u201D', '"')
      .replace('\u2013', '-')
      .replace('\u2014', '-')
      .replace('\u00A0', ' ')
      .replaceAll(RegexTerms.WHITESPACE, " ");
  }

  private static String scrub(String line) {
    if (line == null || line.isEmpty()) return "";

    String cleaned = line;

    cleaned = INLINE_PAGE_HEADER.matcher(cleaned).replaceAll(" ");
    cleaned = PAGE_RANGE_HEADER.matcher(cleaned).replaceAll(" ");
    cleaned = restoreLonePronoun(cleaned);

    return cleaned.replaceAll(RegexTerms.WHITESPACE, " ").trim();
  }

  private static final java.util.regex.Pattern GLYPH_PRONOUN =
    java.util.regex.Pattern.compile("(^|[\\s(\"'“‘])[|\\[](?=\\s[a-z])");
  private static final java.util.regex.Pattern ONE_PRONOUN =
    java.util.regex.Pattern.compile("(^|[.!?…)]\\s)1(?=\\s[a-z])");

  private static String restoreLonePronoun(String line) {
    String out = GLYPH_PRONOUN.matcher(line).replaceAll("$1I");
    return ONE_PRONOUN.matcher(out).replaceAll("$1I");
  }

  private static boolean structural(String line) {
    String t = norm(line);
    if (t.isEmpty()) return false;

    if (SpeakerDetector.looksLike(t)) return true;
    if (StageDetector.is(t, null) || StageDetector.strong(t, null)) return true;

    String lower = t.toLowerCase();
    return (
      lower.matches(RegexTerms.STRUCTURAL_CHARACTERS) ||
      lower.matches(RegexTerms.STRUCTURAL_SOUNDS) ||
      lower.matches(RegexTerms.STRUCTURAL_SCENE) ||
      lower.matches(RegexTerms.STRUCTURAL_AT_RISE) ||
      lower.matches(RegexTerms.STRUCTURAL_AT_THE_RISE) ||
      lower.matches(RegexTerms.STRUCTURAL_BEFORE_CURTAIN) ||
      lower.matches(RegexTerms.STRUCTURAL_EPISODE) ||
      lower.matches(RegexTerms.STRUCTURAL_ACT) ||
      lower.matches(RegexTerms.STRUCTURAL_LIGHTS) ||
      lower.matches(RegexTerms.STRUCTURAL_BLACKOUT) ||
      lower.matches(RegexTerms.STRUCTURAL_CURTAIN)
    );
  }

  private static boolean junk(String line) {
    if (line == null) return true;
    String t = line.trim();
    if (t.isEmpty()) return true;

    if (PAGE_NUMBER_ONLY.matcher(t).matches()) return true;

    if (t.equalsIgnoreCase("a") || t.equalsIgnoreCase("i")) return false;
    if (t.matches(RegexTerms.SHORT_INTERJECTION)) return false;
    if (t.length() <= 2) return true;
    int alnum = 0;
    for (int i = 0; i < t.length(); i++) {
      char c = t.charAt(i);
      if (Character.isLetterOrDigit(c)) alnum++;
    }
    return alnum == 0;
  }

  private static boolean badExtraction(String line) {
    if (line == null) return false;
    String t = norm(line);
    if (t.isEmpty()) return false;

    if (reversedOcr(t)) return true;

    int letters = 0;
    int weird = 0;
    int vowels = 0;
    int apostrophes = 0;
    int lowerCaseLetters = 0;

    for (int i = 0; i < t.length(); i++) {
      char ch = t.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
        if (Character.isLowerCase(ch)) lowerCaseLetters++;
        if (vowel(ch)) {
          vowels++;
        }
      } else if (ch == '\'') {
        apostrophes++;
      } else if (
        !Character.isWhitespace(ch) &&
        !Character.isDigit(ch) &&
        ".,?!:;-\"()[]".indexOf(ch) == -1
      ) {
        weird++;
      }
    }

    if (letters > 40 && vowels * 10 < letters) return true;
    if (
      t.length() > 120 && apostrophes > 8 && lowerCaseLetters > 40
    ) return true;
    if (
      t.length() > 180 && weird > 10 && lotsOfTinyGarbageWords(t)
    ) return true;
    if (t.length() > 250 && weird > 15 && apostrophes > 6) return true;
    if (t.length() > 350 && lotsOfTinyGarbageWords(t)) return true;
    if (t.length() > 500 && repeatedNonsenseScore(t) >= 18) return true;

    return false;
  }

  private static boolean reversedOcr(String line) {
    if (line == null) return false;
    String t = norm(line);
    if (t.length() < 120) return false;

    String[] words = t.split(RegexTerms.WHITESPACE);
    if (words.length < 20) return false;

    int suspicious = 0;
    int normal = 0;
    int oddCaps = 0;
    int oddPunctuation = 0;

    for (String word : words) {
      String cleaned = word.replaceAll(RegexTerms.NON_LETTER_QUOTE, "");
      if (cleaned.isEmpty()) continue;

      if (cleaned.length() <= 2) {
        suspicious++;
        continue;
      }

      if (oddCaps(cleaned)) {
        oddCaps++;
      }

      int letters = 0;
      int vowels = 0;
      for (int i = 0; i < cleaned.length(); i++) {
        char ch = Character.toLowerCase(cleaned.charAt(i));
        if (Character.isLetter(ch)) {
          letters++;
          if (vowel(ch)) vowels++;
        }
      }

      if (letters >= 5 && vowels == 0) {
        suspicious++;
      } else if (letters >= 4 && vowels > 0) {
        normal++;
      }

      if (word.matches(RegexTerms.DOUBLE_QUOTE_MARKS)) {
        oddPunctuation++;
      }
    }

    if (oddCaps >= 8 && suspicious >= 8) return true;
    if (oddPunctuation >= 5 && suspicious >= 8) return true;
    return suspicious >= 12 && suspicious > normal;
  }

  private static boolean oddCaps(String word) {
    if (word == null || word.length() < 5) return false;

    int upper = 0;
    int lower = 0;
    int switches = 0;
    char prevType = 0;

    for (int i = 0; i < word.length(); i++) {
      char ch = word.charAt(i);
      if (!Character.isLetter(ch)) continue;

      char type = Character.isUpperCase(ch) ? 'U' : 'L';
      if (type == 'U') upper++;
      if (type == 'L') lower++;

      if (prevType != 0 && prevType != type) {
        switches++;
      }
      prevType = type;
    }

    return upper >= 2 && lower >= 2 && switches >= 3;
  }

  private static boolean vowel(char ch) {
    char c = Character.toLowerCase(ch);
    return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
  }

  private static int repeatedNonsenseScore(String line) {
    String[] words = line.split(RegexTerms.WHITESPACE);
    Map<String, Integer> counts = new HashMap<>();
    int score = 0;

    for (String word : words) {
      String cleaned = word.replaceAll(RegexTerms.NON_LETTER, "").toUpperCase();
      if (cleaned.length() < 5) continue;

      int vowels = 0;
      for (int i = 0; i < cleaned.length(); i++) {
        if (vowel(cleaned.charAt(i))) vowels++;
      }

      boolean odd =
        vowels == 0 || oddCaps(word) || cleaned.matches(RegexTerms.CONSONANT_RUN);
      if (!odd) continue;

      int count = counts.getOrDefault(cleaned, 0) + 1;
      counts.put(cleaned, count);
      if (count >= 2) score += count;
    }

    return score;
  }

  private static boolean lotsOfTinyGarbageWords(String line) {
    String[] words = line.split(RegexTerms.WHITESPACE);
    if (words.length < 40) return false;

    int suspicious = 0;
    for (String word : words) {
      String cleaned = word.replaceAll(RegexTerms.NON_LETTER, "");
      if (cleaned.length() == 0) continue;
      if (cleaned.length() <= 2) {
        suspicious++;
        continue;
      }
      int vowels = 0;
      for (int i = 0; i < cleaned.length(); i++) {
        if (vowel(cleaned.charAt(i))) vowels++;
      }
      if (cleaned.length() >= 6 && vowels == 0) suspicious++;
    }

    return suspicious * 3 >= words.length;
  }

  private static boolean headerFooter(String line) {
    String t = norm(line);
    if (t.isEmpty()) return false;

    if (PAGE_NUMBER_ONLY.matcher(t).matches()) return true;

    if (
      PAGE_TITLE_NUMBER.matcher(t).matches() &&
      t.length() < 35 &&
      mostlyUpper(t)
    ) {
      String[] words = t.split(RegexTerms.WHITESPACE);
      if (words.length >= 3) {
        return true;
      }
    }

    if (t.matches(RegexTerms.NUMBER_CAPS_NUMBER)) {
      return true;
    }

    if (t.matches(RegexTerms.STRUCTURAL_CHARACTERS)) return false;
    if (t.matches(RegexTerms.STRUCTURAL_SOUNDS)) return false;
    if (t.matches(RegexTerms.STRUCTURAL_AT_RISE)) return false;
    if (t.matches(RegexTerms.STRUCTURAL_EPISODE_CI)) return false;
    if (t.matches(RegexTerms.STRUCTURAL_SCENE)) return false;

    return false;
  }

  private static boolean mostlyUpper(String line) {
    if (line == null) return false;
    int letters = 0;
    int uppercase = 0;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
        if (Character.isUpperCase(ch)) uppercase++;
      }
    }
    return letters > 0 && uppercase * 10 >= letters * 8;
  }

  private static List<String> removeRepeated(List<String> lines) {
    List<String> result = new ArrayList<>();

    Map<String, Integer> counts = countLines(lines);

    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line == null) {
        result.add("");
        continue;
      }

      String t = norm(line);
      int count = counts.getOrDefault(t, 0);

      if (structural(t)) {
        result.add(line);
        continue;
      }

      String cleanedLine = stripFurniture(line);
      String cleanedNormalized = norm(cleanedLine);

      if (alwaysSuppress(t)) {
        result.add("");
      } else if (suppressRepeated(lines, i, t, count)) {
        result.add("");
      } else if (
        lowValueArtifact(cleanedNormalized) &&
        !numericSpeakerLine(lines, i, cleanedNormalized)
      ) {
        result.add("");
      } else {
        result.add(cleanedLine);
      }
    }
    return result;
  }

  private static Map<String, Integer> countLines(List<String> lines) {
    Map<String, Integer> counts = new HashMap<>();
    if (lines == null) return counts;

    for (String line : lines) {
      String t = norm(line);
      if (t.isEmpty()) continue;
      counts.put(t, counts.getOrDefault(t, 0) + 1);
    }

    return counts;
  }

  private static String stripFurniture(String line) {
    if (line == null) return "";

    String cleaned = line.trim();

    cleaned = cleaned.replaceAll(RegexTerms.REPLACEMENT_CHAR, "");
    cleaned = cleaned.replaceAll(RegexTerms.BROKEN_FFFE_CHARS, "");

    cleaned = cleaned.replaceAll(
      RegexTerms.HEADING_WITH_TRAILING_PAGE_NUMBER,
      "$1"
    );

    cleaned = cleaned.replaceAll(
      RegexTerms.SCRIPT_LEADING_NAME_BLEED,
      ""
    );

    if (!structural(cleaned) && !SpeakerDetector.looksLike(cleaned)) {
      cleaned = cleaned.replaceAll(
        RegexTerms.SCRIPT_TRAILING_NAME_BLEED,
        ""
      );
    }

    return cleaned.trim();
  }

  private static boolean alwaysSuppress(String line) {
    if (line == null) return false;
    String t = norm(line);
    if (t.isEmpty()) return false;
    if (structural(t)) return false;

    if (t.matches(RegexTerms.PAGE_MARKER_PARSED_TEXT)) return true;
    if (t.matches(RegexTerms.PAGE_MARKER_REGION)) return true;
    if (t.matches(RegexTerms.PAGE_MARKER_IMAGE)) return true;

    if (PAGE_NUMBER_ONLY.matcher(t).matches()) return true;

    if (t.matches(RegexTerms.PUNCTUATION_RUN_ONLY)) return true;

    if (t.matches(RegexTerms.CONTAINS_ISBN_CI)) return true;
    if (t.matches(RegexTerms.CONTAINS_WWW_CI)) return true;
    if (t.matches(RegexTerms.CONTAINS_WEB_TLD_CI)) return true;

    return false;
  }

  private static boolean lowValueArtifact(String line) {
    if (line == null) return false;
    String t = norm(line);
    if (t.isEmpty()) return false;
    if (structural(t)) return false;

    if (SpeakerDetector.looksLike(t)) return false;
    if (
      StageDetector.is(t, null) || StageDetector.strong(t, null)
    ) return false;
    if (playableDialogue(t)) return false;
    if (alwaysSuppress(t)) return false;

    int letters = 0;
    int digits = 0;
    int punctuation = 0;
    int weird = 0;

    for (int i = 0; i < t.length(); i++) {
      char ch = t.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
      } else if (Character.isDigit(ch)) {
        digits++;
      } else if (!Character.isWhitespace(ch)) {
        punctuation++;
        if (!".,;:!?()[]{}'\"-/–—&".contains(String.valueOf(ch))) {
          weird++;
        }
      }
    }

    if (t.length() <= 3 && letters <= 1) return true;
    if (letters == 0 && digits > 0 && punctuation >= digits) return true;
    if (letters <= 2 && punctuation + digits >= 4) return true;
    if (weird >= 3 && letters <= 6) return true;

    return false;
  }

  private static boolean numericSpeakerLine(
    List<String> lines,
    int index,
    String line
  ) {
    String t = norm(line);
    if (t.isEmpty() || PAGE_NUMBER_ONLY.matcher(t).matches()) {
      return false;
    }

    int digits = 0;
    int letters = 0;
    for (int i = 0; i < t.length(); i++) {
      char ch = t.charAt(i);
      if (Character.isDigit(ch)) digits++;
      if (Character.isLetter(ch)) letters++;
    }

    if (digits == 0 || letters > 0) {
      return false;
    }

    for (int i = index - 1; i >= 0 && i >= index - 4; i--) {
      String prev = norm(lines.get(i));
      if (prev.isEmpty()) {
        continue;
      }
      if (SpeakerDetector.looksLike(prev)) {
        return true;
      }
      if (prev.startsWith("(") && prev.endsWith(")")) {
        continue;
      }
      break;
    }

    return false;
  }

  private static boolean suppressRepeated(
    List<String> lines,
    int index,
    String line,
    int count
  ) {
    if (line == null || line.isEmpty()) return false;
    if (count < 3) return false;
    if (!repeatedHeaderFooter(line)) return false;

    return !SpeakerDetector.looksLike(line) && !roleHeading(line);
  }

  private static boolean roleHeading(String line) {
    String t = norm(line).replaceAll(RegexTerms.TRAILING_DOT_COLON, "").trim();
    if (t.isEmpty() || t.length() > 70) return false;

    if (t.contains("/")) {
      String[] parts = t.split(RegexTerms.SLASH);
      if (parts.length < 2) return false;
      for (String part : parts) {
        if (!roleHeading(part)) return false;
      }
      return true;
    }

    if (!t.matches(RegexTerms.ROLE_HEADING_SHAPE)) return false;

    int letters = 0;
    int uppercase = 0;
    for (int i = 0; i < t.length(); i++) {
      char ch = t.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
        if (Character.isUpperCase(ch)) uppercase++;
      }
    }

    if (letters < 2) return false;
    if (uppercase * 10 < letters * 7) return false;

    String[] words = t.split(RegexTerms.WHITESPACE);
    if (words.length > 6) return false;

    boolean hasRoleWord = false;
    for (String word : words) {
      if (roleWord(word)) {
        hasRoleWord = true;
        break;
      }
    }

    boolean hasNumber = t.matches(RegexTerms.CONTAINS_SMALL_NUMBER);
    boolean articlePrefixed = t.matches(
      RegexTerms.SPEAKER_ARTICLE_HEADING_PATTERN
    );

    return hasRoleWord || hasNumber || articlePrefixed;
  }

  private static boolean roleWord(String word) {
    String w =
      word == null ? "" : word.replaceAll(RegexTerms.NON_LETTER, "").toUpperCase();
    return (
      w.equals("CLERK") ||
      w.equals("REPORTER") ||
      w.equals("VOICE") ||
      w.equals("LAWYER") ||
      w.equals("JUDGE") ||
      w.equals("PRIEST") ||
      w.equals("MOTHER") ||
      w.equals("FATHER") ||
      w.equals("HUSBAND") ||
      w.equals("WIFE") ||
      w.equals("MAN") ||
      w.equals("WOMAN") ||
      w.equals("GIRL") ||
      w.equals("BOY") ||
      w.equals("GUARD") ||
      w.equals("BELLBOY") ||
      w.equals("JANITOR") ||
      w.equals("MATRON") ||
      w.equals("DOCTOR") ||
      w.equals("NURSE") ||
      w.equals("OFFICER")
    );
  }

  private static boolean playableDialogue(String line) {
    String t = norm(line);
    if (t.isEmpty()) return false;
    if (SpeakerDetector.looksLike(t)) return false;
    if (headerFooter(t)) return false;
    if (badExtraction(t)) return false;
    if (alwaysSuppress(t)) return false;

    int letters = 0;
    int lowercase = 0;
    for (int i = 0; i < t.length(); i++) {
      char ch = t.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
        if (Character.isLowerCase(ch)) lowercase++;
      }
    }

    if (letters < 2) return false;
    if (lowercase == 0 && t.length() <= 45) return false;

    return true;
  }

  private static boolean repeatedHeaderFooter(String line) {
    if (line.isEmpty()) return false;
    if (line.length() > 60) return false;
    if (!hasLetter(line)) return false;
    if (!mostlyUpper(line)) return false;

    String[] words = line.split(RegexTerms.WHITESPACE);
    if (words.length > 6) return false;

    if (line.matches(RegexTerms.CONTAINS_DIGIT)) return true;
    if (words.length >= 3) return true;

    return words.length <= 2 && line.length() <= 35;
  }

  private static boolean hasLetter(String s) {
    if (s == null) return false;
    for (int i = 0; i < s.length(); i++) {
      if (Character.isLetter(s.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  private static boolean joinHyphen(String line, String next) {
    if (line == null || next == null) return false;
    String a = norm(line);
    String b = norm(next);
    if (a.isEmpty() || b.isEmpty()) return false;
    if (!startsWithLetter(b)) return false;

    if (mostlyUpper(b) && b.length() <= 35) return false;
    if (ALL_CAPS_RUN.matcher(b).matches() && b.length() <= 35) return false;

    String lastWord = a.substring(0, a.length() - 1).replaceAll(RegexTerms.AFTER_LAST_SPACE, "");
    String firstWord = b.replaceAll(RegexTerms.BEFORE_FIRST_SPACE, "");
    if (lastWord.length() <= 1 || firstWord.length() <= 1) return false;

    return (
      Character.isLowerCase(lastWord.charAt(0)) ||
      Character.isLowerCase(firstWord.charAt(0))
    );
  }

  private static boolean startsWithLetter(String s) {
    if (s == null) return false;
    String t = s.trim();
    if (t.isEmpty()) return false;
    return Character.isLetter(t.charAt(0));
  }
}
