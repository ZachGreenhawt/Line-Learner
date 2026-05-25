import java.util.List;
import java.util.Set;

public class StageDetector {

  public static final String[] ACTION_WORDS = {
    "enters",
    "exits",
    "goes",
    "comes",
    "walks",
    "runs",
    "sits",
    "stands",
    "applauds",
    "applaud",
    "winces",
    "wince",
    "demonstrates",
    "demonstrate",
    "straightens",
    "straighten",
    "shines",
    "shine",
    "combs",
    "comb",
    "helps",
    "help",
    "follows",
    "follow",
    "reads",
    "read",
    "twirls",
    "twirl",
    "consoles",
    "console",
    "cleans",
    "clean",
    "remembers",
    "remember",
    "forgets",
    "forget",
    "reports",
    "report",
    "plants",
    "plant",
    "prunes",
    "prune",
    "stocks",
    "stock",
    "wraps",
    "wrap",
    "tightens",
    "tighten",
    "traces",
    "trace",
    "sips",
    "sip",
    "eats",
    "eat",
    "waves",
    "wave",
    "points",
    "point",
    "indicates",
    "indicate",
    "exclaims",
    "exclaim",
    "calls",
    "call",
    "answers",
    "answer",
    "replies",
    "reply",
    "sorts",
    "sort",
    "looks",
    "watches",
    "nods",
    "shrugs",
    "shakes",
    "kisses",
    "touches",
    "takes",
    "puts",
    "lays",
    "brings",
    "speaks",
    "whispers",
    "cries",
    "smiles",
    "laughs",
    "dances",
    "comforts",
    "appears",
    "tolls",
    "turns",
    "moves",
    "crosses",
    "holds",
    "picks",
    "drops",
    "opens",
    "closes",
    "exchange",
    "exchanges",
    "stare",
    "stares",
    "gets",
    "raises",
    "gestures",
    "re-enters",
    "reenter",
    "re-enter",
    "reappears",
    "leaves",
    "kneels",
    "kneel",
    "cry",
    "exit",
    "enter",
    "blackout",
    "fades",
    "fade",
    "continues",
    "continue",
    "disclosed",
    "accompany",
    "accompanies",
    "sings",
    "sang",
    "stops",
    "stop",
    "go",
    "cross",
    "start",
    "starts",
    "started",
    "finish",
    "finishes",
    "wait",
    "waits",
    "waiting",
    "pause",
    "pauses",
    "listens",
    "listen",
    "rise",
    "rises",
    "fall",
    "falls",
    "standing",
    "sitting",
  };

  private static final String[] LOCATION_PREFIXES = {
    "inside ",
    "outside ",
    "onstage",
    "offstage",
    "backstage",
    "upstage",
    "downstage",
    "stage left",
    "stage right",
    "lights ",
    "light ",
    "sound ",
    "music ",
    "blackout",
    "curtain",
    "at rise",
    "at the rise",
    "later",
    "moments later",
    "a room",
    "a kitchen",
    "a bedroom",
    "a living room",
    "a hotel room",
    "a sitting room",
    "a dining room",
    "a courtroom",
    "a cell",
    "a jail cell",
    "a prison cell",
    "a garden",
    "a yard",
    "a stage",
    "a window",
    "a door",
    "a street",
    "the room",
    "the kitchen",
    "the bedroom",
    "the living room",
    "the hotel room",
    "the sitting room",
    "the dining room",
    "the courtroom",
    "the cell",
    "the jail cell",
    "the prison cell",
    "the garden",
    "the yard",
    "the stage",
    "the window",
    "the door",
    "the street",
  };

  private static final String[] SUBJECT_PREFIXES = {
    "worker ",
    "workers ",
    "clerk ",
    "clerks ",
    "reporter ",
    "reporters ",
    "lawyer ",
    "lawyers ",
    "voice ",
    "voices ",
    "man ",
    "men ",
    "woman ",
    "women ",
    "boy ",
    "boys ",
    "girl ",
    "girls ",
    "all ",
    "both ",
    "everyone ",
    "everybody ",
    "somebody ",
    "someone ",
    "nobody ",
    "anybody ",
    "anyone ",
    "one ",
    "two ",
    "a man ",
    "a woman ",
    "the man ",
    "the woman ",
    "a voice ",
    "the voice ",
  };

