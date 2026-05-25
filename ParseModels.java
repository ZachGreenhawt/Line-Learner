public class ParseModels {

  public enum IssueType {
    SPLIT_NAME,
    BAD_SPEAKER_MATCH,
    STAGE_AS_DIALOGUE,
    DIALOGUE_AS_STAGE,
    INLINE_SPEAKER_FAILURE,
    OCR_ARTIFACT,
    PAGE_OR_HEADER_JUNK,
    CAST_OR_SCENE_LIST,
    EMPTY_OR_UNKNOWN,
  }

  public static class Issue {

    public final int lineNumber;
    public final String rawLine;
    public final String parsedSpeaker;
    public final String parsedText;
    public final String decision;
    public final IssueType issueType;
    public final String reason;

    public Issue(
      int lineNumber,
      String rawLine,
      String parsedSpeaker,
      String parsedText,
      String decision,
      IssueType issueType,
      String reason
    ) {
      this.lineNumber = lineNumber;
      this.rawLine = safe(rawLine);
      this.parsedSpeaker = safe(parsedSpeaker);
      this.parsedText = safe(parsedText);
      this.decision = safe(decision);
      this.issueType =
        issueType == null ? IssueType.EMPTY_OR_UNKNOWN : issueType;
      this.reason = safe(reason);
    }
  }

  public static class DebugLine {

    public final int lineNumber;
    public final String line;
    public final String speaker;
    public final String afterSpeaker;
    public final boolean character;
    public final boolean prefix;
    public final boolean dialogue;
    public final boolean prose;
    public final boolean stage;
    public final boolean front;
    public final boolean junk;
    public final boolean skip;
    public final boolean bodyBefore;
    public final boolean bodyAfter;
    public final String activeBefore;
    public final String activeAfter;
    public final String cueBefore;
    public final String cueAfter;
    public final String action;
    public final String spoken;
    public final String addedCue;
    public final String addedMine;

    public DebugLine(
      int lineNumber,
      String line,
      String speaker,
      String afterSpeaker,
      boolean character,
      boolean prefix,
      boolean dialogue,
      boolean prose,
      boolean stage,
      boolean front,
      boolean junk,
      boolean skip,
      boolean bodyBefore,
      boolean bodyAfter,
      String activeBefore,
      String activeAfter,
      String cueBefore,
      String cueAfter,
      String action,
      String spoken,
      String addedCue,
      String addedMine
    ) {
      this.lineNumber = lineNumber;
      this.line = safe(line);
      this.speaker = safe(speaker);
      this.afterSpeaker = safe(afterSpeaker);
      this.character = character;
      this.prefix = prefix;
      this.dialogue = dialogue;
      this.prose = prose;
      this.stage = stage;
      this.front = front;
      this.junk = junk;
      this.skip = skip;
      this.bodyBefore = bodyBefore;
      this.bodyAfter = bodyAfter;
      this.activeBefore = safe(activeBefore);
      this.activeAfter = safe(activeAfter);
      this.cueBefore = safe(cueBefore);
      this.cueAfter = safe(cueAfter);
      this.action = safe(action);
      this.spoken = safe(spoken);
      this.addedCue = safe(addedCue);
      this.addedMine = safe(addedMine);
    }
  }

  public static class ScriptTurn {

    public final int startLine;
    public final int endLine;
    public final String speaker;
    public final String text;
    public final boolean stage;
    public final String source;
    public final String notes;

    public ScriptTurn(
      int startLine,
      int endLine,
      String speaker,
      String text,
      boolean stage,
      String source,
      String notes
    ) {
      this.startLine = startLine;
      this.endLine = endLine;
      this.speaker = safe(speaker);
      this.text = safe(text);
      this.stage = stage;
      this.source = safe(source);
      this.notes = safe(notes);
    }
  }

  public enum BlockType {
    SPEAKER_BLOCK,
    STAGE_BLOCK,
    UNKNOWN_DIALOGUE,
    AMBIGUOUS_ENSEMBLE,
    PAGE_OR_FRONT_MATTER,
  }

  public static class Block {

    public final int startLine;
    public final int endLine;
    public final String speaker;
    public final String text;
    public final String source;
    public final String confidence;
    public final String reason;
    public final BlockType type;
    public final boolean stage;

    public Block(
      int startLine,
      int endLine,
      String speaker,
      String text,
      String source,
      String confidence,
      String reason,
      BlockType type,
      boolean stage
    ) {
      this.startLine = startLine;
      this.endLine = endLine;
      this.speaker = safe(speaker);
      this.text = safe(text);
      this.source = safe(source);
      this.confidence = safe(confidence);
      this.reason = safe(reason);
      this.type = type == null ? BlockType.UNKNOWN_DIALOGUE : type;
      this.stage = stage;
    }

    public ScriptTurn turn() {
      return new ScriptTurn(
        startLine,
        endLine,
        speaker,
        text,
        stage,
        source,
        reason
      );
    }
  }

  private static String safe(String text) {
    return text == null ? "" : text;
  }
}
