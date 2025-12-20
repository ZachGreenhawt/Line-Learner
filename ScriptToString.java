import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class ScriptToString {
    public static String scriptToRead(Scanner sc) {
        StringBuilder processedScript = new StringBuilder();//init processedScript; string builder
        System.out.println("Please enter to number that corresponds with your file extension:\n[1] .txt\n*note that the default is .txt; if there is an error, program will default to .txt*");
        String fileExtension = sc.nextLine();
        if(fileExtension.equals("1")){
            fileExtension = ".txt";
        } else{
            fileExtension = ".txt";
        }
        System.out.println("Enter the file name of your script.\nPlease exclude the file extension: ");
        String fileName = sc.nextLine().trim(); //take file name
        File script = new File("Example-Scripts/"+ fileName + fileExtension); //this is the script utilizing the name
        try (Scanner fileScanner = new Scanner(script)) {
            while (fileScanner.hasNextLine()) {
                processedScript.append(fileScanner.nextLine());//take the scan of the script, append it
                if (fileScanner.hasNextLine()) {//if there is a next line, append \n for a next line
                    processedScript.append("\n");
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not find file: " + script.getName());
            return "";
        }
        return processedScript.toString();
    }
}