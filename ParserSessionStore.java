import java.io.*;
import java.nio.file.*;

public class ParserSessionStore {

  private final Path sessionDir;

  public ParserSessionStore(String sessionName) {
    this.sessionDir = Paths.get(
      "parser_sessions",
      cleanSessionName(sessionName)
    );
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

  public Path textPath() {
    return sessionDir.resolve("extracted_text.txt");
  }

  public boolean hasChars() {
    return Files.exists(charsCsv());
  }

  public boolean hasAliases() {
    return Files.exists(aliasesCsv());
  }

  public void saveText(String text) {
    ensureFolders();

    try {
      Files.writeString(textPath(), text == null ? "" : text);
    } catch (IOException e) {
      System.out.println("Could not save extracted text: " + e.getMessage());
    }
  }

  public String loadText() {
    try {
      if (Files.exists(textPath())) {
        return Files.readString(textPath());
      }
    } catch (IOException e) {
      System.out.println("Could not load extracted text: " + e.getMessage());
    }
    return "";
  }

  private static String cleanSessionName(String name) {
    String cleaned = name == null ? "" : name.trim();
    cleaned = cleaned.replaceAll("\\.[^.]+$", "");
    cleaned = cleaned.replaceAll("[^A-Za-z0-9._-]", "_");
    return cleaned.isEmpty() ? "current_script" : cleaned;
  }

  private static String escapeJson(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
