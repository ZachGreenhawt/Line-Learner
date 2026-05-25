package parser.export;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import parser.CharacterExtractor;
import parser.detect.SpeakerHeadingIndex;
import parser.model.ParseModels;
import util.TextNormalizer;

public class CsvExporter {

  public static class ContextDebugRow {

    public final int lineNumber;
    public final String rawLine;
    public final String detectedSpeaker;
    public final String decision;
    public final String expectedSpeaker;
    public final String lastExplicitSpeaker;
    public final String lastDialogueSpeaker;
    public final boolean rapidFireMode;
    public final String stageStrength;
    public final String confidence;
    public final String reason;
    public final String contextSummary;

    public ContextDebugRow(
      int lineNumber,
      String rawLine,
      String detectedSpeaker,
      String decision,
      String expectedSpeaker,
      String lastExplicitSpeaker,
      String lastDialogueSpeaker,
      boolean rapidFireMode,
      String stageStrength,
      String confidence,
      String reason,
      String contextSummary
    ) {
      this.lineNumber = lineNumber;
      this.rawLine = rawLine == null ? "" : rawLine;
      this.detectedSpeaker = detectedSpeaker == null ? "" : detectedSpeaker;
      this.decision = decision == null ? "" : decision;
      this.expectedSpeaker = expectedSpeaker == null ? "" : expectedSpeaker;
      this.lastExplicitSpeaker =
        lastExplicitSpeaker == null ? "" : lastExplicitSpeaker;
      this.lastDialogueSpeaker =
        lastDialogueSpeaker == null ? "" : lastDialogueSpeaker;
      this.rapidFireMode = rapidFireMode;
      this.stageStrength = stageStrength == null ? "" : stageStrength;
      this.confidence = confidence == null ? "" : confidence;
      this.reason = reason == null ? "" : reason;
      this.contextSummary = contextSummary == null ? "" : contextSummary;
    }
  }

  private static String exportSessionName = "current_script";

  public static void session(String sessionName) {
    String cleaned = sessionName == null ? "" : sessionName.trim();
    cleaned = cleaned.replaceAll("\\.[^.]+$", "");
    cleaned = cleaned.replaceAll("[^A-Za-z0-9._-]", "_");
    exportSessionName = cleaned.isEmpty() ? "current_script" : cleaned;
  }

  private static String path(String filename) {
    if (filename == null || filename.trim().isEmpty()) {
      filename = "parser_export.csv";
    }

    File directFile = new File(filename);
    if (directFile.isAbsolute() || filename.contains(File.separator)) {
      return filename;
    }

    File folder = new File(
      "parser_exports" +
        File.separator +
        "csvs" +
        File.separator +
        exportSessionName
    );
    if (!folder.exists() && !folder.mkdirs()) {
      return filename;
    }

    return new File(folder, filename).getPath();
  }

  public static String csv(String text) {
    if (text == null) {
      return "\"\"";
    }

    String cleaned = text
      .replace("\"", "\"\"")
      .replace("\n", " ")
      .replace("\r", " ");

    return "\"" + cleaned + "\"";
  }

  public static String safe(String text) {
    if (text == null) {
      return "";
    }

    return text.replace("\t", " ").replace("\n", " ").replace("\r", " ").trim();
  }

  public static void characters(
    Set<String> chars,
    String target,
    String filename
  ) {
    if (chars == null) {
      return;
    }

    try (PrintWriter out = new PrintWriter(new FileWriter(path(filename)))) {
      out.println("character,is_target");

      String cleanTarget = TextNormalizer.cleanName(target);
      for (String character : CharacterExtractor.sortedNamesByLength(chars)) {
        row(
          out,
          character,
          String.valueOf(
            TextNormalizer.cleanName(character).equals(cleanTarget)
          )
        );
      }
    } catch (IOException e) {
      System.err.println("Could not write characters CSV: " + e.getMessage());
    }
  }

