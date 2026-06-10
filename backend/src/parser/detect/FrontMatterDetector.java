package parser.detect;

import java.util.List;
import java.util.Set;
import parser.ScriptParser;
import util.RegexTerms;
import util.TextNormalizer;

public class FrontMatterDetector {

  private static final int LOOKAHEAD_WINDOW = 24;
  private static final int MIN_BODY_SCORE = 14;

  public static int bodyStart(List<String> lines, Set<String> chars) {
    if (lines == null || lines.isEmpty()) {
      return 0;
    }

    int bestIndex = 0;
    int bestScore = Integer.MIN_VALUE;

    for (int i = 0; i < lines.size(); i++) {
      int score = score(lines, i, chars);

      if (score > bestScore) {
        bestScore = score;
        bestIndex = i;
      }

      if (score >= MIN_BODY_SCORE && sceneStart(lines, i, chars)) {
        return backtrack(lines, i, chars);
      }
    }

    return bestScore >= MIN_BODY_SCORE ? backtrack(lines, bestIndex, chars) : 0;
  }

  private static int score(List<String> lines, int index, Set<String> chars) {
    if (lines == null || index < 0 || index >= lines.size()) {
      return Integer.MIN_VALUE;
    }

    int score = 0;
    int bodySignals = 0;
    int frontMatterSignals = 0;
    int speakerSignals = 0;
    int stageSignals = 0;
    int proseSignals = 0;

    int end = Math.min(lines.size(), index + LOOKAHEAD_WINDOW);
    for (int i = index; i < end; i++) {
      String line = TextNormalizer.norm(lines.get(i));
      if (line.isEmpty()) {
        continue;
      }

      if (inFrontZone(lines, index, i)) {
        score -= 6;
      }

      if (hard(line) || castList(line, chars)) {
        frontMatterSignals++;
        score -= 4;
        continue;
      }

      if (soft(line)) {
        frontMatterSignals++;
        score -= 2;
      }

      if (proseParagraph(line)) {
        proseSignals++;
        score -= 3;
      }

      if (bodyMarker(line)) {
        bodySignals++;
        score += 4;
      }

      if (playableTitle(line)) {
        bodySignals++;
        score += 3;
      }

      if (stageLine(line, chars)) {
        stageSignals++;
        bodySignals++;
        score += 2;
      }

      if (speakerHeading(line, chars)) {
        speakerSignals++;
        bodySignals++;
        score += 3;
      }

      if (dialogue(line, chars)) {
        bodySignals++;
        score += 1;
      }
    }

    if (speakerSignals >= 1 && stageSignals >= 1) {
      score += 5;
    }

    if (bodySignals >= 3 && speakerSignals >= 1) {
      score += 4;
    }

    if (speakerSignals >= 2) {
      score += 2;
    }

    if (frontMatterSignals >= bodySignals && frontMatterSignals >= 2) {
      score -= 6;
    }

    if (proseSignals >= 3 && speakerSignals < 2) {
      score -= 8;
    }

    return score;
  }

  private static int backtrack(
    List<String> lines,
    int index,
    Set<String> chars
  ) {
    int start = Math.max(0, index);

    for (int i = start; i >= Math.max(0, start - 6); i--) {
      String line = TextNormalizer.norm(lines.get(i));
      if (line.isEmpty()) {
        continue;
      }

      if (hard(line) || castList(line, chars)) {
        break;
      }

      if (playableTitle(line) || bodyMarker(line)) {
        return i;
      }

      if (speakerHeading(line, chars)) {
        return i;
      }
    }

    return start;
  }

