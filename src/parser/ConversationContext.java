package parser;

import java.util.Set;
import parser.detect.*;
import parser.model.ParseModels;
import util.TextNormalizer;

public class ConversationContext {

  private static final int EXPECTED_WINDOW = 4;
  private static final int LAST_DIALOGUE_WINDOW = 3;
  private static final int MAX_EXCHANGE_LENGTH = 120;

  private String expectedSpeaker;
  private int expectedLine;
  private String expectedSource;
  private String expectedNotes;

  private String lastExplicitSpeaker;
  private int lastExplicitLine;

  private String lastDialogueSpeaker;
  private int lastDialogueLine;

  private ParseModels.ScriptTurn lastTurn;

  private int recentDialogueTurns;
  private int recentStageTurns;
  private int recentUnknownTurns;

  public ConversationContext() {
    clear();
  }

  public void clear() {
    expectedSpeaker = "";
    expectedLine = -1;
    expectedSource = "";
    expectedNotes = "";

    lastExplicitSpeaker = "";
    lastExplicitLine = -1;

    lastDialogueSpeaker = "";
    lastDialogueLine = -1;

    lastTurn = null;

    recentDialogueTurns = 0;
    recentStageTurns = 0;
    recentUnknownTurns = 0;
  }

  public void expect(String speaker, int line, String source, String notes) {
    expectedSpeaker = TextNormalizer.cleanName(speaker);
    expectedLine = line;
    expectedSource = TextNormalizer.norm(source);
    expectedNotes = TextNormalizer.norm(notes);

    lastExplicitSpeaker = expectedSpeaker;
    lastExplicitLine = line;
  }

  public boolean hasExpected(int line) {
    return (
      !expectedSpeaker.isEmpty() &&
      expectedLine > 0 &&
      line - expectedLine <= EXPECTED_WINDOW
    );
  }

  public boolean expectedExpired(int line) {
    return (
      !expectedSpeaker.isEmpty() &&
      expectedLine > 0 &&
      line - expectedLine > EXPECTED_WINDOW
    );
  }

  public String expectedSpeaker() {
    return expectedSpeaker;
  }

  public int expectedLine() {
    return expectedLine;
  }

  public String expectedSource() {
    return expectedSource;
  }

  public String expectedNotes() {
    return expectedNotes;
  }

  public void bridge(String line, String note) {
    expectedSource = TextNormalizer.norm(expectedSource + " / " + line);
    expectedNotes = note(expectedNotes, note);
  }

  public void clearExpected() {
    expectedSpeaker = "";
    expectedLine = -1;
    expectedSource = "";
    expectedNotes = "";
  }

  public boolean waitAfterBridge(String line, Set<String> chars) {
    return !expectedSpeaker.isEmpty() && bridge(line, chars);
  }

  public boolean attachExpected(
    String line,
    int currentLine,
    Set<String> chars
  ) {
    if (!hasExpected(currentLine)) {
      return false;
    }
    if (TextNormalizer.cleanSpokenText(line).isEmpty()) {
      return false;
    }
    return !StageDetector.strong(line, chars);
  }

  public boolean ambiguousEnsemble(
    String line,
    int currentLine,
    Set<String> chars
  ) {
    String spoken = TextNormalizer.cleanSpokenText(line);
    if (spoken.isEmpty()) {
      return false;
    }
    if (StageDetector.strong(line, chars)) {
      return false;
    }
    if (!rapidFire()) {
      return false;
    }
    if (
      lastDialogueSpeaker.isEmpty() || "UNKNOWN".equals(lastDialogueSpeaker)
    ) {
      return false;
    }
    if (currentLine - lastDialogueLine > LAST_DIALOGUE_WINDOW) {
      return false;
    }
    return exchangeLine(spoken);
  }

  public boolean leaveUnknown(String line, int currentLine, Set<String> chars) {
    String spoken = TextNormalizer.cleanSpokenText(line);
    if (spoken.isEmpty()) {
      return false;
    }
    if (StageDetector.strong(line, chars)) {
      return true;
    }
    if (ambiguousEnsemble(line, currentLine, chars)) {
      return true;
    }
    return (
      recentUnknownTurns >= 2 ||
      currentLine - lastDialogueLine > LAST_DIALOGUE_WINDOW
    );
  }

  public boolean rapidFire() {
    return recentDialogueTurns >= 3 && recentStageTurns <= 1;
  }

  public String confidence(String notes) {
    String clean = TextNormalizer.norm(notes);
    if (clean.contains("speaker_with_text")) {
      return "HIGH";
    }
    if (
      clean.contains("pending_speaker_attached") ||
      clean.contains("context_expected_speaker_attached")
    ) {
      return "HIGH";
    }
    if (clean.contains("continued_wrapped_dialogue")) {
      return "MEDIUM";
    }
    if (clean.contains("recovered_recent_bare_heading")) {
      return "HIGH";
    }
    if (clean.contains("ambiguous_ensemble_dialogue")) {
      return "REVIEW";
    }
    if (clean.contains("context_")) {
      return "LOW";
    }
    if (clean.contains("anonymous_dialogue_unmatched_speaker")) {
      return "REVIEW";
    }
    return "MEDIUM";
  }

