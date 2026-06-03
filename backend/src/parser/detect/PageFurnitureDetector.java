package parser.detect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import util.RegexTerms;

public class PageFurnitureDetector {

  public static final String FURNITURE_CANDIDATE_OPEN = "<FURNITURE_CANDIDATE";
  public static final String FURNITURE_CANDIDATE_CLOSE =
    "</FURNITURE_CANDIDATE>";
  private static final int EDGE_WINDOW_LINES = 6;
  private static final int MIN_REPEATED_COUNT = 3;
  private static final Pattern PAGE_NUMBER_ONLY = Pattern.compile(RegexTerms.PAGE_NUMBER_ONLY);

  public static DetectionModel learn(List<String> pageTexts) {
    DetectionModel model = new DetectionModel();

    if (pageTexts == null || pageTexts.isEmpty()) {
      return model;
    }

    for (int pageIndex = 0; pageIndex < pageTexts.size(); pageIndex++) {
      List<String> lines = nonBlankLines(pageTexts.get(pageIndex));
      if (lines.isEmpty()) {
        continue;
      }

      for (int i = 0; i < lines.size(); i++) {
        String line = norm(lines.get(i));
        if (line.isEmpty()) {
          continue;
        }

        boolean edge =
          i < EDGE_WINDOW_LINES || i >= lines.size() - EDGE_WINDOW_LINES;
        if (edge && candidate(line)) {
          model.observe(line, pageIndex, i, lines.size());
        }
      }
    }

    model.finalizeModel();
    return model;
  }

  public static String remove(String pageText, DetectionModel model) {
    if (pageText == null || pageText.isBlank()) {
      return "";
    }

    DetectionModel effectiveModel =
      model == null ? new DetectionModel() : model;
    String[] rawLines = pageText.split(RegexTerms.LINE_BREAK, -1);
    StringBuilder out = new StringBuilder();

    for (int i = 0; i < rawLines.length; i++) {
      String rawLine = rawLines[i];
      String normalized = norm(rawLine);

      if (normalized.isEmpty()) {
        blank(out);
        continue;
      }

      String replacement = replacementFor(
        normalized,
        i,
        rawLines.length,
        effectiveModel
      );

      if (replacement != null) {
        if (!replacement.isEmpty()) {
          out.append(replacement).append('\n');
        }
        continue;
      }

      out.append(strip(rawLine)).append('\n');
    }

    return collapse(out.toString()).trim();
  }

  public static List<String> removePages(List<String> pageTexts) {
    if (pageTexts == null || pageTexts.isEmpty()) {
      return List.of();
    }

    DetectionModel model = learn(pageTexts);
    List<String> cleaned = new ArrayList<>();

    for (String pageText : pageTexts) {
      cleaned.add(remove(pageText, model));
    }

    return cleaned;
  }

  public static boolean is(String line) {
    String normalized = norm(line);
    if (normalized.isEmpty()) {
      return false;
    }
    if (structural(normalized)) {
      return false;
    }

    return (
      pageNumber(normalized) ||
      publisher(normalized) ||
      marker(normalized) ||
      shortHeaderFooter(normalized)
    );
  }

  public static String strip(String line) {
    if (line == null) {
      return "";
    }

    String cleaned = line.trim();
    boolean protectedLine =
      structural(cleaned) || protectedSpeaker(norm(cleaned));

    cleaned = cleaned.replaceAll(RegexTerms.REPLACEMENT_CHAR, "");
    cleaned = cleaned.replaceAll(RegexTerms.BROKEN_FFFE_CHARS, "");

    cleaned = cleaned.replaceAll(
      RegexTerms.HEADING_WITH_TRAILING_PAGE_NUMBER,
      "$1"
    );

    cleaned = cleaned.replaceAll(
      RegexTerms.LEADING_NAME_BLEED,
      ""
    );

    if (!protectedLine) {
      cleaned = cleaned.replaceAll(
        RegexTerms.TRAILING_NAME_BLEED,
        ""
      );
    }

    return cleaned.trim();
  }

