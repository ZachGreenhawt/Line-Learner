import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;

public class ScriptStore {
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

    public static ParsedScript lineProcessor(String[] args){
        Scanner characterScan = new Scanner(System.in);
        System.out.println("Enter the name of your character: ");
        String characterName = characterScan.nextLine().trim().toUpperCase();
        List <String> myLines = new ArrayList<>();
        List <String> cueLines = new ArrayList<>();
        String currentCue = "**" + characterName + " STARTS THE SCENE**";
        boolean myTurn = false;
        String[] lines = useableScript.split("\n"); //break each line down
        for (int i = 0; i <lines.length; i++){
            String raw = lines[i]; //assign raw  to each line in lines
            String line = raw.strip(); //now we take our raw lines and we strip them of superfluous things
            if (line.isEmpty())
                continue;//continue past empty lines
            if (line.startsWith("(")){
                continue;
            }
            if (line.indexOf("(") != -1){
                String newLine = line.substring(0, line.indexOf("("));
                newLine += line.substring(line.indexOf(")", line.indexOf("(")) + 1);
                line = newLine.strip();
                if (line.isEmpty()){
                    continue;
                }
            }
            int colon = -1;
            boolean hasNext = (i + 1 < lines.length);
            String nextLine;
            if(hasNext){
                nextLine =lines[i + 1].strip();
            }  else{
               nextLine = "";
            } //Prevent out of bounds
            boolean nameOnOwnLine = (hasNext) && (raw.length() <= 15) && (line.equals(line.toUpperCase()))  && (!nextLine.isEmpty()) && (!nextLine.equals(nextLine.toUpperCase()));

            if (nameOnOwnLine) {
                colon = raw.length(); // treat the whole line as the name
            } else {
                colon = line.indexOf(":");
                if (colon == -1) {
                    colon = line.indexOf(".");
                }
            }
            boolean posChar = (colon > 0 && colon <= 15);
            if (posChar){ //if above is true
                String name = line.substring(0, colon); //the name is from 0 to the colon
                //we don't scan at the beginning of each line because some characters might have lines that flow onto the next
                boolean hasLetter = false;
                boolean caps = true; //must be all caps and must have a letter
                for (int j = 0; j < name.length(); j++){ //from 0 to the length of the name
                    char k = name.charAt(j); //character of k is whatever character we are at
                    if (Character.isLetter(k)){
                        hasLetter = true; //must be a letter
                        if (!Character.isUpperCase(k)){
                            caps = false; //if it isn't uppercase, we break because then this isn't a character name --- could be a random colon at the start of the line in an overflow line
                            break;
                        }
                    }
                }
                if (caps && hasLetter){ //if the above is true (if the word is a character name)
                    String spoken;
                    if (nameOnOwnLine) {
                        if(nextLine.indexOf("(") != 0){
                            spoken = nextLine;
                        } else{
                            String newNextLine = nextLine.substring(0, nextLine.indexOf("("));
                            newNextLine += nextLine.substring(nextLine.indexOf(")", nextLine.indexOf("(")) + 1);
                            spoken = newNextLine.strip();
                        }
                        
                    } else {
                        spoken = line.substring(colon + 1).strip();
                    }
                    myTurn = name.strip().equals(characterName); 

                    if (myTurn && !spoken.isEmpty()) {
                        cueLines.add(currentCue);
                        myLines.add(spoken);
                    } else if (!myTurn && !spoken.isEmpty()) {
                        currentCue = line;
                    }
                    if (nameOnOwnLine) {
                        i++;
                    }
                    continue;
                }
            }
            if (myTurn) {
                if (!myLines.isEmpty()) {
                    int lastIndex = myLines.size() - 1;
                    String lastLine = myLines.get(lastIndex);
                    myLines.set(lastIndex, lastLine + " " + line.strip());
                }
            } else {
                if (currentCue != null && !currentCue.isEmpty()) {
                    currentCue = currentCue + " " + line.strip();
                }
            }
        }
        return new ParsedScript(characterName, cueLines, myLines);
} 
}
