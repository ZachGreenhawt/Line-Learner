package parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import parser.detect.*;
import parser.export.CsvExporter;
import parser.model.ParseModels;
import parser.session.ParserSessionStore;
import practice.*;
import util.TextNormalizer;

public class ScriptParser {

  public static ParsedScript parse(
    String scriptText,
    Settings settings,
    Scanner sc
  ) {
    if (scriptText == null) {
      scriptText = "";
    }

    ParserSessionStore session = new ParserSessionStore(
      inferSessionName(scriptText)
    );
    session.ensureFolders();

    Set<String> chars = loadOrCreateCharacters(scriptText, session, sc);
    Map<String, String> aliases = CharacterExtractor.loadAliases(session);
    chars = CharacterExtractor.apply(chars, aliases);
    chars = CharacterExtractor.expand(chars);

    System.out.println("Enter the name of your character: ");
    String target = CharacterExtractor.target(sc.nextLine(), chars);
    if (!target.isEmpty()) {
      chars.add(target);
    }
    chars = CharacterExtractor.expand(chars);

    List<String> lines = LogicalLineBuilder.build(scriptText, chars);
    lines = FurnitureCandidateResolver.resolve(lines, chars);
    int suggestedBodyStartIndex = chooseStrongestBodyStart(lines, chars);

    if (
      !lines.isEmpty() &&
      suggestedBodyStartIndex >= 0 &&
      suggestedBodyStartIndex < lines.size()
    ) {
      System.out.println(
        "\nSuggested script start line: " +
          (suggestedBodyStartIndex + 1) +
          " -> " +
          lines.get(suggestedBodyStartIndex)
      );
    }

    int bodyStartIndex = ParserPrompts.bodyStart(lines, chars, sc);

    List<String> bodyLines = bodyLinesFrom(lines, bodyStartIndex);

    Map<Integer, SpeakerHeadingIndex.HeadingRecord> headingIndex =
      SpeakerHeadingIndex.build(bodyLines, chars, aliases);
    List<ParseModels.Block> blocks = SpeakerBlockBuilder.build(
      bodyLines,
      headingIndex
    );
    List<ParseModels.ScriptTurn> turns = TurnBuilder.fromBlocks(blocks);

    List<String> cues = new ArrayList<>();
    List<String> mine = new ArrayList<>();
    CuePairBuilder.build(turns, target, cues, mine, settings);

    CsvExporter.pairs(cues, mine, "parser_output_pairs.csv");
    CsvExporter.characters(chars, target, "parser_characters.csv");
    CsvExporter.lines(bodyLines, "parser_logical_lines.csv");
    CsvExporter.headings(headingIndex, "parser_heading_index.csv");
    CsvExporter.blocks(blocks, "parser_blocks.csv");
    CsvExporter.turns(turns, "parser_turns.csv");
    CsvExporter.suspicious(turns, "parser_suspicious_turns.csv");
    CsvExporter.health(turns, cues, mine, target, "parser_health.csv");

    return new ParsedScript(target, cues, mine);
  }

  private static int chooseStrongestBodyStart(
    List<String> lines,
    Set<String> chars
  ) {
    if (lines == null || lines.isEmpty()) {
      return 0;
    }

    int initial = FrontMatterDetector.bodyStart(lines, chars);
    int safeInitial = Math.max(0, Math.min(initial, lines.size() - 1));
    int bestIndex = safeInitial;
    int bestScore = playableWindowScore(lines, safeInitial, chars);

    for (int i = safeInitial + 1; i < lines.size(); i++) {
      int score = playableWindowScore(lines, i, chars);

      if (score >= bestScore + 6 && looksLikePlayableWindow(lines, i, chars)) {
        bestScore = score;
        bestIndex = i;
      }
    }

    return backtrackFromBridgeLine(lines, bestIndex, chars);
  }

