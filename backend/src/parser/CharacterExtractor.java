package parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import parser.detect.*;
import parser.session.ParserSessionStore;
import util.RegexTerms;
import util.TextNormalizer;

public class CharacterExtractor {

  public static final Set<String> BAD_HEADINGS = Set.of(
    "ACT",
    "SCENE",
    "EPISODE",
    "MOMENT",
    "CHARACTER",
    "CHARACTERS",
    "CAST",
    "CONTENTS",
    "PREFACE",
    "CREDITS",
    "COPYRIGHT",
    "INT",
    "EXT",
    "I/E",
    "INT/EXT",
    "EST",
    "FADE IN",
    "FADE OUT",
    "CUT TO",
    "DISSOLVE TO",
    "BACK TO",
    "AT RISE",
    "SOUNDS",
    "SONG",
    "PHONE RINGS",
    "THE SCENE BLACKS OUT",
    "ENTER",
    "EXIT"
  );

  public static final Set<String> BAD_SHORT_LINES = Set.of(
    "A",
    "I",
    "O",
    "OH",
    "NO",
    "YES",
    "OK",
    "OLD",
    "NEW",
    "END",
    "O.K",
    "WELL",
    "SO",
    "WHAT",
    "WHY",
    "RIGHT",
    "NOTHING",
    "PLEASE",
    "LOOK",
    "COME BACK",
    "ALL RIGHT",
    "THAT'S RIGHT",
    "GOOD MORNING",
    "HOT DOG",
    "CONFERENCE",
    "PROCEED",
    "OBJECTION OVERRULED",
    "HASTE MAKES WASTE",
    "HEW TO THE LINE",
    "IT'S TIME",
    "NOT YET",
    "I DO",
    "I KNOW",
    "I SEE",
    "I DID NOT",
    "I DON'T KNOW",
    "ALL RIGHT WITH ME",
    "MR",
    "MRS",
    "MS",
    "MISS",
    "DR"
  );

  private static final int MAX_NAME_LENGTH = 45;
  private static final int MAX_NAME_WORDS = 5;

  private static final Set<String> BARE_NUMBER_WORDS = Set.of(
    "ONE",
    "TWO",
    "THREE",
    "FOUR",
    "FIVE",
    "SIX",
    "SEVEN",
    "EIGHT",
    "NINE",
    "TEN",
    "ELEVEN",
    "TWELVE",
    "THIRTEEN",
    "FOURTEEN",
    "FIFTEEN",
    "SIXTEEN",
    "SEVENTEEN",
    "EIGHTEEN",
    "NINETEEN",
    "TWENTY"
  );

  public static Set<String> load(ParserSessionStore session) {
    Set<String> saved = new LinkedHashSet<>();
    if (session == null) {
      return saved;
    }

    Path path = session.charsCsv();
    if (path == null || !Files.exists(path)) {
      return saved;
    }

    try {
      List<String> lines = Files.readAllLines(path);
      for (int i = 1; i < lines.size(); i++) {
        String line = lines.get(i);
        if (line == null || line.trim().isEmpty()) {
          continue;
        }

        String[] cells = line.split(RegexTerms.COMMA, -1);
        if (cells.length == 0) {
          continue;
        }

        String canonicalName = TextNormalizer.cleanName(
          unquoteCsvCell(cells[0])
        );
        boolean enabled =
          cells.length < 3 ||
          !unquoteCsvCell(cells[2]).equalsIgnoreCase("false");
        if (!canonicalName.isEmpty() && enabled) {
          saved.add(canonicalName);
        }
      }
    } catch (IOException e) {
      System.out.println("Could not load saved characters: " + e.getMessage());
    }

    return saved;
  }

  public static Map<String, String> loadAliases(ParserSessionStore session) {
    Map<String, String> aliases = new HashMap<>();
    if (session == null) {
      return aliases;
    }

    Path path = session.aliasesCsv();
    if (path == null || !Files.exists(path)) {
      return aliases;
    }

    try {
      List<String> lines = Files.readAllLines(path);
      for (int i = 1; i < lines.size(); i++) {
        String line = lines.get(i);
        if (line == null || line.trim().isEmpty()) {
          continue;
        }

        String[] cells = line.split(RegexTerms.COMMA, -1);
        if (cells.length < 2) {
          continue;
        }

        String alias = TextNormalizer.cleanName(unquoteCsvCell(cells[0]));
        String canonicalName = TextNormalizer.cleanName(
          unquoteCsvCell(cells[1])
        );
        boolean enabled =
          cells.length < 4 ||
          !unquoteCsvCell(cells[3]).equalsIgnoreCase("false");
        if (!alias.isEmpty() && !canonicalName.isEmpty() && enabled) {
          aliases.put(alias, canonicalName);
        }
      }
    } catch (IOException e) {
      System.out.println("Could not load saved aliases: " + e.getMessage());
    }

    return aliases;
  }

