import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TurnBuilder {

  private static final int MAX_FORWARD_SCAN = 4;

  public static List<ParseModels.ScriptTurn> build(
    List<String> lines,
    int start,
    Set<String> chars
  ) {
    if (lines == null || lines.isEmpty()) {
      return new ArrayList<>();
    }

    Map<Integer, SpeakerHeadingIndex.HeadingRecord> headings =
      SpeakerHeadingIndex.build(lines, chars, null);
    List<ParseModels.Block> blocks = SpeakerBlockBuilder.build(lines, headings);
    return fromBlocks(blocks, start);
  }

  public static List<ParseModels.ScriptTurn> fromBlocks(
    List<ParseModels.Block> blocks
  ) {
    return fromBlocks(blocks, 0);
  }

  private static List<ParseModels.ScriptTurn> fromBlocks(
    List<ParseModels.Block> blocks,
    int start
  ) {
    List<ParseModels.ScriptTurn> turns = new ArrayList<>();
    if (blocks == null || blocks.isEmpty()) {
      return turns;
    }

    for (ParseModels.Block block : blocks) {
      if (block == null || block.endLine < start) {
        continue;
      }
      turns.add(turnFrom(block));
    }

    return turns;
  }

  private static ParseModels.ScriptTurn turnFrom(ParseModels.Block block) {
    String speaker = TextNormalizer.cleanName(block.speaker);
    String text = block.stage
      ? TextNormalizer.norm(block.text)
      : TextNormalizer.cleanSpokenText(block.text);

    return new ParseModels.ScriptTurn(
      block.startLine + 1,
      block.endLine + 1,
      speaker,
      text,
      block.stage,
      block.source,
      notesFor(block)
    );
  }

  private static String notesFor(ParseModels.Block block) {
    String notes = TextNormalizer.norm(block.reason);
    String confidence = TextNormalizer.norm(block.confidence);
    String type = block.type == null ? "" : block.type.name();

    if (!confidence.isEmpty()) {
      notes = append(notes, "confidence_" + confidence);
    }
    if (!type.isEmpty()) {
      notes = append(notes, "block_" + type);
    }

    if (block.type == ParseModels.BlockType.UNKNOWN_DIALOGUE) {
      return append(notes, "anonymous_dialogue_unmatched_speaker");
    }
    if (block.type == ParseModels.BlockType.AMBIGUOUS_ENSEMBLE) {
      return append(
        notes,
        "anonymous_dialogue_unmatched_speaker ambiguous_ensemble_dialogue"
      );
    }
    if (block.type == ParseModels.BlockType.SPEAKER_BLOCK) {
      return append(notes, "speaker_with_text");
    }

    return notes;
  }

  private static String append(String notes, String note) {
    if (note == null || note.isEmpty()) {
      return TextNormalizer.norm(notes);
    }
    if (notes == null || notes.isEmpty()) {
      return note;
    }
    return TextNormalizer.norm(notes + " " + note);
  }

  public static int nextLine(
    List<String> lines,
    int speakerIndex,
    Set<String> chars
  ) {
    if (lines == null || speakerIndex < 0) {
      return -1;
    }

    int maxIndex = Math.min(lines.size() - 1, speakerIndex + MAX_FORWARD_SCAN);
    for (int i = speakerIndex + 1; i <= maxIndex; i++) {
      String line = TextNormalizer.norm(lines.get(i));

      if (line.isEmpty() || shouldSkipLine(line, chars)) {
        continue;
      }
      if (!SpeakerDetector.name(line, chars).isEmpty()) {
        return -1;
      }
      if (StageDetector.whole(line) || StageDetector.location(line)) {
        continue;
      }
      if (
        StageDetector.prose(line, chars) && !ScriptParser.dialogue(line, chars)
      ) {
        continue;
      }
      if (ScriptParser.dialogue(line, chars)) {
        return i;
      }
    }

    return -1;
  }

  private static boolean shouldSkipLine(String line, Set<String> chars) {
    boolean knownSpeaker =
      SpeakerDetector.is(line, chars) || SpeakerDetector.has(line, chars);
    return (
      StageDetector.skip(line) || (StageDetector.junk(line) && !knownSpeaker)
    );
  }
}