  private static int playableWindowScore(
    List<String> lines,
    int start,
    Set<String> chars
  ) {
    if (
      lines == null || lines.isEmpty() || start < 0 || start >= lines.size()
    ) {
      return Integer.MIN_VALUE;
    }

    int score = 0;
    int speakerSignals = 0;
    int stageSignals = 0;
    int bodyMarkers = 0;
    int frontMatterSignals = 0;
    int unknownProseSignals = 0;

    int end = Math.min(lines.size(), start + 24);
    for (int i = start; i < end; i++) {
      String line = TextNormalizer.norm(lines.get(i));
      if (line.isEmpty()) {
        continue;
      }

      if (FrontMatterDetector.blockHeading(line, chars)) {
        frontMatterSignals++;
        score -= 5;
        continue;
      }

      if (looksLikeBodyMarkerForParser(line)) {
        bodyMarkers++;
        score += 5;
      }

      if (looksLikeStageLineForParser(line, chars)) {
        stageSignals++;
        score += 3;
      }

      if (looksLikeSpeakerLineForParser(line, chars)) {
        speakerSignals++;
        score += 4;
      }

      if (looksLikeBodyDialogueLineForParser(line, chars)) {
        score += 1;
      } else if (looksLikeFrontMatterProseForParser(line)) {
        unknownProseSignals++;
        score -= 2;
      }
    }

    if (bodyMarkers >= 1 && stageSignals >= 1) {
      score += 5;
    }

    if (stageSignals >= 1 && speakerSignals >= 1) {
      score += 6;
    }

    if (speakerSignals >= 2) {
      score += 4;
    }

    if (frontMatterSignals >= 2 && frontMatterSignals >= speakerSignals) {
      score -= 10;
    }

    if (unknownProseSignals >= 8 && speakerSignals == 0) {
      score -= 12;
    }

    return score;
  }

  private static boolean looksLikePlayableWindow(
    List<String> lines,
    int start,
    Set<String> chars
  ) {
    int speakerSignals = 0;
    int stageSignals = 0;
    int bodyMarkers = 0;
    int blocked = 0;

    int end = Math.min(lines.size(), start + 24);
    for (int i = start; i < end; i++) {
      String line = TextNormalizer.norm(lines.get(i));
      if (line.isEmpty()) {
        continue;
      }

      if (FrontMatterDetector.blockHeading(line, chars)) {
        blocked++;
        continue;
      }

      if (looksLikeBodyMarkerForParser(line)) {
        bodyMarkers++;
      }

      if (looksLikeStageLineForParser(line, chars)) {
        stageSignals++;
      }

      if (looksLikeSpeakerLineForParser(line, chars)) {
        speakerSignals++;
      }
    }

    return (
      blocked <= 1 &&
      speakerSignals >= 1 &&
      (stageSignals >= 1 || bodyMarkers >= 1)
    );
  }

  private static int backtrackFromBridgeLine(
    List<String> lines,
    int index,
    Set<String> chars
  ) {
    if (lines == null || lines.isEmpty()) {
      return 0;
    }

    int safeIndex = Math.max(0, Math.min(index, lines.size() - 1));
    String current = TextNormalizer.norm(lines.get(safeIndex));

    if (!isBridgeLineForParser(current)) {
      return safeIndex;
    }

    for (int i = safeIndex - 1; i >= Math.max(0, safeIndex - 5); i--) {
      String previous = TextNormalizer.norm(lines.get(i));
      if (previous.isEmpty()) {
        continue;
      }

      if (
        looksLikeSpeakerLineForParser(previous, chars) ||
        looksLikeBodyMarkerForParser(previous)
      ) {
        return i;
      }
    }

    return safeIndex;
  }

