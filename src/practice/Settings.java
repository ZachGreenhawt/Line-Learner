import java.util.Scanner;

public class Settings {

    private final boolean includeStageDirectionsInCue;
    private final boolean caseSensitive;
    private final boolean Punctuation;
    private final boolean timedMode;
    

    public Settings(boolean includeStageDirectionsInCue, boolean caseSensitive, boolean Punctuation, boolean timedMode) {
        this.includeStageDirectionsInCue = includeStageDirectionsInCue;
        this.caseSensitive = caseSensitive;
        this.Punctuation = Punctuation;
        this.timedMode = timedMode;
    }

    public static Settings menu(Scanner sc) {

        System.out.println("Please Select Your Settings:");

        //Stage direction setting
        System.out.println("Include stage directions in cue lines? (yes/no)");
        String stageDirInput = sc.nextLine().trim();
        boolean includeStageDirectionsInCue = stageDirInput.equalsIgnoreCase("yes") ? true : false;

        //Case sensitivity setting
        System.out.println("Case sensitivity: type 1 for case-insensitive, 2 for case-sensitive");
        String caseSensitivityInput = sc.nextLine().trim();
        boolean caseSensitive = caseSensitivityInput.equals("2") ? true : false;

        //Punctuation setting
        System.out.println("Keep punctuation in responses? (yes/no)");
        String punctuationInput = sc.nextLine().trim();
        boolean Punctuation = punctuationInput.equalsIgnoreCase("yes") ? true : false;

        //Timer setting
        System.out.println("Timed mode? (yes/no)");
        String timerInput = sc.nextLine().trim();
        boolean timedMode = timerInput.equalsIgnoreCase("yes") ? true : false;

        return new Settings(includeStageDirectionsInCue, caseSensitive, Punctuation, timedMode);
    }

    public boolean includeStageDirectionsInCue() {
        return includeStageDirectionsInCue;
    }

    public boolean caseSensitive() {
        return caseSensitive;
    }

    public boolean timedMode() {
        return timedMode;
    }
    public boolean punctuation() {
        return Punctuation;
    }
}
