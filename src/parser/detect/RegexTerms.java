package parser.detect;

public final class RegexTerms {

  public static final String PUBLICATION_OR_FURNITURE =
    "isbn|copyright|all rights|permission|publisher|published|publishing|" +
    "press|catalogue|cataloging|manufactured|book design|cover art|" +
    "cover design|directed by|produced by|commissioned by|premiere|" +
    "licensed|license|licence|royalty|royalties|street|avenue|road|" +
    "lane|drive|boulevard|suite|floor|building|city|state|country|" +
    "website|www\\.|\\.com|\\.org|\\.net";

  public static final String STAGE_TAIL_ACTION =
    "gives|give|goes|go|enters|enter|exits|exit|crosses|cross|turns|" +
    "turn|looks|look|takes|take|puts|put|pulls|pull|pushes|push|opens|" +
    "open|closes|close|starts|start|stops|stop|moves|move|walks|rises|" +
    "rise|sits|sit|stands|stand|shakes|shake|begins|begin";

  public static final String CHARACTER_BAD_FURNITURE =
    "FOOTNOTE|COPYRIGHT|ISBN|PUBLISHER|PUBLISHED|PERMISSION|LICENSE|LICENCE";

  public static final String CHARACTER_DIALOGUE_PRONOUN =
    "I|I'LL|I'M|I'D|ME|MY|MINE|YOU|YOU'LL|YOU'RE|YOUR|YOURS|WE|WE'LL|" +
    "WE'RE|OUR|OURS";

  public static final String CHARACTER_DIALOGUE_VERB =
    "IS|ARE|WAS|WERE|BE|BEEN|BEING|AM|WILL|WOULD|SHOULD|COULD|CAN|" +
    "CAN'T|NEED|NEEDS|WANT|WANTS|NOTICE|MET|PLAYED|HITS|CAME|CALL|CALLS";

  public static final String CHARACTER_STAGE_ACTION =
    "ENTERS|ENTER|EXITS|EXIT|GOES|GO|CROSSES|CROSS|TURNS|TURN|LOOKS|" +
    "LOOK|WATCHES|WATCH|KISSES|KISS|SLAPS|SLAP|SCREAMS|SCREAM|SHAKES|" +
    "SHAKE|OPENS|OPEN|DRAWS|DRAW|RISES|RISE|SITS|SIT|STANDS|STAND|" +
    "WALKS|WALK|MOVES|MOVE|TAKES|TAKE|PUTS|PUT|GIVES|GIVE|BEGINS|" +
    "BEGIN|APPRECIATES|APPRECIATE|TOLLS|TOLL";

  public static final String CHARACTER_NAME_CONNECTOR =
    "AND|ON|IN|TO|FROM|WITH|AT|BY|BUT";

  public static final String CHARACTER_ROLE_PHRASE_HEAD =
    "LAWYER|ATTORNEY|VOICE|VOICES|MAN|WOMAN|BOY|GIRL|MOTHER|FATHER|" +
    "SON|DAUGHTER|CHILD|CLERK|GUARD|NURSE|DOCTOR|REPORTER|JUDGE|" +
    "PRIEST|OFFICER|DETECTIVE|COLONEL|CAPTAIN|SERGEANT|TEACHER|" +
    "PROFESSOR|WAITER|WAITRESS|BELLBOY|JANITOR|MAID|SERVANT|KING|" +
    "QUEEN|PRINCE|PRINCESS|LORD|LADY|ANNOUNCER|NARRATOR|HUSBAND|WIFE";

  public static final String AUTHOR_PUBLISHER_TERM =
    "PREFACE|FOREWORD|INTRODUCTION|CREDITS|CAST|CHARACTERS|COPYRIGHT|" +
    "ISBN|PUBLISHER|PUBLISHED|PUBLICATION|SERVICE|PRESS|THEATRE|THEATER|" +
    "COMPANY|AGENCY|LICENSE|LICENCE|RIGHTS|PERMISSION|CATALOGUE|" +
    "CATALOGING|MANUFACTURED|DESIGN|DIRECTOR|DIRECTED|PRODUCED|PREMIERE|" +
    "ARTISTIC|EXECUTIVE|BROADWAY|TRIBUNE|VARIETY|WORLD|MAGAZINE|JOURNAL|" +
    "REVIEW|REVIEWS|PRAISE";

  public static final String AUTHOR_PUBLISHER_PHRASE =
    "A PLAY BY|PLAY BY|BOOK DESIGN|COVER ART|COVER DESIGN|ALL RIGHTS|" +
    "NO PROFESSIONAL|NONPROFESSIONAL|WRITTEN PERMISSION|ORIGINALLY PRODUCED|" +
    "FIRST PUBLISHED|ADAPTED BY|WRITTEN BY|BASED ON|STORY BY|MUSIC BY|" +
    "LYRICS BY";

