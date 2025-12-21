import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

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
            return text.replace("\r\n", "\n").replace("\r", "\n");
        } catch (IOException e) {
            System.out.println("Could not read PDF: " + script.getName());
            System.out.println("Error: " + e.getMessage());
            return "";
            
        }
    }
}