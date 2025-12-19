public class Main {
    public static void main(String[] args) {
        ScriptStore.scriptToRead(args);
        ParsedScript parsed = ScriptStore.lineProcessor(args);
        System.out.println(parsed.getCharName()+ " has " + parsed.size() + " lines.");
        for (int i = 0; i < parsed.size(); i++){
            System.out.println( "Cue line: ");
            System.out.println(parsed.getCue(i));
            System.out.println(parsed.getCharName() + "'s line " + (i+1) + ":");
            System.out.println(parsed.getCharLine(i));
        }
    }
}
