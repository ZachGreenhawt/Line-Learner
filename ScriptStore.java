import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;

public class ScriptStore {
    public static String useableScript = "";
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

    public static void lineCounter(String[] args){
        Scanner characterScan = new Scanner(System.in);
        System.out.println("Enter the name of your character: ");
        String characterName = characterScan.nextLine().trim().toUpperCase();
        List <String> myLines = new ArrayList<>();
        int lineCount = 0;
        boolean myTurn = false;

        String[] lines = useableScript.split("\n"); //break each line down
        for (String raw : lines){ //assign raw  to each line in lines
            String line = raw.strip(); //now we take our raw lines and we strip them of superfluous things
            if (line.isEmpty())
                continue;//continue past empty lines
            if (line.startsWith("(")){
                myTurn = false;
                continue;
            }
            int colon = line.indexOf(":");//find the colons for each line
            boolean posChar = (colon > 0 && colon <= 15);//possible character must not be after the colon and must be less than or greater than 15 away from :
            if (posChar){ //if above is true
                String name = line.substring(0, colon); //the name is from 0 to the colon
                //we don't scan at the beginning of each line because some characters might have lines that flow onto the next
                boolean hasLetter = false;
                boolean caps = true; //must be all caps and must have a letter
                for (int i = 0; i < name.length(); i++){ //from 0 to the length of the name
                    char k = name.charAt(i); //character of k is whatever character we are at
                    if (Character.isLetter(k)){
                        hasLetter = true; //must be a letter
                        if (!Character.isUpperCase(k)){
                            caps = false; //if it isn't uppercase, we break because then this isn't a character name --- could be a random colon at the start of the line in an overflow line
                            break;
                        }
                    }
                }
                if (caps && hasLetter){ //if the above is true (if the word is a character name)
                    myTurn = name.strip().equals(characterName); //then check and see if it is the character name, myTurn is true if it = characterName
                    String spoken = line.substring(colon + 1).strip(); //spoken line is the substring after the colon, stripped of superfluous things
                    if (myTurn && !spoken.isEmpty()){ //if it is my turn, and there is a line
                        lineCount++; //add to the running count
                        myLines.add(spoken); //add the lines to my lines then add a break
                    }
                    continue;
                }
            }
            if (myTurn){//this executes once it is not possibly a character --- this means it is a continuation of the last line, if it is your turn, keep printing it out as part of the line.
                int lastIndex = myLines.size() - 1;
                String lastLine = myLines.getLast();
                myLines.set(lastIndex,lastLine + " " + line.strip()); //append to last line
            }
        }
        System.out.println(characterName + " has " + lineCount + " lines.\nHere they are:");
        for(String myLine : myLines){
            System.out.println(myLine);
    }
}
}
