package practice;

import java.util.Scanner;

public class Settings {

  private final boolean includeStageDirectionsInCue;
  private final boolean caseSensitive;
  private final boolean punctuation;
  private final boolean timedMode;
  private final boolean includeMusicAsLines;

  public Settings(
    boolean includeStageDirectionsInCue,
    boolean caseSensitive,
    boolean punctuation,
    boolean timedMode,
    boolean includeMusicAsLines
  ) {
    this.includeStageDirectionsInCue = includeStageDirectionsInCue;
    this.caseSensitive = caseSensitive;
    this.punctuation = punctuation;
    this.timedMode = timedMode;
    this.includeMusicAsLines = includeMusicAsLines;
  }

  public static Settings menu(Scanner sc) {
    System.out.println("Please Select Your Settings:");

    //Stage direction setting
    System.out.println("Include stage directions in cue lines? (yes/no)");
    boolean includeStageDirectionsInCue = sc
      .nextLine()
      .trim()
      .equalsIgnoreCase("yes");

    //Case sensitivity setting
    System.out.println(
      "Case sensitivity: type 1 for case-insensitive, 2 for case-sensitive"
    );
    boolean caseSensitive = sc.nextLine().trim().equals("2");

    //Punctuation setting
    System.out.println("Keep punctuation in responses? (yes/no)");
    boolean punctuation = sc.nextLine().trim().equalsIgnoreCase("yes");

    //Timer setting
    System.out.println("Timed mode? (yes/no)");
    boolean timedMode = sc.nextLine().trim().equalsIgnoreCase("yes");

    //Music setting
    System.out.println("Include music (songs/lyrics) as lines? (yes/no)");
    boolean includeMusicAsLines = sc.nextLine().trim().equalsIgnoreCase("yes");

    return new Settings(
      includeStageDirectionsInCue,
      caseSensitive,
      punctuation,
      timedMode,
      includeMusicAsLines
    );
  }

  public boolean includeStageDirectionsInCue() {
    return includeStageDirectionsInCue;
  }

  public boolean caseSensitive() {
    return caseSensitive;
  }

  public boolean timedMode() {
    return timedMode;
  }

  public boolean punctuation() {
    return punctuation;
  }

  public boolean includeMusicAsLines() {
    return includeMusicAsLines;
  }
}