  public static final String CHARACTER_FUNCTION_ROLE =
    "VOICE|VOICES|OFFSTAGE|ONSTAGE|FIRST|SECOND|THIRD|FOURTH|FIFTH|" +
    "SIXTH|SEVENTH|EIGHTH|NINTH|TENTH|ONE|TWO|THREE|FOUR|FIVE|SIX|" +
    "SEVEN|EIGHT|NINE|TEN|YOUNG|OLD|OLDER|LITTLE|BIG|SMALL|TALL|" +
    "SHORT|LEFT|RIGHT|LEAD|HEAD|ASSISTANT|DEPUTY|CHIEF|FOREMAN|WORKER|" +
    "CUSTOMER|STRANGER|VISITOR|NEIGHBOR|NEIGHBOUR|PASSERBY|PASSER-BY|" +
    "PERSON|SOMEONE|SOMEBODY|ANYBODY|EVERYBODY|WITNESS|SPECTATOR|" +
    "SPECTATORS";

  public static final String STREET_ADDRESS_TERM =
    "STREET|ST|AVENUE|AVE|ROAD|RD|LANE|LN|DRIVE|DR|BOULEVARD|BLVD|" +
    "COURT|CT|PLACE|PL|SQUARE|SQ|BUILDING|FLOOR|SUITE";

  public static final String COUNTRY_OR_REGION_TERM =
    "CITY|STATE|COUNTRY|UNITED STATES|UNITED KINGDOM|CANADA|ENGLAND";

  public static final String CHARACTER_ROLE_WORD =
    "GIRL|BOY|MAN|WOMAN|MOTHER|FATHER|SON|DAUGHTER|CHILD|BABY|FETUS|" +
    "FOETUS|VOICE|VOICES|CLERK|JUDGE|PRIEST|LAWYER|ATTORNEY|REPORTER|" +
    "GUARD|MATRON|HUSBAND|WIFE|COLONEL|CAPTAIN|SERGEANT|DOCTOR|NURSE|" +
    "OFFICER|INSPECTOR|DETECTIVE|PROFESSOR|TEACHER|STUDENT|WAITER|" +
    "WAITRESS|BELLBOY|JANITOR|MAID|SERVANT|KING|QUEEN|PRINCE|PRINCESS|" +
    "DUKE|DUCHESS|LORD|LADY|FIRST|SECOND|THIRD|FOURTH|YOUNG|OLD|OLDER|" +
    "ELDERLY|CHORUS|ENSEMBLE|CROWD|GROUP|OFFSTAGE|ANNOUNCER|NARRATOR|" +
    "ADDING|FILING|TELEPHONE|DEFENSE|DEFENCE|PROSECUTION|BARBER|LOVER|" +
    "HUCKSTER|SPECTATOR|SPECTATORS|JUROR|JURY|WITNESS|POLICEMAN|POLICE|" +
    "ATTENDANT|STRANGER|CUSTOMER|WORKER|SECRETARY|STENOGRAPHER|OPERATOR|" +
    "MESSENGER|BAILIFF|USHER|CLERK";

  public static final String SPEAKER_ARTICLE_HEADING_PATTERN =
    "(?i)^(A|AN|THE)\\s+[A-Z][A-Z0-9 .'’\\-]{1,50}$";

  public static final String SPEAKER_SLASH_HEADING_PATTERN =
    "[A-Z][A-Z0-9 .'’\\-]+(?:\\s*/\\s*[A-Z][A-Z0-9 .'’\\-]+)+";

  public static final String SPEAKER_BARE_STAGE_START =
    "enter|exit|exeunt|re-enter|reenter";

  public static final String SPEAKER_ENTRANCE_CUE =
    "enter|enters|exit|exits|re enter|re enters|re-enter|re-enters|" +
    "reenter|reenters";

  public static final String SPEAKER_INSIDE_STAGE_ACTION =
    "enters?|exits?|crosses|looks at|watches|follows|touches|kisses|" +
    "helps|leads|brings|takes|puts|lays|hands to|gives to|speaks to|" +
    "talks to|turns to|goes to|comes to";

  public static final String CHARACTER_ROLE_PHRASE_PATTERN =
    "^(" +
    CHARACTER_ROLE_PHRASE_HEAD +
    ")(\\s+(FOR|OF|AT|IN)\\s+[A-Z0-9'’\\-]+){1,3}$";

  public static final String SPEAKER_BARE_STAGE_START_PATTERN =
    "^(?i)(" + SPEAKER_BARE_STAGE_START + ")\\b.*";

  public static final String SPEAKER_ENTRANCE_CUE_PATTERN =
    ".*\\b(" + SPEAKER_ENTRANCE_CUE + ")$";

  public static final String SPEAKER_INSIDE_STAGE_PATTERN =
    ".*\\b(" + SPEAKER_INSIDE_STAGE_ACTION + ")\\s+$";

  public static final String PUBLICATION_OR_FURNITURE_PATTERN = containsAnyWord(
    PUBLICATION_OR_FURNITURE
  );

  private RegexTerms() {}

  public static String containsAnyWord(String alternates) {
    return ".*\\b(" + alternates + ")\\b.*";
  }

  public static boolean containsPublicationOrFurniture(String text) {
    return text != null && text.matches(PUBLICATION_OR_FURNITURE_PATTERN);
  }
}
