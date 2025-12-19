import java.util.Scanner;
public class PracticeSession {
    public static void run(ParsedScript parsed){
        Scanner lineReader = new Scanner(System.in);
        int r = 0;
        int w = 0;
        for (int i = 0; i < parsed.size(); i++){
            System.out.println("Cue line: " + parsed.getCue(i));
            System.out.println( "[" + (i+1) + "/" + parsed.size() + "] What is your line?");
            String answer = lineReader.nextLine();
            if (answer.equals(parsed.getCharLine(i))){
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
        System.out.println("That means you had an accuracy of " + (int) (100*(r/(r+w))) + "%");
    }
}