  public static void headings(
    Map<Integer, SpeakerHeadingIndex.HeadingRecord> headingIndex,
    String filename
  ) {
    if (headingIndex == null || headingIndex.isEmpty()) {
      return;
    }

    try (PrintWriter out = new PrintWriter(new FileWriter(path(filename)))) {
      out.println(
        "line_index,line_number,raw_line,matched_alias,canonical_speaker,remaining_text,confidence,inline_dialogue,parenthetical_heading"
      );

      for (Map.Entry<
        Integer,
        SpeakerHeadingIndex.HeadingRecord
      > entry : headingIndex.entrySet()) {
        Integer lineIndex = entry.getKey();
        SpeakerHeadingIndex.HeadingRecord record = entry.getValue();

        if (lineIndex == null || record == null) {
          continue;
        }

        row(
          out,
          String.valueOf(lineIndex),
          String.valueOf(lineIndex + 1),
          record.rawLine,
          record.matchedAlias,
          record.canonicalSpeaker,
          record.remainingText,
          record.confidence == null ? "" : record.confidence.name(),
          String.valueOf(record.inlineDialogue),
          String.valueOf(record.parentheticalHeading)
        );
      }
    } catch (IOException e) {
      System.err.println(
        "Could not write parser heading index CSV: " + e.getMessage()
      );
    }
  }

  public static void blocks(List<ParseModels.Block> blocks, String filename) {
    if (blocks == null || blocks.isEmpty()) {
      return;
    }

    try (PrintWriter out = new PrintWriter(new FileWriter(path(filename)))) {
      out.println(
        "block_index,start_line,end_line,stage,block_type,speaker,confidence,reason,text,source,raw_line_count,text_line_count,source_line_count,source_contains_more_than_text,was_furniture_candidate,resolved_furniture_action,block_absorbed_lines,block_skipped_source_only_lines"
      );

      for (int i = 0; i < blocks.size(); i++) {
        ParseModels.Block block = blocks.get(i);

        if (block == null) {
          continue;
        }

        row(
          out,
          String.valueOf(i),
          String.valueOf(block.startLine + 1),
          String.valueOf(block.endLine + 1),
          String.valueOf(block.stage),
          block.type == null ? "" : block.type.name(),
          block.speaker,
          block.confidence,
          block.reason,
          block.text,
          block.source,
          String.valueOf(rawLines(block.startLine, block.endLine)),
          String.valueOf(pieces(block.text)),
          String.valueOf(pieces(block.source)),
          String.valueOf(sourceHasMore(block.source, block.text)),
          String.valueOf(hasFurniture(block.source)),
          furnitureAction(block.source, block.text),
          String.valueOf(absorbed(block.text)),
          String.valueOf(skipped(block.source, block.text))
        );
      }
    } catch (IOException e) {
      System.err.println(
        "Could not write parser blocks CSV: " + e.getMessage()
      );
    }
  }

  public static void lines(List<String> lines, String filename) {
    if (lines == null || lines.isEmpty()) {
      return;
    }

    try (PrintWriter out = new PrintWriter(new FileWriter(path(filename)))) {
      out.println("line_index,line_number,logical_line");

      for (int i = 0; i < lines.size(); i++) {
        row(out, String.valueOf(i), String.valueOf(i + 1), lines.get(i));
      }
    } catch (IOException e) {
      System.err.println(
        "Could not write parser logical lines CSV: " + e.getMessage()
      );
    }
  }

  public static void pairs(
    List<String> cues,
    List<String> mine,
    String filename
  ) {
    if (cues == null || mine == null) {
      return;
    }

    int max = Math.min(cues.size(), mine.size());
    Map<String, Integer> cueCounts = countCues(cues, max);
    Map<String, Integer> seenCounts = new HashMap<>();

    try (PrintWriter out = new PrintWriter(new FileWriter(path(filename)))) {
      out.println(
        "pair_index,cue,target_line,cue_repeat_index,cue_repeat_count,cue_repeat_warning"
      );

      for (int i = 0; i < max; i++) {
        String cueKey = TextNormalizer.norm(cues.get(i));
        int repeatCount = cueCounts.getOrDefault(cueKey, 0);
        int repeatIndex = seenCounts.getOrDefault(cueKey, 0) + 1;
        seenCounts.put(cueKey, repeatIndex);

        row(
          out,
          String.valueOf(i),
          cues.get(i),
          mine.get(i),
          String.valueOf(repeatIndex),
          String.valueOf(repeatCount),
          String.valueOf(repeatCount > 3)
        );
      }
    } catch (IOException e) {
      System.err.println("Could not write output pairs CSV: " + e.getMessage());
    }
  }

