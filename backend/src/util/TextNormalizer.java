package util;

public class TextNormalizer {

  public static String norm(String text) {
    if (text == null) {
      return "";
    }
    return text.strip().replaceAll(RegexTerms.WHITESPACE, " ");
  }

  public static String cleanName(String text) {
    String out = rawHeadingName(text).toUpperCase();
    return stripTrailingSpeakerPunctuation(out);
  }

  public static String rawHeadingName(String text) {
    String out = norm(text);
    out = out.replaceAll(RegexTerms.INVISIBLE_CHARS, "");
    out = out.replaceAll(RegexTerms.WHITESPACE_AROUND_SLASH, " / ");
    out = out.replaceAll(RegexTerms.WHITESPACE, " ").trim();
    return stripTrailingSpeakerPunctuation(out);
  }

  private static String stripTrailingSpeakerPunctuation(String text) {
    String out = norm(text);
    while (out.endsWith(":") || out.endsWith(".") || out.endsWith(",")) {
      out = out.substring(0, out.length() - 1).strip();
    }
    return out;
  }

  public static boolean hasLetter(String text) {
    if (text == null) {
      return false;
    }

    for (int i = 0; i < text.length(); i++) {
      if (Character.isLetter(text.charAt(i))) {
        return true;
      }
    }

    return false;
  }

  public static String removeParen(String line) {
    String out = norm(line);

    out = removeLeadingParentheticals(out);
    out = removeInlineParentheticals(out, "(", ")");
    out = removeInlineParentheticals(out, "[", "]");

    return norm(out);
  }

  public static String stripLeadingParentheticals(String text) {
    String out = norm(text);

    while (!out.isEmpty()) {
      String close;
      if (out.startsWith("(")) {
        close = ")";
      } else if (out.startsWith("[")) {
        close = "]";
      } else if (out.startsWith("{")) {
        close = "}";
      } else {
        break;
      }

      int end = out.indexOf(close);
      if (end < 0) {
        break;
      }
      out = norm(out.substring(end + 1));
    }

    return out;
  }

  private static String removeLeadingParentheticals(String text) {
    String out = norm(text);

    while (!out.isEmpty()) {
      String close;
      if (out.startsWith("(")) {
        close = ")";
      } else if (out.startsWith("[")) {
        close = "]";
      } else {
        break;
      }

      int end = out.indexOf(close);
      if (end < 0) {
        return "";
      }
      out = norm(out.substring(end + 1));
    }

    return out;
  }

  private static String removeInlineParentheticals(
    String text,
    String openSymbol,
    String closeSymbol
  ) {
    String out = norm(text);
    int open = out.indexOf(openSymbol);

    while (open >= 0) {
      int close = out.indexOf(closeSymbol, open);
      if (close < 0) {
        out = norm(out.substring(0, open));
        break;
      }

      String before = out.substring(0, open);
      String after = out.substring(close + 1);
      out = norm(before + " " + after);
      open = out.indexOf(openSymbol);
    }

    return out;
  }

  public static String cleanSpokenText(String line) {
    String out = removeParen(line);
    out = stripLeadingSpeakerPunctuation(out);
    return norm(out);
  }

  private static String stripLeadingSpeakerPunctuation(String text) {
    String out = norm(text);

    while (
      out.startsWith(":") ||
      out.startsWith(".") ||
      out.startsWith(",") ||
      out.startsWith(")") ||
      out.startsWith("]")
    ) {
      out = out.substring(1).strip();
    }

    return out;
  }
}
