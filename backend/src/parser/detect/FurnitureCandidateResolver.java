package parser.detect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import util.TextNormalizer;

public class FurnitureCandidateResolver {

  public static List<String> resolve(List<String> lines, Set<String> chars) {
    List<String> resolved = new ArrayList<>();
    if (lines == null || lines.isEmpty()) {
      return resolved;
    }

    Set<String> names = names(chars);

    for (String line : lines) {
      String normalized = TextNormalizer.norm(line);

      if (!PageFurnitureDetector.wrapped(normalized)) {
        resolved.add(line);
        continue;
      }

      String inner = PageFurnitureDetector.unwrap(normalized);
      if (keep(inner, names, chars)) {
        resolved.add(inner);
      } else {
        resolved.add("");
      }
    }

    return resolved;
  }

  private static boolean keep(
    String inner,
    Set<String> names,
    Set<String> chars
  ) {
    String cleaned = TextNormalizer.norm(inner);
    if (cleaned.isEmpty()) {
      return false;
    }

    if (known(cleaned, names)) {
      return true;
    }

    String speaker = SpeakerDetector.name(cleaned, chars);
    if (!speaker.isEmpty() && known(speaker, names)) {
      return true;
    }

    if (SpeakerDetector.is(cleaned, chars)) {
      return true;
    }

    String bare = strip(cleaned);
    return known(bare, names);
  }

  private static Set<String> names(Set<String> chars) {
    Set<String> names = new HashSet<>();
    if (chars == null) {
      return names;
    }

    for (String character : chars) {
      String clean = TextNormalizer.cleanName(character);
      if (!clean.isEmpty()) {
        names.add(clean);
      }
    }

    return names;
  }

  private static boolean known(String candidate, Set<String> names) {
    String clean = TextNormalizer.cleanName(candidate);
    if (clean.isEmpty()) {
      return false;
    }

    return names != null && names.contains(clean);
  }

  private static String strip(String line) {
    String cleaned = TextNormalizer.cleanName(line);
    while (cleaned.endsWith(".") || cleaned.endsWith(":")) {
      cleaned = TextNormalizer.cleanName(
        cleaned.substring(0, cleaned.length() - 1)
      );
    }
    return cleaned;
  }
}