  private static Map<String, Integer> countCues(List<String> cues, int max) {
    Map<String, Integer> counts = new HashMap<>();

    for (int i = 0; i < max; i++) {
      String key = TextNormalizer.norm(cues.get(i));
      counts.put(key, counts.getOrDefault(key, 0) + 1);
    }

    return counts;
  }

  public static void debugLines(
    List<ParseModels.DebugLine> rows,
    String filename
  ) {
    if (rows == null || rows.isEmpty()) {
      return;
    }

    try (PrintWriter out = new PrintWriter(new FileWriter(path(filename)))) {
      out.println(
        "line_number,line,speaker,after_speaker,is_char,has_prefix,dialogue,prose_or_stage,stage_like,front_matter,page_junk,skip,body_before,body_after,active_before,active_after,cue_before,cue_after,action,spoken,added_cue,added_mine"
      );

      for (ParseModels.DebugLine row : rows) {
        row(
          out,
          String.valueOf(row.lineNumber),
          row.line,
          row.speaker,
          row.afterSpeaker,
          String.valueOf(row.character),
          String.valueOf(row.prefix),
          String.valueOf(row.dialogue),
          String.valueOf(row.prose),
          String.valueOf(row.stage),
          String.valueOf(row.front),
          String.valueOf(row.junk),
          String.valueOf(row.skip),
          String.valueOf(row.bodyBefore),
          String.valueOf(row.bodyAfter),
          row.activeBefore,
          row.activeAfter,
          row.cueBefore,
          row.cueAfter,
          row.action,
          row.spoken,
          row.addedCue,
          row.addedMine
        );
      }
    } catch (IOException e) {
      System.err.println("Could not write parser debug CSV: " + e.getMessage());
    }
  }

  public static void context(List<ContextDebugRow> rows, String filename) {
    if (rows == null || rows.isEmpty()) {
      return;
    }

    try (PrintWriter out = new PrintWriter(new FileWriter(path(filename)))) {
      out.println(
        "line_number,raw_line,detected_speaker,decision,expected_speaker,last_explicit_speaker,last_dialogue_speaker,rapid_fire_mode,stage_strength,confidence,reason,context_summary"
      );

      for (ContextDebugRow row : rows) {
        row(
          out,
          String.valueOf(row.lineNumber),
          row.rawLine,
          row.detectedSpeaker,
          row.decision,
          row.expectedSpeaker,
          row.lastExplicitSpeaker,
          row.lastDialogueSpeaker,
          String.valueOf(row.rapidFireMode),
          row.stageStrength,
          row.confidence,
          row.reason,
          row.contextSummary
        );
      }
    } catch (IOException e) {
      System.err.println(
        "Could not write parser context debug CSV: " + e.getMessage()
      );
    }
  }

  public static void issues(List<ParseModels.Issue> issues, String filename) {
    if (issues == null || issues.isEmpty()) {
      return;
    }

    try (PrintWriter out = new PrintWriter(new FileWriter(path(filename)))) {
      out.println(
        "line,issue_type,decision,parsed_speaker,parsed_text,reason,raw_line"
      );

      for (ParseModels.Issue issue : issues) {
        row(
          out,
          String.valueOf(issue.lineNumber),
          issue.issueType.toString(),
          issue.decision,
          issue.parsedSpeaker,
          issue.parsedText,
          issue.reason,
          issue.rawLine
        );
      }
    } catch (IOException e) {
      System.err.println("Could not write parse issue CSV: " + e.getMessage());
    }
  }