  private static boolean inFrontZone(
    List<String> lines,
    int candidateStart,
    int currentIndex
  ) {
    int start = Math.max(0, candidateStart - 18);

    for (int i = start; i <= currentIndex && i < lines.size(); i++) {
      String upper = TextNormalizer.norm(lines.get(i)).toUpperCase();
      if (upper.isEmpty()) {
        continue;
      }

      if (bodyMarker(upper)) {
        return false;
      }

      if (
        upper.equals("PREFACE") ||
        upper.equals("FOREWORD") ||
        upper.equals("INTRODUCTION") ||
        upper.equals("CREDITS") ||
        upper.equals("CAST") ||
        upper.equals("CAST OF") ||
        upper.equals("CAST OF CHARACTERS") ||
        upper.equals("CHARACTERS") ||
        upper.equals("DRAMATIS PERSONAE") ||
        upper.equals("ACKNOWLEDGMENTS") ||
        upper.equals("ACKNOWLEDGEMENTS") ||
        upper.equals("ABOUT THE AUTHOR") ||
        upper.equals("AUTHOR BIO") ||
        upper.equals("AUTHOR BIOGRAPHY") ||
        upper.equals("REVIEWS") ||
        upper.equals("PRAISE") ||
        upper.startsWith("COPYRIGHT") ||
        upper.startsWith("CAUTION:")
      ) {
        return true;
      }
    }

    return false;
  }

