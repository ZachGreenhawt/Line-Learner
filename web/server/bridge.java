package web.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import parser.CharacterExtractor;
import parser.CuePairBuilder;
import parser.LogicalLineBuilder;
import parser.ScriptLoader;
import parser.ScriptParser;
import parser.ScriptPreProcess;
import parser.SpeakerBlockBuilder;
import parser.TurnBuilder;
import parser.detect.FurnitureCandidateResolver;
import parser.detect.SpeakerHeadingIndex;
import parser.detect.StageHints;
import parser.model.ParseModels;
import practice.Settings;

public class bridge {

  private static final String LIST_SEPARATOR = "\u001F";

  public static void main(String[] args) {
    PrintStream stdout = System.out;
    ByteArrayOutputStream logs = new ByteArrayOutputStream();

    try {
      System.setOut(new PrintStream(logs));
      String response = route(args);
      System.setOut(stdout);
      stdout.println(response);
    } catch (Throwable error) {
      System.setOut(stdout);
      stdout.println(errorJson(error, logs.toString()));
      System.exit(1);
    }
  }

  private static String route(String[] args) throws Exception {
    if (args == null || args.length < 2) {
      throw new IllegalArgumentException(
        "Usage: analyze|parse <file-path> ..."
      );
    }

    String command = args[0];
    if ("analyze".equals(command)) {
      return analyze(args);
    }
    if ("parse".equals(command)) {
      return parse(args);
    }

    throw new IllegalArgumentException("Unknown command: " + command);
  }

  private static String analyze(String[] args) throws Exception {
    File file = Paths.get(args[1]).toFile();
    String name = args.length > 2 ? args[2] : file.getName();
    Script script = script(file, name, new LinkedHashSet<>());
    int bodyStart = ScriptParser.suggestedBodyStart(script.lines, script.chars);

    return (
      "{" +
      "\"ok\":true," +
      "\"fileName\":" +
      quote(name) +
      "," +
      "\"textLength\":" +
      script.text.length() +
      "," +
      "\"lineCount\":" +
      script.lines.size() +
      "," +
      "\"bodyStartIndex\":" +
      bodyStart +
      "," +
      "\"characters\":" +
      jsonArray(CharacterExtractor.sort(script.chars)) +
      "," +
      "\"preview\":" +
      preview(script.lines, bodyStart) +
      "}"
    );
  }

  private static String parse(String[] args) throws Exception {
    if (args.length < 12) {
      throw new IllegalArgumentException(
        "Usage: parse <file-path> <name> <target> <start> <stage> <case> <punctuation> <timed> <music> <characters> <remove-phrases>"
      );
    }

    File file = Paths.get(args[1]).toFile();
    String name = args[2];
    String target = args[3];
    int requestedStart = parseInt(args[4], -1);
    Settings settings = new Settings(
      yes(args[5]),
      yes(args[6]),
      yes(args[7]),
      yes(args[8]),
      yes(args[9])
    );

    Script script = script(file, name, characters(args[10]), list(args[11]));
    String cleanTarget = CharacterExtractor.target(target, script.chars);
    int start =
      requestedStart >= 0
        ? clamp(requestedStart, 0, Math.max(0, script.lines.size() - 1))
        : ScriptParser.suggestedBodyStart(script.lines, script.chars);

    List<String> bodyLines = script.lines.isEmpty()
      ? new ArrayList<>()
      : new ArrayList<>(script.lines.subList(start, script.lines.size()));

    Set<String> hintKeys = ScriptLoader.stageHintKeys();
    boolean[] stageHints = StageHints.match(bodyLines, hintKeys);
    Map<Integer, SpeakerHeadingIndex.HeadingRecord> headings =
      SpeakerHeadingIndex.build(bodyLines, script.chars, script.aliases);
    List<ParseModels.Block> blocks = SpeakerBlockBuilder.build(
      bodyLines,
      headings,
      script.chars,
      stageHints,
      StageHints.authoritative(hintKeys)
    );
    List<ParseModels.ScriptTurn> turns = TurnBuilder.fromBlocks(blocks);
    CuePairBuilder.Result pairs = CuePairBuilder.build(
      turns,
      cleanTarget,
      settings
    );

    return (
      "{" +
      "\"ok\":true," +
      "\"fileName\":" +
      quote(name) +
      "," +
      "\"targetCharacter\":" +
      quote(cleanTarget) +
      "," +
      "\"bodyStartIndex\":" +
      start +
      "," +
      "\"turnCount\":" +
      turns.size() +
      "," +
      "\"total\":" +
      pairs.mine.size() +
      "," +
      "\"items\":" +
      items(pairs.cues, pairs.mine) +
      "}"
    );
  }

