package app;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import parser.ScriptLoader;
import parser.ScriptParser;
import parser.ScriptPreProcess;
import practice.ParsedScript;
import practice.PracticeSession;
import practice.Settings;
import util.RegexTerms;

public class Main {

  public static void main(String[] args) {
    Scanner sc = new Scanner(System.in);
    Settings settings = Settings.menu(sc);

    String raw = loadScript(sc);
    if (raw.isEmpty()) {
      sc.close();
      return;
    }

    String cleaned = ScriptPreProcess.clean(raw);
    cleaned = applyUserCleanup(sc, cleaned);
    cleaned = ScriptPreProcess.clean(cleaned);

    ParsedScript parsed = ScriptParser.parse(cleaned, settings, sc);

    if (parsed == null || !parsed.isConsistent() || parsed.size() == 0) {
      System.out.println("No usable lines were found for that character.");
      sc.close();
      return;
    }

    PracticeSession.run(parsed, settings, sc);
    PracticeSession.postPracticeMenu(parsed, settings, sc);
    sc.close();
  }

  private static String loadScript(Scanner sc) {
    String text = "";

    while (text == null || text.isBlank()) {
      text = ScriptLoader.load(sc);

      if (text == null || text.isBlank()) {
        System.out.println(
          "No script loaded. Type 'q' to quit or press Enter to try again."
        );
        String choice = sc.nextLine().trim();
        if (choice.equalsIgnoreCase("q")) {
          return "";
        }
      }
    }

    return text;
  }

  private static String applyUserCleanup(Scanner sc, String text) {
    if (text == null || text.isBlank()) return "";

    System.out.println("Optional cleanup before parsing:");
    System.out.println(
      "Type words or phrases to remove from the script, separated by commas."
    );
    System.out.println(
      "Use this for repeated headers, footers, OCR junk, or false character names."
    );
    System.out.println("Press Enter to skip.");

    String input = sc.nextLine().trim();
    if (input.isEmpty()) return text;

    List<String> phrases = new ArrayList<>();

    for (String phrase : input.split(RegexTerms.COMMA)) {
      String cleaned = phrase.trim();
      if (!cleaned.isEmpty()) {
        phrases.add(cleaned);
      }
    }

    return ScriptPreProcess.removePhrases(text, phrases);
  }
}
