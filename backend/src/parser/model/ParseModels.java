package parser.model;

public class ParseModels {

  public static class ScriptTurn {

    public final int startLine;
    public final int endLine;
    public final String speaker;
    public final String text;
    public final boolean stage;
    public final boolean music;
    public final String source;
    public final String notes;

    public ScriptTurn(
      int startLine,
      int endLine,
      String speaker,
      String text,
      boolean stage,
      boolean music,
      String source,
      String notes
    ) {
      this.startLine = startLine;
      this.endLine = endLine;
      this.speaker = safe(speaker);
      this.text = safe(text);
      this.stage = stage;
      this.music = music;
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
    public final boolean music;

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
      this(
        startLine,
        endLine,
        speaker,
        text,
        source,
        confidence,
        reason,
        type,
        stage,
        false
      );
    }

    public Block(
      int startLine,
      int endLine,
      String speaker,
      String text,
      String source,
      String confidence,
      String reason,
      BlockType type,
      boolean stage,
      boolean music
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
      this.music = music;
    }
  }

  private static String safe(String text) {
    return text == null ? "" : text;
  }
}
