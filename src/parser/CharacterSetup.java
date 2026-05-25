package parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class CharacterSetup {

  public static class Result {

    private final ArrayList<String> chars;
    private final ArrayList<String> removed;
    private final Map<String, String> aliases;

    public Result(
      ArrayList<String> chars,
      ArrayList<String> removed,
      Map<String, String> aliases
    ) {
      this.chars = chars == null ? new ArrayList<>() : chars;
      this.removed = removed == null ? new ArrayList<>() : removed;
      this.aliases = aliases == null ? new LinkedHashMap<>() : aliases;
    }

    public ArrayList<String> chars() {
      return chars;
    }

    public ArrayList<String> removed() {
      return removed;
    }

    public Map<String, String> aliases() {
      return aliases;
    }
  }

  public static Result review(List<String> detected, Scanner input) {
    ArrayList<String> chars = cleanList(detected);
    ArrayList<String> removed = new ArrayList<>();
    Map<String, String> aliases = new LinkedHashMap<>();

    if (input == null || chars.isEmpty()) {
      return new Result(chars, removed, aliases);
    }

    chars = remove(chars, removed, input);
    edit(chars, aliases, input);
    chars = cleanList(chars);
    add(chars, input);
    chars = cleanList(chars);

    bullets("Using characters:", chars);
    bullets("Using removed names as stage-direction hints:", removed);

    return new Result(chars, removed, aliases);
  }

  private static ArrayList<String> remove(
    ArrayList<String> chars,
    ArrayList<String> removed,
    Scanner input
  ) {
    numbered("Detected possible characters:", chars);

    System.out.println();
    System.out.println(
      "Type numbers to remove, separated by commas. Removed names become stage-direction hints."
    );
    System.out.println("Press Enter to keep all.");

    Set<Integer> nums = nums(input.nextLine());
    ArrayList<String> kept = new ArrayList<>();

    for (int i = 0; i < chars.size(); i++) {
      String name = chars.get(i);
      if (nums.contains(i + 1)) {
        removed.add(name);
        continue;
      }
      kept.add(name);
    }

    return kept;
  }

  private static void edit(
    ArrayList<String> chars,
    Map<String, String> aliases,
    Scanner input
  ) {
    numbered("Characters available to edit:", chars);

    System.out.println();
    System.out.println(
      "Type edits as number=new name, separated by commas. Press Enter if none."
    );

    edits(chars, aliases, input.nextLine());
  }

  private static void add(ArrayList<String> chars, Scanner input) {
    System.out.println();
    System.out.println(
      "Type missing character names to add, separated by commas. Press Enter if none."
    );

    addNames(chars, input.nextLine());
  }

  private static void numbered(String title, List<String> items) {
    System.out.println();
    System.out.println(title);

    if (items == null || items.isEmpty()) {
      System.out.println("(none)");
      return;
    }

    for (int i = 0; i < items.size(); i++) {
      System.out.println((i + 1) + ". " + items.get(i));
    }
  }

  private static void bullets(String title, List<String> items) {
    System.out.println();
    System.out.println(title);

    if (items == null || items.isEmpty()) {
      System.out.println("- (none)");
      return;
    }

    for (String item : items) {
      System.out.println("- " + item);
    }
  }

  private static Set<Integer> nums(String text) {
    Set<Integer> nums = new LinkedHashSet<>();
    if (blank(text)) {
      return nums;
    }

    for (String part : text.split(",")) {
      try {
        nums.add(Integer.parseInt(part.trim()));
      } catch (NumberFormatException ignored) {}
    }

    return nums;
  }

  private static void edits(
    ArrayList<String> chars,
    Map<String, String> aliases,
    String text
  ) {
    if (chars == null || aliases == null || blank(text)) {
      return;
    }

    for (String edit : text.split(",")) {
      edit(chars, aliases, edit);
    }
  }

  private static void edit(
    ArrayList<String> chars,
    Map<String, String> aliases,
    String text
  ) {
    if (blank(text) || !text.contains("=")) {
      return;
    }

    String[] parts = text.split("=", 2);
    String name = clean(parts[1]);
    if (name.isEmpty()) {
      return;
    }

    try {
      int index = Integer.parseInt(parts[0].trim()) - 1;
      if (index < 0 || index >= chars.size()) {
        return;
      }

      String old = clean(chars.get(index));
      chars.set(index, name);
      if (!old.isEmpty() && !old.equals(name)) {
        aliases.put(old, name);
      }
    } catch (NumberFormatException ignored) {}
  }

  private static void addNames(ArrayList<String> chars, String text) {
    if (chars == null || blank(text)) {
      return;
    }

    for (String part : text.split(",")) {
      String name = clean(part);
      if (!name.isEmpty()) {
        chars.add(name);
      }
    }
  }

  private static ArrayList<String> cleanList(List<String> names) {
    ArrayList<String> out = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();

    if (names == null) {
      return out;
    }

    for (String name : names) {
      String cleaned = clean(name);
      if (!cleaned.isEmpty() && seen.add(cleaned)) {
        out.add(cleaned);
      }
    }

    return out;
  }

  private static String clean(String name) {
    if (name == null) {
      return "";
    }
    return name.trim().replaceAll("\\s+", " ").toUpperCase();
  }

  private static boolean blank(String text) {
    return text == null || text.trim().isEmpty();
  }
}