  public static void turns(
    List<ParseModels.ScriptTurn> turns,
    String filename
  ) {
    if (turns == null) {
      return;
    }

    try (PrintWriter out = new PrintWriter(new FileWriter(path(filename)))) {
      out.println(
        "turn_index,start_line,end_line,stage,speaker,confidence,text,source,notes,raw_line_count,text_line_count,source_line_count,source_contains_more_than_text,was_furniture_candidate,resolved_furniture_action"
      );

      for (int i = 0; i < turns.size(); i++) {
        ParseModels.ScriptTurn turn = turns.get(i);
        row(
          out,
          String.valueOf(i),
          String.valueOf(turn.startLine),
          String.valueOf(turn.endLine),
          String.valueOf(turn.stage),
          turn.speaker,
          confidence(turn.notes),
          turn.text,
          turn.source,
          turn.notes,
          String.valueOf(rawLines(turn.startLine, turn.endLine)),
          String.valueOf(pieces(turn.text)),
          String.valueOf(pieces(turn.source)),
          String.valueOf(sourceHasMore(turn.source, turn.text)),
          String.valueOf(hasFurniture(turn.source)),
          furnitureAction(turn.source, turn.text)
        );
      }
    } catch (IOException e) {
      System.err.println("Could not write turns CSV: " + e.getMessage());
    }
  }

  public static void suspicious(
    List<ParseModels.ScriptTurn> turns,
    String filename
  ) {
    if (turns == null || turns.isEmpty()) {
      return;
    }

    try (PrintWriter out = new PrintWriter(new FileWriter(path(filename)))) {
      out.println(
        "turn_index,start_line,end_line,stage,speaker,confidence,text_length,reason,text,source,notes"
      );

      for (int i = 0; i < turns.size(); i++) {
        ParseModels.ScriptTurn turn = turns.get(i);
        String confidence = confidence(turn.notes);
        String reason = reason(turn, confidence);

        if (reason.isEmpty()) {
          continue;
        }

        row(
          out,
          String.valueOf(i),
          String.valueOf(turn.startLine),
          String.valueOf(turn.endLine),
          String.valueOf(turn.stage),
          turn.speaker,
          confidence,
          String.valueOf(TextNormalizer.norm(turn.text).length()),
          reason,
          turn.text,
          turn.source,
          turn.notes
        );
      }
    } catch (IOException e) {
      System.err.println(
        "Could not write suspicious turns CSV: " + e.getMessage()
      );
    }
  }