  private static String replacementFor(
    String normalized,
    int lineIndex,
    int totalLines,
    DetectionModel model
  ) {
    if (normalized.isEmpty() || structural(normalized)) {
      return null;
    }

    if (pageNumber(normalized) || publisher(normalized) || marker(normalized)) {
      return "";
    }

    if (protectedSpeaker(normalized)) {
      return null;
    }

    boolean edge =
      lineIndex < EDGE_WINDOW_LINES ||
      lineIndex >= totalLines - EDGE_WINDOW_LINES;

    if (
      edge &&
      (model.repeated(normalized) ||
        shortHeaderFooter(normalized) ||
        titleAuthor(normalized) ||
        genericTitle(normalized))
    ) {
      return wrap(normalized, reason(normalized));
    }

    return null;
  }

  private static boolean structural(String line) {
    String normalized = norm(line);
    if (normalized.isEmpty()) {
      return false;
    }
    return (
      bodyMarker(normalized) ||
      speakerShape(normalized) ||
      stageAction(normalized)
    );
  }

  private static boolean bodyMarker(String line) {
    String lower = norm(line).toLowerCase(Locale.ROOT);
    if (lower.isEmpty()) {
      return false;
    }

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

  private static boolean speakerShape(String line) {
    String normalized = norm(line);
    if (normalized.length() < 2 || normalized.length() > 65) {
      return false;
    }

    if (!hasLetter(normalized)) {
      return false;
    }

    if (!mostlyUpper(normalized)) {
      return false;
    }

    if (
      pageNumber(normalized) ||
      publisher(normalized) ||
      genericTitle(normalized)
    ) {
      return false;
    }

    if (!roleWord(normalized) && plainCapsName(normalized)) {
      return false;
    }

    return (
      normalized.matches(RegexTerms.WRAPPED_NAME_ONLY) ||
      normalized.matches(RegexTerms.SLASH_SPEAKER_LINE) ||
      normalized.matches(RegexTerms.WRAPPED_NAME_PAREN) ||
      normalized.matches(RegexTerms.WRAPPED_NAME_BRACKET)
    );
  }

  private static boolean stageAction(String line) {
    String normalized = norm(line);
    String lower = normalized.toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      return false;
    }

    if (
      normalized.matches(RegexTerms.PAREN_WHOLE) ||
      normalized.matches(RegexTerms.BRACKET_WHOLE)
    ) {
      return true;
    }

    return (
      lower.matches(
        RegexTerms.ENTRANCE_EXIT_START
      ) ||
      lower.matches(
        RegexTerms.TECH_CUE_PAUSE
      ) ||
      lower.matches(
        RegexTerms.LOCATION_DIRECTION
      ) ||
      lower.matches(
        RegexTerms.ARTICLE_SETTING_SHORT
      ) ||
      lower.matches(
        RegexTerms.LOWER_STAGE_ACTION
      )
    );
  }

  private static boolean titleAuthor(String line) {
    String normalized = norm(line);
    if (
      normalized.isEmpty() || structural(normalized) || roleWord(normalized)
    ) {
      return false;
    }

    String upper = normalized.toUpperCase(Locale.ROOT);
    return (
      upper.matches(
        RegexTerms.CAPS_WORDS_THEN_NUMBER
      ) ||
      upper.matches(
        RegexTerms.NUMBER_THEN_CAPS_WORDS
      )
    );
  }

  private static boolean candidate(String line) {
    String normalized = norm(line);
    if (normalized.isEmpty() || structural(normalized)) {
      return false;
    }

    return (
      pageNumber(normalized) ||
      publisher(normalized) ||
      marker(normalized) ||
      shortHeaderFooter(normalized) ||
      titleAuthor(normalized) ||
      genericTitle(normalized)
    );
  }

  public static boolean wrapped(String line) {
    String normalized = norm(line);
    return (
      normalized.startsWith(FURNITURE_CANDIDATE_OPEN) &&
      normalized.endsWith(FURNITURE_CANDIDATE_CLOSE)
    );
  }

  public static String unwrap(String line) {
    String normalized = norm(line);
    if (!wrapped(normalized)) {
      return normalized;
    }

    int closeBracket = normalized.indexOf('>');
    int closeTag = normalized.lastIndexOf(FURNITURE_CANDIDATE_CLOSE);
    if (closeBracket < 0 || closeTag <= closeBracket) {
      return normalized;
    }

    return norm(normalized.substring(closeBracket + 1, closeTag));
  }