  private static boolean playableTitle(String line) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty() || t.length() > 60) {
      return false;
    }

    if (hard(t) || soft(t)) {
      return false;
    }

    String letters = t.replaceAll(RegexTerms.NON_LETTER, "");
    if (letters.length() < 5) {
      return false;
    }

    String upper = t.toUpperCase();
    boolean titleCaseWords = t.matches(
      RegexTerms.TITLE_CASE_LINE
    );
    boolean mostlyUpper = upper.equals(t) && t.matches(RegexTerms.CONTAINS_UPPERCASE);

    return ((titleCaseWords || mostlyUpper) && !publicationCredit(t));
  }

  private static boolean sceneStart(
    List<String> lines,
    int index,
    Set<String> chars
  ) {
    int speakerOrDialogue = 0;
    int stageOrScene = 0;
    int frontMatter = 0;
    int speakerHeadings = 0;
    int prose = 0;

    int end = Math.min(lines.size(), index + LOOKAHEAD_WINDOW);
    for (int i = index; i < end; i++) {
      String line = TextNormalizer.norm(lines.get(i));
      if (line.isEmpty()) {
        continue;
      }

      if (hard(line) || castList(line, chars)) {
        frontMatter++;
        continue;
      }

      if (proseParagraph(line)) {
        prose++;
      }

      if (speakerHeading(line, chars)) {
        speakerHeadings++;
        speakerOrDialogue++;
      } else if (dialogue(line, chars)) {
        speakerOrDialogue++;
      }

      if (bodyMarker(line) || stageLine(line, chars)) {
        stageOrScene++;
      }
    }

    return (
      frontMatter <= 1 &&
      prose <= 4 &&
      speakerHeadings >= 1 &&
      speakerOrDialogue >= 2 &&
      stageOrScene >= 1 &&
      sustained(lines, index, chars)
    );
  }

  private static boolean sustained(
    List<String> lines,
    int index,
    Set<String> chars
  ) {
    int playableSignals = 0;
    int blockedSignals = 0;
    int speakerSignals = 0;
    int proseSignals = 0;
    int end = Math.min(lines.size(), index + LOOKAHEAD_WINDOW);

    for (int i = index; i < end; i++) {
      String line = TextNormalizer.norm(lines.get(i));
      if (line.isEmpty()) {
        continue;
      }

      if (hard(line) || castList(line, chars)) {
        blockedSignals++;
        continue;
      }

      if (proseParagraph(line)) {
        proseSignals++;
      }

      if (speakerHeading(line, chars)) {
        speakerSignals++;
      }

      if (
        bodyMarker(line) ||
        stageLine(line, chars) ||
        speakerHeading(line, chars) ||
        dialogue(line, chars)
      ) {
        playableSignals++;
      }
    }

    return (
      playableSignals >= 3 &&
      speakerSignals >= 1 &&
      blockedSignals <= 1 &&
      proseSignals <= 5
    );
  }

  private static boolean publicationCredit(String line) {
    String lower = TextNormalizer.norm(line).toLowerCase();
    if (lower.isEmpty()) {
      return false;
    }

    return (
      lower.matches(
        RegexTerms.containsAnyWord(RegexTerms.FRONT_PUBLICATION_TERM)
      ) ||
      lower.matches(
        RegexTerms.containsAnyWord(RegexTerms.FRONT_PLACE_TERM)
      ) ||
      lower.matches(RegexTerms.containsAnyWord(RegexTerms.WEB_REF_TERM))
    );
  }

  private static boolean reviewAttribution(String line) {
    String lower = TextNormalizer.norm(line).toLowerCase();
    if (lower.isEmpty() || lower.length() > 120) {
      return false;
    }

    return (
      lower.matches(RegexTerms.REVIEW_ATTRIBUTION_PAIR) ||
      lower.matches(
        RegexTerms.REVIEW_ATTRIBUTION_OUTLET
      )
    );
  }

  private static boolean bodyMarker(String line) {
    String upper = TextNormalizer.norm(line).toUpperCase();

    return (
      upper.startsWith("AT RISE") ||
      upper.startsWith("AT THE RISE") ||
      upper.startsWith("LIGHTS ") ||
      upper.startsWith("SOUNDS:") ||
      upper.startsWith("SCENE:") ||
      StageDetector.screenplayScene(upper) ||
      StageDetector.screenplayTransition(upper) ||
      upper.startsWith("BEFORE THE CURTAIN") ||
      upper.matches(RegexTerms.EPISODE_HEADING) ||
      upper.matches(RegexTerms.ACT_HEADING) ||
      upper.matches(RegexTerms.SCENE_HEADING)
    );
  }

  private static boolean speakerHeading(String line, Set<String> chars) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty()) {
      return false;
    }

    if (blockHeading(t, chars)) {
      return false;
    }

    return (
      SpeakerDetector.is(t, chars) || !SpeakerDetector.name(t, chars).isEmpty()
    );
  }

  private static boolean proseParagraph(String line) {
    String t = TextNormalizer.norm(line);
    if (t.length() < 70) {
      return false;
    }
    if (speakerHeading(t, null) || bodyMarker(t) || stageLine(t, null)) {
      return false;
    }

    int spaces = t.length() - t.replace(" ", "").length();
    return spaces >= 9 && t.matches(RegexTerms.CONTAINS_LOWERCASE);
  }

  private static boolean stageLine(String line, Set<String> chars) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty()) {
      return false;
    }

    if (is(t) || castList(t, chars)) {
      return false;
    }

    return (
      StageDetector.entranceExit(t) ||
      StageDetector.whole(t) ||
      StageDetector.location(t) ||
      StageDetector.is(t, chars) ||
      StageDetector.strong(t, chars)
    );
  }

  private static boolean dialogue(String line, Set<String> chars) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty()) {
      return false;
    }

    if (is(t) || castList(t, chars)) {
      return false;
    }

    if (StageDetector.is(t, chars) || StageDetector.prose(t, chars)) {
      return false;
    }

    return (ScriptParser.dialogue(t, chars) || SpeakerDetector.bareTurn(t));
  }

  public static boolean castList(String line, Set<String> chars) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty() || chars == null || chars.isEmpty()) {
      return false;
    }

    for (String character : chars) {
      String name = TextNormalizer.cleanName(character);
      if (name.isEmpty()) {
        continue;
      }

      if (!startsWithName(t, name)) {
        continue;
      }

      String after = TextNormalizer.norm(
        t.substring(Math.min(t.length(), name.length()))
      );
      if (after.startsWith("/") || after.startsWith(",")) {
        after = TextNormalizer.norm(after.substring(1));
      }

      if (after.isEmpty()) {
        continue;
      }

      if (after.matches(RegexTerms.CAST_PERSON_NAME)) {
        return true;
      }

      if (
        after
          .toLowerCase()
          .matches(
            RegexTerms.containsAnyWord(RegexTerms.CAST_CREDIT_TERM)
          )
      ) {
        return true;
      }
    }

    return false;
  }

  public static boolean characterDescription(String line, Set<String> chars) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty() || chars == null || chars.isEmpty()) {
      return false;
    }

    for (String character : chars) {
      String name = TextNormalizer.cleanName(character);
      if (name.isEmpty() || !startsWithName(t, name)) {
        continue;
      }

      String after = TextNormalizer.norm(
        t.substring(Math.min(t.length(), name.length()))
      );
      if (after.startsWith(":") || after.startsWith(".")) {
        after = TextNormalizer.norm(after.substring(1));
      }

      String lower = after.toLowerCase();
      if (
        lower.matches(
          RegexTerms.containsAnyWord(RegexTerms.CHARACTER_DESCRIPTION_TERM)
        )
      ) {
        return true;
      }
    }

    return false;
  }

  private static boolean credit(String line) {
    String lower = TextNormalizer.norm(line).toLowerCase();
    if (lower.isEmpty()) {
      return false;
    }

    return lower.matches(
      RegexTerms.containsAnyWord(RegexTerms.CREDIT_TERM)
    );
  }

  private static boolean startsWithName(String line, String cleanName) {
    String t = TextNormalizer.norm(line);
    if (t.length() < cleanName.length()) {
      return false;
    }

    if (!t.regionMatches(true, 0, cleanName, 0, cleanName.length())) {
      return false;
    }

    if (t.length() == cleanName.length()) {
      return true;
    }

    char next = t.charAt(cleanName.length());
    return " :.,/([ ".indexOf(next) >= 0;
  }

  private static boolean hard(String line) {
    String t = TextNormalizer.norm(line);
    String upper = t.toUpperCase();

    if (t.isEmpty()) {
      return true;
    }

    return (
      upper.equals("CAST") ||
      upper.equals("CAST OF") ||
      upper.equals("CAST OF CHARACTERS") ||
      upper.equals("CHARACTERS") ||
      upper.equals("DRAMATIS PERSONAE") ||
      upper.equals("EPISODES") ||
      upper.equals("SCENES") ||
      upper.equals("CREDITS") ||
      upper.equals("PREFACE") ||
      upper.equals("FOREWORD") ||
      upper.equals("INTRODUCTION") ||
      upper.equals("PRODUCTION HISTORY") ||
      upper.equals("AUTHOR'S NOTE") ||
      upper.equals("AUTHORS NOTE") ||
      upper.equals("NOTES") ||
      upper.equals("ACKNOWLEDGMENTS") ||
      upper.equals("ACKNOWLEDGEMENTS") ||
      upper.equals("ABOUT THE AUTHOR") ||
      upper.equals("AUTHOR BIO") ||
      upper.equals("AUTHOR BIOGRAPHY") ||
      upper.equals("REVIEWS") ||
      upper.equals("PRAISE") ||
      upper.startsWith("COPYRIGHT") ||
      upper.startsWith("ISBN") ||
      upper.startsWith("CAUTION:") ||
      upper.startsWith("ALL RIGHTS RESERVED") ||
      upper.startsWith("PUBLISHED BY ") ||
      upper.startsWith("THIS EDITION ") ||
      upper.startsWith("BOOK DESIGN") ||
      upper.startsWith("MANUFACTURED ") ||
      upper.startsWith("CATALOGING") ||
      upper.startsWith("A CATALOGUE RECORD") ||
      upper.startsWith("ADAPTED BY ") ||
      upper.startsWith("ORIGINAL ") ||
      upper.startsWith("ORIGINALLY ") ||
      upper.startsWith("PRODUCTION NOTE")
    );
  }

  private static boolean soft(String line) {
    String t = TextNormalizer.norm(line);
    String lower = t.toLowerCase();

    if (t.isEmpty()) {
      return true;
    }

    return (
      lower.contains("directed by") ||
      lower.contains("artistic director") ||
      lower.contains("executive director") ||
      lower.contains("premiere") ||
      lower.contains("originally produced") ||
      lower.contains("commissioned by") ||
      lower.contains("play service") ||
      lower.contains("publishing") ||
      lower.contains("publisher") ||
      lower.contains("press") ||
      lower.contains("all rights") ||
      lower.contains("copyright") ||
      lower.contains("www.") ||
      lower.contains("@") ||
      publicationCredit(t) ||
      reviewAttribution(t)
    );
  }

  public static boolean is(String line) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty()) {
      return true;
    }

    return hard(t) || soft(t);
  }

  public static boolean blockHeading(String line, Set<String> chars) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty()) {
      return true;
    }

    return (
      is(t) || castList(t, chars) || characterDescription(t, chars) || credit(t)
    );
  }
}