  public static void health(
    List<ParseModels.ScriptTurn> turns,
    List<String> cues,
    List<String> mine,
    String target,
    String filename
  ) {
    if (turns == null) {
      return;
    }

    int totalTurns = turns.size();
    int unknownTurns = 0;
    int stageTurns = 0;
    int speakerTurns = 0;
    int targetLines = mine == null ? 0 : mine.size();
    int longTurns = 0;
    int speakerWithoutTextTurns = 0;
    int unknownSingleLineTurns = 0;
    int unknownMultiLineTurns = 0;
    int unknownAfterStageTurns = 0;
    int unknownAfterKnownSpeakerTurns = 0;
    ParseModels.ScriptTurn previousTurn = null;
    int recoveredFromPendingTurns = 0;
    int recoveredFromPreviousSpeakerTurns = 0;
    int recoveredFromContextTurns = 0;
    int generatedUnknownTurns = 0;
    int recoveredFromRecentHeadingTurns = 0;
    int ambiguousEnsembleTurns = 0;
    int highConfidenceTurns = 0;
    int mediumConfidenceTurns = 0;
    int lowConfidenceTurns = 0;
    int reviewConfidenceTurns = 0;
    int maxSameSpeakerRun = 0;
    int sameSpeakerRunsOver10 = 0;
    String currentRunSpeaker = "";
    int currentRunLength = 0;

    for (ParseModels.ScriptTurn turn : turns) {
      String speaker = TextNormalizer.cleanName(turn.speaker);
      String text = TextNormalizer.norm(turn.text);
      String notes = TextNormalizer.norm(turn.notes);

      String confidence = confidence(turn.notes);

      if ("HIGH".equals(confidence)) {
        highConfidenceTurns++;
      } else if ("MEDIUM".equals(confidence)) {
        mediumConfidenceTurns++;
      } else if ("LOW".equals(confidence)) {
        lowConfidenceTurns++;
      } else if ("REVIEW".equals(confidence)) {
        reviewConfidenceTurns++;
      }

      String runSpeaker = turn.stage ? "" : speaker;
      if (!runSpeaker.isEmpty() && !"UNKNOWN".equals(runSpeaker)) {
        if (runSpeaker.equals(currentRunSpeaker)) {
          currentRunLength++;
        } else {
          if (currentRunLength > maxSameSpeakerRun) {
            maxSameSpeakerRun = currentRunLength;
          }
          if (currentRunLength > 10) {
            sameSpeakerRunsOver10++;
          }
          currentRunSpeaker = runSpeaker;
          currentRunLength = 1;
        }
      } else {
        if (currentRunLength > maxSameSpeakerRun) {
          maxSameSpeakerRun = currentRunLength;
        }
        if (currentRunLength > 10) {
          sameSpeakerRunsOver10++;
        }
        currentRunSpeaker = "";
        currentRunLength = 0;
      }

      if (turn.stage) {
        stageTurns++;
      } else {
        speakerTurns++;
      }

      if ("UNKNOWN".equals(speaker)) {
        unknownTurns++;

        if (turn.startLine == turn.endLine) {
          unknownSingleLineTurns++;
        } else {
          unknownMultiLineTurns++;
        }

        if (previousTurn != null && previousTurn.stage) {
          unknownAfterStageTurns++;
        }

        if (
          previousTurn != null &&
          !previousTurn.stage &&
          !TextNormalizer.cleanName(previousTurn.speaker).isEmpty() &&
          !"UNKNOWN".equals(TextNormalizer.cleanName(previousTurn.speaker))
        ) {
          unknownAfterKnownSpeakerTurns++;
        }
      }

      if (
        notes.contains("pending_speaker_attached") ||
        notes.contains("context_expected_speaker_attached")
      ) {
        recoveredFromPendingTurns++;
      }

      if (notes.contains("recovered_recent_bare_heading")) {
        recoveredFromRecentHeadingTurns++;
      }

      if (notes.contains("ambiguous_ensemble_dialogue")) {
        ambiguousEnsembleTurns++;
      }

      if (notes.contains("previous_speaker_carryover_rescued_unknown")) {
        recoveredFromPreviousSpeakerTurns++;
      }

      if (notes.contains("context_")) {
        recoveredFromContextTurns++;
      }

      if (
        notes.contains("anonymous_dialogue_unmatched_speaker") ||
        "UNKNOWN".equals(speaker)
      ) {
        generatedUnknownTurns++;
      }

      if (!turn.stage && !speaker.isEmpty() && text.isEmpty()) {
        speakerWithoutTextTurns++;
      }

      if (text.length() > 500) {
        longTurns++;
      }

      previousTurn = turn;
    }

    if (currentRunLength > maxSameSpeakerRun) {
      maxSameSpeakerRun = currentRunLength;
    }
    if (currentRunLength > 10) {
      sameSpeakerRunsOver10++;
    }

    int cueRepeatWarnings = cueWarnings(cues, mine);
    int lowConfidenceCues = cuePrefixCount(cues, mine, "[LOW CONFIDENCE CUE");
    int reviewCues = cuePrefixCount(cues, mine, "[REVIEW CUE");

    try (PrintWriter out = new PrintWriter(new FileWriter(path(filename)))) {
      out.println("metric,value");
      row(out, "target", TextNormalizer.cleanName(target));
      row(out, "total_turns", String.valueOf(totalTurns));
      row(out, "speaker_turns", String.valueOf(speakerTurns));
      row(out, "stage_turns", String.valueOf(stageTurns));
      row(out, "unknown_turns", String.valueOf(unknownTurns));
      row(out, "unknown_turn_rate", percent(unknownTurns, totalTurns));
      row(
        out,
        "unknown_single_line_turns",
        String.valueOf(unknownSingleLineTurns)
      );
      row(
        out,
        "unknown_multi_line_turns",
        String.valueOf(unknownMultiLineTurns)
      );
      row(
        out,
        "unknown_after_stage_turns",
        String.valueOf(unknownAfterStageTurns)
      );
      row(
        out,
        "unknown_after_known_speaker_turns",
        String.valueOf(unknownAfterKnownSpeakerTurns)
      );
      row(
        out,
        "recovered_from_pending_turns",
        String.valueOf(recoveredFromPendingTurns)
      );
      row(
        out,
        "recovered_from_previous_speaker_turns",
        String.valueOf(recoveredFromPreviousSpeakerTurns)
      );
      row(
        out,
        "recovered_from_context_turns",
        String.valueOf(recoveredFromContextTurns)
      );
      row(
        out,
        "generated_unknown_turns",
        String.valueOf(generatedUnknownTurns)
      );
      row(
        out,
        "recovered_from_recent_heading_turns",
        String.valueOf(recoveredFromRecentHeadingTurns)
      );
      row(
        out,
        "ambiguous_ensemble_turns",
        String.valueOf(ambiguousEnsembleTurns)
      );
      row(out, "high_confidence_turns", String.valueOf(highConfidenceTurns));
      row(
        out,
        "medium_confidence_turns",
        String.valueOf(mediumConfidenceTurns)
      );
      row(out, "low_confidence_turns", String.valueOf(lowConfidenceTurns));
      row(
        out,
        "review_confidence_turns",
        String.valueOf(reviewConfidenceTurns)
      );
      row(out, "low_confidence_cues", String.valueOf(lowConfidenceCues));
      row(out, "review_cues", String.valueOf(reviewCues));
      row(out, "max_same_speaker_run", String.valueOf(maxSameSpeakerRun));
      row(
        out,
        "same_speaker_runs_over_10",
        String.valueOf(sameSpeakerRunsOver10)
      );
      row(out, "target_lines", String.valueOf(targetLines));
      row(out, "cue_repeat_warnings", String.valueOf(cueRepeatWarnings));
      row(out, "long_turns_over_500_chars", String.valueOf(longTurns));
      row(
        out,
        "speaker_without_text_turns",
        String.valueOf(speakerWithoutTextTurns)
      );
    } catch (IOException e) {
      System.err.println(
        "Could not write parser health CSV: " + e.getMessage()
      );
    }
  }

