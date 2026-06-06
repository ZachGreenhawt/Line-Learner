package parser;

import java.util.ArrayList;
import java.util.List;
import parser.model.ParseModels;
import util.TextNormalizer;

public class TurnBuilder {

  public static List<ParseModels.ScriptTurn> fromBlocks(
    List<ParseModels.Block> blocks
  ) {
    List<ParseModels.ScriptTurn> turns = new ArrayList<>();
    if (blocks == null || blocks.isEmpty()) {
      return turns;
    }

    for (ParseModels.Block block : blocks) {
      if (block != null) {
        turns.add(turnFrom(block));
      }
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
      block.music,
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
}
