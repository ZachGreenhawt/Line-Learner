import java.util.List;
import java.util.ArrayList;

public class ParsedScript {
    private final String characterName;
    private final List<String> cueLines;
    private final List<String> charLines;

    public ParsedScript(String characterName, List<String> cueLines, List<String> charLines){
        if (characterName == null){
            throw new IllegalArgumentException("characterName must not be null");
        }
        if(cueLines == null || charLines == null){
            throw new IllegalArgumentException("cue/charLines must not be null");
        }
        if(cueLines.size() != charLines.size()){
            throw new IllegalArgumentException("cuelines and charLines must be of the same length");
        }
        this.characterName = characterName;
        this.charLines = new ArrayList<>(charLines);
        this.cueLines = new ArrayList<>(cueLines);
    }
    public String getCharName(){
        return characterName;
    }
    public int size(){
        return charLines.size();
    }
    public String getCue(int i){
        return cueLines.get(i);
    }
    public String getCharLine(int i){
        return charLines.get(i);
    }
}
