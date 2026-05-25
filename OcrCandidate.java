public class OcrCandidate {

  public final String text;
  public final int rotation;
  public final double score;

  public final int speakerLines;
  public final int dialogue;
  public final int junk;
  public final int caps;
  public final int stage;
  public final int words;
  public final int tokens;
  public final int mirrored;
  public final int index;
  public final int lines;
  public final String name;
  public final String tier;
  public final double junkRatio;
  public final double englishRatio;
  public final double artifacts;
  public final double readability;
  public final double confidence;

  public OcrCandidate(
    String text,
    int rotation,
    double score,
    int speakerLines,
    int dialogue,
    int junk,
    int caps,
    int stage,
    int words,
    int tokens,
    int mirrored,
    int index,
    int lines,
    String name,
    String tier,
    double junkRatio,
    double englishRatio,
    double artifacts,
    double readability,
    double confidence
  ) {
    this.text = text == null ? "" : text;
    this.rotation = rotation;
    this.score = score;

    this.speakerLines = speakerLines;
    this.dialogue = dialogue;
    this.junk = junk;
    this.caps = caps;
    this.stage = stage;
    this.words = words;
    this.tokens = tokens;
    this.mirrored = mirrored;
    this.index = index;
    this.lines = lines;

    this.name = name == null ? "unknown" : name;
    this.tier = tier == null ? "full-search" : tier;

    this.junkRatio = junkRatio;
    this.englishRatio = englishRatio;
    this.artifacts = artifacts;
    this.readability = readability;
    this.confidence = confidence;
  }

  public void printDebug() {
    System.out.println(
      String.format(
        "OCR | rotation=%d deg index=%d name=%s tier=%s score=%s confidence=%.2f speakers=%d dialogue=%d stage=%d uppercase=%d garbage=%d mirrored=%d tokens=%d words=%d lines=%d junkRatio=%.2f english=%.2f artifacts=%.2f readable=%.2f",
        rotation,
        index,
        name,
        tier,
        score,
        confidence,
        speakerLines,
        dialogue,
        stage,
        caps,
        junk,
        mirrored,
        tokens,
        words,
        lines,
        junkRatio,
        englishRatio,
        artifacts,
        readability
      )
    );
  }
}
