import java.util.ArrayList;
import java.util.List;

public class CuePairBuilder {

  private static final String UNKNOWN = "UNKNOWN";
  private static final String REVIEW = "[REVIEW CUE";

  public static class Result {

    public final List<String> cues;
    public final List<String> mine;

    public Result(List<String> cues, List<String> mine) {
      this.cues = cues;
      this.mine = mine;
    }
  }

  public static Result build(
    List<ParseModels.ScriptTurn> turns,
    String target,
    Settings settings
  ) {
    List<String> cues = new ArrayList<>();
    List<String> mine = new ArrayList<>();
    build(turns, target, cues, mine, settings);
    return new Result(cues, mine);
  }

  public static void build(
    List<ParseModels.ScriptTurn> turns,
    String target,
    List<String> cues,
    List<String> mine,
    Settings settings
  ) {
    String cleanTarget = TextNormalizer.cleanName(target);
    String cue = startCue(cleanTarget);
    String unknownCue = "";

    for (ParseModels.ScriptTurn turn : turns) {
      if (empty(turn)) {
        continue;
      }

      if (turn.stage) {
        cue = addStage(cue, turn.text, settings);
        unknownCue = "";
        continue;
      }

      if (reviewOnly(turn)) {
        unknownCue = "";
        continue;
      }

      String speaker = TextNormalizer.cleanName(turn.speaker);
      if (targetTurn(turn, cleanTarget)) {
        cues.add(unknownCue.isBlank() ? cue : unknownCue);
        mine.add(turn.text);
        unknownCue = "";
        continue;
      }

      String nextCue = cueFrom(turn, speaker);
      if (useCue(turn, speaker, nextCue)) {
        if (UNKNOWN.equals(speaker) || speaker.isBlank()) {
          unknownCue = nextCue;
        } else {
          cue = nextCue;
          unknownCue = "";
        }
        continue;
      }

      if (!UNKNOWN.equals(speaker) && !speaker.isBlank()) {
        unknownCue = "";
      }
    }
  }

  private static boolean targetTurn(
    ParseModels.ScriptTurn turn,
    String target
  ) {
    String speaker = TextNormalizer.cleanName(turn.speaker);
    if (target == null || target.isEmpty() || speaker.isEmpty()) {
      return false;
    }
    if (speaker.equals(target)) {
      return true;
    }

    for (String part : speaker.split("\\s*/\\s*")) {
      if (TextNormalizer.cleanName(part).equals(target)) {
        return true;
      }
    }

    return false;
  }

  private static String cueFrom(ParseModels.ScriptTurn turn, String speaker) {
    if (empty(turn)) {
      return "";
    }
    if (UNKNOWN.equals(speaker) || speaker.isEmpty()) {
      return UNKNOWN + ": " + turn.text;
    }
    if (low(turn)) {
      return reviewCue(turn, reason(turn));
    }
    return line(turn, speaker);
  }

  private static boolean useCue(
    ParseModels.ScriptTurn turn,
    String speaker,
    String cue
  ) {
    if (turn == null || cue == null || cue.isBlank()) {
      return false;
    }
    if (UNKNOWN.equals(speaker) || speaker == null || speaker.isBlank()) {
      return useUnknown(turn);
    }
    return !low(turn);
  }

  private static String reviewCue(ParseModels.ScriptTurn turn, String reason) {
    reason = TextNormalizer.norm(reason);
    if (reason.isEmpty()) {
      reason = "review speaker";
    }
    return REVIEW + " - " + reason + "]: " + turn.text;
  }

  private static String startCue(String target) {
    return "**" + target + " STARTS THE SCENE**";
  }

  private static String addStage(String cue, String stage, Settings settings) {
    if (!settings.includeStageDirectionsInCue()) {
      return cue;
    }
    return TextNormalizer.norm(cue + " " + stage);
  }

  private static String reason(ParseModels.ScriptTurn turn) {
    if (turn == null) {
      return "missing turn";
    }

    String notes = TextNormalizer.norm(turn.notes);
    if (notes.contains("unknown_preserved_actual_line")) {
      return "unknown speaker preserved actual line";
    }
    if (notes.contains("recovered_contextual_continuation")) {
      return "recovered contextual continuation";
    }
    if (notes.contains("front_matter") || notes.contains("page_furniture")) {
      return "front matter or page furniture";
    }
    if (notes.contains("ambiguous_ensemble_dialogue")) {
      return "ambiguous ensemble exchange";
    }
    if (notes.contains("anonymous_dialogue_unmatched_speaker")) {
      return "unmatched speaker";
    }
    if (notes.contains("context_last_dialogue_speaker_carryover")) {
      return "inferred from last dialogue speaker";
    }
    if (notes.contains("context_previous_speaker_carryover")) {
      return "inferred from previous speaker";
    }
    if (notes.contains("previous_speaker_carryover")) {
      return "previous-speaker carryover";
    }

    return "inferred context";
  }