  private static final String[] PRONOUN_STAGE_STARTS = {
    "he ",
    "she ",
    "they ",
    "we ",
    "it ",
  };

  private static final String[] OBJECT_STAGE_STARTS = {
    "the door ",
    "the doors ",
    "the window ",
    "the windows ",
    "the light ",
    "the lights ",
    "the sound ",
    "the music ",
    "the curtain ",
    "the phone ",
    "the telephone ",
    "a door ",
    "a window ",
    "a light ",
    "a sound ",
    "a phone ",
    "a telephone ",
    "the womb ",
    "a bell ",
    "the bell ",
    "church bell ",
    "the church bell ",
    "the front door ",
    "the stairs ",
    "the table ",
    "the newspaper ",
    "the phone rings ",
  };

  public static boolean skip(String line) {
    line = TextNormalizer.norm(line);
    String up = line.toUpperCase();

    return (
      line.isEmpty() ||
      up.equals("ACT") ||
      up.equals("SCENE") ||
      up.matches("^ACT\\s+.*") ||
      up.matches("^SCENE\\s+.*")
    );
  }

  public static boolean junk(String line) {
    line = TextNormalizer.norm(line);
    if (line.isEmpty()) {
      return false;
    }

    String up = line.toUpperCase();

    if (looksLikeNumberedSpeaker(up) || looksLikeParentheticalSpeaker(up)) {
      return false;
    }
    if (line.contains("?") || line.contains("!")) {
      return false;
    }
    if (isHeaderButPlayable(line)) {
      return false;
    }

    if (up.matches("^\\d{1,4}$")) {
      return true;
    }
    if (up.matches("^\\d{1,4}\\s+[A-Z][A-Z .'-]{2,}$")) {
      return true;
    }
    if (up.matches("^[A-Z][A-Z .'-]{2,}\\s+\\d{1,4}$")) {
      return true;
    }

    return hasPageNumberAndHeaderWords(up);
  }

  private static boolean looksLikeNumberedSpeaker(String up) {
    return up.matches("^[A-Z][A-Z .'/&-]{1,30}\\s+\\d{1,2}\\.?$");
  }

  private static boolean looksLikeParentheticalSpeaker(String up) {
    return up.matches("^[A-Z][A-Z0-9 /'.&-]{1,45}\\s*\\(.*\\)\\.?$");
  }

  private static boolean hasPageNumberAndHeaderWords(String up) {
    int nums = 0;
    int caps = 0;

    String[] parts = up.split("\\s+");
    for (String part : parts) {
      String cleaned = part.replaceAll("[^A-Z0-9]", "");
      if (cleaned.matches("\\d{1,4}")) {
        nums++;
      }
      if (cleaned.matches("[A-Z]{3,}")) {
        caps++;
      }
    }

    return nums > 0 && caps >= 2 && up.length() <= 90;
  }

  public static boolean actionStart(String text) {
    String lower = TextNormalizer.norm(text).toLowerCase();
    if (lower.isEmpty()) {
      return false;
    }

    for (String word : ACTION_WORDS) {
      if (matchesWordStart(lower, word)) {
        return true;
      }
    }

    return (
      lower.startsWith("toward ") ||
      lower.startsWith("towards ") ||
      lower.startsWith("offstage") ||
      lower.startsWith("onstage") ||
      lower.startsWith("aside") ||
      lower.startsWith("pause") ||
      lower.startsWith("beat") ||
      lower.startsWith("silence") ||
      lower.startsWith("exit ") ||
      lower.startsWith("enter ") ||
      lower.startsWith("re-enter ") ||
      lower.startsWith("reenter ") ||
      lower.matches("^(blackout|lights?|sound|music|curtain)\\b.*")
    );
  }

  private static boolean matchesWordStart(String lower, String word) {
    return (
      lower.equals(word) ||
      lower.startsWith(word + " ") ||
      lower.startsWith(word + ".") ||
      lower.startsWith(word + ",")
    );
  }

  public static boolean entranceExit(String line) {
    String lower = TextNormalizer.norm(line).toLowerCase();
    if (lower.isEmpty()) {
      return false;
    }

    return (
      lower.matches(
        "^(enter|enters|exit|exits|exeunt|re-enter|re-enters|reenter|reenters)\\b.*"
      ) ||
      lower.matches(
        "^.*\\b(enter|enters|exit|exits|exeunt|re-enter|re-enters|reenter|reenters)\\b.*"
      )
    );
  }