  private static String wrap(String line, String reason) {
    String inner = norm(line);
    if (inner.isEmpty()) {
      return "";
    }

    String safeReason =
      reason == null || reason.isBlank()
        ? "ambiguous"
        : reason.replaceAll(RegexTerms.NON_FILENAME_CHAR, "_");

    return (
      FURNITURE_CANDIDATE_OPEN +
      " reason=\"" +
      safeReason +
      "\">" +
      inner +
      FURNITURE_CANDIDATE_CLOSE
    );
  }

  private static String reason(String line) {
    String normalized = norm(line);

    if (normalized.matches(RegexTerms.CONTAINS_PAGE_NUMBER)) {
      return roleWord(normalized)
        ? "possible_numbered_heading"
        : "title_page_or_page_header";
    }

    if (plainCapsName(normalized)) {
      return "short_all_caps_header";
    }

    if (shortHeaderFooter(normalized)) {
      return "repeated_header_footer";
    }

    if (titleAuthor(normalized)) {
      return "title_author_header";
    }

    return "ambiguous_furniture";
  }

  private static boolean pageNumber(String line) {
    return (
      PAGE_NUMBER_ONLY.matcher(line).matches() ||
      line.matches(RegexTerms.PAGE_LABEL)
    );
  }

  private static boolean publisher(String line) {
    String upper = line.toUpperCase(Locale.ROOT);

    return (
      upper.matches(
        RegexTerms.containsAnyWord(RegexTerms.PUBLISHER_FURNITURE_WORD)
      ) ||
      upper.matches(RegexTerms.CONTAINS_WWW_CI) ||
      upper.matches(RegexTerms.CONTAINS_WEB_TLD_CI) ||
      upper.matches(RegexTerms.CONTAINS_DRAMA_PRICE) ||
      upper.matches(RegexTerms.CAPS_CAPS_NUMBER_LINE) ||
      publicationPlace(upper)
    );
  }

  private static boolean marker(String line) {
    return (
      line.matches(RegexTerms.PAGE_MARKER_PARSED_TEXT) ||
      line.matches(RegexTerms.PAGE_MARKER_REGION) ||
      line.matches(RegexTerms.PAGE_MARKER_IMAGE)
    );
  }

  private static boolean shortHeaderFooter(String line) {
    if (roleWord(line)) {
      return false;
    }
    if (line.length() > 60) {
      return false;
    }
    if (structural(line)) {
      return false;
    }

    if (!hasLetter(line)) {
      return false;
    }

    if (!mostlyUpper(line)) {
      return false;
    }

    String[] words = line.split(RegexTerms.WHITESPACE);
    if (words.length <= 4 && genericTitle(line)) {
      return true;
    }
    return words.length <= 6;
  }

  private static boolean protectedSpeaker(String line) {
    if (line.length() < 2 || line.length() > 55) {
      return false;
    }
    if (structural(line)) {
      return true;
    }

    if (pageNumber(line) || publisher(line) || genericTitle(line)) {
      return false;
    }

    if (!roleWord(line) && plainCapsName(line)) {
      return false;
    }

    return (
      line.matches(RegexTerms.WRAPPED_NAME_ONLY) ||
      line.matches(RegexTerms.SLASH_SPEAKER_LINE)
    );
  }

  private static List<String> nonBlankLines(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }

