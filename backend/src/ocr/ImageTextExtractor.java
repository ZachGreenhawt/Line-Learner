package ocr;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

public class ImageTextExtractor {

  private static final int MAX_OCR_LONG_SIDE = 2600;

  public static String extract(File image) throws Exception {
    if (image == null || !image.exists()) {
      throw new IOException("Could not find image: " + image);
    }

    BufferedImage page = readImage(image);
    if (page == null) {
      throw new IOException(
        "Could not read image (unsupported or corrupt file): " + image.getName()
      );
    }

    page = downscaleForOcr(page);

    DocumentLearningCache cache = new DocumentLearningCache();
    OcrDiagnosticsExporter.reset();
    ImagePreprocessor.reset();

    OcrRunProfile profile = new OcrRunProfile(cache.preferredTrial(image));
    TesseractCli tesseract = PdfTextExtractor.makeTesseract();

    System.out.println("Processing image " + image.getName());

    String pageText = PdfTextExtractor.ocrOnePage(
      image,
      page,
      1,
      tesseract,
      profile,
      cache
    );

    OcrDiagnosticsExporter.printLocation();
    ImagePreprocessor.printSummary();
    System.out.println(profile.summary());

    String text = PdfTextExtractor.cleanPages(List.of(pageText));

    cache.remember(
      image,
      HybridTextExtraction.Source.OCR_TEXT,
      TextExtractionQualityScorer.score(text).score
    );

    return text;
  }

  private static BufferedImage readImage(File image) {
    try {
      return ImageIO.read(image);
    } catch (IOException e) {
      System.out.println(
        "ImageIO could not read " + image.getName() + ": " + e.getMessage()
      );
      return null;
    }
  }

  private static BufferedImage downscaleForOcr(BufferedImage image) {
    if (image == null) {
      return null;
    }

    int width = image.getWidth();
    int height = image.getHeight();
    int longSide = Math.max(width, height);

    if (longSide <= MAX_OCR_LONG_SIDE) {
      return image;
    }

    double scale = MAX_OCR_LONG_SIDE / (double) longSide;
    int newWidth = Math.max(1, (int) Math.round(width * scale));
    int newHeight = Math.max(1, (int) Math.round(height * scale));

    BufferedImage scaled = new BufferedImage(
      newWidth,
      newHeight,
      BufferedImage.TYPE_INT_RGB
    );

    Graphics2D graphics = scaled.createGraphics();
    graphics.setRenderingHint(
      RenderingHints.KEY_INTERPOLATION,
      RenderingHints.VALUE_INTERPOLATION_BICUBIC
    );
    graphics.setRenderingHint(
      RenderingHints.KEY_RENDERING,
      RenderingHints.VALUE_RENDER_QUALITY
    );
    graphics.setRenderingHint(
      RenderingHints.KEY_ANTIALIASING,
      RenderingHints.VALUE_ANTIALIAS_ON
    );
    graphics.drawImage(image, 0, 0, newWidth, newHeight, null);
    graphics.dispose();

    System.out.println(
      "Downscaled photo for OCR: " +
        width +
        "x" +
        height +
        " -> " +
        newWidth +
        "x" +
        newHeight
    );

    return scaled;
  }
}
