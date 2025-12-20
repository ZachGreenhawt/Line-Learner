import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class ScriptToString {
private static String useableScript = "";
    public static String scriptToRead(String[] args) {
        Scanner input = new Scanner(System.in); //new scanner for input
        StringBuilder processedScript = new StringBuilder();//init processedScript; string builder
        System.out.println("Enter the file name of your script.\nPlease exclude the file extension (it must be type .txt): ");
        String fileName = input.nextLine().trim(); //take file name
        File script = new File("Example-Scripts/"+fileName + ".txt"); //this is the script utilizing the name
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
        useableScript = processedScript.toString();
        return useableScript; //return to string
    }
public static String getUseableScript(){
    return useableScript;
}
}