  private static boolean isBridgeLineForParser(String line) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty()) {
      return false;
    }

    return (
      t.startsWith("(") ||
      t.startsWith("[") ||
      t.startsWith("{") ||
      StageDetector.whole(t) ||
      StageDetector.strong(t, null)
    );
  }

  private static boolean looksLikeBodyMarkerForParser(String line) {
    String upper = TextNormalizer.norm(line).toUpperCase();
    if (upper.isEmpty()) {
      return false;
    }

    return (
      upper.startsWith("AT RISE") ||
      upper.startsWith("AT THE RISE") ||
      upper.startsWith("BEFORE THE CURTAIN") ||
      upper.startsWith("SCENE:") ||
      upper.startsWith("SOUNDS:") ||
      upper.matches("^EPISODE\\s+[A-Z0-9IVX -]+$") ||
      upper.matches("^ACT\\s+[A-Z0-9IVX -]+$") ||
      upper.matches("^SCENE\\s+[A-Z0-9IVX -]+$")
    );
  }

  private static boolean looksLikeStageLineForParser(
    String line,
    Set<String> chars
  ) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty()) {
      return false;
    }

    if (FrontMatterDetector.blockHeading(t, chars)) {
      return false;
    }

    return (
      StageDetector.entranceExit(t) ||
      StageDetector.whole(t) ||
      StageDetector.location(t) ||
      StageDetector.strong(t, chars)
    );
  }

  private static boolean looksLikeSpeakerLineForParser(
    String line,
    Set<String> chars
  ) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty()) {
      return false;
    }

    if (FrontMatterDetector.blockHeading(t, chars)) {
      return false;
    }

    return (
      SpeakerDetector.is(t, chars) || !SpeakerDetector.name(t, chars).isEmpty()
    );
  }

  private static boolean looksLikeBodyDialogueLineForParser(
    String line,
    Set<String> chars
  ) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty()) {
      return false;
    }

    if (FrontMatterDetector.blockHeading(t, chars)) {
      return false;
    }

    return dialogue(t, chars);
  }

  private static boolean looksLikeFrontMatterProseForParser(String line) {
    String t = TextNormalizer.norm(line);
    if (t.length() < 80) {
      return false;
    }

    String lower = t.toLowerCase();
    return (
      lower.contains("published") ||
      lower.contains("copyright") ||
      lower.contains("directed by") ||
      lower.contains("produced") ||
      lower.contains("premiere") ||
      lower.contains("playwright") ||
      lower.contains("publisher") ||
      lower.contains("rights") ||
      lower.contains("isbn")
    );
  }

  private static List<String> bodyLinesFrom(
    List<String> lines,
    int bodyStartIndex
  ) {
    if (lines == null || lines.isEmpty()) {
      return new ArrayList<>();
    }

    int safeStart = Math.max(0, Math.min(bodyStartIndex, lines.size() - 1));
    return new ArrayList<>(lines.subList(safeStart, lines.size()));
  }

  private static Set<String> loadOrCreateCharacters(
    String scriptText,
    ParserSessionStore session,
    Scanner sc
  ) {
    Set<String> savedChars = CharacterExtractor.load(session);

    if (!savedChars.isEmpty()) {
      System.out.println("\nSaved character list found for this session.");
      System.out.println("Use saved character list? (yes/no, default yes)");
      String useSaved = sc.nextLine().trim();
      if (useSaved.isEmpty() || yes(useSaved)) {
        System.out.println(
          "Review/edit saved character list before parsing? (yes/no, default no)"
        );
        String review = sc.nextLine().trim();
        if (yes(review)) {
          savedChars = ParserPrompts.chars(savedChars, sc);
          CharacterExtractor.save(session, savedChars);
        }
        return savedChars;
      }
    }

    Set<String> detectedChars = CharacterExtractor.find(scriptText);
    detectedChars = ParserPrompts.chars(detectedChars, sc);
    CharacterExtractor.save(session, detectedChars);
    return detectedChars;
  }

  private static String inferSessionName(String scriptText) {
    if (scriptText == null || scriptText.isBlank()) {
      return "current_script";
    }

    String[] lines = scriptText.split("\\R");
    for (String line : lines) {
      String cleaned = TextNormalizer.norm(line);
      if (!cleaned.isEmpty() && cleaned.length() <= 60) {
        return cleaned;
      }
    }

    return "current_script";
  }

  private static boolean yes(String answer) {
    return answer.equalsIgnoreCase("yes") || answer.equalsIgnoreCase("y");
  }

  public static boolean dialogue(String line, Set<String> chars) {
    line = TextNormalizer.norm(line);
    if (line.isEmpty() || StageDetector.skip(line)) {
      return false;
    }
    if (
      StageDetector.junk(line) &&
      !SpeakerDetector.is(line, chars) &&
      !SpeakerDetector.has(line, chars)
    ) {
      return false;
    }
    if (SpeakerDetector.is(line, chars) || SpeakerDetector.has(line, chars)) {
      return false;
    }
    return !StageDetector.prose(line, chars);
  }
}