  public static void save(ParserSessionStore session, Set<String> chars) {
    if (session == null || chars == null) {
      return;
    }

    session.ensureFolders();
    List<String> lines = new ArrayList<>();
    lines.add("canonical_name,actor,enabled,notes");

    for (String ch : sort(chars)) {
      String cleaned = TextNormalizer.cleanName(ch);
      if (!cleaned.isEmpty()) {
        lines.add(csvCell(cleaned) + ",,true,");
      }
    }

    try {
      Files.write(session.charsCsv(), lines);
    } catch (IOException e) {
      System.out.println("Could not save characters: " + e.getMessage());
    }
  }

  public static void saveAliases(
    ParserSessionStore session,
    Map<String, String> aliases
  ) {
    if (session == null || aliases == null) {
      return;
    }

    session.ensureFolders();
    List<String> lines = new ArrayList<>();
    lines.add("alias,canonical_name,confidence,enabled,notes");

    List<String> sortedAliases = new ArrayList<>(aliases.keySet());
    sortedAliases.sort(String::compareTo);
    for (String alias : sortedAliases) {
      String cleanAlias = TextNormalizer.cleanName(alias);
      String canonical = TextNormalizer.cleanName(aliases.get(alias));
      if (!cleanAlias.isEmpty() && !canonical.isEmpty()) {
        lines.add(
          csvCell(cleanAlias) + "," + csvCell(canonical) + ",HIGH,true,"
        );
      }
    }

    try {
      Files.write(session.aliasesCsv(), lines);
    } catch (IOException e) {
      System.out.println("Could not save aliases: " + e.getMessage());
    }
  }

  public static Set<String> apply(
    Set<String> chars,
    Map<String, String> aliases
  ) {
    Set<String> result = new LinkedHashSet<>();
    if (chars != null) {
      for (String ch : chars) {
        String cleaned = TextNormalizer.cleanName(ch);
        if (!cleaned.isEmpty()) {
          result.add(cleaned);
        }
      }
    }

    if (aliases != null) {
      for (Map.Entry<String, String> entry : aliases.entrySet()) {
        String alias = TextNormalizer.cleanName(entry.getKey());
        String canonical = TextNormalizer.cleanName(entry.getValue());
        if (!alias.isEmpty() && !canonical.isEmpty()) {
          result.add(alias);
          result.add(canonical);
        }
      }
    }

    return result;
  }

  private static String csvCell(String text) {
    String safe = text == null ? "" : text;
    if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
      return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
    return safe;
  }

  private static String unquoteCsvCell(String text) {
    if (text == null) {
      return "";
    }
    String trimmed = text.trim();
    if (
      trimmed.length() >= 2 &&
      trimmed.startsWith("\"") &&
      trimmed.endsWith("\"")
    ) {
      return trimmed.substring(1, trimmed.length() - 1).replace("\"\"", "\"");
    }
    return trimmed;
  }

  public static Set<String> find(String text) {
    Map<String, Integer> counts = new HashMap<>();
    Map<String, Integer> headingUse = new HashMap<>();
    if (text == null) {
      text = "";
    }

    Set<String> headerPrefixes = headerPrefixes(text);
    List<String> furnitureTexts = new ArrayList<>();

    for (String raw : text.split(RegexTerms.NEWLINE)) {
      String line = TextNormalizer.norm(raw);
      if (PageFurnitureDetector.wrapped(line)) {
        furnitureTexts.add(PageFurnitureDetector.unwrap(line).toUpperCase());
        continue;
      }
      if (
        headerPrefixLine(line, headerPrefixes) ||
        StageDetector.skip(line) ||
        (StageDetector.junk(line) &&
          !heading(line) &&
          castListRole(line).isEmpty())
      ) {
        continue;
      }
      addHeadingUse(headingUse, line);
      addFromLine(counts, line);
    }

    Set<String> chars = keep(counts);
    chars.removeIf(name -> furnitureVocab(name, counts, furnitureTexts));
    chars.addAll(keepHeadingNames(headingUse));

    Map<String, Integer> usage = garbleUsage(text);
    for (int pass = 0; pass < 6; pass++) {
      Set<String> garbles = internalGarbles(chars, usage);
      if (garbles.isEmpty()) {
        break;
      }
      chars.removeAll(garbles);
    }

    return chars;
  }

  private static boolean furnitureVocab(
    String name,
    Map<String, Integer> counts,
    List<String> furnitureTexts
  ) {
    String clean = TextNormalizer.cleanName(name);
    if (clean.length() < 4 || counts.getOrDefault(clean, 0) > 15) {
      return false;
    }

    java.util.regex.Pattern word = java.util.regex.Pattern.compile(
      "\\b" + java.util.regex.Pattern.quote(clean) + "\\b"
    );

    int hits = 0;
    for (String furniture : furnitureTexts) {
      if (word.matcher(furniture).find() && ++hits >= 2) {
        return true;
      }
    }
    return false;
  }

  private static final java.util.regex.Pattern HEADER_NUMBER_LINE =
    java.util.regex.Pattern.compile(
      "^([A-Z][A-Z .'\\-]{2,40}?)\\s+(\\d{1,4})$"
    );

