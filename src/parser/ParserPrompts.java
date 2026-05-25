import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class ParserPrompts {

  public static Set<String> chars(Set<String> found, Scanner sc) {
    List<String> names = CharacterExtractor.sort(found);

    names = remove(names, sc);
    edit(names, sc);
    add(names, sc);

    Set<String> result = clean(names);
    result = CharacterExtractor.expand(result);

    print(result);
    return result;
  }

  private static List<String> remove(List<String> names, Scanner sc) {
    if (names.isEmpty()) {
      return names;
    }

    System.out.println("\nDetected possible characters:");
    printList(names);

    System.out.println(
      "\nType numbers to remove, separated by commas. Press Enter to keep all."
    );
    Set<Integer> removed = nums(sc.nextLine(), names.size());

    List<String> kept = new ArrayList<>();
    for (int i = 0; i < names.size(); i++) {
      if (!removed.contains(i + 1)) {
        kept.add(names.get(i));
      }
    }
    return kept;
  }

  private static void edit(List<String> names, Scanner sc) {
    if (names.isEmpty()) {
      return;
    }

    System.out.println("\nCharacters available to edit:");
    printList(names);

    System.out.println(
      "\nType edits as number=new name, separated by commas. Press Enter if none."
    );
    applyEdits(names, sc.nextLine());
  }

  private static void add(List<String> names, Scanner sc) {
    System.out.println(
      "\nType missing character names to add, separated by commas. Press Enter if none."
    );
    String input = sc.nextLine().trim();
    if (input.isEmpty()) {
      return;
    }

    for (String part : input.split(",")) {
      String name = TextNormalizer.cleanName(part);
      if (!name.isEmpty()) {
        names.add(name);
      }
    }
  }

  private static Set<String> clean(List<String> names) {
    Set<String> result = new LinkedHashSet<>();

    for (String name : names) {
      String cleaned = CharacterExtractor.dropLeadAction(name);
      if (valid(cleaned)) {
        result.add(cleaned);
      }
    }

    return result;
  }

  private static boolean valid(String name) {
    return (
      name != null &&
      !name.isEmpty() &&
      !CharacterExtractor.BAD_HEADINGS.contains(name) &&
      !CharacterExtractor.BAD_SHORT_LINES.contains(name)
    );
  }

  private static void print(Set<String> chars) {
    System.out.println("\nUsing characters:");
    for (String name : chars) {
      System.out.println("- " + name);
    }
    System.out.println();
  }

  private static void printList(List<String> names) {
    for (int i = 0; i < names.size(); i++) {
      System.out.println((i + 1) + ". " + names.get(i));
    }
  }

  public static Set<Integer> nums(String input, int max) {
    Set<Integer> out = new HashSet<>();
    if (input == null || input.trim().isEmpty()) {
      return out;
    }

    for (String part : input.split(",")) {
      try {
        int n = Integer.parseInt(part.trim());
        if (n >= 1 && n <= max) {
          out.add(n);
        }
      } catch (NumberFormatException ignored) {}
    }
    return out;
  }

  public static void applyEdits(List<String> names, String input) {
    if (input == null || input.trim().isEmpty()) {
      return;
    }

    for (String edit : input.split(",")) {
      String[] pair = edit.split("=", 2);
      if (pair.length != 2) {
        continue;
      }

      try {
        int n = Integer.parseInt(pair[0].trim());
        String newName = TextNormalizer.cleanName(pair[1]);
        if (n >= 1 && n <= names.size() && !newName.isEmpty()) {
          names.set(n - 1, newName);
        }
      } catch (NumberFormatException ignored) {}
    }
  }

  public static int bodyStart(
    List<String> lines,
    Set<String> chars,
    Scanner sc
  ) {
    if (lines == null || lines.isEmpty()) {
      return 0;
    }

    int suggested = StageDetector.bodyStartIndex(lines, chars);
    printPreview(lines, suggested);

    System.out.println(
      "\nEnter the line number where dialogue/stage action should begin, or press Enter to use the suggestion."
    );
    String input = sc.nextLine().trim();
    if (input.isEmpty()) {
      return suggested;
    }

    try {
      int chosen = Integer.parseInt(input);
      if (chosen >= 1 && chosen <= lines.size()) {
        return chosen - 1;
      }
    } catch (NumberFormatException ignored) {}

    System.out.println(
      "Invalid start line. Using suggested start: " + (suggested + 1)
    );
    return suggested;
  }

  private static void printPreview(List<String> lines, int suggested) {
    int start = Math.max(0, suggested - 12);
    int end = Math.min(lines.size(), suggested + 60);

    System.out.println("\nChoose where the actual script text starts.");
    System.out.println(
      "This prevents cast lists, title pages, publisher pages, and prefaces from becoming cues."
    );
    System.out.println(
      "Suggested start: " + (suggested + 1) + " -> " + lines.get(suggested)
    );
    System.out.println("\nNearby logical lines:");

    for (int i = start; i < end; i++) {
      String marker = i == suggested ? "  <-- suggested" : "";
      System.out.println((i + 1) + ". " + lines.get(i) + marker);
    }
  }
}
