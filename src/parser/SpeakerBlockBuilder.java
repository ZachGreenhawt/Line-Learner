import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpeakerBlockBuilder {

  private enum LineRole {
    TEXT,
    SOURCE_ONLY,
    BRIDGE,
    BOUNDARY,
  }

  public static List<ParseModels.Block> build(
    List<String> lines,
    Map<Integer, SpeakerHeadingIndex.HeadingRecord> headings
  ) {
    List<ParseModels.Block> blocks = new ArrayList<>();
    if (lines == null || lines.isEmpty()) {
      return blocks;
    }

    Set<String> knownSpeakers = knownSpeakers(headings);
    String lastSpeaker = "";
    boolean canRecoverNext = false;

    for (int i = 0; i < lines.size(); i++) {
      String raw = lines.get(i);
      String line = TextNormalizer.norm(raw);
      if (line.isEmpty()) {
        continue;
      }

      SpeakerHeadingIndex.HeadingRecord heading = headingAt(headings, i);
      if (heading != null) {
        Built built = speakerBlock(lines, headings, knownSpeakers, i, heading);

        if (hasText(built.block)) {
          blocks.add(built.block);
        }
        blocks.addAll(built.extraBlocks);
        lastSpeaker = built.lastSpeaker.isBlank()
          ? heading.canonicalSpeaker
          : built.lastSpeaker;
        canRecoverNext = hasText(built.block) || !boundary(line);
        i = built.endIndex;
        continue;
      }

      if (standaloneStage(line)) {
        BuiltStage built = stageBlock(lines, headings, knownSpeakers, i);
        blocks.add(built.block);

        if (!built.mentionedSpeaker.isEmpty()) {
          lastSpeaker = built.mentionedSpeaker;
        }

        canRecoverNext = false;
        i = built.endIndex;
        continue;
      }

      if (recoverable(line, lastSpeaker, canRecoverNext)) {
        blocks.add(recoveredBlock(i, lastSpeaker, line, raw));
        canRecoverNext = true;
        continue;
      }

      blocks.add(unknownBlock(i, line, raw));
      canRecoverNext = false;
    }

    return blocks;
  }

  private static boolean hasText(ParseModels.Block block) {
    return block != null && !TextNormalizer.norm(block.text).isEmpty();
  }

  private static SpeakerHeadingIndex.HeadingRecord headingAt(
    Map<Integer, SpeakerHeadingIndex.HeadingRecord> headings,
    int index
  ) {
    return headings == null ? null : headings.get(index);
  }

  private static BuiltStage stageBlock(
    List<String> lines,
    Map<Integer, SpeakerHeadingIndex.HeadingRecord> headings,
    Set<String> knownSpeakers,
    int start
  ) {
    StringBuilder text = new StringBuilder();
    StringBuilder source = new StringBuilder();
    String mentionedSpeaker = "";
    int end = start;

    for (int i = start; i < lines.size(); i++) {
      if (i != start && headingAt(headings, i) != null) {
        break;
      }

      String raw = lines.get(i);
      String line = TextNormalizer.norm(raw);
      if (line.isEmpty()) {
        continue;
      }
      if (i != start && !standaloneStage(line)) {
        break;
      }

      appendSpace(text, line);
      if (source.length() == 0) {
        source.append(raw);
      } else {
        appendSource(source, raw);
      }

      String speaker = speakerMentionedIn(line, knownSpeakers);
      if (!speaker.isEmpty()) {
        mentionedSpeaker = speaker;
      }
      end = i;
    }

    ParseModels.Block block = new ParseModels.Block(
      start,
      end,
      "",
      text.toString().trim(),
      source.toString().trim(),
      "HIGH",
      start == end ? "stage_block" : "stage_block_accumulated",
      ParseModels.BlockType.STAGE_BLOCK,
      true
    );

    return new BuiltStage(block, end, mentionedSpeaker);
  }

  private static Built speakerBlock(
    List<String> lines,
    Map<Integer, SpeakerHeadingIndex.HeadingRecord> headings,
    Set<String> knownSpeakers,
    int start,
    SpeakerHeadingIndex.HeadingRecord heading
  ) {
    StringBuilder text = new StringBuilder();
    StringBuilder source = new StringBuilder(lines.get(start));
    String reason = headingReason(heading);
    int end = start;
    List<ParseModels.Block> extras = new ArrayList<>();
    String lastSpeaker = heading.canonicalSpeaker;

    if (heading.hasDialogue()) {
      appendSpace(text, heading.remainingText);
    }

    Mode mode = new Mode(text.toString());

    for (int i = start + 1; i < lines.size(); i++) {
      if (headings != null && headings.containsKey(i)) {
        break;
      }

      String raw = lines.get(i);
      String line = TextNormalizer.norm(raw);

      EmbeddedHeading embedded = embedded(line, knownSpeakers);
      if (embedded.exists && text.length() > 0) {
        EmbeddedResult result = applyEmbedded(
          embedded,
          line,
          i,
          source,
          text,
          reason,
          mode,
          extras
        );

        reason = result.reason;
        end = result.endIndex;
        lastSpeaker = result.lastSpeaker.isBlank()
          ? lastSpeaker
          : result.lastSpeaker;
        break;
      }

      boolean hasText = text.length() > 0;
      LineRole role = roleFor(line, hasText, mode);
      if (role == LineRole.BOUNDARY) {
        break;
      }

      if (role == LineRole.SOURCE_ONLY && !hasText) {
        break;
      }

      appendSource(source, raw);
      end = i;

      if (role == LineRole.BRIDGE) {
        reason = appendReason(reason, "speaker_bridge");
        continue;
      }

      if (role == LineRole.SOURCE_ONLY) {
        reason = appendReason(reason, "source_only_stage");
        continue;
      }

      boolean firstText = text.length() == 0;
      appendSpace(text, line);
      reason = appendReason(
        reason,
        firstText ? "dialogue_after_heading" : "dialogue_continuation"
      );
      mode.observe(line);
    }

    ParseModels.Block block = new ParseModels.Block(
      start,
      end,
      heading.canonicalSpeaker,
      text.toString().trim(),
      source.toString().trim(),
      heading.confidence.name(),
      reason,
      ParseModels.BlockType.SPEAKER_BLOCK,
      false
    );

    return new Built(block, end, extras, lastSpeaker);
  }

  private static EmbeddedResult applyEmbedded(
    EmbeddedHeading embedded,
    String sourceLine,
    int index,
    StringBuilder source,
    StringBuilder text,
    String reason,
    Mode mode,
    List<ParseModels.Block> extras
  ) {
    String before = embedded.before.trim();

    if (!before.isEmpty()) {
      LineRole beforeRole = roleFor(before, true, mode);
      appendSource(source, before);

      if (beforeRole == LineRole.SOURCE_ONLY) {
        reason = appendReason(
          reason,
          "source_only_stage_before_embedded_heading"
        );
      } else if (beforeRole != LineRole.BOUNDARY) {
        appendSpace(text, before);
        reason = appendReason(
          reason,
          "dialogue_continuation_before_embedded_heading"
        );
        mode.observe(before);
      }
    }

    String lastSpeaker = "";
    if (!embedded.after.isBlank()) {
      extras.add(
        embeddedBlock(index, embedded.speaker, embedded.after, sourceLine)
      );
      lastSpeaker = embedded.speaker;
    }

    return new EmbeddedResult(reason, index, lastSpeaker);
  }

  private static LineRole roleFor(String line, boolean hasText, Mode mode) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty()) {
      return LineRole.BOUNDARY;
    }

    if (boundary(cleaned) || majorStageTransition(cleaned)) {
      return LineRole.BOUNDARY;
    }

    if (spokenAddress(cleaned)) {
      return LineRole.TEXT;
    }

    if (!hasText && bridge(cleaned)) {
      return LineRole.BRIDGE;
    }

    if (sourceOnlyBeat(cleaned) && (!mode.narrative || bridge(cleaned))) {
      return LineRole.SOURCE_ONLY;
    }

    return LineRole.TEXT;
  }

  private static EmbeddedHeading embedded(String line, Set<String> speakers) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty() || speakers == null || speakers.isEmpty()) {
      return EmbeddedHeading.none();
    }
    if (spokenAddress(cleaned)) {
      return EmbeddedHeading.none();
    }

    int start = SpeakerDetector.inside(cleaned, 1, speakers);
    if (start < 0) {
      return EmbeddedHeading.none();
    }

    String speaker = SpeakerDetector.name(cleaned.substring(start), speakers);
    if (speaker.isEmpty()) {
      return EmbeddedHeading.none();
    }

    String before = cleaned.substring(0, start).trim();
    String after = SpeakerDetector.afterSpeaker(
      cleaned.substring(start),
      speaker
    );
    if (before.isEmpty()) {
      return EmbeddedHeading.none();
    }
    if (spokenAddress(cleaned.substring(start).trim())) {
      return EmbeddedHeading.none();
    }

    return new EmbeddedHeading(speaker, before, after);
  }

  private static boolean spokenAddress(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty()) {
      return false;
    }

    if (cleaned.matches("^[A-Z][A-Z0-9'’ -]{1,40}\\.\\s*$")) {
      return false;
    }

    if (!cleaned.matches("^[A-Z][a-zA-Z'’\\-]{1,30}\\.\\s+\\S+.*")) {
      return false;
    }

    return cleaned.split("\\s+").length >= 3;
  }

  private static ParseModels.Block embeddedBlock(
    int index,
    String speaker,
    String text,
    String source
  ) {
    return new ParseModels.Block(
      index,
      index,
      speaker,
      TextNormalizer.norm(text),
      source,
      "MEDIUM",
      "embedded_heading_recovered_same_line",
      ParseModels.BlockType.SPEAKER_BLOCK,
      false
    );
  }

  private static ParseModels.Block recoveredBlock(
    int index,
    String speaker,
    String line,
    String raw
  ) {
    return new ParseModels.Block(
      index,
      index,
      speaker,
      line,
      raw,
      "LOW",
      "recovered_contextual_continuation",
      ParseModels.BlockType.SPEAKER_BLOCK,
      false
    );
  }

  private static ParseModels.Block unknownBlock(
    int index,
    String line,
    String raw
  ) {
    return new ParseModels.Block(
      index,
      index,
      "UNKNOWN",
      line,
      raw,
      "REVIEW",
      "unknown_preserved_actual_line",
      ParseModels.BlockType.UNKNOWN_DIALOGUE,
      false
    );
  }

  private static boolean recoverable(
    String line,
    String lastSpeaker,
    boolean canRecoverNext
  ) {
    String cleaned = TextNormalizer.norm(line);
    if (!canRecoverNext || lastSpeaker == null || lastSpeaker.isBlank()) {
      return false;
    }
    if (cleaned.isEmpty()) {
      return false;
    }
    if (boundary(cleaned) || majorStageTransition(cleaned)) {
      return false;
    }
    if (standaloneStage(cleaned) || sourceOnlyBeat(cleaned)) {
      return false;
    }
    if (!SpeakerDetector.name(cleaned, null).isEmpty()) {
      return false;
    }
    if (startsLikeContinuationFragment(cleaned)) {
      return true;
    }

    return continuation(cleaned);
  }

  private static boolean startsLikeContinuationFragment(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty() || cleaned.length() > 90) {
      return false;
    }

    char first = cleaned.charAt(0);
    return (
      Character.isLowerCase(first) ||
      cleaned.matches("^(and|but|or|so|because|then)\\b.*")
    );
  }

  private static boolean continuation(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty() || cleaned.length() > 140) {
      return false;
    }
    if (standaloneStage(cleaned) || sourceOnlyBeat(cleaned)) {
      return false;
    }

    String lower = cleaned.toLowerCase();
    return (
      safeDialogue(cleaned) || lower.startsWith("-") || lower.startsWith("—")
    );
  }

  private static boolean standaloneStage(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty() || boundary(cleaned)) {
      return false;
    }
    if (cueList(cleaned)) {
      return false;
    }

    return (
      majorStageTransition(cleaned) || StageDetector.prose(cleaned, null)
    );
  }

  private static boolean bridge(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty() || boundary(cleaned)) {
      return false;
    }

    return (
      cleaned.startsWith("(") ||
      cleaned.startsWith("[") ||
      cleaned.startsWith("{") ||
      StageDetector.whole(cleaned) ||
      StageDetector.strong(cleaned, null)
    );
  }

  private static boolean sourceOnlyBeat(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty() || cueList(cleaned)) {
      return false;
    }
    if (bridge(cleaned) || shortActionBeat(cleaned) || stageHeavy(cleaned)) {
      return true;
    }

    return (
      StageDetector.whole(cleaned) ||
      StageDetector.location(cleaned) ||
      StageDetector.strong(cleaned, null)
    );
  }

  private static boolean majorStageTransition(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty()) {
      return false;
    }

    String lower = cleaned.toLowerCase();
    return (
      StageDetector.entranceExit(cleaned) ||
      lower.contains("scene blacks out") ||
      lower.contains("scene fades out") ||
      lower.contains("lights fade") ||
      lower.contains("blackout") ||
      lower.matches("^(enter|exit|exeunt|re-enter|reenter)\\b.*")
    );
  }

  private static boolean shortActionBeat(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty() || cleaned.length() > 120) {
      return false;
    }
    if (cleaned.contains("?") || cleaned.contains("!")) {
      return false;
    }
    if (cleaned.matches("^[\"'“‘].*")) {
      return false;
    }

    String lower = cleaned.toLowerCase();
    return lower.matches(
      "^(he|she|they|we|[a-z][a-z'’\\-]+(?:\\s+[a-z][a-z'’\\-]+){0,3})\\s+" +
        "(nods|waves|shrugs|smiles|laughs|cries|sighs|sits|stands|rises|turns|crosses|goes|comes|enters|exits|walks|runs|takes|puts|gets|holds|touches|kisses|whispers|stares|looks|pulls|pushes|opens|closes|eats|sips|shows|hands|helps|starts|stops|moves|throws|reads|watches|waits|points|kneels|falls|backs|leans|reaches|begins)\\b.*"
    );
  }

  private static boolean stageHeavy(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (
      cleaned.length() < 45 || cleaned.contains("?") || cleaned.contains("!")
    ) {
      return false;
    }

    String lower = cleaned.toLowerCase();
    int subjects = countMatches(
      lower,
      "\\b(he|she|they|his|her|their|him|them)\\b"
    );
    int actions = countMatches(
      lower,
      "\\b(looks|turns|puts|takes|opens|closes|kisses|touches|goes|comes|crosses|moves|walks|sits|stands|rises|falls|holds|pulls|pushes|reaches|leans|stares|watches|throws|carries|brings|lifts|lowers|releases|fastens|closes|clutching|spreads)\\b"
    );

    return subjects >= 2 && actions >= 2;
  }

  private static int countMatches(String text, String regex) {
    Matcher matcher = Pattern.compile(regex).matcher(text);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  private static boolean safeDialogue(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty() || cleaned.length() > 110) {
      return false;
    }
    if (standaloneStage(cleaned) || sourceOnlyBeat(cleaned)) {
      return false;
    }

    String lower = cleaned.toLowerCase();
    return (
      cleaned.contains("?") ||
      cleaned.contains("!") ||
      cleaned.matches("^[\"'“‘].*") ||
      lower.matches(
        "^(and|but|or|because|so)\\b.*\\b(i|i'm|i’ll|i'll|i've|you|you're|we|we're|don't|can't|won't|would|could|should|please|yes|no)\\b.*"
      ) ||
      lower.matches(
        ".*\\b(i|i'm|i’ll|i'll|i've|you|you're|we|we're|don't|can't|won't|would|could|should|please|yes|no)\\b.*"
      )
    );
  }

  private static boolean cueList(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty() || cleaned.length() > 180) {
      return false;
    }
    if (frontOrFurniture(cleaned)) {
      return false;
    }
    if (
      StageDetector.strong(cleaned, null) ||
      StageDetector.whole(cleaned) ||
      StageDetector.entranceExit(cleaned)
    ) {
      return false;
    }

    return (
      cleaned.matches(".*\\b[A-Z]\\.\\b.*") ||
      cleaned.matches(
        ".*\\b[A-Z][a-zA-Z'’ -]{1,30}\\s*[-–—]\\s*[A-Z0-9]\\b.*"
      ) ||
      (cleaned.matches(".*\\d+.*") &&
        cleaned.matches(".*\\b[A-Za-z][A-Za-z'’]+\\b.*") &&
        !cleaned.matches("^\\d{1,4}\\s+[A-Z][A-Z'’ -]+$"))
    );
  }

  private static boolean frontOrFurniture(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty()) {
      return true;
    }
    if (FrontMatterDetector.is(cleaned)) {
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

    return plainCapsFurniture(cleaned);
  }

  private static boolean plainCapsFurniture(String line) {
    String cleaned = TextNormalizer.norm(line);
    if (cleaned.isEmpty() || cleaned.length() > 55) {
      return false;
    }
    if (
      cleaned.contains("/") || cleaned.contains("(") || cleaned.contains("[")
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

  private static boolean boundary(String line) {
    String upper = TextNormalizer.norm(line).toUpperCase();
    return (
      upper.startsWith("EPISODE ") ||
      upper.startsWith("SCENE:") ||
      upper.startsWith("SOUNDS:") ||
      upper.startsWith("CHARACTERS") ||
      upper.startsWith("AT RISE") ||
      upper.startsWith("BEFORE THE CURTAIN") ||
      upper.startsWith("ACT ") ||
      upper.startsWith("END OF") ||
      upper.startsWith("INTERMISSION") ||
      upper.contains("THE SCENE BLACKS OUT") ||
      upper.contains("SCENE BLACKS OUT") ||
      upper.contains("THE SCENE FADES OUT") ||
      upper.contains("SCENE FADES OUT")
    );
  }

  private static Set<String> knownSpeakers(
    Map<Integer, SpeakerHeadingIndex.HeadingRecord> headings
  ) {
    Set<String> speakers = new HashSet<>();
    if (headings == null) {
      return speakers;
    }

    for (SpeakerHeadingIndex.HeadingRecord heading : headings.values()) {
      if (heading == null) {
        continue;
      }

      String speaker = TextNormalizer.cleanName(heading.canonicalSpeaker);
      if (!speaker.isEmpty()) {
        speakers.add(speaker);
      }
    }

    return speakers;
  }

  private static String speakerMentionedIn(
    String line,
    Set<String> knownSpeakers
  ) {
    String cleaned = TextNormalizer.cleanName(line);
    if (cleaned.isEmpty() || knownSpeakers == null || knownSpeakers.isEmpty()) {
      return "";
    }

    String best = "";
    for (String speaker : knownSpeakers) {
      if (speaker.isEmpty()) {
        continue;
      }

      if (
        SpeakerDetector.starts(cleaned, speaker) &&
        speaker.length() > best.length()
      ) {
        best = speaker;
      }
    }

    return best;
  }

  private static String headingReason(
    SpeakerHeadingIndex.HeadingRecord heading
  ) {
    if (heading == null) {
      return "unknown_heading";
    }
    if (heading.parentheticalHeading) {
      return "explicit_parenthetical_heading";
    }
    if (heading.inlineDialogue) {
      return "explicit_inline_heading";
    }
    return "explicit_heading";
  }

  private static void appendSource(StringBuilder source, String raw) {
    source.append(" / ").append(raw);
  }

  private static void appendSpace(StringBuilder sb, String text) {
    if (text == null || text.trim().isEmpty()) {
      return;
    }
    if (sb.length() > 0) {
      sb.append(' ');
    }
    sb.append(text.trim());
  }

  private static String appendReason(String current, String extra) {
    String cleanCurrent = TextNormalizer.norm(current);
    String cleanExtra = TextNormalizer.norm(extra);
    if (cleanExtra.isEmpty()) {
      return cleanCurrent;
    }
    if (cleanCurrent.isEmpty()) {
      return cleanExtra;
    }
    return TextNormalizer.norm(cleanCurrent + " " + cleanExtra);
  }

  private static class Mode {

    boolean narrative;

    Mode(String text) {
      narrative = looksNarrative(text);
    }

    void observe(String line) {
      if (looksNarrative(line)) {
        narrative = true;
      }
    }

    private boolean looksNarrative(String text) {
      if (text == null || text.isBlank()) {
        return false;
      }

      String cleaned = TextNormalizer.norm(text).toLowerCase();
      int words = cleaned.trim().split("\\s+").length;
      if (words >= 12) {
        return true;
      }

      if (
        cleaned.matches(
          "^[a-z][a-z'’\\-]{1,30}\\s+number\\s+[a-z0-9ivxlcdm]+\\.?$"
        )
      ) {
        return true;
      }

      return cleaned.matches(
        ".*\\b(years ago|months ago|weeks ago|days ago|morning|afternoon|evening|night|story|remember|once|before|after|when|while|until)\\b.*"
      );
    }
  }

  private static class EmbeddedResult {

    final String reason;
    final int endIndex;
    final String lastSpeaker;

    EmbeddedResult(String reason, int endIndex, String lastSpeaker) {
      this.reason = reason;
      this.endIndex = endIndex;
      this.lastSpeaker = lastSpeaker;
    }
  }

  private static class EmbeddedHeading {

    final boolean exists;
    final String speaker;
    final String before;
    final String after;

    private EmbeddedHeading(
      boolean exists,
      String speaker,
      String before,
      String after
    ) {
      this.exists = exists;
      this.speaker = speaker;
      this.before = before;
      this.after = after;
    }

    static EmbeddedHeading none() {
      return new EmbeddedHeading(false, "", "", "");
    }

    EmbeddedHeading(String speaker, String before, String after) {
      this(true, speaker, before, after);
    }
  }

  private static class BuiltStage {

    final ParseModels.Block block;
    final int endIndex;
    final String mentionedSpeaker;

    BuiltStage(ParseModels.Block block, int endIndex, String mentionedSpeaker) {
      this.block = block;
      this.endIndex = endIndex;
      this.mentionedSpeaker = mentionedSpeaker;
    }
  }

  private static class Built {

    final ParseModels.Block block;
    final int endIndex;
    final List<ParseModels.Block> extraBlocks;
    final String lastSpeaker;

    Built(
      ParseModels.Block block,
      int endIndex,
      List<ParseModels.Block> extraBlocks,
      String lastSpeaker
    ) {
      this.block = block;
      this.endIndex = endIndex;
      this.extraBlocks = extraBlocks;
      this.lastSpeaker = lastSpeaker;
    }
  }
}
