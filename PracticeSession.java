import java.util.Scanner;
public class PracticeSession {
    public static void run(ParsedScript parsed, Settings settings){
        boolean caseSensitive = settings.caseSensitive();
        boolean punctuation = settings.punctuation();
        Scanner lineReader = new Scanner(System.in);
        int r = 0;
        int w = 0;
        long sessionStartMs = System.currentTimeMillis();
        for (int i = 0; i < parsed.size(); i++){
            long lineStartMs = System.currentTimeMillis();
            System.out.println("Cue line: " + parsed.getCue(i));
            System.out.println( "[" + (i+1) + "/" + parsed.size() + "] What is your line?");
            String answer = lineReader.nextLine();
            long lineElapsedMs = System.currentTimeMillis() - lineStartMs;
            System.out.println("Time for this line: " + (lineElapsedMs / 1000.0) + "s");
            String expected = parsed.getCharLine(i);
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
                System.out.println("The correct line was: " + parsed.getCharLine(i));
            }
        }
        System.out.println("You got " + r + " lines correct!");
        System.out.println("You got " + w + " lines wrong!");
        System.out.println("That means you had an accuracy of " + (int) (100 * ((double) r / (r + w))) + "%");
        long sessionElapsedMs = System.currentTimeMillis() - sessionStartMs;
        System.out.println("Total session time: " + (sessionElapsedMs / 1000.0) + "s");
    }
}
