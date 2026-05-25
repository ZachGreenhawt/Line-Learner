import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class ParsedScript {
    private final String characterName;
    private final List<String> cueLines;
    private final List<String> charLines;
    private final Set<String> characterNames;
    private final List<String> stageDirectionsBefore;
    private final List<String> stageDirectionsAfter;

    public ParsedScript(String characterName, List<String> cueLines, List<String> charLines){
        this(characterName, cueLines, charLines, new HashSet<>(), null, null);
    }

    public ParsedScript(
            String characterName,
            List<String> cueLines,
            List<String> charLines,
            Set<String> characterNames,
            List<String> stageDirectionsBefore,
            List<String> stageDirectionsAfter
    ){
        if (characterName == null){
            throw new IllegalArgumentException("characterName must not be null");
        }
        if(cueLines == null || charLines == null){
            throw new IllegalArgumentException("cue/charLines must not be null");
        }
        if(cueLines.size() != charLines.size()){
            throw new IllegalArgumentException("cueLines and charLines must be of the same length");
        }

        this.characterName = normalizeText(characterName);
        this.charLines = new ArrayList<>();
        this.cueLines = new ArrayList<>();
        this.characterNames = new HashSet<>();
        this.stageDirectionsBefore = new ArrayList<>();
        this.stageDirectionsAfter = new ArrayList<>();

        if (characterNames != null) {
            for (String name : characterNames) {
                String cleanedName = normalizeText(name).toUpperCase();
                if (!cleanedName.isEmpty()) {
                    this.characterNames.add(cleanedName);
                }
            }
        }
        this.characterNames.add(this.characterName.toUpperCase());

        for (int i = 0; i < charLines.size(); i++) {
            this.charLines.add(normalizeText(charLines.get(i)));
            this.cueLines.add(normalizeText(cueLines.get(i)));

            String before = getOrBlank(stageDirectionsBefore, i);
            String after = getOrBlank(stageDirectionsAfter, i);
            this.stageDirectionsBefore.add(normalizeText(before));
            this.stageDirectionsAfter.add(normalizeText(after));
        }
    }

    private static String getOrBlank(List<String> lines, int i) {
        if (lines == null || i < 0 || i >= lines.size()) {
            return "";
        }
        return lines.get(i);
    }

    private static String normalizeText(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s+", " ");
    }

    public String getCharName(){
        return characterName;
    }

    public Set<String> getCharacterNames(){
        return new HashSet<>(characterNames);
    }

    public int size(){
        return charLines.size();
    }
    public String getCue(int i){
        if (i < 0 || i >= cueLines.size()) {
            throw new IndexOutOfBoundsException("Cue index out of range: " + i);
        }
        return cueLines.get(i);
    }
    public String getCharLine(int i){
        if (i < 0 || i >= charLines.size()) {
            throw new IndexOutOfBoundsException("Char line index out of range: " + i);
        }
        return charLines.get(i);
    }

    public String getStageDirectionsBefore(int i){
        if (i < 0 || i >= stageDirectionsBefore.size()) {
            throw new IndexOutOfBoundsException("Stage direction before index out of range: " + i);
        }
        return stageDirectionsBefore.get(i);
    }

    public String getStageDirectionsAfter(int i){
        if (i < 0 || i >= stageDirectionsAfter.size()) {
            throw new IndexOutOfBoundsException("Stage direction after index out of range: " + i);
        }
        return stageDirectionsAfter.get(i);
    }

    public boolean isConsistent() {
        return cueLines.size() == charLines.size()
                && stageDirectionsBefore.size() == charLines.size()
                && stageDirectionsAfter.size() == charLines.size();
    }
}
