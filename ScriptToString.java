import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class ScriptToString {
    public static String scriptToRead(Scanner sc) {
        System.out.println("Please enter the number that corresponds with your file type:\n[1] .txt\n[2] .pdf\n(Default is 1)");
        String choice = sc.nextLine().trim();
        String fileExtension = choice.equals("2") ? ".pdf" : ".txt";
        String fileName = "";
        while (fileName.isEmpty()) {
            System.out.println("Enter the file name of your script.\nPlease exclude the file extension:");
            fileName = sc.nextLine().trim();
        }
        File script = new File("Example-Scripts/" + fileName + fileExtension);
        if (!script.exists()) {
            System.out.println("Could not find file: " + script.getPath());
            return "";
        }
        if (fileExtension.equals(".txt")) {
            return readTxt(script);
        }
        return readPdf(script);
    }

    private static String readTxt(File script) {
        StringBuilder processedScript = new StringBuilder();
        try (Scanner fileScanner = new Scanner(script)) {
            while (fileScanner.hasNextLine()) {
                processedScript.append(fileScanner.nextLine());
                if (fileScanner.hasNextLine()) {
                    processedScript.append("\n");
                }
            }
        } catch (Exception e) {
            System.out.println("Could not read txt file: " + script.getName());
            return "";
        }
        return processedScript.toString();
    }

    private static String readPdf(File script) {
        try (PDDocument pdf = Loader.loadPDF(script)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(pdf);
            if (!(text.isEmpty()) && !(text.trim().length() < 20)) {
                return normalize(text);
            } else {
                try {
                    return ocrPdf(script);
                } catch (TesseractException e) {
                    System.out.println("OCR failed for PDF: " + script.getName());
                    System.out.println("OCR Error: " + e.getMessage());
                    return "";
                }
            }
        } catch (IOException e) {
            System.out.println("Could not read PDF: " + script.getName());
            System.out.println("Error: " + e.getMessage());
            return "";
        }
    }

    private static String ocrPdf(File script) throws TesseractException, IOException {
        try (PDDocument pdf = Loader.loadPDF(script)) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            ITesseract ocrPDFt = new Tesseract();
            ocrPDFt.setDatapath("lib");
            ocrPDFt.setLanguage("eng");

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, 300);
                String pageText = ocrSmart(img, ocrPDFt);
                sb.append(pageText);
                if (i < pdf.getNumberOfPages() - 1) {
                    sb.append("\n");
                }
            }
            return normalize(sb.toString());
        }
    }

    private static BufferedImage rotateImage(BufferedImage img, int degrees) {
        //Math declarations to determine the new height and width of rotated image
        double radians = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        int w = img.getWidth();
        int h = img.getHeight();
        int newH = (int) Math.floor(h * cos + w * sin);
        int newW = (int) Math.floor(h * sin + w * cos);

        //new buffered image for rotated img
        BufferedImage rotatedImage = new BufferedImage(newW, newH, img.getType());
        Graphics2D imageBase = rotatedImage.createGraphics();
        AffineTransform transformController = new AffineTransform();

        //transform coordinates
        transformController.translate((newW - w) / 2.0, (newH - h) / 2.0);
        transformController.rotate(radians, (double) w / 2, (double) h / 2);

        //apply to image
        imageBase.setTransform(transformController);
        imageBase.drawImage(img, 0, 0, null);
        imageBase.dispose();

        return rotatedImage;
    }

    private static double textScore(String s) {
        if (s == null) return Double.NEGATIVE_INFINITY;
        String t = s.trim();
        if (t.isEmpty()) return Double.NEGATIVE_INFINITY;
        int total = t.length();
        int letters = 0;
        int spaces = 0;
        int digits = 0;
        int weird = 0;
        for (int i = 0; i < total; i++) {
            char c = t.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
            } else if (Character.isWhitespace(c)) {
                spaces++;
            } else if (Character.isDigit(c)) {
                digits++;
            } else if (c < 32 || c == '�') {
                weird++;
            }
        }
        double letterRatio = letters * 1.0 / Math.max(1, total); //most important; 9
        double spaceRatio = spaces * 1.0 / Math.max(1, total); //medium importance; 6
        double digitRatio = digits * 1.0 / Math.max(1, total); //least important; 3
        return (letterRatio * 9.0) + (spaceRatio * 6.0) - (digitRatio * 3.0) + Math.log(Math.max(1, total)) - (weird * 0.7);
    }
    
    private static String ocrImage(BufferedImage img, ITesseract ocr) throws TesseractException {
        return ocr.doOCR(img);
    }

    private static String ocrSmart(BufferedImage img, ITesseract ocr) throws TesseractException {
        int[] rotations = {0, 90, 180, 270};
        String bestText = "";
        double best = Double.NEGATIVE_INFINITY;

        for (int deg : rotations) {
            BufferedImage candidateImg = (deg == 0) ? img : rotateImage(img, deg);
            String candidateText = processPage(candidateImg, ocr);
            double score = textScore(candidateText);
            if (score > best) {
                best = score;
                bestText = candidateText;
            }
        }

        return bestText;
    }

    private static boolean looksTwo(BufferedImage img){
        int bgSum = 0;
        int bgSamples = 0;
        for (int x = 5; x < 35 && x < img.getWidth(); x++) {
            for (int y = 5; y < 35 && y < img.getHeight(); y++) {
                bgSum += brightness(img, x, y);
                bgSamples++;
            }
        }
        if (bgSamples == 0) return false;
        int bg = bgSum / bgSamples;

        int w = img.getWidth();
        int h = img.getHeight();
        int stripW = Math.max(10, w / 40);
        int xStart = (w / 2) - (stripW / 2);
        if (xStart < 0) xStart = 0;
        int xEnd = xStart + stripW;
        if (xEnd > w) xEnd = w;

        int xStep = Math.max(2, stripW / 10);
        int yStep = Math.max(10, h / 200);
        int tolerance = 20;

        int samples = 0;
        int bgLike = 0;
        for (int y = 0; y < h; y += yStep) {
            for (int x = xStart; x < xEnd; x += xStep) {
                int pxBrightness = brightness(img, x, y);
                samples++;
                if (Math.abs(pxBrightness - bg) <= tolerance) {
                    bgLike++;
                }
            }
        }
        if (samples == 0) return false;
        double ratio = bgLike / (double) samples;
        return ratio >= 0.90;
    }
    
    private static int brightness(BufferedImage img, int x, int y){
        int pixel = img.getRGB(x, y);
        int red = (pixel >> 16) & 0xFF; 
        int green = (pixel >> 8) & 0xFF;
        int blue = (pixel >> 0) & 0xFF;
        return (int) ((0.2126*red) + (0.7152*green) + (0.0722*blue));
    }

    private static String processPage(BufferedImage img, ITesseract ocr) throws TesseractException{
        int w = img.getWidth();
        int h = img.getHeight();
        int mid = w/2;
        int gutterPad = Math.max(10, w/100);
        int rightHalfWidth = w - (mid+gutterPad);
        int leftHalfWidth = mid - gutterPad;
        int rightHalfX = mid + gutterPad;
        if (looksTwo(img) && leftHalfWidth > 0 && rightHalfWidth > 0 && rightHalfX >= 0 && rightHalfX < w){
            BufferedImage leftHalf = img.getSubimage(0, 0, leftHalfWidth, h);
            BufferedImage rightHalf = img.getSubimage(rightHalfX, 0, rightHalfWidth, h);
            String leftText = ocrImage(leftHalf, ocr);
            String rightText = ocrImage(rightHalf, ocr);
            return leftText + "\n" + rightText;
        }else{
            return ocrImage(img, ocr);
        }

    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }
}