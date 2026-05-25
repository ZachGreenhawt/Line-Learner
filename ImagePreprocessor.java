import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImagePreprocessor {

  private static final Map<String, Integer> WIN_COUNTS = new LinkedHashMap<>();
  private static final Map<String, Integer> ATTEMPT_COUNTS =
    new LinkedHashMap<>();

  public static List<Candidate> candidates(BufferedImage image) {
    return candidates(image, null);
  }

  public static List<Candidate> candidates(
    BufferedImage image,
    List<Integer> requested
  ) {
    List<Candidate> out = new ArrayList<>();

    if (image == null) {
      return out;
    }

    Set<Integer> wanted = wanted(requested);

    if (wanted.contains(0)) {
      out.add(new Candidate(0, "original", image));
    }

    BufferedImage gray = null;
    BufferedImage contrast = null;
    BufferedImage otsu = null;

    if (
      wanted.contains(1) ||
      wanted.contains(2) ||
      wanted.contains(3) ||
      wanted.contains(4)
    ) {
      gray = toGrayscale(image);
    }

    if (wanted.contains(1)) {
      out.add(new Candidate(1, "grayscale", gray));
    }

    if (wanted.contains(2) || wanted.contains(3) || wanted.contains(4)) {
      contrast = boostContrast(gray, 1.35);
    }

    if (wanted.contains(2)) {
      out.add(new Candidate(2, "contrast-1.35", contrast));
    }

    if (wanted.contains(3) || wanted.contains(4)) {
      otsu = otsuThreshold(contrast);
    }

    if (wanted.contains(3)) {
      out.add(new Candidate(3, "otsu-threshold", otsu));
    }

    if (wanted.contains(4)) {
      BufferedImage denoised = removeTinyNoise(otsu);
      out.add(new Candidate(4, "otsu-denoised", denoised));
    }

    recordAttempts(out);

    return out;
  }

  private static Set<Integer> wanted(List<Integer> requested) {
    Set<Integer> wanted = new HashSet<>();

    if (requested == null || requested.isEmpty()) {
      wanted.add(0);
      wanted.add(1);
      wanted.add(2);
      wanted.add(3);
      wanted.add(4);
      return wanted;
    }

    for (Integer index : requested) {
      if (index == null) {
        continue;
      }
      if (index >= 0 && index <= 4) {
        wanted.add(index);
      }
    }

    if (wanted.isEmpty()) {
      wanted.add(0);
    }

    return wanted;
  }

  public static BufferedImage preprocess(BufferedImage image) {
    List<Candidate> options = candidates(image);
    if (options.isEmpty()) {
      return image;
    }

    return options.get(options.size() - 1).image;
  }

  public static void win(Candidate candidate) {
    if (candidate == null) {
      return;
    }

    win(candidate.name);
  }

  public static void win(String name) {
    if (name == null || name.isBlank()) {
      name = "unknown";
    }

    WIN_COUNTS.put(name, WIN_COUNTS.getOrDefault(name, 0) + 1);
  }

  public static Map<String, Integer> wins() {
    return new LinkedHashMap<>(WIN_COUNTS);
  }

  public static Map<String, Integer> attempts() {
    return new LinkedHashMap<>(ATTEMPT_COUNTS);
  }

  public static void reset() {
    WIN_COUNTS.clear();
    ATTEMPT_COUNTS.clear();
  }

  public static void printSummary() {
    System.out.println("ImagePreprocessor candidate tracking:");

    if (ATTEMPT_COUNTS.isEmpty()) {
      System.out.println("  No preprocessing candidates attempted yet.");
      return;
    }

    for (String name : ATTEMPT_COUNTS.keySet()) {
      int tried = ATTEMPT_COUNTS.getOrDefault(name, 0);
      int won = WIN_COUNTS.getOrDefault(name, 0);
      double rate = tried == 0 ? 0.0 : won / (double) tried;

      System.out.println(
        "  " +
          name +
          " | attempts=" +
          tried +
          " wins=" +
          won +
          " winRate=" +
          String.format("%.2f", rate)
      );
    }
  }

  private static void recordAttempts(List<Candidate> candidates) {
    if (candidates == null) {
      return;
    }

    for (Candidate candidate : candidates) {
      if (candidate == null) {
        continue;
      }

      String name = candidate.name == null ? "unknown" : candidate.name;

      ATTEMPT_COUNTS.put(name, ATTEMPT_COUNTS.getOrDefault(name, 0) + 1);
    }
  }

  private static BufferedImage toGrayscale(BufferedImage image) {
    BufferedImage gray = new BufferedImage(
      image.getWidth(),
      image.getHeight(),
      BufferedImage.TYPE_BYTE_GRAY
    );

    Graphics2D g2d = gray.createGraphics();
    g2d.setRenderingHint(
      RenderingHints.KEY_INTERPOLATION,
      RenderingHints.VALUE_INTERPOLATION_BICUBIC
    );
    g2d.drawImage(image, 0, 0, null);
    g2d.dispose();

    return gray;
  }

  private static BufferedImage boostContrast(
    BufferedImage image,
    double factor
  ) {
    BufferedImage output = new BufferedImage(
      image.getWidth(),
      image.getHeight(),
      BufferedImage.TYPE_BYTE_GRAY
    );

    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int b = brightness(image, x, y);
        int adjusted = clamp((int) ((b - 128) * factor + 128));
        int rgb = grayRgb(adjusted);

        output.setRGB(x, y, rgb);
      }
    }

    return output;
  }

  private static BufferedImage otsuThreshold(BufferedImage image) {
    int[] histogram = new int[256];

    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        histogram[brightness(image, x, y)]++;
      }
    }

    int threshold = otsuThresholdValue(
      histogram,
      image.getWidth() * image.getHeight()
    );

    BufferedImage output = new BufferedImage(
      image.getWidth(),
      image.getHeight(),
      BufferedImage.TYPE_BYTE_BINARY
    );

    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int b = brightness(image, x, y);
        int value = b < threshold ? 0 : 255;

        output.setRGB(x, y, grayRgb(value));
      }
    }

    return output;
  }

  private static int otsuThresholdValue(int[] histogram, int totalPixels) {
    if (histogram == null || histogram.length != 256 || totalPixels <= 0) {
      return 180;
    }

    long totalBrightness = 0;

    for (int i = 0; i < histogram.length; i++) {
      totalBrightness += (long) i * histogram[i];
    }

    long backgroundBrightness = 0;
    int backgroundWeight = 0;

    double bestVariance = -1;
    int bestThreshold = 180;

    for (int threshold = 0; threshold < 256; threshold++) {
      backgroundWeight += histogram[threshold];

      if (backgroundWeight == 0) {
        continue;
      }

      int foregroundWeight = totalPixels - backgroundWeight;

      if (foregroundWeight == 0) {
        break;
      }

      backgroundBrightness += (long) threshold * histogram[threshold];

      double backgroundMean = backgroundBrightness / (double) backgroundWeight;
      double foregroundMean =
        (totalBrightness - backgroundBrightness) / (double) foregroundWeight;
      double difference = backgroundMean - foregroundMean;

      double betweenClassVariance =
        backgroundWeight * (double) foregroundWeight * difference * difference;

      if (betweenClassVariance > bestVariance) {
        bestVariance = betweenClassVariance;
        bestThreshold = threshold;
      }
    }

    return bestThreshold;
  }

  private static BufferedImage removeTinyNoise(BufferedImage image) {
    BufferedImage output = new BufferedImage(
      image.getWidth(),
      image.getHeight(),
      BufferedImage.TYPE_BYTE_BINARY
    );

    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if (
          x == 0 ||
          y == 0 ||
          x == image.getWidth() - 1 ||
          y == image.getHeight() - 1
        ) {
          output.setRGB(x, y, image.getRGB(x, y));
          continue;
        }

        boolean dark = brightness(image, x, y) < 128;

        if (!dark) {
          output.setRGB(x, y, grayRgb(255));
          continue;
        }

        int darkNeighbors = 0;

        for (int yy = y - 1; yy <= y + 1; yy++) {
          for (int xx = x - 1; xx <= x + 1; xx++) {
            if (xx == x && yy == y) {
              continue;
            }
            if (brightness(image, xx, yy) < 128) {
              darkNeighbors++;
            }
          }
        }

        output.setRGB(x, y, grayRgb(darkNeighbors <= 1 ? 255 : 0));
      }
    }

    return output;
  }

  private static int brightness(BufferedImage image, int x, int y) {
    int rgb = image.getRGB(x, y);

    int red = (rgb >> 16) & 0xFF;
    int green = (rgb >> 8) & 0xFF;
    int blue = rgb & 0xFF;

    return (int) ((0.2126 * red) + (0.7152 * green) + (0.0722 * blue));
  }

  private static int grayRgb(int value) {
    int v = clamp(value);

    return (v << 16) | (v << 8) | v;
  }

  private static int clamp(int value) {
    return Math.max(0, Math.min(255, value));
  }

  public static class Candidate {

    public final int index;
    public final String name;
    public final BufferedImage image;

    public Candidate(int index, String name, BufferedImage image) {
      this.index = index;
      this.name = name == null ? "unknown" : name;
      this.image = image;
    }
  }
}
