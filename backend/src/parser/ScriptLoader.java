package parser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Supplier;
import ocr.ImageTextExtractor;
import ocr.PdfTextExtractor;
import parser.export.CsvExporter;
import parser.session.ParserSessionStore;
import util.RegexTerms;

public class ScriptLoader {

  private static final boolean USE_EXTRACTED_TEXT_CACHE = true;
  private static final String EXTRACTION_PIPELINE_VERSION =
    "pdf-ocr-v9-visual-stage";

  private static final Set<String> IMAGE_EXTENSIONS = Set.of(
    ".jpg",
    ".jpeg",
    ".png",
    ".gif",
    ".bmp",
    ".tif",
    ".tiff",
    ".webp",
    ".heic",
    ".heif"
  );

  public static String load(Scanner sc) {
    String extension = extension(sc);
    String name = name(sc);
    ParserSessionStore session = new ParserSessionStore(name);
    session.ensureFolders();
    CsvExporter.session(name);

    File script = new File("Example-Scripts/" + name + extension);
    if (!script.exists()) {
      System.out.println("Could not find file: " + script.getPath());
      return "";
    }

    if (extension.equals(".txt")) {
      String text = readTxt(script);
      session.saveText(text);
      return text;
    }

    return readPdf(script, session);
  }

  public static String read(File script) throws IOException {
    return read(script, null);
  }

  public static String read(File script, String originalName)
    throws IOException {
    if (script == null || !script.exists()) {
      throw new IOException("Could not find file: " + script);
    }

    String name = scriptName(script, originalName);
    String lower = name.toLowerCase();
    String pathLower = script.getName().toLowerCase();

    if (lower.endsWith(".txt") || pathLower.endsWith(".txt")) {
      return readTxt(script);
    }
    if (lower.endsWith(".pdf") || pathLower.endsWith(".pdf")) {
      ParserSessionStore session = new ParserSessionStore(sessionName(name));
      session.ensureFolders();
      return readPdf(script, session, name);
    }
    if (isImage(lower) || isImage(pathLower)) {
      ParserSessionStore session = new ParserSessionStore(sessionName(name));
      session.ensureFolders();
      return readImage(script, session, name);
    }

    throw new IOException("Unsupported file type: " + name);
  }

  private static String extension(Scanner sc) {
    System.out.println(
      "Please enter the number that corresponds with your file type:\n[1] .txt\n[2] .pdf\n(Default is 1)"
    );
    String choice = sc.nextLine().trim();
    return choice.equals("2") ? ".pdf" : ".txt";
  }

  private static String name(Scanner sc) {
    String name = "";
    while (name.isEmpty()) {
      System.out.println(
        "Enter the file name of your script.\nPlease exclude the file extension:"
      );
      name = sc.nextLine().trim();
    }
    return name;
  }

  private static String baseName(String name) {
    if (name == null || name.isBlank()) {
      return "current_script";
    }
    return name.replaceFirst(util.RegexTerms.EXTENSION_SUFFIX, "");
  }

  private static String sessionName(String name) {
    return baseName(name).toLowerCase();
  }

  private static String scriptName(File script, String originalName) {
    if (originalName != null && !originalName.isBlank()) {
      return originalName.trim();
    }
    return script.getName();
  }

  private static String readTxt(File script) {
    try {
      return normalize(
        Files.readString(script.toPath(), StandardCharsets.UTF_8)
      );
    } catch (IOException e) {
      System.out.println("Could not read txt file: " + script.getName());
      return "";
    }
  }

  private static String readPdf(File script, ParserSessionStore session) {
    return readPdf(script, session, script.getName());
  }

  private static String readPdf(
    File script,
    ParserSessionStore session,
    String name
  ) {
    return readCached(script, session, name, () -> pdf(script));
  }

  private static String readImage(
    File script,
    ParserSessionStore session,
    String name
  ) {
    return readCached(script, session, name, () -> image(script));
  }

  private static Set<String> lastStageHintKeys = new HashSet<>();

  public static Set<String> stageHintKeys() {
    return lastStageHintKeys;
  }

  private static String readCached(
    File script,
    ParserSessionStore session,
    String name,
    Supplier<String> extractor
  ) {
    if (!USE_EXTRACTED_TEXT_CACHE) {
      String text = extractor.get();
      lastStageHintKeys = PdfTextExtractor.stageHintKeys();
      return text;
    }

    Path metaFile = session.textCsv().resolveSibling("extracted_text.meta");
    Path hintsFile = session.textCsv().resolveSibling("stage_hints.txt");
    String expectedMeta = meta(script, name);

    String cached = readCache(session, metaFile, expectedMeta);
    if (!cached.isEmpty()) {
      lastStageHintKeys = loadHintKeys(hintsFile);
      return cached;
    }

    String text = extractor.get();
    lastStageHintKeys = PdfTextExtractor.stageHintKeys();
    writeCache(session, metaFile, expectedMeta, text);
    saveHintKeys(hintsFile, lastStageHintKeys);
    return text;
  }

