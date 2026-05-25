import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class PracticeSession {

  private static final List<String> wrongLines = new ArrayList<>();
  private static final List<String> wrongCues = new ArrayList<>();
  private static final List<String> retryLines = new ArrayList<>();
  private static final List<String> retryCues = new ArrayList<>();

  public static void run(ParsedScript parsed, Settings settings, Scanner sc) {
    wrongLines.clear();
    wrongCues.clear();
    retryLines.clear();
    retryCues.clear();

    boolean timed = settings.timedMode();
    int r = 0;
    int w = 0;
    long sessionStartMs = timed ? System.currentTimeMillis() : 0;
    for (int i = 0; i < parsed.size(); i++) {
      long lineStartMs = timed ? System.currentTimeMillis() : 0;
      String cue = parsed.getCue(i);
      String expected = parsed.getCharLine(i);

      System.out.println("Cue line: " + cue);
      System.out.println(
        "[" + (i + 1) + "/" + parsed.size() + "] What is your line?"
      );
      String answer = sc.nextLine();
      if (timed) {
        long lineElapsedMs = System.currentTimeMillis() - lineStartMs;
        System.out.println(
          "Time for this line: " + (lineElapsedMs / 1000.0) + "s"
        );
      }
      if (matches(answer, expected, settings)) {
        r++;
        System.out.println("You are correct!");
      } else {
        w++;
        System.out.println("You are wrong!");
        System.out.println("The correct line was: " + expected);
        wrongCues.add(cue);
        wrongLines.add(expected);
      }
    }
    printStats(r, w);
    if (timed) {
      long sessionElapsedMs = System.currentTimeMillis() - sessionStartMs;
      System.out.println(
        "Total session time: " + (sessionElapsedMs / 1000.0) + "s"
      );
    }
  }

  public static void retryWrong(Settings settings, Scanner sc) {
    boolean timed = settings.timedMode();
    int r = 0;
    int w = 0;
    long sessionStartMs = timed ? System.currentTimeMillis() : 0;
    for (int i = 0; i < wrongLines.size(); i++) {
      long lineStartMs = timed ? System.currentTimeMillis() : 0;
      System.out.println("Cue line: " + wrongCues.get(i));
      System.out.println(
        "[" + (i + 1) + "/" + wrongLines.size() + "] What is your line?"
      );
      String answer = sc.nextLine();
      if (timed) {
        long lineElapsedMs = System.currentTimeMillis() - lineStartMs;
        System.out.println(
          "Time for this line: " + (lineElapsedMs / 1000.0) + "s"
        );
      }
      String expected = wrongLines.get(i);
      if (matches(answer, expected, settings)) {
        r++;
        System.out.println("You are correct!");
      } else {
        w++;
        System.out.println("You are wrong!");
        System.out.println("The correct line was: " + expected);
        retryCues.add(wrongCues.get(i));
        retryLines.add(expected);
      }
    }
    printStats(r, w);
    if (timed) {
      long sessionElapsedMs = System.currentTimeMillis() - sessionStartMs;
      System.out.println(
        "Total session time: " + (sessionElapsedMs / 1000.0) + "s"
      );
    }
    wrongLines.addAll(retryLines);
    wrongCues.addAll(retryCues);
    retryLines.clear();
    retryCues.clear();
  }

  private static boolean matches(
    String answer,
    String expected,
    Settings settings
  ) {
    return normalize(answer, settings).contains(normalize(expected, settings));
  }

  private static String normalize(String text, Settings settings) {
    String value = text == null ? "" : text;
    if (!settings.caseSensitive()) {
      value = value.toLowerCase();
    }
    if (!settings.punctuation()) {
      value = value.replaceAll("\\p{Punct}", "").strip();
    }
    return value;
  }

  private static void printStats(int right, int wrong) {
    System.out.println("You got " + right + " lines correct!");
    System.out.println("You got " + wrong + " lines wrong!");

    int total = right + wrong;
    if (total == 0) {
      System.out.println("There were no lines!");
      return;
    }

    int accuracy = (int) (100 * (right / (double) total));
    System.out.println("That means you had an accuracy of " + accuracy + "%");
  }

  public static void postPracticeMenu(
    ParsedScript parsed,
    Settings settings,
    Scanner sc
  ) {
    while (true) {
      System.out.println(
        "Would you like to: [1] Try again, [2] Retry only missed lines, or [3] Quit?"
      );
      String postChoice = sc.nextLine().trim();
      if (postChoice.equals("1")) {
        run(parsed, settings, sc);
      } else if (postChoice.equals("2")) {
        if (wrongLines.isEmpty()) {
          System.out.println("No missed lines to retry.");
        } else {
          retryWrong(settings, sc);
        }
      } else if (postChoice.equals("3")) {
        return;
      } else {
        System.out.println("Please type 1, 2, or 3.");
      }
    }
  }
}