  private static boolean low(ParseModels.ScriptTurn turn) {
    if (turn == null) {
      return true;
    }

    String notes = TextNormalizer.norm(turn.notes);
    return (
      notes.contains("context_previous_speaker_carryover") ||
      notes.contains("context_last_dialogue_speaker_carryover") ||
      notes.contains("previous_speaker_carryover") ||
      notes.contains("ambiguous_ensemble_dialogue") ||
      notes.contains("anonymous_dialogue_unmatched_speaker") ||
      notes.contains("recovered_contextual_continuation") ||
      notes.contains("front_matter") ||
      notes.contains("page_furniture") ||
      notes.contains("suppressed_furniture")
    );
  }

  private static boolean useUnknown(ParseModels.ScriptTurn turn) {
    String text = TextNormalizer.norm(turn.text);
    if (text.isEmpty() || furniture(text)) {
      return false;
    }
    return !low(turn) || has(turn, "unknown_preserved_actual_line");
  }

  private static boolean reviewOnly(ParseModels.ScriptTurn turn) {
    if (empty(turn)) {
      return true;
    }
    if (furniture(turn.text)) {
      return true;
    }

    String notes = TextNormalizer.norm(turn.notes);
    return (
      notes.contains("front_matter") ||
      notes.contains("page_furniture") ||
      notes.contains("suppressed_furniture")
    );
  }

  private static boolean has(ParseModels.ScriptTurn turn, String token) {
    if (turn == null || token == null || token.isBlank()) {
      return false;
    }
    return TextNormalizer.norm(turn.notes).contains(token);
  }

  private static boolean empty(ParseModels.ScriptTurn turn) {
    return turn == null || TextNormalizer.norm(turn.text).isEmpty();
  }

  private static boolean furniture(String text) {
    String cleaned = TextNormalizer.norm(text);
    if (cleaned.isEmpty()) {
      return true;
    }

    String lower = cleaned.toLowerCase();
    if (
      lower.matches(
        ".*\\b(isbn|copyright|all rights|permission|publisher|published|publishing|press|catalogue|cataloging|manufactured|book design|cover art|cover design|directed by|produced by|commissioned by|premiere|licensed|license|licence|royalty|royalties|street|avenue|road|lane|drive|boulevard|suite|floor|building|city|state|country|website|www\\.|\\.com|\\.org|\\.net)\\b.*"
      )
    ) {
      return true;
    }
    if (cleaned.matches("^\\d{1,4}$")) {
      return true;
    }

    return capsFurniture(cleaned);
  }

  private static boolean capsFurniture(String text) {
    String cleaned = TextNormalizer.norm(text);
    if (cleaned.isEmpty() || cleaned.length() > 55) {
      return false;
    }
    if (
      cleaned.contains("/") ||
      cleaned.contains("(") ||
      cleaned.contains("[") ||
      roleWord(cleaned)
    ) {
      return false;
    }

    String upper = cleaned.toUpperCase();
    if (!upper.equals(cleaned)) {
      return false;
    }
    if (upper.matches(".*\\d{1,4}.*")) {
      return true;
    }

    String[] words = upper.split("\\s+");
    if (words.length < 1 || words.length > 3) {
      return false;
    }

    for (String word : words) {
      if (!word.matches("[A-Z][A-Z'’\\-]{1,}")) {
        return false;
      }
    }

    return words.length >= 2 || upper.length() > 10;
  }

  private static boolean roleWord(String text) {
    String upper = TextNormalizer.cleanName(text).toUpperCase();
    return upper.matches(
      ".*\\b(GIRL|BOY|MAN|WOMAN|MOTHER|FATHER|SON|DAUGHTER|CHILD|VOICE|VOICES|CLERK|JUDGE|PRIEST|LAWYER|REPORTER|GUARD|MATRON|HUSBAND|WIFE|COLONEL|CAPTAIN|SERGEANT|DOCTOR|NURSE|OFFICER|INSPECTOR|DETECTIVE|PROFESSOR|TEACHER|STUDENT|WAITER|WAITRESS|BELLBOY|JANITOR|MAID|SERVANT|KING|QUEEN|PRINCE|PRINCESS|DUKE|DUCHESS|LORD|LADY|FIRST|SECOND|THIRD|FOURTH|YOUNG|OLD|OLDER|ELDERLY|CHORUS|ENSEMBLE|CROWD|GROUP|OFFSTAGE|ANNOUNCER|NARRATOR|ADDING|FILING|TELEPHONE|DEFENSE|DEFENCE|PROSECUTION|BARBER|LOVER|HUCKSTER|SPECTATOR|SPECTATORS|JUROR|JURY|WITNESS|POLICEMAN|POLICE|ATTENDANT|STRANGER|CUSTOMER|WORKER|SECRETARY|STENOGRAPHER|PERSON|SOMEONE|SOMEBODY|ACTOR|ACTRESS|PLAYER|PERFORMER|OPERATOR|TYPIST|BYSTANDER)\\b.*"
    );
  }

  private static String line(ParseModels.ScriptTurn turn, String speaker) {
    if (UNKNOWN.equals(speaker)) {
      return UNKNOWN + ": " + turn.text;
    }
    return turn.speaker + ": " + turn.text;
  }
}