  public static boolean endsLikeWrappedLine(String text) {
    String t = TextNormalizer.norm(text);
    if (t.isEmpty()) {
      return false;
    }

    return (
      t.endsWith("-") ||
      t.endsWith("—") ||
      t.endsWith(",") ||
      t.endsWith(";") ||
      t.endsWith(":")
    );
  }

  public static boolean startsLikeWrappedContinuation(String text) {
    String t = TextNormalizer.norm(text);
    if (t.isEmpty()) {
      return false;
    }

    char first = t.charAt(0);
    if (Character.isLowerCase(first)) {
      return true;
    }

    String lower = t.toLowerCase();
    return (
      lower.startsWith("and ") ||
      lower.startsWith("or ") ||
      lower.startsWith("but ") ||
      lower.startsWith("because ") ||
      lower.startsWith("that ") ||
      lower.startsWith("which ") ||
      lower.startsWith("who ") ||
      lower.startsWith("with ") ||
      lower.startsWith("to ") ||
      lower.startsWith("of ") ||
      lower.startsWith("in ") ||
      lower.startsWith("from ") ||
      lower.startsWith("into ") ||
      lower.startsWith("onto ") ||
      lower.startsWith("then ") ||
      lower.startsWith("so ")
    );
  }

  public static boolean whole(String line) {
    line = TextNormalizer.norm(line);
    return (
      line.matches("^\\([^)]*\\)\\.?$") ||
      line.matches("^\\[[^\\]]*\\]\\.?$") ||
      line.matches("^\\{[^}]*\\}\\.?$")
    );
  }

  public static boolean location(String text) {
    String lower = TextNormalizer.norm(text).toLowerCase();
    if (lower.isEmpty()) {
      return false;
    }

    if (looksLikeDialogueFragment(lower)) {
      return false;
    }

    for (String prefix : LOCATION_PREFIXES) {
      if (lower.equals(prefix.trim()) || lower.startsWith(prefix)) {
        return true;
      }
    }

    return (
      lower.matches(
        "^(inside|outside|onstage|offstage|upstage|downstage)\\b.*"
      ) ||
      lower.matches("^(a|an|the)\\s+[a-z][a-z '-]{1,45}:.*") ||
      articleStageSettingLine(lower) ||
      lower.matches("^(lights?|sound|music)\\b.*") ||
      lower.matches(
        "^(morning|afternoon|evening|night|later|silence|pause|beat)\\.?$"
      )
    );
  }

  private static boolean articleStageSettingLine(String lower) {
    if (lower == null || lower.isBlank()) {
      return false;
    }

    if (looksLikeDialogueFragment(lower)) {
      return false;
    }

    return lower.matches(
      "^(a|an|the)\\s+(room|office|kitchen|bedroom|living room|sitting room|dining room|street|hall|hotel|hotel room|womb|church|house|apartment|courtroom|cell|jail cell|prison cell|yard|garden|stage|window|door|stairway|stairs|landing|porch|garage|bar|restaurant|hospital|cemetery|bathroom)\\b.*"
    );
  }

  private static boolean looksLikeDialogueFragment(String lower) {
    if (lower == null || lower.isBlank()) {
      return false;
    }
    if (lower.matches(".*[?!].*")) {
      return true;
    }
    if (
      lower.matches(
        "^(the minute|the early|the rest|the way|the thing|the one|the other|the same|the last|the first|the next)\\b.*"
      ) ||
      lower.matches(
        "^(a little|a lot|a double|a single|a good|a bad|a big|a small)\\b.*"
      )
    ) {
      return true;
    }

    return lower.matches(
      ".*\\b(i|i'm|i'll|i've|you|you're|you'll|we|we're|don't|can't|won't|would|could|should|want|know|think|feel|love|hate|need|mean|remember)\\b.*"
    );
  }

  public static boolean is(String line, Set<String> chars) {
    line = TextNormalizer.norm(line);
    if (notStage(line, chars)) {
      return false;
    }

    return (
      entranceExit(line) ||
      whole(line) ||
      location(line) ||
      actionStart(line) ||
      likelyNounActionStageLine(line) ||
      pronounActionStageLine(line) ||
      objectActionStageLine(line) ||
      characterActionStageLine(line, chars) ||
      terseActionBeat(line) ||
      sceneImageLine(line)
    );
  }

