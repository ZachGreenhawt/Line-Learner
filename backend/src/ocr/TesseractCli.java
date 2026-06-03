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

    File input = File.createTempFile("line-learner-ocr-", ".png");
    try {
      if (!ImageIO.write(image, "png", input)) {
        throw new IOException(
          "No PNG writer available to encode the OCR image"
        );
      }
      return run(input);
    } finally {
      if (!input.delete()) {
        input.deleteOnExit();
      }
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
      stdout.join();
      stderr.join();
      System.out.println(
        "tesseract CLI timed out after " +
          TIMEOUT_SECONDS +
          "s; skipping this region"
      );
      return "";
    }

    String text = stdout.join();
    String errors = stderr.join();
    int exit = process.exitValue();

    if (exit != 0) {
      System.out.println(
        "tesseract CLI exited with status " +
          exit +
          (errors.isBlank() ? "" : " | " + errors.trim()) +
          "; skipping this region"
      );
      return "";
    }

    return text;
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