  private static Set<String> loadHintKeys(Path hintsFile) {
    try {
      if (!Files.exists(hintsFile)) {
        return new HashSet<>();
      }
      return new HashSet<>(Files.readAllLines(hintsFile));
    } catch (IOException e) {
      return new HashSet<>();
    }
  }

  private static void saveHintKeys(Path hintsFile, Set<String> keys) {
    try {
      Files.writeString(
        hintsFile,
        keys == null || keys.isEmpty() ? "" : String.join("\n", keys)
      );
    } catch (IOException e) {
      System.out.println("Could not save stage hints: " + e.getMessage());
    }
  }

  private static String readCache(
    ParserSessionStore session,
    Path metaFile,
    String expectedMeta
  ) {
    try {
      if (!Files.exists(session.textCsv()) || !Files.exists(metaFile)) {
        return "";
      }

      String actualMeta = Files.readString(
        metaFile,
        StandardCharsets.UTF_8
      ).trim();
      if (!sameMeta(actualMeta, expectedMeta)) {
        return "";
      }

      System.out.println("Using cached extracted text: " + session.textCsv());
      return normalize(session.loadText());
    } catch (IOException e) {
      System.out.println("Could not read extracted text cache; rebuilding it.");
      return "";
    }
  }

  private static void writeCache(
    ParserSessionStore session,
    Path metaFile,
    String expectedMeta,
    String text
  ) {
    if (text == null || text.isEmpty()) {
      return;
    }

    try {
      session.saveText(text);
      Files.writeString(metaFile, expectedMeta, StandardCharsets.UTF_8);
      System.out.println("Saved extracted text cache: " + session.textCsv());
    } catch (IOException e) {
      System.out.println(
        "Could not save extracted text cache: " + e.getMessage()
      );
    }
  }

  private static String meta(File script, String name) {
    return String.format(
      "pipeline=%s\nfile=%s\nsize=%d",
      EXTRACTION_PIPELINE_VERSION,
      scriptName(script, name).toLowerCase(),
      script.length()
    );
  }

  private static boolean sameMeta(String actual, String expected) {
    if (expected.equals(actual)) {
      return true;
    }

    return (
      metaValue(actual, "pipeline").equals(metaValue(expected, "pipeline")) &&
      metaValue(actual, "file").equalsIgnoreCase(metaValue(expected, "file")) &&
      metaValue(actual, "size").equals(metaValue(expected, "size"))
    );
  }

  private static String metaValue(String meta, String key) {
    if (meta == null || key == null) {
      return "";
    }

    String prefix = key + "=";
    for (String line : meta.split(util.RegexTerms.LINE_BREAK)) {
      String trimmed = line.trim();
      if (trimmed.startsWith(prefix)) {
        return trimmed.substring(prefix.length()).trim();
      }
    }

    return "";
  }

  private static String pdf(File script) {
    try {
      return normalize(PdfTextExtractor.extract(script));
    } catch (IOException e) {
      System.out.println("Could not read PDF: " + script.getName());
      System.out.println("Error: " + e.getMessage());
      return "";
    } catch (Throwable e) {
      System.out.println("Could not finish PDF read for: " + script.getName());
      System.out.println(
        "Error: " + e.getClass().getSimpleName() + ": " + e.getMessage()
      );
      return "";
    }
  }

  private static String image(File script) {
    try {
      return normalize(ImageTextExtractor.extract(script));
    } catch (IOException e) {
      System.out.println("Could not read image: " + script.getName());
      System.out.println("Error: " + e.getMessage());
      return "";
    } catch (Throwable e) {
      System.out.println(
        "Could not finish image read for: " + script.getName()
      );
      System.out.println(
        "Error: " + e.getClass().getSimpleName() + ": " + e.getMessage()
      );
      return "";
    }
  }

  private static boolean isImage(String name) {
    if (name == null) {
      return false;
    }

    String lower = name.toLowerCase();
    for (String ext : IMAGE_EXTENSIONS) {
      if (lower.endsWith(ext)) {
        return true;
      }
    }

    return false;
  }

  private static String normalize(String text) {
    if (text == null) {
      return "";
    }

    String fixed = basicUnicodeCleanup(text);
    fixed = repairHyphens(fixed);
    fixed = splitMarkers(fixed);
    fixed = fixSplitNames(fixed);
    fixed = collapseBlankLines(fixed);
    return fixed.trim();
  }

  private static String basicUnicodeCleanup(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }

