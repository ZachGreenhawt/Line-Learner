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

public class CharacterExtractor {

  public static final Set<String> BAD_HEADINGS = Set.of(
    "ACT",
    "SCENE",
    "EPISODE",
    "CHARACTER",
    "CHARACTERS",
    "CAST",
    "CONTENTS",
    "PREFACE",
    "CREDITS",
    "COPYRIGHT",
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
    "O.K",
    "WELL",
    "SO",
    "WHAT",
    "WHY",
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
    "ALL RIGHT WITH ME"
  );

  private static final int MAX_NAME_LENGTH = 45;
  private static final int MAX_NAME_WORDS = 5;

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

        String[] cells = line.split(",", -1);
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

        String[] cells = line.split(",", -1);
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
    if (text == null) {
      text = "";
    }

    for (String raw : text.split("\\n")) {
      String line = TextNormalizer.norm(raw);
      if (skipScan(line)) {
        continue;
      }

      addFromLine(counts, line);
    }

    return keep(counts);
  }

  private static boolean skipScan(String line) {
    if (StageDetector.skip(line)) {
      return true;
    }
    return StageDetector.junk(line) && !heading(line);
  }

  private static void addFromLine(Map<String, Integer> counts, String line) {
    String cleaned = TextNormalizer.norm(line);
    String stripped = stripArticlePrefix(cleaned);

    if (addCast(counts, cleaned)) {
      if (stripped.equals(cleaned)) {
        return;
      }
      addCast(counts, stripped);
      return;
    }

    if (addCast(counts, stripped)) {
      return;
    }

    int colon = stripped.indexOf(":");
    if (colon > 0) {
      addCandidate(counts, stripped.substring(0, colon));
      return;
    }

    int dot = SpeakerDetector.speakerDot(stripped);
    if (dot > 0) {
      addCandidate(counts, stripped.substring(0, dot));
      return;
    }

    addCandidate(counts, stripped);
  }

  private static boolean addCast(Map<String, Integer> counts, String line) {
    String candidate = castRole(line);
    if (candidate.isEmpty()) {
      return false;
    }

    addRole(counts, candidate);
    return true;
  }

  private static String castRole(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty()) {
      return "";
    }

    String withoutActor = cleaned
      .replaceFirst("(?i)\\s+ACTOR\\s+\\d+\\s*$", "")
      .trim();
    if (!withoutActor.equals(cleaned) && roleName(withoutActor)) {
      return withoutActor;
    }

    if (roleName(cleaned)) {
      return cleaned;
    }

    return "";
  }

  private static boolean roleName(String raw) {
    String clean = TextNormalizer.cleanName(raw);
    if (!validNameShape(clean)) {
      return false;
    }

    String[] words = clean.split("\\s+");
    if (words.length > MAX_NAME_WORDS + 1) {
      return false;
    }

    if (clean.contains("/")) {
      String[] parts = clean.split("\\s*/\\s*");
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
    boolean hasNumber = clean.matches(".*\\b\\d{1,3}\\b.*");
    boolean ordinalPrefixed = clean.matches(
      "(?i)^(FIRST|SECOND|THIRD|FOURTH|FIFTH|SIXTH|SEVENTH|EIGHTH|NINTH|TENTH)\\s+[A-Z][A-Za-z0-9 .'’\\-]{1,50}$"
    );
    boolean numberedSuffix = clean.matches(
      "(?i)^[A-Z][A-Za-z0-9 .'’\\-]{1,50}\\s+(ONE|TWO|THREE|FOUR|FIVE|SIX|SEVEN|EIGHT|NINE|TEN)$"
    );
    boolean articlePrefixed = clean.matches(
      "(?i)^(A|AN|THE)\\s+[A-Z][A-Za-z0-9 .'’\\-]{1,50}$"
    );

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

  private static void addRole(Map<String, Integer> counts, String raw) {
    String name = TextNormalizer.cleanName(raw);
    if (!roleName(name)) {
      return;
    }
    if (
      looksLikeAuthorOrPublisherFurniture(name) ||
      looksLikeCastActorName(name) ||
      looksLikePageHeaderOrFooterName(name)
    ) {
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

  private static boolean validNameShape(String name) {
    return (
      !name.isEmpty() &&
      TextNormalizer.hasLetter(name) &&
      name.length() <= MAX_NAME_LENGTH &&
      !BAD_HEADINGS.contains(name) &&
      !BAD_SHORT_LINES.contains(name) &&
      !name.startsWith("ENTER ") &&
      !name.startsWith("EXIT ") &&
      !looksLikeAuthorOrPublisherFurniture(name) &&
      !looksLikeCastActorName(name) &&
      !looksLikePageHeaderOrFooterName(name)
    );
  }

  private static boolean tooManyWords(String raw) {
    int words = 0;
    for (String word : raw.split("\\s+")) {
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

    String name = TextNormalizer.cleanName(raw);
    if (
      looksLikeAuthorOrPublisherFurniture(name) ||
      looksLikeCastActorName(name) ||
      looksLikePageHeaderOrFooterName(name)
    ) {
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
    String[] words = name.split("\\s+");
    boolean flexibleRoleName = roleName(name);
    int requiredCount = flexibleRoleName ? 1 : 2;

    return (
      count >= requiredCount &&
      TextNormalizer.hasLetter(name) &&
      name.length() <= MAX_NAME_LENGTH &&
      words.length <= MAX_NAME_WORDS &&
      !BAD_HEADINGS.contains(name) &&
      !BAD_SHORT_LINES.contains(name) &&
      !name.startsWith("ENTER ") &&
      !name.startsWith("EXIT ") &&
      !looksLikeAuthorOrPublisherFurniture(name) &&
      !looksLikeCastActorName(name) &&
      !looksLikePageHeaderOrFooterName(name) &&
      !repeated(name) &&
      !combinedName(name, counts)
    );
  }

  private static boolean looksLikeAuthorOrPublisherFurniture(String name) {
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
        ".*\\b(PREFACE|FOREWORD|INTRODUCTION|CREDITS|CAST|CHARACTERS|COPYRIGHT|ISBN|PUBLISHER|PUBLISHED|PUBLICATION|SERVICE|PRESS|THEATRE|THEATER|COMPANY|AGENCY|LICENSE|LICENCE|RIGHTS|PERMISSION|CATALOGUE|CATALOGING|MANUFACTURED|DESIGN|DIRECTOR|DIRECTED|PRODUCED|PREMIERE|ARTISTIC|EXECUTIVE|BROADWAY|TRIBUNE|VARIETY|WORLD|MAGAZINE|JOURNAL|REVIEW|REVIEWS|PRAISE)\\b.*"
      )
    ) {
      return true;
    }

    if (looksLikeAddressOrPublicationPlace(upper)) {
      return true;
    }

    if (
      upper.matches(
        ".*\\b(A PLAY BY|PLAY BY|BOOK DESIGN|COVER ART|COVER DESIGN|ALL RIGHTS|NO PROFESSIONAL|NONPROFESSIONAL|WRITTEN PERMISSION|ORIGINALLY PRODUCED|FIRST PUBLISHED|ADAPTED BY|WRITTEN BY|BASED ON|STORY BY|MUSIC BY|LYRICS BY)\\b.*"
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
      ".*\\b(VOICE|VOICES|OFFSTAGE|ONSTAGE|FIRST|SECOND|THIRD|FOURTH|FIFTH|SIXTH|SEVENTH|EIGHTH|NINTH|TENTH|ONE|TWO|THREE|FOUR|FIVE|SIX|SEVEN|EIGHT|NINE|TEN|YOUNG|OLD|OLDER|LITTLE|BIG|SMALL|TALL|SHORT|LEFT|RIGHT|LEAD|HEAD|ASSISTANT|DEPUTY|CHIEF|FOREMAN|WORKER|CUSTOMER|STRANGER|VISITOR|NEIGHBOR|NEIGHBOUR|PASSERBY|PASSER-BY|PERSON|SOMEONE|SOMEBODY|ANYBODY|EVERYBODY|WITNESS|SPECTATOR|SPECTATORS)\\b.*"
    );
  }

  private static boolean looksLikeAddressOrPublicationPlace(String upper) {
    if (upper == null || upper.isBlank()) {
      return false;
    }

    if (
      upper.matches(
        ".*\\b[A-Z][A-Z]+,?\\s+(NY|CA|IL|MA|TX|PA|WA|OR|CO|DC|UK|USA|US)\\b.*"
      )
    ) {
      return true;
    }

    if (
      upper.matches(".*\\b(NEW|OLD|NORTH|SOUTH|EAST|WEST)\\s+[A-Z]{3,}\\b.*") &&
      !roleWord(upper) &&
      !functionRole(upper)
    ) {
      return true;
    }

    if (
      upper.matches(
        ".*\\b(STREET|ST|AVENUE|AVE|ROAD|RD|LANE|LN|DRIVE|DR|BOULEVARD|BLVD|COURT|CT|PLACE|PL|SQUARE|SQ|BUILDING|FLOOR|SUITE)\\b.*"
      )
    ) {
      return true;
    }

    if (
      upper.matches(
        ".*\\b(CITY|STATE|COUNTRY|UNITED STATES|UNITED KINGDOM|CANADA|ENGLAND)\\b.*"
      )
    ) {
      return true;
    }

    return false;
  }

  private static boolean looksLikeCastActorName(String name) {
    String clean = TextNormalizer.cleanName(name);
    if (clean.isEmpty()) {
      return true;
    }

    if (clean.contains("/")) {
      return false;
    }

    String[] words = clean.split("\\s+");
    if (words.length < 2 || words.length > 3) {
      return false;
    }

    if (roleWord(clean) || functionRole(clean)) {
      return false;
    }

    int titleCaseWords = 0;
    for (String word : words) {
      if (word.matches("[A-Z][a-zA-Z'’.-]+")) {
        titleCaseWords++;
      }
    }

    return titleCaseWords == words.length;
  }

  private static boolean looksLikePageHeaderOrFooterName(String name) {
    String clean = TextNormalizer.cleanName(name);
    if (clean.isEmpty()) {
      return true;
    }

    String upper = clean.toUpperCase();

    if (
      upper.matches(".*\\d{1,4}.*") && !roleWord(upper) && !functionRole(upper)
    ) {
      return true;
    }

    if (
      upper.matches("^[A-Z]+$") &&
      upper.length() > 10 &&
      !roleWord(upper) &&
      !functionRole(upper)
    ) {
      return true;
    }

    if (
      upper.matches("^[A-Z]+\\s+[A-Z]+$") &&
      !roleWord(upper) &&
      !functionRole(upper)
    ) {
      return true;
    }

    if (
      upper.matches("^[A-Z]+\\s+[A-Z]+\\s+[A-Z]+$") &&
      !roleWord(upper) &&
      !functionRole(upper)
    ) {
      return true;
    }

    return false;
  }

  private static boolean roleWord(String name) {
    String upper = TextNormalizer.cleanName(name).toUpperCase();
    return upper.matches(
      ".*\\b(GIRL|BOY|MAN|WOMAN|MOTHER|FATHER|SON|DAUGHTER|CHILD|BABY|FETUS|FOETUS|VOICE|VOICES|CLERK|JUDGE|PRIEST|LAWYER|ATTORNEY|REPORTER|GUARD|MATRON|HUSBAND|WIFE|COLONEL|CAPTAIN|SERGEANT|DOCTOR|NURSE|OFFICER|INSPECTOR|DETECTIVE|PROFESSOR|TEACHER|STUDENT|WAITER|WAITRESS|BELLBOY|JANITOR|MAID|SERVANT|KING|QUEEN|PRINCE|PRINCESS|DUKE|DUCHESS|LORD|LADY|FIRST|SECOND|THIRD|FOURTH|YOUNG|OLD|OLDER|ELDERLY|CHORUS|ENSEMBLE|CROWD|GROUP|OFFSTAGE|ANNOUNCER|NARRATOR|ADDING|FILING|TELEPHONE|DEFENSE|DEFENCE|PROSECUTION|BARBER|LOVER|HUCKSTER|SPECTATOR|SPECTATORS|JUROR|JURY|WITNESS|POLICEMAN|POLICE|ATTENDANT|STRANGER|CUSTOMER|WORKER|SECRETARY|STENOGRAPHER|OPERATOR|MESSENGER|BAILIFF|USHER|CLERK)\\b.*"
    );
  }

  private static boolean repeated(String name) {
    String[] words = TextNormalizer.cleanName(name).split("\\s+");

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
    Map<String, Integer> counts
  ) {
    String[] words = TextNormalizer.cleanName(name).split("\\s+");
    if (words.length < 2 || words.length > MAX_NAME_WORDS) {
      return false;
    }

    for (int cut = 1; cut < words.length; cut++) {
      String left = String.join(" ", Arrays.copyOfRange(words, 0, cut));
      String right = String.join(
        " ",
        Arrays.copyOfRange(words, cut, words.length)
      );

      if (counts.containsKey(left) && counts.containsKey(right)) {
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
      addExpandedNameForms(result, raw);
    }

    return result;
  }

  private static void addExpandedNameForms(Set<String> result, String raw) {
    String name = TextNormalizer.cleanName(raw);
    if (name.isEmpty()) {
      return;
    }

    result.add(name);
    addWithoutLeadAction(result, name);
    addWithoutDots(result, name);
    addSlashParts(result, name);
  }

  private static void addWithoutLeadAction(Set<String> result, String name) {
    String noAction = dropLeadAction(name);
    if (!noAction.equals(name) && !noAction.isEmpty()) {
      result.add(noAction);
    }
  }

  private static void addWithoutDots(Set<String> result, String name) {
    String noDots = name.replace(".", "");
    if (!noDots.equals(name) && !noDots.isEmpty()) {
      result.add(noDots);
    }
  }

  private static void addSlashParts(Set<String> result, String name) {
    String[] slashParts = name.split("\\s*/\\s*");
    if (slashParts.length <= 1) {
      return;
    }

    for (String part : slashParts) {
      String cleaned = TextNormalizer.cleanName(part);
      if (!cleaned.isEmpty()) {
        result.add(cleaned);
      }
    }
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
    return name.replaceAll("[^A-Z0-9]", "");
  }
}
