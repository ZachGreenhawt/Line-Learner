import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Settings settings = Settings.menu(sc);
        String scriptText = ScriptToString.scriptToRead(sc);
        if (scriptText == null || scriptText.isEmpty()) {
            System.out.println("No script loaded, exiting");
            sc.close();
            return;
        }
        ParsedScript parsed = ScriptParser.lineProcessor(scriptText, settings, sc);
        PracticeSession.run(parsed, settings, sc);
        PracticeSession.postPracticeMenu(parsed, settings, sc);
        sc.close();
    }
}