    return text
      .replace("\r\n", "\n")
      .replace("\r", "\n")
      .replace('\u00A0', ' ')
      .replace("\u00AD", "")
      .replace('\u200B', ' ')
      .replace('\u200C', ' ')
      .replace('\u200D', ' ')
      .replace('\uFEFF', ' ')
      .replace('\u2028', '\n')
      .replace('\u2029', '\n')
      .replace("\uFB00", "ff")
      .replace("\uFB01", "fi")
      .replace("\uFB02", "fl")
      .replace("\uFB03", "ffi")
      .replace("\uFB04", "ffl")
      .replace("\uFB05", "st")
      .replace("\uFB06", "st")
      .replace('“', '"')
      .replace('”', '"')
      .replace('‘', '\'')
      .replace('’', '\'')
      .replace('—', '-')
      .replace('–', '-')
      .replace('…', '.');
  }

  private static String repairHyphens(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    return text.replaceAll(RegexTerms.HYPHEN_LINE_WRAP, "$1$2");
  }

  private static String splitMarkers(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }

    String fixed = text;
    String[] markers = {
      "Characters:",
      "Character:",
      "AT RISE",
      "At rise",
      "EPISODE ",
      "Episode ",
      "ACT ",
      "Act ",
      "SCENE ",
      "Scene:",
      "INT.",
      "EXT.",
      "I/E.",
      "Sounds:",
      "Sound:",
      "The scene blacks out",
      "The Scene Blacks Out",
    };

    for (String marker : markers) {
      fixed = fixed.replaceAll(
        "(?m)(?<!^)\\h+(?=" + java.util.regex.Pattern.quote(marker) + ")",
        "\n"
      );
    }

    return fixed;
  }

  private static String fixSplitNames(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }

    String[] lines = text.split(RegexTerms.NEWLINE_CHAR, -1);
    List<String> out = new ArrayList<>();

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmed = line.trim();

      if (upperNamePart(trimmed)) {
        List<String> split = collectSplitName(lines, i);
        if (split.size() > 1) {
          out.add(String.join(" ", split));
          i += split.size() - 1;
          continue;
        }
      }

      out.add(line);
    }

    return String.join("\n", out);
  }

  private static List<String> collectSplitName(String[] lines, int startIndex) {
    List<String> parts = new ArrayList<>();
    parts.add(lines[startIndex].trim());

    int index = startIndex + 1;
    while (
      index < lines.length &&
      parts.size() < 4 &&
      upperNamePart(lines[index].trim())
    ) {
      String next = lines[index].trim();
      parts.add(next);

      String joined = String.join(" ", parts);
      if (stageHeading(joined) || !splitName(parts, joined)) {
        parts.remove(parts.size() - 1);
        break;
      }
      index++;
    }

    return parts;
  }

  private static boolean upperNamePart(String text) {
    if (text == null) {
      return false;
    }

    String t = text.trim();
    if (t.length() < 2 || t.length() > 24) {
      return false;
    }
    if (!t.matches(RegexTerms.CAPS_NAME_PART)) {
      return false;
    }

    int letters = 0;
    for (int i = 0; i < t.length(); i++) {
      if (Character.isLetter(t.charAt(i))) {
        letters++;
      }
    }

    return letters >= 2;
  }

  private static boolean splitName(List<String> parts, String joined) {
    if (parts == null || joined == null) {
      return false;
    }

    String name = joined.trim();
    if (stageHeading(name)) {
      return false;
    }
    if (name.length() > 45) {
      return false;
    }
    if (!name.matches(RegexTerms.CAPS_ALNUM_NAME)) {
      return false;
    }
    if (parts.size() < 2 || parts.size() > 4) {
      return false;
    }

    for (String part : parts) {
      String cleaned =
        part == null
          ? ""
          : part.replaceAll(RegexTerms.NON_ALNUM_UPPER_QUOTE, "");
      if (cleaned.length() <= 1 && !cleaned.matches(RegexTerms.DIGITS_ONLY)) {
        return false;
      }
    }

    String last = parts.get(parts.size() - 1);
    return (
      !last.endsWith(".") &&
      !last.endsWith(":") &&
      !last.endsWith(";") &&
      !last.endsWith(",")
    );
  }

  private static boolean stageHeading(String text) {
    if (text == null) {
      return false;
    }

    String upper = text.trim().toUpperCase();
    if (upper.isEmpty()) {
      return false;
    }

    String[] stageWords = {
      "BLACKOUT",
      "BLACK OUT",
      "LIGHTS",
      "LIGHT",
      "SOUND",
      "SOUNDS",
      "MUSIC",
      "CURTAIN",
      "END",
      "SCENE",
      "ACT",
      "INTERMISSION",
      "PAUSE",
      "BEAT",
      "ENTRANCE",
      "EXIT",
      "EXEUNT",
      "OFFSTAGE",
      "ONSTAGE",
    };

    for (String word : stageWords) {
      if (
        upper.equals(word) ||
        upper.startsWith(word + " ") ||
        upper.endsWith(" " + word) ||
        upper.contains(" " + word + " ")
      ) {
        return true;
      }
    }

    return false;
  }

  private static String collapseBlankLines(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    return text.replaceAll(RegexTerms.NEWLINE_RUN, "\n\n");
  }
}
