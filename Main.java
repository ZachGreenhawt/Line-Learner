import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Settings settings = Settings.menu(sc);
        String scriptText = "";
        while (scriptText == null || scriptText.isEmpty()) {
            scriptText = ScriptToString.scriptToRead(sc);
            if (scriptText == null || scriptText.isEmpty()) {
                System.out.println("No script loaded. Type 'q' to quit or press Enter to try again.");
                String choice = sc.nextLine().trim();
                if (choice.equalsIgnoreCase("q")) {
                    sc.close();
                    return;
                }
            }
        }
        ParsedScript parsed = ScriptParser.lineProcessor(scriptText, settings, sc);
        PracticeSession.run(parsed, settings, sc);
        PracticeSession.postPracticeMenu(parsed, settings, sc);
        sc.close();
    }
}