import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ScriptPreProcess {

  private static final Pattern PAGE_NUMBER_ONLY = Pattern.compile("^\\d{1,4}$");
  private static final Pattern PAGE_TITLE_NUMBER = Pattern.compile(
    "^.+\\s+\\d{1,4}$"
  );
  private static final Pattern INLINE_PAGE_HEADER = Pattern.compile(
    "\\b\\d{1,4}\\s+(?:[A-Z][A-Z.'-]*\\s+){1,8}\\d{1,4}\\b"
  );
  private static final Pattern ZERO_WIDTH = Pattern.compile(
    "[\\u200B\\u200C\\u200D\\uFEFF\\u00AD]"
  );
  private static final Pattern PAGE_RANGE_HEADER = Pattern.compile(
    "\\b\\d{1,4}\\s+[A-Z][A-Z .'-]{2,80}\\s+\\d{1,4}\\b"
  );
  private static final Pattern ALL_CAPS_RUN = Pattern.compile(
    "\\b[A-Z][A-Z.'-]*(?:\\s+[A-Z][A-Z.'-]*){0,5}\\b"
  );

  public static String clean(String text) {
    if (text == null || text.isEmpty()) return "";
    String normalized = raw(text);
    String[] lines = normalized.split("\n", -1);
    List<String> cleanedLines = new ArrayList<>();

    StringBuilder out = new StringBuilder(normalized.length());

    for (int i = 0; i < lines.length; i++) {
      String raw = lines[i] == null ? "" : lines[i];
      if (raw.trim().isEmpty()) {
        cleanedLines.add("");
        continue;
      }
      String line = scrub(norm(raw));
      if (
        !structural(line) &&
        (junk(line) || badExtraction(line) || headerFooter(line))
      ) {
        cleanedLines.add("");
        continue;
      }
      if (line.endsWith("-") && (i + 1) < lines.length) {
        String nextRaw = lines[i + 1] == null ? "" : lines[i + 1];
        String next = scrub(norm(nextRaw));

        if (joinHyphen(line, next)) {
          String joined = line.substring(0, line.length() - 1) + next;
          joined = joined.replaceAll("\\s+", " ").trim();
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
    return out.toString().replaceFirst("\\s+$", "");
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
      .replaceAll("\\s+", " ");
  }

  private static String scrub(String line) {
    if (line == null || line.isEmpty()) return "";

    String cleaned = line;

    cleaned = INLINE_PAGE_HEADER.matcher(cleaned).replaceAll(" ");
    cleaned = PAGE_RANGE_HEADER.matcher(cleaned).replaceAll(" ");

    return cleaned.replaceAll("\\s+", " ").trim();
  }

  private static boolean structural(String line) {
    String t = norm(line);
    if (t.isEmpty()) return false;

    if (SpeakerDetector.looksLike(t)) return true;
    if (StageDetector.is(t, null) || StageDetector.strong(t, null)) return true;

    String lower = t.toLowerCase();
    return (
      lower.matches("^characters?:?.*$") ||
      lower.matches("^sounds?:?.*$") ||
      lower.matches("^scene:.*$") ||
      lower.matches("^at rise\\b.*$") ||
      lower.matches("^at the rise\\b.*$") ||
      lower.matches("^before the curtain\\b.*$") ||
      lower.matches("^episode\\s+[a-z0-9ivx -]+.*$") ||
      lower.matches("^act\\s+[a-z0-9ivx -]+.*$") ||
      lower.matches("^lights?\\b.*$") ||
      lower.matches("^blackout\\b.*$") ||
      lower.matches("^curtain\\b.*$")
    );
  }

  private static boolean junk(String line) {
    if (line == null) return true;
    String t = line.trim();
    if (t.isEmpty()) return true;

    if (PAGE_NUMBER_ONLY.matcher(t).matches()) return true;

    if (t.equalsIgnoreCase("a") || t.equalsIgnoreCase("i")) return false;
    if (t.matches("(?i)^(no|go|oh|yes|ok|hi)$")) return false;
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

    String[] words = t.split("\\s+");
    if (words.length < 20) return false;

    int suspicious = 0;
    int normal = 0;
    int oddCaps = 0;
    int oddPunctuation = 0;

    for (String word : words) {
      String cleaned = word.replaceAll("[^A-Za-z']", "");
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

      if (word.matches(".*['\"‘’“”].*['\"‘’“”].*")) {
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
    String[] words = line.split("\\s+");
    Map<String, Integer> counts = new HashMap<>();
    int score = 0;

    for (String word : words) {
      String cleaned = word.replaceAll("[^A-Za-z]", "").toUpperCase();
      if (cleaned.length() < 5) continue;

      int vowels = 0;
      for (int i = 0; i < cleaned.length(); i++) {
        if (vowel(cleaned.charAt(i))) vowels++;
      }

      boolean odd =
        vowels == 0 || oddCaps(word) || cleaned.matches(".*[QXZ]{2,}.*");
      if (!odd) continue;

      int count = counts.getOrDefault(cleaned, 0) + 1;
      counts.put(cleaned, count);
      if (count >= 2) score += count;
    }

    return score;
  }

  private static boolean lotsOfTinyGarbageWords(String line) {
    String[] words = line.split("\\s+");
    if (words.length < 40) return false;

    int suspicious = 0;
    for (String word : words) {
      String cleaned = word.replaceAll("[^A-Za-z]", "");
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
      String[] words = t.split("\\s+");
      if (words.length >= 3) {
        return true;
      }
    }

    if (t.matches("^\\d{1,4}\\s+[A-Z][A-Z .'-]{3,}\\s+\\d{1,4}$")) {
      return true;
    }

    if (t.matches("(?i)^characters?:?.*$")) return false;
    if (t.matches("(?i)^sounds?:?.*$")) return false;
    if (t.matches("(?i)^at rise\\b.*$")) return false;
    if (t.matches("(?i)^episode\\s+\\w+.*$")) return false;
    if (t.matches("(?i)^scene:.*$")) return false;

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
      } else if (lowValueArtifact(cleanedNormalized)) {
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

    cleaned = cleaned.replaceAll("\\uFFFD", "");
    cleaned = cleaned.replaceAll("[￾￿]", "");

    cleaned = cleaned.replaceAll(
      "^([A-Z][A-Z0-9'’\\- ]{1,45}(?:\\s*/\\s*[A-Z][A-Z0-9'’\\- ]{1,45})?\\.)\\s*\\d{1,4}$",
      "$1"
    );

    cleaned = cleaned.replaceAll(
      "^\\s*\\d{1,4}\\s+[A-Z][A-Z'’\\-]+(?:\\s+[A-Z][A-Z'’\\-]+){0,3}\\s+(?=[A-Z][A-Z0-9'’\\- ]{1,45}(?:[.:]|$))",
      ""
    );

    if (!structural(cleaned) && !SpeakerDetector.looksLike(cleaned)) {
      cleaned = cleaned.replaceAll(
        "\\s+[A-Z][A-Z'’\\-]+(?:\\s+[A-Z][A-Z'’\\-]+){0,3}\\s+\\d{1,4}\\s*$",
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

    if (t.matches("^<\\s*PARSED TEXT FOR PAGE:.*>$")) return true;
    if (t.matches("^<\\s*REGION\\s+\\d+\\s+OF\\s+\\d+.*>$")) return true;
    if (t.matches("^<\\s*IMAGE FOR PAGE:.*>$")) return true;

    if (PAGE_NUMBER_ONLY.matcher(t).matches()) return true;

    if (t.matches("^[|\\[\\]{}()_\\-–—=+~`'\".,:;\\s]{2,}$")) return true;

    if (t.matches("(?i).*\\bISBN\\b.*")) return true;
    if (t.matches("(?i).*\\bWWW\\..*")) return true;
    if (t.matches("(?i).*\\.(COM|CO\\.UK|ORG|NET)\\b.*")) return true;

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

  private static boolean suppressRepeated(
    List<String> lines,
    int index,
    String line,
    int count
  ) {
    if (line == null || line.isEmpty()) return false;
    if (count < 3) return false;
    if (!repeatedHeaderFooter(line)) return false;

    if (SpeakerDetector.looksLike(line) || roleHeading(line)) {
      return false;
    }

    return true;
  }

  private static boolean roleHeading(String line) {
    String t = norm(line).replaceAll("[.:]+$", "").trim();
    if (t.isEmpty() || t.length() > 70) return false;

    if (t.contains("/")) {
      String[] parts = t.split("/");
      if (parts.length < 2) return false;
      for (String part : parts) {
        if (!roleHeading(part)) return false;
      }
      return true;
    }

    if (!t.matches("[A-Za-z][A-Za-z0-9 .'’\\-]*")) return false;

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

    String[] words = t.split("\\s+");
    if (words.length > 6) return false;

    boolean hasRoleWord = false;
    for (String word : words) {
      if (roleWord(word)) {
        hasRoleWord = true;
        break;
      }
    }

    boolean hasNumber = t.matches(".*\\b\\d{1,3}\\b.*");
    boolean articlePrefixed = t.matches(
      "(?i)^(A|AN|THE)\\s+[A-Z][A-Z0-9 .'’\\-]{1,50}$"
    );

    return hasRoleWord || hasNumber || articlePrefixed;
  }

  private static boolean roleWord(String word) {
    String w =
      word == null ? "" : word.replaceAll("[^A-Za-z]", "").toUpperCase();
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

    String[] words = line.split("\\s+");
    if (words.length > 6) return false;

    if (line.matches(".*\\d.*")) return true;
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

    String lastWord = a.substring(0, a.length() - 1).replaceAll(".*\\s+", "");
    String firstWord = b.replaceAll("\\s+.*", "");
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

  public static String text(String raw) {
    return clean(raw);
  }
}