  private static String confidence(String notes) {
    String cleanNotes = TextNormalizer.norm(notes);

    if (
      cleanNotes.contains("ambiguous_ensemble_dialogue") ||
      cleanNotes.contains("anonymous_dialogue_unmatched_speaker")
    ) {
      return "REVIEW";
    }

    if (
      cleanNotes.contains("previous_speaker_carryover") ||
      cleanNotes.contains("context_previous_speaker_carryover") ||
      cleanNotes.contains("context_last_dialogue_speaker_carryover")
    ) {
      return "LOW";
    }

    if (
      cleanNotes.contains("speaker_with_text") ||
      cleanNotes.contains("pending_speaker_attached") ||
      cleanNotes.contains("context_expected_speaker_attached") ||
      cleanNotes.contains("recovered_recent_bare_heading")
    ) {
      return "HIGH";
    }

    if (cleanNotes.contains("continued_wrapped_dialogue")) {
      return "MEDIUM";
    }

    if (cleanNotes.contains("stage") || cleanNotes.contains("prose")) {
      return "MEDIUM";
    }

    return "MEDIUM";
  }

  private static String reason(ParseModels.ScriptTurn turn, String confidence) {
    String speaker = TextNormalizer.cleanName(turn.speaker);
    String text = TextNormalizer.norm(turn.text);
    String notes = TextNormalizer.norm(turn.notes);

    if (notes.contains("ambiguous_ensemble_dialogue")) {
      return "ambiguous_ensemble_dialogue";
    }

    if ("UNKNOWN".equals(speaker)) {
      return "unknown_speaker";
    }

    if ("LOW".equals(confidence)) {
      return "low_confidence_inference";
    }

    if ("REVIEW".equals(confidence)) {
      return "review_confidence";
    }

    if (text.length() > 500) {
      return "long_turn_over_500_chars";
    }

    if (!turn.stage && !speaker.isEmpty() && text.isEmpty()) {
      return "speaker_without_text";
    }

    return "";
  }