  public static boolean strong(String line, Set<String> chars) {
    line = TextNormalizer.norm(line);
    if (notStage(line, chars)) {
      return false;
    }

    return (
      whole(line) ||
      entranceExit(line) ||
      location(line) ||
      technicalCueLine(line) ||
      cast(line, chars) ||
      mentionsManyChars(line, chars, 3) ||
      characterActionStageLine(line, chars) ||
      objectActionStageLine(line) ||
      sceneImageLine(line)
    );
  }

  private static boolean notStage(String line, Set<String> chars) {
    if (line.isEmpty()) {
      return true;
    }
    if (looksLikeDialogueFragment(line.toLowerCase())) {
      return true;
    }
    return looksLikeDialogueSentence(line, chars);
  }

  private static boolean likelyNounActionStageLine(String line) {
    String lower = TextNormalizer.norm(line).toLowerCase();
    if (lower.isEmpty()) {
      return false;
    }

    if (!startsWithAny(lower, SUBJECT_PREFIXES)) {
      return false;
    }
    if (looksLikeDialogueSentence(line, null) || shortPlayableSentence(line)) {
      return false;
    }

    return containsActionWord(lower);
  }

  private static boolean pronounActionStageLine(String line) {
    String lower = TextNormalizer.norm(line).toLowerCase();
    if (!startsWithAny(lower, PRONOUN_STAGE_STARTS)) {
      return false;
    }
    if (looksLikeDialogueSentence(line, null) || shortPlayableSentence(line)) {
      return false;
    }

    return containsActionWord(lower);
  }

  private static boolean objectActionStageLine(String line) {
    String lower = TextNormalizer.norm(line).toLowerCase();
    if (looksLikeDialogueFragment(lower) || shortPlayableSentence(line)) {
      return false;
    }
    if (!startsWithAny(lower, OBJECT_STAGE_STARTS)) {
      return false;
    }

    return (
      containsActionWord(lower) ||
      lower.matches(".*\\b(rings|tolls|sounds|opens|closes|shakes)\\b.*")
    );
  }

  private static boolean shortPlayableSentence(String line) {
    String text = TextNormalizer.norm(line);
    if (text.isEmpty() || text.length() > 85) {
      return false;
    }
    if (
      whole(text) ||
      location(text) ||
      technicalCueLine(text) ||
      entranceExit(text)
    ) {
      return false;
    }

    String lower = text.toLowerCase();
    if (
      lower.matches("^(he|she|they|we|i|you)\\b.*") && text.matches(".*[.!?]$")
    ) {
      return true;
    }

    return (
      text.matches("^[A-Z][a-z].*") &&
      text.matches(".*[.!?]$") &&
      lower.matches(
        ".*\\b(i|you|we|they|he|she|me|my|your|our|their|his|her|them|us)\\b.*"
      )
    );
  }

  private static boolean technicalCueLine(String line) {
    String lower = TextNormalizer.norm(line).toLowerCase();
    return (
      lower.startsWith("lights ") ||
      lower.startsWith("light ") ||
      lower.startsWith("sound ") ||
      lower.startsWith("sounds ") ||
      lower.startsWith("music ") ||
      lower.startsWith("blackout") ||
      lower.startsWith("curtain") ||
      lower.startsWith("silence") ||
      lower.startsWith("pause") ||
      lower.startsWith("beat") ||
      lower.startsWith("the scene blacks out") ||
      lower.startsWith("scene blacks out")
    );
  }

