package web.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import parser.CharacterExtractor;
import parser.LogicalLineBuilder;
import parser.ScriptLoader;
import parser.ScriptParser;
import parser.ScriptPreProcess;
import parser.detect.FurnitureCandidateResolver;

public class bridge {

  public static void main(String[] args) {
    PrintStream stdout = System.out;
    ByteArrayOutputStream logs = new ByteArrayOutputStream();

    try {
      System.setOut(new PrintStream(logs));
      String response = analyze(args);
      System.setOut(stdout);
      stdout.println(response);
    } catch (Throwable error) {
      System.setOut(stdout);
      stdout.println(errorJson(error, logs.toString()));
      System.exit(1);
    }
  }

  private static String analyze(String[] args) throws Exception {
    if (args == null || args.length < 2) {
      throw new IllegalArgumentException("Usage: analyze <file-path> [name]");
    }

    String command = args[0];
    if (!"analyze".equals(command) && !"parse".equals(command)) {
      throw new IllegalArgumentException("Unknown command: " + command);
    }

    File file = Paths.get(args[1]).toFile();
    String name = args.length > 2 ? args[2] : file.getName();
    String text = ScriptPreProcess.clean(ScriptLoader.read(file));
    Set<String> chars = CharacterExtractor.expand(
      CharacterExtractor.find(text)
    );
    List<String> lines = LogicalLineBuilder.build(text, chars);
    lines = FurnitureCandidateResolver.resolve(lines, chars);
    int bodyStart = ScriptParser.suggestedBodyStart(lines, chars);

    return (
      "{" +
      "\"ok\":true," +
      "\"fileName\":" +
      quote(name) +
      "," +
      "\"textLength\":" +
      text.length() +
      "," +
      "\"lineCount\":" +
      lines.size() +
      "," +
      "\"bodyStartIndex\":" +
      bodyStart +
      "," +
      "\"characters\":" +
      jsonArray(CharacterExtractor.sort(chars)) +
      "," +
      "\"preview\":" +
      preview(lines, bodyStart) +
      "}"
    );
  }

  private static String preview(List<String> lines, int bodyStart) {
    StringBuilder json = new StringBuilder("[");
    int first = Math.max(0, bodyStart - 12);
    int last = Math.min(lines.size(), bodyStart + 60);

    for (int i = first; i < last; i++) {
      if (i > first) {
        json.append(',');
      }
      json
        .append('{')
        .append("\"index\":")
        .append(i)
        .append(',')
        .append("\"lineNumber\":")
        .append(i + 1)
        .append(',')
        .append("\"suggested\":")
        .append(i == bodyStart)
        .append(',')
        .append("\"text\":")
        .append(quote(lines.get(i)))
        .append('}');
    }

    return json.append(']').toString();
  }

  private static String jsonArray(List<String> values) {
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append(quote(values.get(i)));
    }
    return json.append(']').toString();
  }

  private static String quote(String value) {
    if (value == null) {
      return "\"\"";
    }

    StringBuilder out = new StringBuilder(value.length() + 16);
    out.append('"');
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      switch (ch) {
        case '"':
          out.append("\\\"");
          break;
        case '\\':
          out.append("\\\\");
          break;
        case '\n':
          out.append("\\n");
          break;
        case '\r':
          out.append("\\r");
          break;
        case '\t':
          out.append("\\t");
          break;
        default:
          if (ch < 0x20) {
            out.append(String.format("\\u%04x", (int) ch));
          } else {
            out.append(ch);
          }
      }
    }
    return out.append('"').toString();
  }

  private static String errorJson(Throwable error, String logs) {
    String message = error.getMessage();
    if (message == null || message.isBlank()) {
      message = error.getClass().getSimpleName();
    }

    return (
      "{" +
      "\"ok\":false," +
      "\"error\":" +
      quote(message) +
      "," +
      "\"type\":" +
      quote(error.getClass().getSimpleName()) +
      "," +
      "\"logs\":" +
      quote(logs) +
      "}"
    );
  }
}
