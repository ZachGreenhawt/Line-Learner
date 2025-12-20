import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ScriptParser {
    public static String removeInlineP(String line){
        if (line == null) return "";
        String out = line.strip();
        while (out.startsWith("(")) {
            int close = out.indexOf(")");
            if (close == -1) {
                return "";
            }
            out = out.substring(close + 1).strip();
        }
        int open = out.indexOf("(");
        while (open != -1) {
            int close = out.indexOf(")", open);
            String before = out.substring(0, open);
            String after = "";
            if (close != -1) {
                after = out.substring(close + 1);
            }
            out = (before + after).strip();
            open = out.indexOf("(");
        }
        return out;
    }
    public static ParsedScript lineProcessor(String[] args, Settings settings){
        Scanner characterScan = new Scanner(System.in);
        System.out.println("Enter the name of your character: ");
        String characterName = characterScan.nextLine().trim().toUpperCase();
        List <String> myLines = new ArrayList<>();
        List <String> cueLines = new ArrayList<>();
        String currentCue = "**" + characterName + " STARTS THE SCENE**";
        boolean myTurn = false;
        String[] lines = ScriptToString.getUseableScript().split("\n"); //break each line down
        for (int i = 0; i <lines.length; i++){
            String raw = lines[i]; //assign raw  to each line in lines
            String line = raw.strip(); //now we take our raw lines and we strip them of superfluous things
            if (line.isEmpty())
                continue;//continue past empty lines
            if (line.startsWith("(")) {
                if (settings.includeStageDirectionsInCue() && !myTurn) {
                    currentCue = currentCue + " " + line;
                }
                continue;
            }
            if (line.indexOf("(") != -1 && !settings.includeStageDirectionsInCue()){
                line = removeInlineP(line);
                if (line.isEmpty()){
                    continue;
                }
            }
            int colon = -1;
            boolean hasNext = (i + 1 < lines.length); //is there a next line?
            String nextLine;
            if(hasNext){
                nextLine =lines[i + 1].strip(); //if yes, this is the next
            }  else{
               nextLine = ""; 
            } //Prevent out of bounds
            boolean nameOnOwnLine = (hasNext) && (line.length() <= 15) && (line.equals(line.toUpperCase()))  && (!nextLine.isEmpty()) && (!nextLine.equals(nextLine.toUpperCase()));
            if (nameOnOwnLine) {
                colon = line.length();
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
                    String spoken = "";
                    if (nameOnOwnLine) {
                        spoken = nextLine;
                    } else {
                        spoken = line.substring(colon + 1).strip();
                    }
                    spoken = removeInlineP(spoken);
                    myTurn = name.strip().equals(characterName);  //check if it is truly your turn

                    if (myTurn && !spoken.isEmpty()) { //when my turn, add the cue line, add your spoken line; ensures cueLines.size() == myLines.size()
                        cueLines.add(currentCue);
                        myLines.add(spoken);
                    } else if (!myTurn && !spoken.isEmpty()) {
                        currentCue = line; //update cue line -- if your line is after this, this line becomes your cue -- keep updating this value.
                    }
                    if (nameOnOwnLine) {
                        i++; //if the name is on the line above, move to the next line
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