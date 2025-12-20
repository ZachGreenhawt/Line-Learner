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
    public static ParsedScript lineProcessor(String scriptText, Settings settings, Scanner sc){
        System.out.println("Enter the name of your character: ");
        String characterName = sc.nextLine().trim().toUpperCase();
        List<String> myLines = new ArrayList<>();
        List<String> cueLines = new ArrayList<>();
        String currentCue = "**" + characterName + " STARTS THE SCENE**";
        boolean myTurn = false;
        String[] lines = scriptText.split("\n"); //break each line down
        for (int i = 0; i < lines.length; i++){
            String raw = lines[i];
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
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
            int colon;
            boolean hasNext = (i + 1 < lines.length);
            String nextLine = hasNext ? lines[i + 1].strip() : "";

            boolean nameOnOwnLine = (hasNext)
                    && (line.length() <= 15)
                    && (line.equals(line.toUpperCase()))
                    && (!nextLine.isEmpty())
                    && (!nextLine.equals(nextLine.toUpperCase()));

            if (nameOnOwnLine) {
                colon = line.length();
            } else {
                colon = line.indexOf(":");
                if (colon == -1) {
                    colon = line.indexOf(".");
                }
            }
            boolean posChar = (colon > 0 && colon <= 15);
            if (posChar){
                String name = line.substring(0, colon);

                boolean hasLetter = false;
                boolean caps = true;
                for (int j = 0; j < name.length(); j++){
                    char k = name.charAt(j);
                    if (Character.isLetter(k)){
                        hasLetter = true;
                        if (!Character.isUpperCase(k)){
                            caps = false;
                            break;
                        }
                    }
                }
                if (caps && hasLetter){
                    String spoken;
                    if (nameOnOwnLine) {
                        spoken = nextLine;
                    } else {
                        spoken = line.substring(colon + 1).strip();
                    }

                    spoken = removeInlineP(spoken);
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