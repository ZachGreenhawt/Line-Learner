package ocr;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import parser.detect.StageHints;

public class VisualStageScorer {

  private static final int INK = 200;
  private static final double SLANT_STRONG = 0.18;
  private static final double SLANT_WEAK = 0.12;
  private static final double SLANT_UNIFORM_MIN = 0.10;
  private static final int INDENT_STRONG_PX = 60;
  private static final double LIGHT_DELTA = 12.0;
  private static final int MIN_KEY_LEN = 8;
  private static final int MIN_INK_PIXELS = 120;

  private static final Set<String> keys = new HashSet<>();
  private static boolean scanDominant = false;

  public static void reset() {
    keys.clear();
    scanDominant = false;
  }

  public static void setScanDominant(boolean value) {
    scanDominant = value;
  }

  public static Set<String> keys() {
    Set<String> out = new HashSet<>(keys);
    if (scanDominant && !keys.isEmpty()) {
      out.add(StageHints.VISUAL_AUTHORITATIVE);
    }
    return out;
  }

  public static void scorePage(BufferedImage page, TesseractCli tess) {
    if (page == null || tess == null) {
      return;
    }
    List<TesseractCli.TextLine> lines = tess.doOCRWithLines(page);
    if (lines == null || lines.isEmpty()) {
      return;
    }

    int w = page.getWidth();
    int h = page.getHeight();
    int[][] gray = grayscale(page, w, h);

    List<Integer> lefts = new ArrayList<>();
    List<Double> lights = new ArrayList<>();
    List<double[]> measured = new ArrayList<>();
    List<String> lineKeys = new ArrayList<>();

    for (TesseractCli.TextLine ln : lines) {
      String key = StageHints.key(ln.text);
      int x0 = clamp(ln.x, 0, w - 1);
      int y0 = clamp(ln.y, 0, h - 1);
      int x1 = clamp(ln.x + ln.width, x0 + 1, w);
      int y1 = clamp(ln.y + ln.height, y0 + 1, h);
      double[] li = lightnessAndInk(gray, x0, y0, x1, y1);
      double light = li[0];
      int ink = (int) li[1];

      double slant = 0;
      double uniform = 0;
      if (ink >= MIN_INK_PIXELS) {
        slant = slant(gray, x0, y0, x1, y1);
        int mid = x0 + (x1 - x0) / 2;
        double left = slant(gray, x0, y0, mid, y1);
        double right = slant(gray, mid, y0, x1, y1);
        uniform = Math.min(left, right);
      }

      lineKeys.add(key);
      measured.add(
        new double[] { slant, x0, light, key.length(), ink, uniform }
      );
      if (ink >= MIN_INK_PIXELS) {
        lefts.add(x0);
        lights.add(light);
      }
    }

    int bodyLeft = mode(lefts, 15);
    double medianLight = median(lights);

    for (int i = 0; i < measured.size(); i++) {
      double[] m = measured.get(i);
      double slant = m[0];
      int leftX = (int) m[1];
      double light = m[2];
      int keyLen = (int) m[3];
      int ink = (int) m[4];
      double uniform = m[5];
      if (keyLen < MIN_KEY_LEN || ink < MIN_INK_PIXELS) {
        continue;
      }
      if (slant < SLANT_WEAK) {
        continue;
      }
      if (uniform < SLANT_UNIFORM_MIN) {
        continue;
      }
      boolean strongSlant = slant >= SLANT_STRONG;
      boolean indented = (leftX - bodyLeft) >= INDENT_STRONG_PX;
      boolean lighter = (light - medianLight) >= LIGHT_DELTA;
      if (strongSlant || indented || lighter) {
        keys.add(lineKeys.get(i));
      }
    }
  }

  private static int[][] grayscale(BufferedImage img, int w, int h) {
    int[][] gray = new int[h][w];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int rgb = img.getRGB(x, y);
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        gray[y][x] = (int) (0.299 * r + 0.587 * g + 0.114 * b);
      }
    }
    return gray;
  }

  private static double[] lightnessAndInk(
    int[][] g,
    int x0,
    int y0,
    int x1,
    int y1
  ) {
    long sum = 0;
    long n = 0;
    for (int y = y0; y < y1; y++) {
      for (int x = x0; x < x1; x++) {
        if (g[y][x] < INK) {
          sum += g[y][x];
          n++;
        }
      }
    }
    return new double[] { n == 0 ? 255 : (double) sum / n, n };
  }

  private static double slant(int[][] g, int x0, int y0, int x1, int y1) {
    int h = y1 - y0;
    if (h < 6) {
      return 0;
    }

    int[] rowInk = new int[h];
    int peak = 0;
    for (int yy = 0; yy < h; yy++) {
      int c = 0;
      for (int x = x0; x < x1; x++) {
        if (g[y0 + yy][x] < INK) {
          c++;
        }
      }
      rowInk[yy] = c;
      peak = Math.max(peak, c);
    }
    int thr = (int) (peak * 0.45);
    int cy0 = -1;
    int cy1 = -1;
    for (int yy = 0; yy < h; yy++) {
      if (rowInk[yy] >= thr) {
        if (cy0 < 0) {
          cy0 = yy;
        }
        cy1 = yy;
      }
    }
    if (cy0 < 0 || cy1 - cy0 < 4) {
      cy0 = 0;
      cy1 = h - 1;
    }

    List<int[]> px = new ArrayList<>();
    for (int yy = cy0; yy <= cy1; yy++) {
      for (int x = x0; x < x1; x++) {
        if (g[y0 + yy][x] < INK) {
          px.add(new int[] { x - x0, yy - cy0 });
        }
      }
    }
    if (px.size() < 40) {
      return 0;
    }
    int width = x1 - x0;
    int ch = cy1 - cy0 + 1;
    double best = 0;
    double bestSharp = -1;
    int span = width + (int) (ch * 0.5) + 2;
    for (double sh = -0.5; sh <= 0.5001; sh += 0.02) {
      int[] hist = new int[span + 2];
      for (int[] p : px) {
        int col = (int) (p[0] + p[1] * sh);
        if (col >= 0 && col < hist.length) {
          hist[col]++;
        }
      }
      double sharp = 0;
      for (int c : hist) {
        sharp += (double) c * c;
      }
      if (sharp > bestSharp) {
        bestSharp = sharp;
        best = sh;
      }
    }
    return best;
  }

  private static int clamp(int v, int lo, int hi) {
    return v < lo ? lo : (v > hi ? hi : v);
  }

  private static int mode(List<Integer> xs, int bucket) {
    if (xs.isEmpty()) {
      return 0;
    }
    java.util.Map<Integer, Integer> m = new java.util.HashMap<>();
    for (int x : xs) {
      m.merge(x / bucket, 1, Integer::sum);
    }
    int bestBucket = 0;
    int bestCount = -1;
    for (java.util.Map.Entry<Integer, Integer> e : m.entrySet()) {
      if (e.getValue() > bestCount) {
        bestCount = e.getValue();
        bestBucket = e.getKey();
      }
    }
    return bestBucket * bucket;
  }

  private static double median(List<Double> xs) {
    if (xs.isEmpty()) {
      return 255;
    }
    List<Double> s = new ArrayList<>(xs);
    java.util.Collections.sort(s);
    return s.get(s.size() / 2);
  }
}
