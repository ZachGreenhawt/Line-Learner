package ocr;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class PageRegionExtractor {

  private static final int[] GEOMETRY_ROTATIONS = { 0, 90, 180, 270 };

  public static List<BufferedImage> regions(BufferedImage image) {
    List<BufferedImage> out = new ArrayList<>();

    if (image == null) {
      return out;
    }

    OrientedGeometry oriented = orient(image);
    Geometry geometry = oriented.geometry;
    BufferedImage page = oriented.image;

    geometry.printDebug();

    if (!geometry.shouldSplitIntoColumns()) {
      out.add(cropToInk(page));
      return out;
    }

    int width = page.getWidth();
    int height = page.getHeight();

    int gutterPadding = Math.max(12, width / 120);
    int outerPaddingX = Math.max(8, width / 150);
    int outerPaddingY = Math.max(8, height / 120);

    int leftStart = 0;
    int leftEnd = Math.max(1, geometry.gutterX - gutterPadding);

    int rightStart = Math.min(width - 1, geometry.gutterX + gutterPadding);
    int rightEnd = width;

    List<RegionCandidate> halves = new ArrayList<>();

    halves.add(
      new RegionCandidate(
        padded(
          page,
          leftStart,
          0,
          leftEnd,
          height,
          outerPaddingX,
          outerPaddingY
        ),
        "left",
        leftStart,
        leftEnd,
        0
      )
    );

    halves.add(
      new RegionCandidate(
        padded(
          page,
          rightStart,
          0,
          rightEnd,
          height,
          outerPaddingX,
          outerPaddingY
        ),
        "right",
        rightStart,
        rightEnd,
        1
      )
    );

    halves.sort(PageRegionExtractor::compareRegionOrder);

    for (RegionCandidate region : halves) {
      BufferedImage cropped = cropToInk(region.image);
      if (cropped != null) {
        out.add(cropped);
      }
    }

    System.out.println(
      String.format(
        "PageRegionExtractor: detected likely side-by-side OCR columns; rotation=%d deg splitX=%d paddingX=%d paddingY=%d order=%s->%s spread=%s columns=%s",
        oriented.rotation,
        geometry.gutterX,
        outerPaddingX,
        outerPaddingY,
        halves.get(0).side,
        halves.get(1).side,
        geometry.likelySpread,
        geometry.likelyColumns
      )
    );

    return out;
  }

  private static BufferedImage padded(
    BufferedImage image,
    int startX,
    int startY,
    int endX,
    int endY,
    int paddingX,
    int paddingY
  ) {
    int x = clamp(startX - paddingX, 0, image.getWidth() - 1);
    int y = clamp(startY - paddingY, 0, image.getHeight() - 1);
    int endXSafe = clamp(endX + paddingX, x + 1, image.getWidth());
    int endYSafe = clamp(endY + paddingY, y + 1, image.getHeight());

    int width = Math.max(1, endXSafe - x);
    int height = Math.max(1, endYSafe - y);

    return image.getSubimage(x, y, width, height);
  }

  private static BufferedImage cropToInk(BufferedImage image) {
    if (image == null) {
      return null;
    }

    InkBounds bounds = inkBounds(image);

    if (!bounds.hasInk()) {
      return image;
    }

    int width = image.getWidth();
    int height = image.getHeight();

    int horizontalPadding = Math.max(24, width / 35);
    int verticalPadding = Math.max(24, height / 45);

    int x = clamp(bounds.minX - horizontalPadding, 0, width - 1);
    int y = clamp(bounds.minY - verticalPadding, 0, height - 1);
    int endX = clamp(bounds.maxX + horizontalPadding, x + 1, width);
    int endY = clamp(bounds.maxY + verticalPadding, y + 1, height);

    BufferedImage cropped = image.getSubimage(
      x,
      y,
      Math.max(1, endX - x),
      Math.max(1, endY - y)
    );

    System.out.println(
      String.format(
        "PageRegionExtractor: ink bounds crop start=(%d,%d) end=(%d,%d) original=%dx%d cropped=%dx%d",
        x,
        y,
        endX,
        endY,
        width,
        height,
        cropped.getWidth(),
        cropped.getHeight()
      )
    );

    return cropped;
  }

  private static InkBounds inkBounds(BufferedImage image) {
    InkBounds bounds = new InkBounds(image.getWidth(), image.getHeight());

    int width = image.getWidth();
    int height = image.getHeight();

    int xStep = Math.max(1, width / 900);
    int yStep = Math.max(1, height / 1200);

    for (int y = 0; y < height; y += yStep) {
      for (int x = 0; x < width; x += xStep) {
        if (ink(image, x, y)) {
          bounds.include(x, y);
        }
      }
    }

    return bounds;
  }

  private static boolean ink(BufferedImage image, int x, int y) {
    int rgb = image.getRGB(x, y);
    Color color = new Color(rgb);

    int red = color.getRed();
    int green = color.getGreen();
    int blue = color.getBlue();

    int max = Math.max(red, Math.max(green, blue));
    int min = Math.min(red, Math.min(green, blue));
    int light = brightness(image, x, y);
    int colorSpread = max - min;

    return light < 225 || colorSpread > 35;
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static OrientedGeometry orient(BufferedImage image) {
    OrientedGeometry original = null;
    OrientedGeometry bestSpread = null;

    for (int rotation : GEOMETRY_ROTATIONS) {
      BufferedImage rotated = rotate(image, rotation);
      Geometry geometry = analyze(rotated, rotation);

      OrientedGeometry candidate = new OrientedGeometry(
        rotated,
        geometry,
        rotation
      );

      if (rotation == 0) {
        original = candidate;
      }

      if (!geometry.shouldSplitIntoColumns()) {
        continue;
      }

      if (
        bestSpread == null ||
        geometry.columnScore > bestSpread.geometry.columnScore
      ) {
        bestSpread = candidate;
      }
    }

    if (bestSpread != null) {
      return bestSpread;
    }

    return original == null
      ? new OrientedGeometry(image, analyze(image, 0), 0)
      : original;
  }

  private static Geometry analyze(BufferedImage image, int rotation) {
    int width = image.getWidth();
    int height = image.getHeight();

    double aspect = width / (double) Math.max(1, height);
    boolean landscapeLike = aspect > 1.15;

    int gutterX = gutterX(image);
    double centerOffset = Math.abs(gutterX - (width / 2.0)) / width;

    double gutterInk = verticalInk(image, gutterX, Math.max(8, width / 150));

    double leftInk = regionInk(
      image,
      width / 8,
      width / 3,
      height / 8,
      (height * 3) / 4
    );

    double rightInk = regionInk(
      image,
      (width * 2) / 3,
      (width * 7) / 8,
      height / 8,
      (height * 3) / 4
    );

    boolean gutterNearCenter = centerOffset <= 0.12;
    boolean hasTextOnBothSides = leftInk > 0.01 && rightInk > 0.01;
    boolean gutterMostlyBlank = gutterInk < Math.min(leftInk, rightInk) * 0.45;

    boolean likelySpread =
      landscapeLike &&
      gutterNearCenter &&
      hasTextOnBothSides &&
      gutterMostlyBlank;

    boolean portraitOrPageColumns = aspect > 0.65 && aspect <= 1.15;
    boolean likelyColumns =
      portraitOrPageColumns &&
      gutterNearCenter &&
      hasTextOnBothSides &&
      gutterMostlyBlank;

    double columnScore = 0.0;

    if (landscapeLike) {
      columnScore += 40.0;
    }
    if (portraitOrPageColumns) {
      columnScore += 25.0;
    }
    if (gutterNearCenter) {
      columnScore += 20.0;
    }
    if (hasTextOnBothSides) {
      columnScore += 30.0;
    }
    if (gutterMostlyBlank) {
      columnScore += 30.0;
    }

    columnScore += Math.min(leftInk, rightInk) * 200.0;
    columnScore -= gutterInk * 300.0;
    columnScore -= centerOffset * 100.0;

    return new Geometry(
      width,
      height,
      aspect,
      landscapeLike,
      likelySpread,
      likelyColumns,
      gutterX,
      gutterInk,
      leftInk,
      rightInk,
      rotation,
      columnScore
    );
  }

  private static int gutterX(BufferedImage image) {
    int width = image.getWidth();

    int searchStart = (width * 35) / 100;
    int searchEnd = (width * 65) / 100;
    int stripHalfWidth = Math.max(6, width / 250);

    int bestX = width / 2;
    double bestInk = Double.MAX_VALUE;

    for (int x = searchStart; x <= searchEnd; x += Math.max(1, width / 300)) {
      double ink = verticalInk(image, x, stripHalfWidth);

      if (ink < bestInk) {
        bestInk = ink;
        bestX = x;
      }
    }

    return bestX;
  }

  private static double verticalInk(
    BufferedImage image,
    int centerX,
    int halfWidth
  ) {
    int width = image.getWidth();
    int height = image.getHeight();

    int startX = Math.max(0, centerX - halfWidth);
    int endX = Math.min(width - 1, centerX + halfWidth);

    return regionInk(image, startX, endX, height / 12, (height * 11) / 12);
  }

  private static double regionInk(
    BufferedImage image,
    int startX,
    int endX,
    int startY,
    int endY
  ) {
    int samples = 0;
    int ink = 0;

    int x = Math.max(0, Math.min(startX, image.getWidth() - 1));
    int endXSafe = Math.max(0, Math.min(endX, image.getWidth()));
    int y = Math.max(0, Math.min(startY, image.getHeight() - 1));
    int endYSafe = Math.max(0, Math.min(endY, image.getHeight()));

    if (endXSafe <= x || endYSafe <= y) {
      return 0.0;
    }

    int xStep = Math.max(1, (endXSafe - x) / 80);
    int yStep = Math.max(1, (endYSafe - y) / 160);

    for (int row = y; row < endYSafe; row += yStep) {
      for (int col = x; col < endXSafe; col += xStep) {
        samples++;

        if (brightness(image, col, row) < 210) {
          ink++;
        }
      }
    }

    if (samples == 0) {
      return 0.0;
    }

    return ink / (double) samples;
  }

  private static int brightness(BufferedImage image, int x, int y) {
    int rgb = image.getRGB(x, y);

    int red = (rgb >> 16) & 0xFF;
    int green = (rgb >> 8) & 0xFF;
    int blue = rgb & 0xFF;

    return (int) ((0.2126 * red) + (0.7152 * green) + (0.0722 * blue));
  }

  private static BufferedImage rotate(BufferedImage image, int degrees) {
    if (degrees == 0) {
      return image;
    }

    double radians = Math.toRadians(degrees);

    int oldWidth = image.getWidth();
    int oldHeight = image.getHeight();

    int newWidth = (degrees == 90 || degrees == 270) ? oldHeight : oldWidth;

    int newHeight = (degrees == 90 || degrees == 270) ? oldWidth : oldHeight;

    int imageType =
      image.getType() == BufferedImage.TYPE_CUSTOM
        ? BufferedImage.TYPE_INT_RGB
        : image.getType();

    BufferedImage rotated = new BufferedImage(newWidth, newHeight, imageType);

    Graphics2D graphics = rotated.createGraphics();

    AffineTransform transform = new AffineTransform();

    transform.translate(newWidth / 2.0, newHeight / 2.0);
    transform.rotate(radians);
    transform.translate(-oldWidth / 2.0, -oldHeight / 2.0);

    graphics.drawImage(image, transform, null);
    graphics.dispose();

    return rotated;
  }

  private static class InkBounds {

    int minX;
    int minY;
    int maxX;
    int maxY;

    InkBounds(int width, int height) {
      this.minX = width;
      this.minY = height;
      this.maxX = -1;
      this.maxY = -1;
    }

    void include(int x, int y) {
      minX = Math.min(minX, x);
      minY = Math.min(minY, y);
      maxX = Math.max(maxX, x + 1);
      maxY = Math.max(maxY, y + 1);
    }

    boolean hasInk() {
      return maxX >= minX && maxY >= minY;
    }
  }

  private static class RegionCandidate {

    final BufferedImage image;
    final String side;
    final int startX;
    final int readingOrder;

    RegionCandidate(
      BufferedImage image,
      String side,
      int startX,
      int endX,
      int readingOrder
    ) {
      this.image = image;
      this.side = side == null ? "unknown" : side;
      this.startX = startX;
      this.readingOrder = readingOrder;
    }
  }

  private static int compareRegionOrder(
    RegionCandidate first,
    RegionCandidate second
  ) {
    if (first == null && second == null) {
      return 0;
    }
    if (first == null) {
      return 1;
    }
    if (second == null) {
      return -1;
    }

    int order = Integer.compare(first.readingOrder, second.readingOrder);
    if (order != 0) {
      return order;
    }

    int x = Integer.compare(first.startX, second.startX);
    if (x != 0) {
      return x;
    }

    return first.side.compareTo(second.side);
  }

  private static class OrientedGeometry {

    final BufferedImage image;
    final Geometry geometry;
    final int rotation;

    OrientedGeometry(BufferedImage image, Geometry geometry, int rotation) {
      this.image = image;
      this.geometry = geometry;
      this.rotation = rotation;
    }
  }

  private static class Geometry {

    final int width;
    final int height;
    final double aspectRatio;

    final boolean landscapeLike;
    final boolean likelySpread;
    final boolean likelyColumns;

    final int gutterX;
    final double gutterInk;
    final double leftInk;
    final double rightInk;

    final int rotation;
    final double columnScore;

    Geometry(
      int width,
      int height,
      double aspectRatio,
      boolean landscapeLike,
      boolean likelySpread,
      boolean likelyColumns,
      int gutterX,
      double gutterInk,
      double leftInk,
      double rightInk,
      int rotation,
      double columnScore
    ) {
      this.width = width;
      this.height = height;
      this.aspectRatio = aspectRatio;
      this.landscapeLike = landscapeLike;
      this.likelySpread = likelySpread;
      this.likelyColumns = likelyColumns;
      this.gutterX = gutterX;
      this.gutterInk = gutterInk;
      this.leftInk = leftInk;
      this.rightInk = rightInk;
      this.rotation = rotation;
      this.columnScore = columnScore;
    }

    boolean shouldSplitIntoColumns() {
      return likelySpread || likelyColumns;
    }

    void printDebug() {
      System.out.println(
        String.format(
          "Page geometry | rotation=%d deg size=%dx%d aspect=%.2f landscape=%s spread=%s columns=%s columnScore=%.2f gutterX=%d gutterInk=%.4f leftInk=%.4f rightInk=%.4f",
          rotation,
          width,
          height,
          aspectRatio,
          landscapeLike,
          likelySpread,
          likelyColumns,
          columnScore,
          gutterX,
          gutterInk,
          leftInk,
          rightInk
        )
      );
    }
  }
}
