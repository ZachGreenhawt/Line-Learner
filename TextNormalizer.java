public class TextNormalizer {

    private static final String INVISIBLE_CHARS = "[\\u200B\\u200C\\u200D\\uFEFF]";

    public static String norm(String text) {
        if (text == null) {
            return "";
        }
        return text.strip().replaceAll("\\s+", " ");
    }

    public static String cleanName(String text) {
        String out = rawHeadingName(text).toUpperCase();
        return stripTrailingSpeakerPunctuation(out);
    }

    public static String rawHeadingName(String text) {
        String out = norm(text);
        out = out.replaceAll(INVISIBLE_CHARS, "");
        out = out.replaceAll("\\s*/\\s*", " / ");
        out = out.replaceAll("\\s+", " ").trim();
        return stripTrailingSpeakerPunctuation(out);
    }

    private static String stripTrailingSpeakerPunctuation(String text) {
        String out = norm(text);
        while (out.endsWith(":") || out.endsWith(".")) {
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

    private static String removeLeadingParentheticals(String text) {
        String out = norm(text);
        boolean changed = true;

        while (changed && !out.isEmpty()) {
            changed = false;

            if (out.startsWith("(")) {
                int close = out.indexOf(")");
                if (close < 0) {
                    return "";
                }
                out = norm(out.substring(close + 1));
                changed = true;
            } else if (out.startsWith("[")) {
                int close = out.indexOf("]");
                if (close < 0) {
                    return "";
                }
                out = norm(out.substring(close + 1));
                changed = true;
            }
        }

        return out;
    }

    private static String removeInlineParentheticals(String text, String openSymbol, String closeSymbol) {
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

        while (out.startsWith(":") || out.startsWith(".") || out.startsWith(")") || out.startsWith("]")) {
            out = out.substring(1).strip();
        }

        return out;
    }
}
