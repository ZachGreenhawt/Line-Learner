public class Main {
    public static void main(String[] args) {
        ScriptStore.scriptToRead(args);
        ParsedScript parsed = ScriptStore.lineProcessor(args);
        PracticeSession.run(parsed);
    }
}