  public void recordHeading(String speaker, int line) {
    String clean = TextNormalizer.cleanName(speaker);
    if (clean.isEmpty()) {
      return;
    }

    lastExplicitSpeaker = clean;
    lastExplicitLine = line;
  }

  public void record(ParseModels.ScriptTurn turn) {
    lastTurn = turn;
    if (turn == null) {
      return;
    }

    if (turn.stage) {
      recordStage();
      return;
    }

    String speaker = TextNormalizer.cleanName(turn.speaker);
    if ("UNKNOWN".equals(speaker)) {
      recordUnknown();
      return;
    }

    if (speaker.isEmpty()) {
      return;
    }

    recentDialogueTurns++;
    recentUnknownTurns = 0;
    recentStageTurns = Math.max(0, recentStageTurns - 1);

    if (!turn.notes.contains("ambiguous_ensemble_dialogue")) {
      lastDialogueSpeaker = speaker;
      lastDialogueLine = turn.endLine;
    }
  }

  public String lastExplicitSpeaker() {
    return lastExplicitSpeaker;
  }

  public int lastExplicitLine() {
    return lastExplicitLine;
  }

  public String lastDialogueSpeaker() {
    return lastDialogueSpeaker;
  }

  public int lastDialogueLine() {
    return lastDialogueLine;
  }

  public ParseModels.ScriptTurn lastTurn() {
    return lastTurn;
  }

  public int recentDialogueTurns() {
    return recentDialogueTurns;
  }

  public int recentStageTurns() {
    return recentStageTurns;
  }

  public int recentUnknownTurns() {
    return recentUnknownTurns;
  }

  public String debug() {
    return (
      "expected=" +
      show(expectedSpeaker) +
      " expectedLine=" +
      expectedLine +
      " lastExplicit=" +
      show(lastExplicitSpeaker) +
      " lastExplicitLine=" +
      lastExplicitLine +
      " lastDialogue=" +
      show(lastDialogueSpeaker) +
      " lastDialogueLine=" +
      lastDialogueLine +
      " rapidFire=" +
      rapidFire() +
      " leaveUnknownBias=" +
      (recentUnknownTurns >= 2) +
      " recentDialogueTurns=" +
      recentDialogueTurns +
      " recentStageTurns=" +
      recentStageTurns +
      " recentUnknownTurns=" +
      recentUnknownTurns
    );
  }

  private void recordStage() {
    recentStageTurns++;
    recentDialogueTurns = Math.max(0, recentDialogueTurns - 1);
  }

  private void recordUnknown() {
    recentUnknownTurns++;
    recentDialogueTurns = Math.max(0, recentDialogueTurns - 1);
    recentStageTurns = Math.max(0, recentStageTurns - 1);
  }

  private static boolean bridge(String line, Set<String> chars) {
    line = TextNormalizer.norm(line);
    return (
      StageDetector.whole(line) ||
      line.startsWith("(") ||
      line.startsWith("[") ||
      StageDetector.strong(line, chars)
    );
  }

  private static boolean strictContinuation(String text) {
    String clean = TextNormalizer.cleanSpokenText(text);
    if (clean.isEmpty() || clean.length() > MAX_EXCHANGE_LENGTH) {
      return false;
    }
    if (Character.isLowerCase(clean.charAt(0))) {
      return true;
    }

    String lower = clean.toLowerCase();
    return (
      lower.startsWith("and ") ||
      lower.startsWith("but ") ||
      lower.startsWith("or ") ||
      lower.startsWith("then ") ||
      lower.startsWith("so ") ||
      lower.startsWith("because ") ||
      lower.startsWith("with ") ||
      lower.startsWith("to ") ||
      lower.startsWith("of ") ||
      lower.startsWith("that ") ||
      lower.startsWith("which ") ||
      lower.startsWith("who ")
    );
  }

  private static boolean exchangeLine(String text) {
    String clean = TextNormalizer.cleanSpokenText(text);
    if (clean.isEmpty() || clean.length() > MAX_EXCHANGE_LENGTH) {
      return false;
    }
    if (strictContinuation(clean)) {
      return false;
    }

    String lower = clean.toLowerCase();
    return (
      clean.endsWith("?") ||
      clean.endsWith("!") ||
      lower.startsWith("yes") ||
      lower.startsWith("no") ||
      lower.startsWith("oh") ||
      lower.startsWith("well") ||
      lower.startsWith("what") ||
      lower.startsWith("why") ||
      lower.startsWith("where") ||
      lower.startsWith("when") ||
      lower.startsWith("how") ||
      lower.startsWith("who") ||
      lower.equals("good morning") ||
      lower.equals("not yet") ||
      lower.equals("hot dog") ||
      lower.equals("hot dog.")
    );
  }

  private static String note(String notes, String note) {
    if (note == null || note.isEmpty()) {
      return TextNormalizer.norm(notes);
    }
    if (notes == null || notes.isEmpty()) {
      return note;
    }
    return TextNormalizer.norm(notes + " " + note);
  }

  private static String show(String text) {
    String clean = TextNormalizer.norm(text);
    return clean.isEmpty() ? "<none>" : clean;
  }
}
