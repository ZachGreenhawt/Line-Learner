import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
public class PracticeSession {
    //Set the lists we use for replay
    private static List <String> wrongLines = new ArrayList<>();
    private static List <String> wrongCues = new ArrayList<>();
    private static List <String> wrongLines2 = new ArrayList<>();
    private static List <String> wrongCues2 = new ArrayList<>();
    public static void run(ParsedScript parsed, Settings settings, Scanner sc){
        //Clear lists at start of replay loop so we can start tracking agin
        wrongLines.clear();
        wrongCues.clear();
        wrongLines2.clear();
        wrongCues2.clear();
        //Prompt for settings
        
        //Import settings
        boolean caseSensitive = settings.caseSensitive();
        boolean punctuation = settings.punctuation();
        boolean timed = settings.timedMode();
        //Counters for right/wrong
        int r = 0;
        int w = 0;
        //Initialize timer
        long sessionStartMs = timed ? System.currentTimeMillis() : 0;
        //For each line
        for (int i = 0; i < parsed.size(); i++){
            //Timer
            long lineStartMs = timed ? System.currentTimeMillis() : 0;
            //Show cue line
            System.out.println("Cue line: " + parsed.getCue(i));
            System.out.println( "[" + (i+1) + "/" + parsed.size() + "] What is your line?");
            //Ask for line input
            String answer = sc.nextLine();
            if (timed) { //Timer math
                long lineElapsedMs = System.currentTimeMillis() - lineStartMs;
                System.out.println("Time for this line: " + (lineElapsedMs / 1000.0) + "s");
            }
            String expectedOg = parsed.getCharLine(i);
            String expected = expectedOg;
            //Apply settings to line processing
            answer = caseSensitive ? answer : answer.toLowerCase();
            expected = caseSensitive ? expected : expected.toLowerCase();
            answer = punctuation ? answer : answer.replaceAll("\\p{Punct}", "").strip();
            expected = punctuation ? expected : expected.replaceAll("\\p{Punct}", "").strip();
            if (answer.equals(expected)){
                r++; //Add to the right count
                System.out.println("You are correct!");
            }
            else{
                //Add to wrong count
                w++;
                System.out.println("You are wrong!");
                System.out.println("The correct line was: " + parsed.getCharLine(i));
                //Add to wrong lines and cues
                wrongCues.add(parsed.getCue(i));
                wrongLines.add(expectedOg);
            }
        }
        //Show session stats
        System.out.println("You got " + r + " lines correct!");
        System.out.println("You got " + w + " lines wrong!");
        System.out.println("That means you had an accuracy of " + (int) (100 * ((double) r / (r + w))) + "%");
        if (timed) {
            long sessionElapsedMs = System.currentTimeMillis() - sessionStartMs;
            System.out.println("Total session time: " + (sessionElapsedMs / 1000.0) + "s");
        }
    }
    public static void retryWrong(Settings settings, Scanner sc){
        //Import settings
        boolean caseSensitive = settings.caseSensitive();
        boolean punctuation = settings.punctuation();
        boolean timed = settings.timedMode();
        //Right and Wrong counters
        int r = 0;
        int w = 0;
        //Timer
        long sessionStartMs = timed ? System.currentTimeMillis() : 0;
        for (int i = 0; i < wrongLines.size(); i++){ //For each line in Wrong Lines
            long lineStartMs = timed ? System.currentTimeMillis() : 0;
            System.out.println("Cue line: " + wrongCues.get(i));
            System.out.println( "[" + (i+1) + "/" + wrongLines.size() + "] What is your line?");
            String answer = sc.nextLine();
            if (timed) {
                long lineElapsedMs = System.currentTimeMillis() - lineStartMs;
                System.out.println("Time for this line: " + (lineElapsedMs / 1000.0) + "s");
            }
            String expectedOriginal = wrongLines.get(i);
            String expected = expectedOriginal;
            answer = caseSensitive ? answer : answer.toLowerCase();
            expected = caseSensitive ? expected : expected.toLowerCase();
            answer = punctuation ? answer : answer.replaceAll("\\p{Punct}", "").strip();
            expected = punctuation ? expected : expected.replaceAll("\\p{Punct}", "").strip();
            if (answer.equals(expected)){
                r++;
                System.out.println("You are correct!");
            }
            else{
                w++;
                System.out.println("You are wrong!");
                System.out.println("The correct line was: " + wrongLines.get(i));
                wrongCues2.add(wrongCues.get(i));
                wrongLines2.add(expectedOriginal);
            }
        }
        System.out.println("You got " + r + " lines correct!");
        System.out.println("You got " + w + " lines wrong!");
        System.out.println("That means you had an accuracy of " + (int) (100 * ((double) r / (r + w))) + "%");
        if (timed) {
            long sessionElapsedMs = System.currentTimeMillis() - sessionStartMs;
            System.out.println("Total session time: " + (sessionElapsedMs / 1000.0) + "s");
        }
        //Clear previous lists
        wrongLines.clear();
        wrongCues.clear();
        //Add new lists to previous lists
        wrongLines.addAll(wrongLines2);
        wrongCues.addAll(wrongCues2);
        //Clear new lists
        wrongLines2.clear();
        wrongCues2.clear();
    }
    public static void postPracticeMenu(ParsedScript parsed, Settings settings, Scanner sc) {
        while (true) {
            System.out.println("Would you like to: [1] Try again, [2] Retry only missed lines, or [3] Quit?");
            String postChoice = sc.nextLine().trim();
            if (postChoice.equals("1")) {
                run(parsed, settings, sc);
            } else if (postChoice.equals("2")) {
                if (wrongLines.isEmpty()) {
                    System.out.println("No missed lines to retry.");
                } else {
                    retryWrong(settings, sc);
                }
            } else if (postChoice.equals("3")) {
                return;
            } else {
                System.out.println("Please type 1, 2, or 3.");
            }
        }
    }
}
