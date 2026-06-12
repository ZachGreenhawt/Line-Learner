package parser.detect;

import java.util.List;

public class StageHints {

  public static final String OPEN = "<STAGE_HINT>";
  public static final String CLOSE = "</STAGE_HINT>";

  public static String wrap(String text) {
    if (text == null || text.isBlank()) {
      return text;
    }
    return OPEN + text + CLOSE;
  }

  public static boolean hinted(String line) {
    return line != null && line.trim().startsWith(OPEN);
  }

  public static String unwrap(String line) {
    if (line == null) {
      return "";
    }
    String out = line.trim();
    if (out.startsWith(OPEN)) {
      out = out.substring(OPEN.length());
    }
    if (out.endsWith(CLOSE)) {
      out = out.substring(0, out.length() - CLOSE.length());
    }
    return out.replace(OPEN, " ").replace(CLOSE, " ").trim();
  }

  public static String key(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder key = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (Character.isLetterOrDigit(ch)) {
        key.append(Character.toLowerCase(ch));
      }
    }
    return key.toString();
  }

  public static boolean[] match(
    List<String> lines,
    java.util.Set<String> hintKeys
  ) {
    if (lines == null || lines.isEmpty()) {
      return new boolean[0];
    }

    boolean[] hints = new boolean[lines.size()];
    if (hintKeys == null || hintKeys.isEmpty()) {
      return hints;
    }

    for (int i = 0; i < lines.size(); i++) {
      String key = key(lines.get(i));
      hints[i] = !key.isEmpty() && hintKeys.contains(key);
    }
    return hints;
  }

  public static boolean[] extract(List<String> lines) {
    if (lines == null || lines.isEmpty()) {
      return new boolean[0];
    }

    boolean[] hints = new boolean[lines.size()];
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (hinted(line)) {
        hints[i] = true;
        lines.set(i, unwrap(line));
      } else if (line != null && line.contains(OPEN)) {
        lines.set(i, unwrap(line));
      }
    }
    return hints;
  }
}