  private static boolean characterActionStageLine(
    String line,
    Set<String> chars
  ) {
    if (chars == null) {
      return false;
    }
    if (looksLikeDialogueSentence(line, chars)) {
      return false;
    }

    String lower = TextNormalizer.norm(line).toLowerCase();
    for (String ch : chars) {
      String prefix = ch.toLowerCase() + " ";
      if (!lower.startsWith(prefix)) {
        continue;
      }

      String rest = lower.substring(prefix.length());
      for (String word : ACTION_WORDS) {
        if (matchesWordStart(rest, word)) {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean startsWithAny(String lower, String[] prefixes) {
    for (String prefix : prefixes) {
      if (lower.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsActionWord(String lower) {
    String padded = " " + lower.replaceAll("[^a-z'-]+", " ") + " ";
    for (String word : ACTION_WORDS) {
      if (padded.contains(" " + word + " ")) {
        return true;
      }
    }
    return false;
  }

  public static boolean front(String line, Set<String> chars) {
    line = TextNormalizer.norm(line);
    if (line.isEmpty() || junk(line)) {
      return true;
    }

    if (looksLikeCastCharacterDescription(line, chars)) {
      return true;
    }

    return chars != null && mentionsManyChars(line, chars, 3);
  }

  private static boolean looksLikeCastCharacterDescription(
    String line,
    Set<String> chars
  ) {
    if (chars == null) {
      return false;
    }

    int mark = firstSpeakerMark(line);
    if (mark <= 0) {
      return false;
    }

    String name = TextNormalizer.cleanName(line.substring(0, mark));
    String rest = TextNormalizer.norm(line.substring(mark + 1));
    return chars.contains(name) && !rest.isEmpty();
  }

  public static boolean mentionsManyChars(
    String line,
    Set<String> chars,
    int min
  ) {
    if (line == null || chars == null || chars.isEmpty()) {
      return false;
    }

    String up =
      " " +
      line
        .toUpperCase()
        .replaceAll("[^A-Z0-9' -]", " ")
        .replaceAll("\\s+", " ") +
      " ";

    int hits = 0;
    for (String ch : chars) {
      String name = TextNormalizer.cleanName(ch);
      if (name.isEmpty()) {
        continue;
      }
      if (up.contains(" " + name + " ")) {
        hits++;
        if (hits >= min) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean cast(String line, Set<String> chars) {
    line = TextNormalizer.norm(line);
    if (line.isEmpty() || chars == null) {
      return false;
    }

    int mark = firstSpeakerMark(line);
    if (mark <= 0) {
      return false;
    }

    String name = TextNormalizer.cleanName(line.substring(0, mark));
    String rest = TextNormalizer.norm(line.substring(mark + 1));
    if (!chars.contains(name) || rest.isEmpty()) {
      return false;
    }

    return castText(rest);
  }

  private static int firstSpeakerMark(String line) {
    int colon = line.indexOf(":");
    int dot = line.indexOf(".");

    if (colon >= 0 && dot >= 0) {
      return Math.min(colon, dot);
    }
    return Math.max(colon, dot);
  }

  private static boolean castText(String text) {
    String lower = text.toLowerCase();
    return (
      lower.contains("played by") ||
      lower.contains("daughter") ||
      lower.contains("father") ||
      lower.contains("mother") ||
      lower.contains("son") ||
      lower.contains("wife") ||
      lower.contains("husband") ||
      lower.contains("narrator") ||
      lower.contains("doesn't speak") ||
      lower.contains("does not speak") ||
      lower.matches(".*\\b(age|aged|years old|year old)\\b.*")
    );
  }

  public static boolean prose(String line, Set<String> chars) {
    line = TextNormalizer.norm(line);
    if (notStage(line, chars)) {
      return false;
    }

    return (
      is(line, chars) ||
      cast(line, chars) ||
      (line.length() > 85 && containsGeneralStageVerb(line)) ||
      location(line) ||
      mentionsManyChars(line, chars, 3) ||
      sceneImageLine(line) ||
      (line.length() > 120 && mentionsManyChars(line, chars, 2))
    );
  }

  private static boolean containsGeneralStageVerb(String line) {
    String lower = TextNormalizer.norm(line).toLowerCase();
    if (lower.isEmpty()) {
      return false;
    }

    return containsGeneralStageVerbRaw(lower);
  }

  private static boolean containsGeneralStageVerbRaw(String lower) {
    if (lower == null || lower.isBlank()) {
      return false;
    }

    return lower.matches(
      ".*\\b(enters|exits|appears|goes|comes|walks|runs|sits|stands|looks|watches|nods|shrugs|shakes|puts|lays|takes|tolls|speaks|whispers|remains|crosses|opens|closes|holds|drops|kneels|stares|stops|listens|sings|dances|kisses|touches|twirls|consoles|cleans|straightens|waves|winces|demonstrates)\\b.*"
    );
  }

  public static boolean bodyStart(String line, Set<String> chars) {
    line = TextNormalizer.norm(line);
    String lower = line.toLowerCase();

    if (line.isEmpty()) {
      return false;
    }
    if (whole(line) || location(line)) {
      return true;
    }
    if (is(line, chars) && !front(line, chars)) {
      return true;
    }
    if (lower.startsWith("lights ") || lower.startsWith("light ")) {
      return true;
    }
    if (
      lower.startsWith("curtain") ||
      lower.startsWith("at rise") ||
      lower.startsWith("at the rise")
    ) {
      return true;
    }
    if (
      lower.startsWith("act ") ||
      lower.startsWith("scene ") ||
      lower.startsWith("episode ")
    ) {
      return true;
    }

    String speaker = SpeakerDetector.name(line, chars);
    if (speaker.isEmpty() || cast(line, chars)) {
      return false;
    }

    String spoken = TextNormalizer.cleanSpokenText(
      SpeakerDetector.afterSpeaker(line, speaker)
    );
    return !spoken.isEmpty();
  }

  private static boolean terseActionBeat(String line) {
    String lower = TextNormalizer.norm(line).toLowerCase();
    if (lower.isEmpty() || lower.length() > 90) {
      return false;
    }

    return (
      lower.matches("^(pause|beat|silence)\\.?$") ||
      lower.matches(
        "^(he|she|they|it)\\s+(nods|shrugs|smiles|laughs|cries|sighs|waits|listens|watches|stares|turns|exits|enters|leaves)\\.?$"
      ) ||
      lower.matches(
        "^[a-z][a-z' -]{1,35}\\s+(nods|shrugs|smiles|laughs|cries|sighs|waits|listens|watches|stares|turns|exits|enters|leaves)\\.?$"
      )
    );
  }

  private static boolean sceneImageLine(String line) {
    String lower = TextNormalizer.norm(line).toLowerCase();
    if (lower.isEmpty()) {
      return false;
    }
    if (looksLikeDialogueFragment(lower)) {
      return false;
    }
    if (looksLikeDialogueSentence(line, null)) {
      return false;
    }

    return (
      lower.matches("^(there is|there are|there's)\\s+.*") ||
      lower.matches(
        "^(a|an|the)\\s+[a-z][a-z' -]{1,45}\\s+(is|are|stands|sits|hangs|lies|waits|appears|remains)\\b.*"
      ) ||
      lower.matches(
        ".*\\b(is seen|are seen|can be seen|is heard|are heard|can be heard)\\b.*"
      )
    );
  }

  private static boolean isHeaderButPlayable(String line) {
    String lower = TextNormalizer.norm(line).toLowerCase();
    return (
      lower.matches(
        "^(end of|intermission|blackout|curtain|lights?|sound|music)\\b.*"
      ) ||
      lower.matches("^(act|scene|episode)\\s+.*") ||
      lower.matches("^[ivxlcdm]+\\.?\\s+[a-z0-9' -]{2,80}$")
    );
  }

  private static boolean looksLikeDialogueSentence(
    String line,
    Set<String> chars
  ) {
    String text = TextNormalizer.norm(line);
    if (text.isEmpty()) {
      return false;
    }

    if (
      whole(text) ||
      location(text) ||
      technicalCueLine(text) ||
      entranceExit(text)
    ) {
      return false;
    }

    String speaker = SpeakerDetector.name(text, chars);
    if (!speaker.isEmpty()) {
      return false;
    }

    String lower = text.toLowerCase();
    if (
      lower.matches(
        "^(i|i'm|i'll|i'd|ive|i've|you|you're|you'll|we|we're|we'll|they|they're|he|she)\\b.*"
      ) &&
      (text.contains("?") ||
        text.contains("!") ||
        text.matches(
          ".*\\b(am|are|is|was|were|have|has|had|do|does|did|will|would|could|should|want|know|think|feel|love|hate|need|mean|remember)\\b.*"
        ))
    ) {
      return true;
    }

    return (
      text.length() > 80 &&
      text.matches(".*[a-z].*") &&
      !containsGeneralStageVerbRaw(text.toLowerCase()) &&
      !mentionsManyChars(text, chars, 2)
    );
  }

  public static int bodyStartIndex(List<String> lines, Set<String> chars) {
    if (lines == null || lines.isEmpty()) {
      return 0;
    }

    for (int i = 0; i < lines.size(); i++) {
      String line = TextNormalizer.norm(lines.get(i));
      if (line.isEmpty() || skip(line) || junk(line) || front(line, chars)) {
        continue;
      }

      if (bodyStart(line, chars)) {
        return i;
      }
    }

    return 0;
  }
}
