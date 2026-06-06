package parser.session;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import util.RegexTerms;

public class ParserSessionStore {

  private final Path sessionDir;

  public ParserSessionStore(String sessionName) {
    String root = property("ll.sessionRoot", "parser_sessions");
    String id = property("ll.sessionId", "");
    String dirName = id.isEmpty()
      ? cleanSessionName(sessionName)
      : cleanSessionName(id);
    this.sessionDir = Paths.get(root, dirName);
  }

  public void ensureFolders() {
    try {
      Files.createDirectories(sessionDir);
    } catch (IOException e) {
      System.out.println("Could not create session folders: " + e.getMessage());
    }
  }

  public Path dir() {
    return sessionDir;
  }

  public Path charsCsv() {
    return sessionDir.resolve("characters.csv");
  }

  public Path aliasesCsv() {
    return sessionDir.resolve("aliases.csv");
  }

  public Path textCsv() {
    return sessionDir.resolve("extracted_text.csv");
  }

  public boolean hasChars() {
    return Files.exists(charsCsv());
  }

  public boolean hasAliases() {
    return Files.exists(aliasesCsv());
  }

  public void saveText(String text) {
    ensureFolders();

    String body = text == null ? "" : text;
    String[] lines = body.split("\n", -1);
    StringBuilder sb = new StringBuilder("line,text\n");
    for (int i = 0; i < lines.length; i++) {
      sb.append(i + 1).append(',').append(csvCell(lines[i])).append('\n');
    }

    try {
      Files.writeString(textCsv(), sb.toString());
    } catch (IOException e) {
      System.out.println("Could not save extracted text: " + e.getMessage());
    }
  }

  public String loadText() {
    try {
      Path csv = textCsv();
      if (!Files.exists(csv)) {
        return "";
      }

      List<String> rows = Files.readAllLines(csv);
      StringBuilder out = new StringBuilder();
      for (int i = 1; i < rows.size(); i++) {
        if (i > 1) {
          out.append('\n');
        }
        out.append(csvText(rows.get(i)));
      }
      return out.toString();
    } catch (IOException e) {
      System.out.println("Could not load extracted text: " + e.getMessage());
    }
    return "";
  }

  private static String property(String key, String fallback) {
    String value = System.getProperty(key);
    return value == null ? fallback : value.trim();
  }

  private static String csvCell(String text) {
    String cleaned = (text == null ? "" : text).replace("\"", "\"\"")
      .replace("\n", " ")
      .replace("\r", " ");
    return "\"" + cleaned + "\"";
  }

  private static String csvText(String row) {
    if (row == null) {
      return "";
    }

    int comma = row.indexOf(',');
    String cell = comma < 0 ? row : row.substring(comma + 1);
    if (cell.length() >= 2 && cell.startsWith("\"") && cell.endsWith("\"")) {
      cell = cell.substring(1, cell.length() - 1);
    }
    return cell.replace("\"\"", "\"");
  }

  private static String cleanSessionName(String name) {
    String cleaned = name == null ? "" : name.trim();
    cleaned = cleaned.replaceAll(RegexTerms.EXTENSION_SUFFIX, "");
    cleaned = cleaned.replaceAll(RegexTerms.NON_SESSION_NAME_CHAR, "_");
    return cleaned.isEmpty() ? "current_script" : cleaned;
  }
}