  private static Script script(
    File file,
    String name,
    Set<String> suppliedChars
  ) throws Exception {
    return script(file, name, suppliedChars, new ArrayList<>());
  }

  private static Script script(
    File file,
    String name,
    Set<String> suppliedChars,
    List<String> removePhrases
  ) throws Exception {
    String text = ScriptPreProcess.clean(ScriptLoader.read(file, name));
    text = ScriptPreProcess.removePhrases(text, removePhrases);
    text = ScriptPreProcess.clean(text);

    Set<String> chars =
      suppliedChars == null
        ? new LinkedHashSet<>()
        : new LinkedHashSet<>(suppliedChars);

    if (chars.isEmpty()) {
      chars.addAll(CharacterExtractor.find(text));
    }

    chars = CharacterExtractor.expand(chars);
    Map<String, String> aliases = CharacterExtractor.garbleAliases(text, chars);
    text = FurnitureCandidateResolver.unwrapHeadings(text, chars);
    List<String> lines = LogicalLineBuilder.build(text, chars);
    lines = FurnitureCandidateResolver.resolve(lines, chars);
    return new Script(text, chars, lines, aliases);
  }

  private static Set<String> characters(String text) {
    Set<String> chars = new LinkedHashSet<>();
    if (text == null || text.isBlank()) {
      return chars;
    }

    for (String part : text.split(LIST_SEPARATOR, -1)) {
      String name = part.trim();
      if (!name.isEmpty()) {
        chars.add(name);
      }
    }

    return chars;
  }

  private static List<String> list(String text) {
    List<String> values = new ArrayList<>();
    if (text == null || text.isBlank()) {
      return values;
    }

    for (String part : text.split(LIST_SEPARATOR, -1)) {
      String value = part.trim();
      if (!value.isEmpty()) {
        values.add(value);
      }
    }

    return values;
  }

  private static String items(List<String> cues, List<String> lines) {
    StringBuilder json = new StringBuilder("[");
    int count = Math.min(cues.size(), lines.size());

    for (int i = 0; i < count; i++) {
      if (i > 0) {
        json.append(',');
      }
      json
        .append('{')
        .append("\"index\":")
        .append(i)
        .append(',')
        .append("\"cue\":")
        .append(quote(cues.get(i)))
        .append(',')
        .append("\"line\":")
        .append(quote(lines.get(i)))
        .append('}');
    }

    return json.append(']').toString();
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

  private static boolean yes(String value) {
    return "true".equalsIgnoreCase(value) || "1".equals(value);
  }

  private static int parseInt(String value, int fallback) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException error) {
      return fallback;
    }
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(value, max));
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

  private static class Script {

    final String text;
    final Set<String> chars;
    final List<String> lines;
    final Map<String, String> aliases;

    Script(
      String text,
      Set<String> chars,
      List<String> lines,
      Map<String, String> aliases
    ) {
      this.text = text == null ? "" : text;
      this.chars = chars == null ? new LinkedHashSet<>() : chars;
      this.lines = lines == null ? new ArrayList<>() : lines;
      this.aliases = aliases == null ? new HashMap<>() : aliases;
    }
  }
}