  private static int cueWarnings(List<String> cues, List<String> mine) {
    if (cues == null || mine == null) {
      return 0;
    }

    int max = Math.min(cues.size(), mine.size());
    Map<String, Integer> cueCounts = countCues(cues, max);
    int warnings = 0;

    for (int count : cueCounts.values()) {
      if (count > 3) {
        warnings++;
      }
    }

    return warnings;
  }

  private static String percent(int numerator, int denominator) {
    if (denominator <= 0) {
      return "0.00%";
    }
    double value = (numerator * 100.0) / denominator;
    return String.format("%.2f%%", value);
  }

  private static int cuePrefixCount(
    List<String> cues,
    List<String> mine,
    String prefix
  ) {
    if (cues == null || mine == null || prefix == null) {
      return 0;
    }

    int max = Math.min(cues.size(), mine.size());
    int count = 0;
    for (int i = 0; i < max; i++) {
      String cue = TextNormalizer.norm(cues.get(i));
      if (cue.startsWith(prefix)) {
        count++;
      }
    }
    return count;
  }

  private static int rawLines(int startLine, int endLine) {
    if (startLine < 0 || endLine < startLine) {
      return 0;
    }
    return endLine - startLine + 1;
  }

  private static int pieces(String value) {
    String cleaned = TextNormalizer.norm(value);
    if (cleaned.isEmpty()) {
      return 0;
    }

    String[] parts = cleaned.split("\\s+/\\s+");
    int count = 0;
    for (String part : parts) {
      if (!TextNormalizer.norm(part).isEmpty()) {
        count++;
      }
    }
    return count;
  }

  private static boolean sourceHasMore(String source, String text) {
    String cleanSource = TextNormalizer.norm(source);
    String cleanText = TextNormalizer.norm(text);
    if (cleanSource.isEmpty()) {
      return false;
    }
    if (cleanText.isEmpty()) {
      return !cleanSource.isEmpty();
    }

    return (
      pieces(cleanSource) > pieces(cleanText) ||
      !normalizedWithoutSeparators(cleanSource).equals(
        normalizedWithoutSeparators(cleanText)
      )
    );
  }

  private static String normalizedWithoutSeparators(String value) {
    return TextNormalizer.norm(value)
      .replaceAll("\\s+/\\s+", " ")
      .replaceAll("\\s+", " ")
      .trim()
      .toLowerCase();
  }

  private static boolean hasFurniture(String value) {
    String cleaned = TextNormalizer.norm(value);
    return (
      cleaned.contains("<FURNITURE_CANDIDATE") ||
      cleaned.contains("</FURNITURE_CANDIDATE>")
    );
  }

  private static String furnitureAction(String source, String text) {
    boolean sourceWrapped = hasFurniture(source);
    boolean textWrapped = hasFurniture(text);

    if (!sourceWrapped && !textWrapped) {
      return "none";
    }

    if (sourceWrapped && TextNormalizer.norm(text).isEmpty()) {
      return "suppressed";
    }

    if (sourceWrapped && !textWrapped) {
      return "unwrapped_or_absorbed";
    }

    if (textWrapped) {
      return "unresolved_wrapper_leaked";
    }

    return "review";
  }

  private static int absorbed(String text) {
    return Math.max(0, pieces(text) - 1);
  }

  private static int skipped(String source, String text) {
    return Math.max(0, pieces(source) - pieces(text));
  }

  private static void row(PrintWriter out, String... cells) {
    for (int i = 0; i < cells.length; i++) {
      if (i > 0) {
        out.print(",");
      }
      out.print(csv(cells[i]));
    }
    out.println();
  }
}