  private static Set<String> headerPrefixes(String text) {
    Map<String, Set<Integer>> numbersByPrefix = new HashMap<>();

    for (String raw : text.split(RegexTerms.NEWLINE)) {
      java.util.regex.Matcher m = HEADER_NUMBER_LINE.matcher(
        TextNormalizer.norm(raw)
      );
      if (!m.matches()) {
        continue;
      }
      numbersByPrefix
        .computeIfAbsent(m.group(1).trim(), k -> new HashSet<>())
        .add(Integer.parseInt(m.group(2)));
    }

    Set<String> prefixes = new HashSet<>();
    for (Map.Entry<String, Set<Integer>> entry : numbersByPrefix.entrySet()) {
      Set<Integer> numbers = entry.getValue();
      int min = Integer.MAX_VALUE;
      for (int n : numbers) {
        min = Math.min(min, n);
      }
      if (numbers.size() >= 4 && min >= 10) {
        prefixes.add(entry.getKey());
      }
    }
    return prefixes;
  }

  private static boolean headerPrefixLine(String line, Set<String> prefixes) {
    if (prefixes.isEmpty()) {
      return false;
    }
    java.util.regex.Matcher m = HEADER_NUMBER_LINE.matcher(line);
    if (m.matches() && prefixes.contains(m.group(1).trim())) {
      return true;
    }
    for (String prefix : prefixes) {
      if (line.equals(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, Integer> garbleUsage(String text) {
    Map<String, Integer> use = new HashMap<>();
    if (text == null) {
      return use;
    }

    Set<String> headerPrefixes = headerPrefixes(text);

    for (String raw : text.split(RegexTerms.NEWLINE)) {
      String line = TextNormalizer.norm(raw);
      if (
        PageFurnitureDetector.wrapped(line) ||
        headerPrefixLine(line, headerPrefixes) ||
        StageDetector.skip(line) ||
        (StageDetector.junk(line) &&
          !heading(line) &&
          castListRole(line).isEmpty())
      ) {
        continue;
      }

      addHeadingUse(use, line);

      if (looseSpeakerCut(line) < 0 && looseHeadingShape(line)) {
        String name = TextNormalizer.cleanName(line);
        if (!name.isEmpty()) {
          use.merge(name, 1, Integer::sum);
        }
      }
    }

    return use;
  }

  private static Set<String> internalGarbles(
    Set<String> chars,
    Map<String, Integer> headingUse
  ) {
    Set<String> garbles = new HashSet<>();

    Set<String> cleanChars = new HashSet<>();
    for (String name : chars) {
      cleanChars.add(TextNormalizer.cleanName(name));
    }

    for (String candidate : chars) {
      String garble = TextNormalizer.cleanName(candidate);
      if (garble.length() < 4) {
        continue;
      }

      String match = uniqueGarbleTarget(
        garble,
        headingUse.getOrDefault(garble, 0),
        cleanChars,
        headingUse
      );
      if (!match.isEmpty()) {
        garbles.add(candidate);
      }
    }

    return garbles;
  }

  public static Map<String, String> garbleAliases(
    String text,
    Set<String> canonical
  ) {
    Map<String, String> aliases = new HashMap<>();
    if (text == null || canonical == null || canonical.isEmpty()) {
      return aliases;
    }

    Map<String, Integer> headingUse = garbleUsage(text);

    Set<String> cleanCanonical = new HashSet<>();
    for (String name : canonical) {
      cleanCanonical.add(TextNormalizer.cleanName(name));
    }

    for (Map.Entry<String, Integer> entry : headingUse.entrySet()) {
      String garble = entry.getKey();
      if (garble.length() < 4) {
        continue;
      }

      if (cleanCanonical.contains(garble)) {
        String parent = uniqueVariantParent(
          garble,
          entry.getValue(),
          cleanCanonical,
          headingUse
        );
        if (!parent.isEmpty()) {
          aliases.put(garble, parent);
        }
        continue;
      }

      String match = uniqueGarbleTarget(
        garble,
        entry.getValue(),
        cleanCanonical,
        headingUse
      );
      if (!match.isEmpty()) {
        aliases.put(garble, match);
      }
    }

    return aliases;
  }

  private static String uniqueVariantParent(
    String variant,
    int variantUse,
    Set<String> canonical,
    Map<String, Integer> headingUse
  ) {
    String match = "";
    for (String name : canonical) {
      if (name.equals(variant)) {
        continue;
      }
      if (!firstWordOf(variant, name) && !lastWordOf(variant, name)) {
        continue;
      }
      if (!match.isEmpty()) {
        return "";
      }
      match = name;
    }

    if (match.isEmpty()) {
      return "";
    }

    int parentUse = headingUse.getOrDefault(match, 0);
    if (parentUse < Math.max(3, 2 * variantUse)) {
      return "";
    }

    return match;
  }

  private static String uniqueGarbleTarget(
    String garble,
    int garbleUse,
    Set<String> canonical,
    Map<String, Integer> headingUse
  ) {
    String match = "";
    boolean firstWordMatch = false;
    for (String name : canonical) {
      if (name.equals(garble)) {
        continue;
      }
      boolean firstWord = firstWordOf(garble, name) || lastWordOf(garble, name);
      if (!garbleOf(garble, name) && !firstWord) {
        continue;
      }
      if (!match.isEmpty()) {
        return "";
      }
      match = name;
      firstWordMatch = firstWord;
    }

    if (match.isEmpty()) {
      return "";
    }

    int canonicalUse = headingUse.getOrDefault(match, 0);
    int required = firstWordMatch ? 3 : Math.max(3, 3 * garbleUse);
    if (canonicalUse < required) {
      return "";
    }

    return match;
  }

  private static boolean firstWordOf(String word, String name) {
    if (word.length() < 4 || word.contains(" ")) {
      return false;
    }
    int space = name.indexOf(' ');
    return space > 0 && name.substring(0, space).equals(word);
  }

  private static boolean lastWordOf(String word, String name) {
    if (word.length() < 4 || word.contains(" ")) {
      return false;
    }
    int space = name.lastIndexOf(' ');
    return space > 0 && name.substring(space + 1).equals(word);
  }

  private static boolean garbleOf(String garble, String name) {
    if (
      garble.isEmpty() ||
      name.isEmpty() ||
      garble.equals(name) ||
      name.length() < garble.length() ||
      name.length() - garble.length() > 5
    ) {
      return false;
    }

    if (name.length() == garble.length()) {
      return withinOneEdit(garble, name);
    }

    String prefix = name.substring(0, garble.length());
    String suffix = name.substring(name.length() - garble.length());
    return withinOneEdit(garble, prefix) || withinOneEdit(garble, suffix);
  }

  private static boolean withinOneEdit(String a, String b) {
    if (a.length() != b.length()) {
      return false;
    }
    int diff = 0;
    for (int i = 0; i < a.length(); i++) {
      if (a.charAt(i) != b.charAt(i) && ++diff > 1) {
        return false;
      }
    }
    return true;
  }

  private static final int HEADING_USE_MIN = 2;

  private static void addHeadingUse(Map<String, Integer> use, String line) {
    String cleaned = TextNormalizer.norm(line);
    String name = "";

    int cut = looseSpeakerCut(cleaned);
    if (cut > 0 && cut < cleaned.length() - 1) {
      name = TextNormalizer.cleanName(cleaned.substring(0, cut));
    } else if (
      cut > 0 && cut == cleaned.length() - 1 && cleaned.charAt(cut) == ':'
    ) {
      name = TextNormalizer.cleanName(cleaned.substring(0, cut));
    } else {
      name = TextNormalizer.cleanName(castDashRole(cleaned));
    }

    if (!name.isEmpty()) {
      use.put(name, use.getOrDefault(name, 0) + 1);
    }
  }

  private static int looseSpeakerCut(String line) {
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch != '.' && ch != ':') {
        continue;
      }
      String before = line.substring(0, i);
      if (ch == '.') {
        String lastWord = before
          .substring(before.lastIndexOf(' ') + 1)
          .toUpperCase();
        if (SpeakerDetector.titleAbbreviation(lastWord)) {
          continue;
        }
      }
      if (looseHeadingShape(before)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean looseHeadingShape(String raw) {
    String name = TextNormalizer.rawHeadingName(raw);
    return (
      !name.isEmpty() &&
      TextNormalizer.hasLetter(name) &&
      name.length() <= MAX_NAME_LENGTH &&
      !tooManyWords(name) &&
      mostlyUppercaseLetters(name) &&
      containsOnlyNameCharacters(name)
    );
  }

  private static Set<String> keepHeadingNames(Map<String, Integer> use) {
    Set<String> out = new HashSet<>();
    for (Map.Entry<String, Integer> entry : use.entrySet()) {
      String name = entry.getKey();
      if (
        entry.getValue() >= HEADING_USE_MIN &&
        !BAD_HEADINGS.contains(name) &&
        !BAD_SHORT_LINES.contains(name) &&
        !articleOnly(name) &&
        !badPhrase(name) &&
        !name.startsWith("ENTER ") &&
        !name.startsWith("EXIT ") &&
        !authorOrPublisher(name) &&
        !repeated(name) &&
        !bareNumberOrShort(name)
      ) {
        out.add(name);
      }
    }
    return out;
  }

  private static void addFromLine(Map<String, Integer> counts, String line) {
    String cleaned = TextNormalizer.norm(line);

    int earlyDot = SpeakerDetector.speakerDot(cleaned);
    if (earlyDot > 0 && earlyDot < cleaned.length() - 1) {
      addCandidate(counts, cleaned.substring(0, earlyDot));
      return;
    }

    String dashRole = castDashRole(cleaned);
    if (!dashRole.isEmpty()) {
      addCandidate(counts, dashRole);
      return;
    }

    String role = castRole(cleaned);
    if (!role.isEmpty()) {
      addRole(counts, role);
      return;
    }

    String castListed = castListRole(cleaned);
    if (!castListed.isEmpty()) {
      addCandidate(counts, castListed);
      return;
    }

    int colon = cleaned.indexOf(":");
    if (colon > 0) {
      addCandidate(counts, cleaned.substring(0, colon));
      return;
    }

    int dot = SpeakerDetector.speakerDot(cleaned);
    if (dot > 0) {
      addCandidate(counts, cleaned.substring(0, dot));
      return;
    }

    addCandidate(counts, cleaned);
  }

  private static String castRole(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty() || !nameCase(cleaned)) {
      return "";
    }

    String withoutActor = cleaned
      .replaceFirst(RegexTerms.ACTOR_SUFFIX, "")
      .trim();
    if (!withoutActor.equals(cleaned) && roleName(withoutActor)) {
      return withoutActor;
    }

    if (roleName(cleaned)) {
      return cleaned;
    }

    return "";
  }

  static String castDashRole(String line) {
    String cleaned = TextNormalizer.norm(line);
    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
      "^([A-Z][A-Z .,'\\-]{1,34}?)\\s+[-–—]\\s+\\S.{4,}$"
    ).matcher(cleaned);
    if (!m.find()) {
      return "";
    }
    String name = m.group(1).trim();
    while (name.endsWith(",") || name.endsWith(".")) {
      name = name.substring(0, name.length() - 1).trim();
    }
    if (!looseHeadingShape(name)) {
      return "";
    }
    return name;
  }

  static String castListRole(String line) {
    String cleaned = TextNormalizer.norm(line);
    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
      "^([A-Z][A-Z .,'\\-]{1,34}?)[,.]\\s+\\S.*[A-Z][a-z]+[ .]+[A-Z][a-z]+\\.?$"
    ).matcher(cleaned);
    if (!m.find()) {
      return "";
    }
    String name = TextNormalizer.cleanName(m.group(1));
    if (!heading(name) || name.split(RegexTerms.WHITESPACE).length > 4) {
      return "";
    }
    return name;
  }

  private static boolean nameCase(String raw) {
    int letters = 0;
    int caps = 0;
    boolean wordStart = true;
    boolean titled = true;

    for (int i = 0; i < raw.length(); i++) {
      char ch = raw.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
        if (Character.isUpperCase(ch)) {
          caps++;
        } else if (wordStart) {
          titled = false;
        }
        wordStart = false;
      } else {
        wordStart = !Character.isDigit(ch) && ch != '\'';
      }
    }

    return letters > 0 && (caps * 10 >= letters * 8 || titled);
  }

  private static boolean roleName(String raw) {
    String clean = TextNormalizer.cleanName(raw);
    if (!validNameShape(clean)) {
      return false;
    }

    String[] words = clean.split(RegexTerms.WHITESPACE);
    if (words.length > MAX_NAME_WORDS + 1) {
      return false;
    }

    if (clean.contains("/")) {
      String[] parts = clean.split(RegexTerms.WHITESPACE_AROUND_SLASH);
      if (parts.length < 2) {
        return false;
      }
      for (String part : parts) {
        if (!roleName(part)) {
          return false;
        }
      }
      return true;
    }

    boolean hasRoleWord = roleWord(clean) || functionRole(clean);
    boolean hasNumber = clean.matches(RegexTerms.CONTAINS_SMALL_NUMBER);
    boolean ordinalPrefixed = clean.matches(RegexTerms.ORDINAL_PREFIXED_NAME);
    boolean numberedSuffix = clean.matches(RegexTerms.NUMBER_WORD_SUFFIX_NAME);
    boolean articlePrefixed = clean.matches(RegexTerms.ARTICLE_PREFIXED_NAME);

    if (
      !(hasRoleWord ||
        hasNumber ||
        ordinalPrefixed ||
        numberedSuffix ||
        articlePrefixed)
    ) {
      return false;
    }

    return containsOnlyNameCharacters(raw);
  }

  private static String trueRoleName(String raw) {
    String clean = TextNormalizer.cleanName(raw);

    int slash = clean.indexOf("/");
    if (slash > 0) clean = clean.substring(0, slash).trim();
    return TextNormalizer.cleanName(stripArticlePrefix(clean));
  }

  private static void addRole(Map<String, Integer> counts, String raw) {
    String name = trueRoleName(raw);
    if (!roleName(name)) {
      return;
    }
    if (authorOrPublisher(name) || actorName(name) || headerOrFooter(name)) {
      return;
    }
    counts.put(name, counts.getOrDefault(name, 0) + 1);
  }

  private static Set<String> keep(Map<String, Integer> counts) {
    Set<String> chars = new HashSet<>();

    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
      if (keepCandidate(entry.getKey(), entry.getValue(), counts)) {
        chars.add(entry.getKey());
      }
    }

    return chars;
  }