    List<String> lines = new ArrayList<>();
    for (String rawLine : text.split(RegexTerms.LINE_BREAK)) {
      String line = norm(rawLine);
      if (!line.isEmpty()) {
        lines.add(line);
      }
    }
    return lines;
  }

  private static String norm(String line) {
    if (line == null) {
      return "";
    }

    return line
      .replace('\u00A0', ' ')
      .replace('\uFFFD', ' ')
      .replace('￾', ' ')
      .replace('￿', ' ')
      .trim()
      .replaceAll(RegexTerms.WHITESPACE, " ");
  }

  private static boolean hasLetter(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (Character.isLetter(line.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  private static boolean mostlyUpper(String line) {
    int letters = 0;
    int uppercase = 0;

    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
        if (Character.isUpperCase(ch)) {
          uppercase++;
        }
      }
    }

    return letters > 0 && uppercase * 10 >= letters * 8;
  }

  private static void blank(StringBuilder out) {
    int length = out.length();
    if (length == 0) {
      return;
    }
    if (
      length >= 2 &&
      out.charAt(length - 1) == '\n' &&
      out.charAt(length - 2) == '\n'
    ) {
      return;
    }
    out.append('\n');
  }

  private static String collapse(String text) {
    return text.replaceAll(RegexTerms.NEWLINE_RUN, "\\n\\n");
  }

  private static boolean genericTitle(String line) {
    String normalized = norm(line);
    if (normalized.isEmpty() || roleWord(normalized)) {
      return false;
    }

    if (bodyMarker(normalized) || stageAction(normalized)) {
      return false;
    }

    String upper = normalized.toUpperCase(Locale.ROOT);

    if (
      upper.matches(
        RegexTerms.NUMBER_THEN_CAPS_WORDS
      )
    ) {
      return true;
    }

    if (
      upper.matches(
        RegexTerms.CAPS_WORDS_THEN_NUMBER
      )
    ) {
      return true;
    }

    return plainCapsName(upper) && !roleWord(upper);
  }

  private static boolean plainCapsName(String line) {
    String normalized = norm(line);
    if (
      normalized.isEmpty() || normalized.length() > 45 || roleWord(normalized)
    ) {
      return false;
    }

    if (!hasLetter(normalized) || !mostlyUpper(normalized)) {
      return false;
    }

    if (
      normalized.contains("/") ||
      normalized.contains("(") ||
      normalized.contains("[")
    ) {
      return false;
    }

    String[] words = normalized.split(RegexTerms.WHITESPACE);
    if (words.length < 1 || words.length > 3) {
      return false;
    }

    for (String word : words) {
      if (!word.matches(RegexTerms.ALL_CAPS_WORD)) {
        return false;
      }
    }

    return true;
  }

  private static boolean roleWord(String line) {
    String upper = norm(line).toUpperCase(Locale.ROOT);
    return upper.matches(
      RegexTerms.containsAnyWord(RegexTerms.ROLE_WORD)
    );
  }

  private static boolean publicationPlace(String upper) {
    if (upper == null || upper.isBlank()) {
      return false;
    }
    if (upper.matches(RegexTerms.CONTAINS_BANG_QUESTION)) {
      return false;
    }

    return (
      upper.matches(
        RegexTerms.US_STATE_SUFFIX
      ) ||
      upper.matches(
        RegexTerms.containsAnyWord(RegexTerms.STREET_ADDRESS_TERM)
      ) ||
      upper.matches(
        RegexTerms.containsAnyWord(RegexTerms.COUNTRY_OR_REGION_TERM)
      )
    );
  }

  public static class DetectionModel {

    private final Map<String, Integer> counts = new HashMap<>();
    private final Map<String, Set<Integer>> pagesSeen = new HashMap<>();
    private final Set<String> repeatedFurniture = new HashSet<>();

    private void observe(
      String line,
      int pageIndex,
      int lineIndex,
      int totalLines
    ) {
      String normalized = norm(line);
      if (normalized.isEmpty()) {
        return;
      }

      counts.put(normalized, counts.getOrDefault(normalized, 0) + 1);
      pagesSeen
        .computeIfAbsent(normalized, ignored -> new HashSet<>())
        .add(pageIndex);
    }

    private void finalizeModel() {
      repeatedFurniture.clear();

      for (Map.Entry<String, Integer> entry : counts.entrySet()) {
        String line = entry.getKey();
        int count = entry.getValue();
        int pageCount = pagesSeen.getOrDefault(line, Set.of()).size();

        if (
          count >= MIN_REPEATED_COUNT &&
          pageCount >= Math.min(MIN_REPEATED_COUNT, Math.max(2, pageCount)) &&
          candidate(line) &&
          !structural(line) &&
          !protectedSpeaker(line)
        ) {
          repeatedFurniture.add(line);
        }
      }
    }

    public boolean repeated(String line) {
      return repeatedFurniture.contains(norm(line));
    }

    public int count(String line) {
      return counts.getOrDefault(norm(line), 0);
    }

    public int repeatedCount() {
      return repeatedFurniture.size();
    }

    public String summary() {
      return String.format(
        "PageFurnitureDetector{observed=%d, repeatedFurniture=%d}",
        counts.size(),
        repeatedFurniture.size()
      );
    }
  }
}
