package ocr;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Runs OCR by invoking the native {@code tesseract} command-line tool in a child
 * process, instead of binding to libtesseract in-process through Tess4J.
 *
 * <p>Tess4J calls native Tesseract/Leptonica from inside the JVM. If that native
 * code aborts (SIGABRT) it tears down the whole Java process and the failure
 * cannot be caught from Java. Shelling out isolates each OCR call in its own
 * process: a crash there fails a single region, not the parser bridge, so the
 * bridge can still return a normal result or a clean error.
 *
 * <p>This class is a drop-in for the small slice of the Tess4J API the pipeline
 * actually used: a single {@link #doOCR(BufferedImage)} call returning recognized
 * text. All downstream scoring/orientation logic only ever consumes that text.
 */
public class TesseractCli {

  /** Hard cap on a single OCR invocation; a hung child is killed and skipped. */
  private static final long TIMEOUT_SECONDS = 60;

  private static final Pattern ROTATE_PATTERN = Pattern.compile(
    "Rotate:\\s*(\\d+)"
  );
  private static final Pattern ORIENT_CONF_PATTERN = Pattern.compile(
    "Orientation confidence:\\s*([0-9]+(?:\\.[0-9]+)?)"
  );

  private final String binary;
  private final String datapath;
  private final String language;
  private final int oem;
  private final int psm;

  /**
   * @param datapath directory containing the {@code *.traineddata} files
   *                 (passed as {@code --tessdata-dir}); may be null/blank to use
   *                 the system default
   * @param language language code, e.g. {@code "eng"}
   * @param oem      OCR engine mode (1 = LSTM only)
   * @param psm      page segmentation mode (3 = fully automatic)
   */
  public TesseractCli(String datapath, String language, int oem, int psm) {
    String configured = System.getenv("TESSERACT_BIN");
    this.binary =
      configured == null || configured.isBlank()
        ? "tesseract"
        : configured.trim();
    this.datapath = datapath;
    this.language =
      language == null || language.isBlank() ? "eng" : language.trim();
    this.oem = oem;
    this.psm = psm;
  }

  /**
   * Recognizes text in {@code image}.
   *
   * @return the recognized text, or {@code ""} if the image is null or this
   *         particular OCR run failed (non-zero exit or timeout)
   * @throws IOException if the {@code tesseract} binary cannot be started at all
   *         (treated as a configuration error, not a per-image failure)
   */
  public String doOCR(BufferedImage image)
    throws IOException, InterruptedException {
    if (image == null) {
      return "";
    }

    File input = File.createTempFile("line-learner-ocr-", ".bmp");
    try {
      if (!writeForOcr(image, input)) {
        throw new IOException(
          "No image writer available to encode the OCR image"
        );
      }
      return run(input);
    } finally {
      if (!input.delete()) {
        input.deleteOnExit();
      }
    }
  }

  private static boolean writeForOcr(BufferedImage image, File file)
    throws IOException {
    if (ImageIO.write(image, "bmp", file)) {
      return true;
    }
    return ImageIO.write(image, "png", file);
  }

  public static final class TextLine {

    public final String text;
    public final int x;
    public final int y;
    public final int width;
    public final int height;

    TextLine(String text, int x, int y, int width, int height) {
      this.text = text == null ? "" : text;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }
  }

  public List<TextLine> doOCRWithLines(BufferedImage image) {
    if (image == null) {
      return new ArrayList<>();
    }

    File input = null;
    File base = null;
    File tsv = null;
    try {
      input = File.createTempFile("line-learner-tsv-", ".bmp");
      if (!writeForOcr(image, input)) {
        return new ArrayList<>();
      }
      base = File.createTempFile("line-learner-tsvout-", "");
      tsv = new File(base.getAbsolutePath() + ".tsv");

      List<String> command = new ArrayList<>();
      command.add(binary);
      command.add(input.getAbsolutePath());
      command.add(base.getAbsolutePath());
      if (datapath != null && !datapath.isBlank()) {
        command.add("--tessdata-dir");
        command.add(datapath);
      }
      command.add("-l");
      command.add(language);
      command.add("--oem");
      command.add(Integer.toString(oem));
      command.add("--psm");
      command.add(Integer.toString(psm));
      command.add("-c");
      command.add("tessedit_create_tsv=1");

      ExecResult result = exec(command);
      if (result.timedOut || result.exit != 0 || !tsv.exists()) {
        return new ArrayList<>();
      }
      String body = new String(
        java.nio.file.Files.readAllBytes(tsv.toPath()),
        StandardCharsets.UTF_8
      );
      return parseTsv(body);
    } catch (IOException | InterruptedException error) {
      return new ArrayList<>();
    } finally {
      deleteQuietly(input);
      deleteQuietly(base);
      deleteQuietly(tsv);
    }
  }

  private static void deleteQuietly(File file) {
    if (file != null && file.exists() && !file.delete()) {
      file.deleteOnExit();
    }
  }

  private static List<TextLine> parseTsv(String body) {
    List<TextLine> lines = new ArrayList<>();
    if (body == null || body.isBlank()) {
      return lines;
    }

    int[] box = null;
    StringBuilder text = new StringBuilder();

    for (String row : body.split("\n")) {
      String[] f = row.split("\t", -1);
      if (f.length < 12) {
        continue;
      }
      int level;
      try {
        level = Integer.parseInt(f[0].trim());
      } catch (NumberFormatException ignored) {
        continue; // header row
      }

      if (level <= 4) {
        flushLine(lines, box, text);
        box = null;
        text.setLength(0);
        if (level == 4) {
          box = parseBox(f);
        }
      } else if (level == 5) {
        String word = f[11];
        if (word != null && !word.isBlank()) {
          if (text.length() > 0) {
            text.append(' ');
          }
          text.append(word.trim());
        }
      }
    }
    flushLine(lines, box, text);
    return lines;
  }

  private static int[] parseBox(String[] f) {
    try {
      return new int[] {
        Integer.parseInt(f[6].trim()),
        Integer.parseInt(f[7].trim()),
        Integer.parseInt(f[8].trim()),
        Integer.parseInt(f[9].trim()),
      };
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static void flushLine(
    List<TextLine> lines,
    int[] box,
    StringBuilder text
  ) {
    if (box != null && text.length() > 0) {
      lines.add(new TextLine(text.toString(), box[0], box[1], box[2], box[3]));
    }
  }

  private String run(File input) throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    command.add(binary);
    command.add(input.getAbsolutePath());
    command.add("stdout");
    if (datapath != null && !datapath.isBlank()) {
      command.add("--tessdata-dir");
      command.add(datapath);
    }
    command.add("-l");
    command.add(language);
    command.add("--oem");
    command.add(Integer.toString(oem));
    command.add("--psm");
    command.add(Integer.toString(psm));

    ExecResult result = exec(command);
    if (result.timedOut) {
      System.out.println(
        "tesseract CLI timed out after " +
          TIMEOUT_SECONDS +
          "s; skipping this region"
      );
      return "";
    }
    if (result.exit != 0) {
      System.out.println(
        "tesseract CLI exited with status " +
          result.exit +
          (result.stderr.isBlank() ? "" : " | " + result.stderr.trim()) +
          "; skipping this region"
      );
      return "";
    }

    return result.stdout;
  }

  public Osd detectOrientation(BufferedImage image) {
    if (image == null) {
      return null;
    }

    try {
      File input = File.createTempFile("line-learner-osd-", ".bmp");
      try {
        if (!writeForOcr(image, input)) {
          return null;
        }
        return runOsd(input);
      } finally {
        if (!input.delete()) {
          input.deleteOnExit();
        }
      }
    } catch (IOException | InterruptedException error) {
      return null;
    }
  }

  private Osd runOsd(File input) throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    command.add(binary);
    command.add(input.getAbsolutePath());
    command.add("stdout");
    if (datapath != null && !datapath.isBlank()) {
      command.add("--tessdata-dir");
      command.add(datapath);
    }
    command.add("--psm");
    command.add("0");

    ExecResult result = exec(command);
    if (result.timedOut || result.exit != 0) {
      return null;
    }

    return parseOsd(result.stdout + "\n" + result.stderr);
  }

  private static Osd parseOsd(String report) {
    if (report == null || report.isBlank()) {
      return null;
    }

    Integer rotate = null;
    Double confidence = null;

    for (String line : report.split("\\R")) {
      Matcher r = ROTATE_PATTERN.matcher(line);
      if (r.find()) {
        try {
          rotate = Integer.parseInt(r.group(1));
        } catch (NumberFormatException ignored) {
          // leave rotate null
        }
      }
      Matcher c = ORIENT_CONF_PATTERN.matcher(line);
      if (c.find()) {
        try {
          confidence = Double.parseDouble(c.group(1));
        } catch (NumberFormatException ignored) {}
      }
    }

    if (rotate == null) {
      return null;
    }
    return new Osd(rotate, confidence == null ? 0.0 : confidence);
  }

  private ExecResult exec(List<String> command)
    throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder(command);

    Process process;
    try {
      process = builder.start();
    } catch (IOException error) {
      throw new IOException(
        "Could not start the tesseract CLI (looked for '" +
          binary +
          "' on PATH; set the TESSERACT_BIN environment variable to override): " +
          error.getMessage(),
        error
      );
    }

    // No stdin is needed; the image is passed as a file argument.
    process.getOutputStream().close();

    StreamCollector stdout = StreamCollector.start(process.getInputStream());
    StreamCollector stderr = StreamCollector.start(process.getErrorStream());

    boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      return new ExecResult(stdout.join(), stderr.join(), -1, true);
    }

    return new ExecResult(
      stdout.join(),
      stderr.join(),
      process.exitValue(),
      false
    );
  }

  public static final class Osd {

    public final int rotate;

    public final double confidence;

    public Osd(int rotate, double confidence) {
      this.rotate = ((rotate % 360) + 360) % 360;
      this.confidence = confidence;
    }
  }

  private static final class ExecResult {

    final String stdout;
    final String stderr;
    final int exit;
    final boolean timedOut;

    ExecResult(String stdout, String stderr, int exit, boolean timedOut) {
      this.stdout = stdout == null ? "" : stdout;
      this.stderr = stderr == null ? "" : stderr;
      this.exit = exit;
      this.timedOut = timedOut;
    }
  }

  /**
   * Drains a process stream on its own daemon thread so neither the stdout nor
   * the stderr pipe can fill up and deadlock the child.
   */
  private static final class StreamCollector {

    private final Thread thread;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private StreamCollector(InputStream stream) {
      this.thread = new Thread(() -> {
        byte[] chunk = new byte[8192];
        int read;
        try {
          while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
          }
        } catch (IOException ignored) {
          // Stream closed early (e.g. the process was killed); keep what we have.
        }
      });
      this.thread.setDaemon(true);
    }

    static StreamCollector start(InputStream stream) {
      StreamCollector collector = new StreamCollector(stream);
      collector.thread.start();
      return collector;
    }

    String join() throws InterruptedException {
      thread.join();
      return buffer.toString(StandardCharsets.UTF_8);
    }
  }
}