  public static boolean heading(String text) {
    String raw = TextNormalizer.rawHeadingName(text);
    String name = TextNormalizer.cleanName(raw);

    if (!validNameShape(name)) {
      return false;
    }
    if (tooManyWords(raw)) {
      return false;
    }

    return mostlyUppercaseLetters(raw) && containsOnlyNameCharacters(raw);
  }

  private static String stripArticlePrefix(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty()) {
      return "";
    }

    String upper = cleaned.toUpperCase();
    if (upper.startsWith("THE ")) {
      return stripArticle(cleaned, 4);
    }
    if (upper.startsWith("AN ")) {
      return stripArticle(cleaned, 3);
    }
    if (upper.startsWith("A ")) {
      return stripArticle(cleaned, 2);
    }

    return cleaned;
  }

  private static String stripArticle(String line, int length) {
    String rest = TextNormalizer.norm(line.substring(length));
    return articleRole(rest) ? rest : line;
  }

  private static boolean articleRole(String name) {
    String clean = TextNormalizer.cleanName(name);
    if (clean.isEmpty() || clean.length() > MAX_NAME_LENGTH) {
      return false;
    }

    return roleName(clean);
  }

  private static boolean articleOnly(String name) {
    String n = TextNormalizer.cleanName(name);
    return n.equals("A") || n.equals("AN") || n.equals("THE");
  }

  private static boolean badPhrase(String name) {
    String n = TextNormalizer.cleanName(name);
    if (n.isEmpty()) {
      return true;
    }

    String[] w = n.split(RegexTerms.WHITESPACE);

    if (
      w.length >= 2 &&
      n.matches(RegexTerms.containsAnyWord(RegexTerms.CHARACTER_BAD_FURNITURE))
    ) {
      return true;
    }

    if (
      n.matches(
        RegexTerms.containsAnyWord(RegexTerms.CHARACTER_DIALOGUE_PRONOUN)
      )
    ) {
      return true;
    }

    if (
      n.matches(RegexTerms.containsAnyWord(RegexTerms.CHARACTER_DIALOGUE_VERB))
    ) {
      return true;
    }

    if (
      n.matches(RegexTerms.containsAnyWordIgnoreCase(RegexTerms.STAGE_ACTION))
    ) {
      return true;
    }

    if (w.length >= 4 && !rolePhrase(n)) {
      return true;
    }

    if (
      w.length >= 3 &&
      n.matches(
        RegexTerms.containsAnyWord(RegexTerms.CHARACTER_NAME_CONNECTOR)
      ) &&
      !rolePhrase(n)
    ) {
      return true;
    }

    return false;
  }

  private static boolean rolePhrase(String name) {
    String n = TextNormalizer.cleanName(name);
    return n.matches(RegexTerms.CHARACTER_ROLE_PHRASE_PATTERN);
  }

  private static boolean validNameShape(String name) {
    return (
      !name.isEmpty() &&
      TextNormalizer.hasLetter(name) &&
      name.length() <= MAX_NAME_LENGTH &&
      !BAD_HEADINGS.contains(name) &&
      !BAD_SHORT_LINES.contains(name) &&
      !articleOnly(name) &&
      !badPhrase(name) &&
      !name.startsWith("ENTER ") &&
      !name.startsWith("EXIT ") &&
      !authorOrPublisher(name) &&
      !actorName(name) &&
      !headerOrFooter(name)
    );
  }

  private static boolean tooManyWords(String raw) {
    int words = 0;
    for (String word : raw.split(RegexTerms.WHITESPACE)) {
      if (!word.isBlank()) {
        words++;
      }
    }
    return words > MAX_NAME_WORDS;
  }

  private static boolean mostlyUppercaseLetters(String raw) {
    int letters = 0;
    int caps = 0;

    for (int i = 0; i < raw.length(); i++) {
      char ch = raw.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
        if (Character.isUpperCase(ch)) {
          caps++;
        }
      }
    }

    return letters > 0 && caps * 10 >= letters * 8;
  }

  private static boolean containsOnlyNameCharacters(String raw) {
    for (int i = 0; i < raw.length(); i++) {
      char ch = raw.charAt(i);
      if (
        Character.isLetter(ch) ||
        Character.isWhitespace(ch) ||
        Character.isDigit(ch) ||
        ch == '-' ||
        ch == '\'' ||
        ch == '/' ||
        ch == '&' ||
        ch == '.'
      ) {
        continue;
      }
      return false;
    }
    return true;
  }

  private static void addCandidate(Map<String, Integer> counts, String raw) {
    if (!heading(raw)) {
      return;
    }

    String name = trueRoleName(raw);
    if (authorOrPublisher(name) || actorName(name) || headerOrFooter(name)) {
      return;
    }
    counts.put(name, counts.getOrDefault(name, 0) + 1);
  }

  private static boolean keepCandidate(
    String name,
    int count,
    Map<String, Integer> counts
  ) {
    name = TextNormalizer.cleanName(name);
    String[] words = name.split(RegexTerms.WHITESPACE);
    boolean flexibleRoleName = roleName(name);
    int requiredCount = flexibleRoleName ? 1 : 2;

    return (
      count >= requiredCount &&
      TextNormalizer.hasLetter(name) &&
      name.length() <= MAX_NAME_LENGTH &&
      words.length <= MAX_NAME_WORDS &&
      !BAD_HEADINGS.contains(name) &&
      !BAD_SHORT_LINES.contains(name) &&
      !articleOnly(name) &&
      !badPhrase(name) &&
      !name.startsWith("ENTER ") &&
      !name.startsWith("EXIT ") &&
      !authorOrPublisher(name) &&
      !actorName(name) &&
      !headerOrFooter(name) &&
      !repeated(name) &&
      !combinedName(name, count, counts) &&
      !subsumedByStrongerName(name, count, counts) &&
      !bareNumberOrShort(name)
    );
  }

  private static boolean bareNumberOrShort(String name) {
    String n = TextNormalizer.cleanName(name);
    String[] w = n.split(RegexTerms.WHITESPACE);
    if (w.length != 1) {
      return false;
    }
    String only = w[0];
    return (
      only.length() <= 2 ||
      BARE_NUMBER_WORDS.contains(only.toUpperCase()) ||
      only.matches("\\d+")
    );
  }

  private static boolean subsumedByStrongerName(
    String name,
    int count,
    Map<String, Integer> counts
  ) {
    if (count > 2) {
      return false;
    }

    String[] words = TextNormalizer.cleanName(name).split(
      RegexTerms.WHITESPACE
    );
    if (words.length < 2) {
      return false;
    }

    for (int len = words.length - 1; len >= 1; len--) {
      if (dominantHost(join(words, 0, len), count, counts)) {
        return true;
      }
      if (
        dominantHost(
          join(words, words.length - len, words.length),
          count,
          counts
        )
      ) {
        return true;
      }
    }
    return false;
  }

  private static boolean dominantHost(
    String host,
    int count,
    Map<String, Integer> counts
  ) {
    Integer hostCount = counts.get(host);
    if (hostCount == null) {
      return false;
    }
    return (
      hostCount >= 4 &&
      hostCount >= count * 4 &&
      (roleName(host) || heading(host))
    );
  }

  private static String join(String[] words, int from, int to) {
    return String.join(" ", Arrays.copyOfRange(words, from, to));
  }

  private static boolean authorOrPublisher(String name) {
    String clean = TextNormalizer.cleanName(name);
    if (clean.isEmpty()) {
      return true;
    }

    String upper = clean.toUpperCase();

    if (roleWord(upper) || functionRole(upper)) {
      return false;
    }

    if (
      upper.matches(
        RegexTerms.containsAnyWord(RegexTerms.AUTHOR_PUBLISHER_TERM)
      )
    ) {
      return true;
    }

    if (addressOrPlace(upper)) {
      return true;
    }

    if (
      upper.matches(
        RegexTerms.containsAnyWord(RegexTerms.AUTHOR_PUBLISHER_PHRASE)
      )
    ) {
      return true;
    }

    return false;
  }

  private static boolean functionRole(String name) {
    String upper = TextNormalizer.cleanName(name).toUpperCase();
    if (upper.isEmpty()) {
      return false;
    }

    return upper.matches(
      RegexTerms.containsAnyWord(RegexTerms.CHARACTER_FUNCTION_ROLE)
    );
  }

  private static boolean addressOrPlace(String upper) {
    if (upper == null || upper.isBlank()) {
      return false;
    }

    if (upper.matches(RegexTerms.US_STATE_SUFFIX)) {
      return true;
    }

    if (
      upper.matches(RegexTerms.DIRECTION_PLACE) &&
      !roleWord(upper) &&
      !functionRole(upper)
    ) {
      return true;
    }

    if (
      upper.matches(RegexTerms.containsAnyWord(RegexTerms.STREET_ADDRESS_TERM))
    ) {
      return true;
    }

    if (
      upper.matches(
        RegexTerms.containsAnyWord(RegexTerms.COUNTRY_OR_REGION_TERM)
      )
    ) {
      return true;
    }

    return false;
  }

  private static boolean actorName(String name) {
    String clean = TextNormalizer.cleanName(name);
    if (clean.isEmpty()) {
      return true;
    }

    if (clean.contains("/")) {
      return false;
    }

    String[] words = clean.split(RegexTerms.WHITESPACE);
    if (words.length < 2 || words.length > 3) {
      return false;
    }

    if (roleWord(clean) || functionRole(clean)) {
      return false;
    }

    int titleCaseWords = 0;
    for (String word : words) {
      if (word.matches(RegexTerms.TITLE_CASE_WORD)) {
        titleCaseWords++;
      }
    }

    return titleCaseWords == words.length;
  }

  private static boolean headerOrFooter(String name) {
    String clean = TextNormalizer.cleanName(name);
    if (clean.isEmpty()) {
      return true;
    }

    String upper = clean.toUpperCase();

    if (
      upper.matches(RegexTerms.CONTAINS_PAGE_NUMBER) &&
      !roleWord(upper) &&
      !functionRole(upper)
    ) {
      return true;
    }

    if (
      upper.matches(RegexTerms.ALL_CAPS_ONE_WORD) &&
      upper.length() > 10 &&
      !roleWord(upper) &&
      !functionRole(upper)
    ) {
      return true;
    }

    if (
      upper.matches(RegexTerms.ALL_CAPS_TWO_WORDS) &&
      !roleWord(upper) &&
      !functionRole(upper)
    ) {
      return true;
    }

    if (
      upper.matches(RegexTerms.ALL_CAPS_THREE_WORDS) &&
      !roleWord(upper) &&
      !functionRole(upper)
    ) {
      return true;
    }

    return false;
  }

  private static boolean roleWord(String name) {
    String upper = TextNormalizer.cleanName(name).toUpperCase();
    return upper.matches(RegexTerms.containsAnyWord(RegexTerms.ROLE_WORD));
  }

  private static boolean repeated(String name) {
    String[] words = TextNormalizer.cleanName(name).split(
      RegexTerms.WHITESPACE
    );

    if (words.length == 2) {
      return words[0].equals(words[1]);
    }
    if (words.length == 4) {
      return (words[0] + " " + words[1]).equals(words[2] + " " + words[3]);
    }
    return false;
  }

  private static boolean combinedName(
    String name,
    int count,
    Map<String, Integer> counts
  ) {
    String[] words = TextNormalizer.cleanName(name).split(
      RegexTerms.WHITESPACE
    );
    if (words.length < 2 || words.length > MAX_NAME_WORDS) {
      return false;
    }

    int required = Math.max(2, count);

    for (int cut = 1; cut < words.length; cut++) {
      String left = String.join(" ", Arrays.copyOfRange(words, 0, cut));
      String right = String.join(
        " ",
        Arrays.copyOfRange(words, cut, words.length)
      );

      if (
        counts.getOrDefault(left, 0) >= required &&
        counts.getOrDefault(right, 0) >= required
      ) {
        return true;
      }
    }

    return false;
  }

  public static String dropLeadAction(String name) {
    String cleaned = TextNormalizer.cleanName(name);

    if (cleaned.startsWith("ENTER ")) {
      return TextNormalizer.cleanName(cleaned.substring(6));
    }
    if (cleaned.startsWith("EXIT ")) {
      return TextNormalizer.cleanName(cleaned.substring(5));
    }

    return cleaned;
  }

  public static Set<String> expand(Set<String> names) {
    Set<String> result = new LinkedHashSet<>();
    if (names == null) {
      return result;
    }

    for (String raw : names) {
      String name = TextNormalizer.cleanName(raw);
      if (name.isEmpty()) {
        continue;
      }
      result.add(name);

      String noAction = dropLeadAction(name);
      if (!noAction.equals(name) && !noAction.isEmpty()) {
        result.add(noAction);
      }

      String noDots = name.replace(".", "");
      if (!noDots.equals(name) && !noDots.isEmpty()) {
        result.add(noDots);
      }

      String last = lastName(name);
      if (!last.isEmpty() && !bareNumberOrShort(last)) {
        result.add(last);
      }

      String[] slashParts = name.split(RegexTerms.WHITESPACE_AROUND_SLASH);
      if (slashParts.length > 1) {
        for (String part : slashParts) {
          String cleaned = TextNormalizer.cleanName(part);
          if (!cleaned.isEmpty()) {
            result.add(cleaned);
          }
        }
      }
    }

    return result;
  }

  private static String lastName(String name) {
    String[] words = TextNormalizer.cleanName(name).split(
      RegexTerms.WHITESPACE
    );
    if (words.length < 2) {
      return "";
    }

    String last = words[words.length - 1];
    return validNameShape(last) ? last : "";
  }

  public static List<String> sort(Set<String> names) {
    List<String> out = new ArrayList<>();
    if (names != null) {
      out.addAll(names);
    }
    out.sort(String::compareTo);
    return out;
  }

  public static List<String> sortedNamesByLength(Set<String> names) {
    List<String> out = sort(names);
    out.sort((a, b) -> Integer.compare(b.length(), a.length()));
    return out;
  }

  public static String target(String target, Set<String> chars) {
    String cleanedTarget = TextNormalizer.cleanName(target);
    if (chars == null || chars.isEmpty() || cleanedTarget.isEmpty()) {
      return cleanedTarget;
    }
    if (chars.contains(cleanedTarget)) {
      return cleanedTarget;
    }

    String compactTarget = compactName(cleanedTarget);
    for (String ch : chars) {
      String compactCharacter = compactName(TextNormalizer.cleanName(ch));
      if (compactCharacter.equals(compactTarget)) {
        return TextNormalizer.cleanName(ch);
      }
    }

    return cleanedTarget;
  }

  private static String compactName(String name) {
    return name.replaceAll(RegexTerms.NON_ALNUM_UPPER, "");
  }
}
