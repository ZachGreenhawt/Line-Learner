public class Main {
    public static void main(String[] args) {
        Settings settings = Settings.menu();
        ScriptToString.scriptToRead(args);
        ParsedScript parsed = ScriptParser.lineProcessor(args, settings);
        PracticeSession.run(parsed, settings);
    }
}