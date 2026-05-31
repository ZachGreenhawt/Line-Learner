package ocr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import ocr.model.*;
import util.RegexTerms;

public class DocumentLearningCache {

  private static final String CACHE_DIR = ".line-learner-cache";
  private static final String CACHE_FILE = "document-learning-cache.tsv";
  private static final String VERSION = "1";

  private final Path file;
  private final Map<String, Entry> entries = new HashMap<>();

  public DocumentLearningCache() {
    this(defaultFile());
  }

  public DocumentLearningCache(Path file) {
    this.file = file;
    load();
  }

  public Entry get(File pdf) {
    String key = fingerprint(pdf);
    if (key.isEmpty()) {
      return Entry.empty();
    }
    return entries.getOrDefault(key, Entry.empty());
  }

  public void remember(
    File pdf,
    HybridTextExtraction.Source source,
    double score
  ) {
    if (pdf == null || source == null) {
      return;
    }

    String key = fingerprint(pdf);
    if (key.isEmpty()) {
      return;
    }

    Entry entry = entries.getOrDefault(key, Entry.empty());
    entries.put(key, entry.withSource(source, score).withKey(key));
    save();
  }

  public void rememberTrial(
    File pdf,
    int rotation,
    int index,
    String name,
    double confidence,
    double score
  ) {
    String key = fingerprint(pdf);
    if (key.isEmpty()) {
      return;
    }

    Entry entry = entries.getOrDefault(key, Entry.empty());
    entries.put(
      key,
      entry.withTrial(rotation, index, name, confidence, score).withKey(key)
    );
    save();
  }

  public boolean prefersNativeText(File pdf) {
    Entry entry = get(pdf);
    return (
      entry.source == HybridTextExtraction.Source.NATIVE_TEXT &&
      entry.quality >= 0.62
    );
  }

  public boolean prefersOcr(File pdf) {
    return get(pdf).source == HybridTextExtraction.Source.OCR_TEXT;
  }

  public boolean hasUsefulOcrTrial(File pdf) {
    Entry entry = get(pdf);
    return entry.hasTrial() && entry.ocrConfidence >= 0.55;
  }

  public TrialKey preferredTrial(File pdf) {
    Entry entry = get(pdf);
    if (!entry.hasTrial()) {
      return null;
    }
    return new TrialKey(entry.rotation, entry.index, entry.name);
  }

  private void load() {
    entries.clear();
    if (file == null || !Files.exists(file)) {
      return;
    }

    try (
      BufferedReader reader = Files.newBufferedReader(
        file,
        StandardCharsets.UTF_8
      )
    ) {
      String line;
      while ((line = reader.readLine()) != null) {
        Entry entry = Entry.from(line);
        if (entry != null && !entry.key.isEmpty()) {
          entries.put(entry.key, entry);
        }
      }
    } catch (IOException ignored) {
      entries.clear();
    }
  }

  private void save() {
    if (file == null || !ensureParent()) {
      return;
    }

    try (
      BufferedWriter writer = Files.newBufferedWriter(
        file,
        StandardCharsets.UTF_8
      )
    ) {
      for (Entry entry : entries.values()) {
        writer.write(entry.tsv());
        writer.newLine();
      }
    } catch (IOException ignored) {}
  }

  private boolean ensureParent() {
    try {
      Files.createDirectories(file.getParent());
      return true;
    } catch (IOException ignored) {
      return false;
    }
  }

  public static String fingerprint(File file) {
    if (file == null || !file.exists() || !file.isFile()) {
      return "";
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(file.getName().getBytes(StandardCharsets.UTF_8));
      digest.update(
        Long.toString(file.length()).getBytes(StandardCharsets.UTF_8)
      );
      digest.update(
        Long.toString(file.lastModified()).getBytes(StandardCharsets.UTF_8)
      );
      return hex(digest.digest());
    } catch (NoSuchAlgorithmException ignored) {
      return file.getName() + ":" + file.length() + ":" + file.lastModified();
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder out = new StringBuilder();
    for (byte b : bytes) {
      out.append(String.format("%02x", b));
    }
    return out.toString();
  }

  private static Path defaultFile() {
    return Path.of(System.getProperty("user.home"), CACHE_DIR, CACHE_FILE);
  }

  public static class Entry {

    public final String key;
    public final HybridTextExtraction.Source source;
    public final double quality;
    public final int rotation;
    public final int index;
    public final String name;
    public final double ocrConfidence;
    public final double ocrScore;

    private Entry(
      String key,
      HybridTextExtraction.Source source,
      double quality,
      int rotation,
      int index,
      String name,
      double ocrConfidence,
      double ocrScore
    ) {
      this.key = key == null ? "" : key;
      this.source = source == null ? HybridTextExtraction.Source.EMPTY : source;
      this.quality = quality;
      this.rotation = rotation;
      this.index = index;
      this.name = name == null ? "" : name;
      this.ocrConfidence = ocrConfidence;
      this.ocrScore = ocrScore;
    }

    public static Entry empty() {
      return new Entry(
        "",
        HybridTextExtraction.Source.EMPTY,
        0.0,
        Integer.MIN_VALUE,
        Integer.MIN_VALUE,
        "",
        0.0,
        0.0
      );
    }

    public boolean hasTrial() {
      return (
        rotation != Integer.MIN_VALUE &&
        index != Integer.MIN_VALUE &&
        !name.isBlank()
      );
    }

    private Entry withKey(String key) {
      return new Entry(
        key,
        source,
        quality,
        rotation,
        index,
        name,
        ocrConfidence,
        ocrScore
      );
    }

    private Entry withSource(
      HybridTextExtraction.Source source,
      double quality
    ) {
      return new Entry(
        key,
        source,
        quality,
        rotation,
        index,
        name,
        ocrConfidence,
        ocrScore
      );
    }

    private Entry withTrial(
      int rotation,
      int index,
      String name,
      double confidence,
      double score
    ) {
      if (hasTrial() && ocrConfidence > confidence) {
        return this;
      }
      return new Entry(
        key,
        source,
        quality,
        rotation,
        index,
        name,
        confidence,
        score
      );
    }

    private String tsv() {
      return String.join(
        "\t",
        VERSION,
        safe(key),
        safe(source.name()),
        Double.toString(quality),
        Integer.toString(rotation),
        Integer.toString(index),
        safe(name),
        Double.toString(ocrConfidence),
        Double.toString(ocrScore)
      );
    }

    private static Entry from(String line) {
      if (line == null || line.isBlank()) {
        return null;
      }

      String[] parts = line.split(RegexTerms.TAB, -1);
      if (parts.length < 9 || !VERSION.equals(parts[0])) {
        return null;
      }

      return new Entry(
        parts[1],
        source(parts[2]),
        decimal(parts[3]),
        integer(parts[4], Integer.MIN_VALUE),
        integer(parts[5], Integer.MIN_VALUE),
        parts[6],
        decimal(parts[7]),
        decimal(parts[8])
      );
    }

    private static HybridTextExtraction.Source source(String value) {
      try {
        return HybridTextExtraction.Source.valueOf(value);
      } catch (Exception ignored) {
        return HybridTextExtraction.Source.EMPTY;
      }
    }

    private static int integer(String value, int fallback) {
      try {
        return Integer.parseInt(value);
      } catch (Exception ignored) {
        return fallback;
      }
    }

    private static double decimal(String value) {
      try {
        return Double.parseDouble(value);
      } catch (Exception ignored) {
        return 0.0;
      }
    }

    private static String safe(String value) {
      if (value == null) {
        return "";
      }
      return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
  }
}
