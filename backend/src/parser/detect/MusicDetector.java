package parser.detect;

import java.util.List;
import java.util.Set;
import util.RegexTerms;
import util.TextNormalizer;

public class MusicDetector {

  private static final int STRICT_CAPS_RATIO = 8;
  private static final int RELAXED_CAPS_RATIO = 7;
  private static final int SPOKEN_RUN_CLOSES_SONG = 3;

  public static boolean songMarker(String line) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty()) {
      return false;
    }
    return (
      t.matches(RegexTerms.SONG_NUMBER_MARKER) ||
      t.matches(RegexTerms.SONG_NO_MARKER) ||
      t.matches(RegexTerms.MUSICAL_NUMBERS_HEADING) ||
      t.matches(RegexTerms.SONG_CUE_MARKER) ||
      t.matches(RegexTerms.MUSIC_TECH_OPEN)
    );
  }

  public static boolean singsCue(String line) {
    String t = TextNormalizer.norm(line);
    return !t.isEmpty() && t.matches(RegexTerms.SINGS_PARENTHETICAL);
  }

  private static boolean regionBoundary(String line) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty()) {
      return false;
    }
    return (
      t.matches(RegexTerms.SONG_REGION_BOUNDARY) ||
      t.matches(RegexTerms.MUSIC_TECH_CLOSE)
    );
  }

  public static boolean lyricShape(String line, Set<String> chars) {
    return lyricLine(line, chars, STRICT_CAPS_RATIO, 2);
  }

  private static boolean lyricLine(
    String line,
    Set<String> chars,
    int capsRatio,
    int minWords
  ) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty() || t.length() > 120 || !TextNormalizer.hasLetter(t)) {
      return false;
    }
    if (
      SpeakerDetector.heading(t, chars) ||
      !SpeakerDetector.name(t, chars).isEmpty()
    ) {
      return false;
    }
    if (
      StageDetector.is(t, chars) ||
      StageDetector.strong(t, chars) ||
      StageDetector.whole(t) ||
      StageDetector.entranceExit(t)
    ) {
      return false;
    }
    if (FrontMatterDetector.is(t)) {
      return false;
    }
    if (RegexTerms.containsPublicationOrFurniture(t.toLowerCase())) {
      return false;
    }
    if (t.matches(RegexTerms.PAGE_NUMBER_ONLY)) {
      return false;
    }

    int letters = 0;
    int caps = 0;
    for (int i = 0; i < t.length(); i++) {
      char ch = t.charAt(i);
      if (Character.isLetter(ch)) {
        letters++;
        if (Character.isUpperCase(ch)) {
          caps++;
        }
      }
    }
    if (letters < 2 || caps * 10 < letters * capsRatio) {
      return false;
    }

    int words = t.split(RegexTerms.WHITESPACE).length;
    return words >= minWords || t.length() > 10;
  }

  private static boolean spokenLine(String line, Set<String> chars) {
    String t = TextNormalizer.norm(line);
    if (t.isEmpty() || !t.matches(RegexTerms.CONTAINS_LOWERCASE)) {
      return false;
    }
    return !SpeakerDetector.heading(t, chars);
  }

  public static boolean[] classify(List<String> lines, Set<String> chars) {
    int n = lines == null ? 0 : lines.size();
    boolean[] isMusic = new boolean[n];
    if (n == 0) {
      return isMusic;
    }

    boolean[] caps = new boolean[n];
    boolean[] capsRelaxed = new boolean[n];
    boolean[] sung = new boolean[n];
    boolean[] inSong = new boolean[n];

    boolean active = false;
    int spokenRun = 0;

    for (int i = 0; i < n; i++) {
      String line = TextNormalizer.norm(lines.get(i));
      caps[i] = lyricLine(line, chars, STRICT_CAPS_RATIO, 2);
      capsRelaxed[i] = lyricLine(line, chars, RELAXED_CAPS_RATIO, 1);
      sung[i] = singsCue(line);

      if (regionBoundary(line)) {
        active = false;
        spokenRun = 0;
      } else if (songMarker(line) || sung[i]) {
        active = true;
        spokenRun = 0;
      } else if (caps[i] || capsRelaxed[i]) {
        spokenRun = 0;
      } else if (spokenLine(line, chars)) {
        if (++spokenRun >= SPOKEN_RUN_CLOSES_SONG) {
          active = false;
        }
      }

      inSong[i] = active;
    }

    for (int i = 0; i < n; i++) {
      boolean run =
        caps[i] && ((i > 0 && caps[i - 1]) || (i + 1 < n && caps[i + 1]));
      isMusic[i] = sung[i] || (inSong[i] && capsRelaxed[i]) || run;
    }

    return isMusic;
  }

  public static boolean blockIsMusic(boolean[] music, int start, int end) {
    if (music == null || start < 0 || end < start) {
      return false;
    }

    int last = Math.min(end, music.length - 1);
    int total = 0;
    int songLines = 0;
    for (int i = Math.max(0, start); i <= last; i++) {
      total++;
      if (music[i]) {
        songLines++;
      }
    }

    if (songLines == 0) {
      return false;
    }
    return songLines >= 2 || songLines * 2 >= total;
  }
}
