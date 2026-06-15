package util;

public final class RegexTerms {

  public static final String PUBLICATION_OR_FURNITURE =
    "isbn|copyright|all rights|permission|publisher|published|publishing|" +
    "press|catalogue|cataloging|manufactured|book design|cover art|" +
    "cover design|directed by|produced by|commissioned by|premiere|" +
    "licensed|license|licence|royalty|royalties|street|avenue|road|" +
    "lane|drive|boulevard|suite|floor|building|city|state|country|" +
    "website|www\\.|\\.com|\\.org|\\.net";

  // Stage action verbs

  public static final String STAGE_ACTION =
    "gives|give|goes|go|enters|enter|exits|exit|crosses|cross|turns|" +
    "turn|looks|look|takes|take|puts|put|pulls|pull|pushes|push|opens|" +
    "open|closes|close|starts|start|stops|stop|moves|move|walks|walk|" +
    "rises|rise|sits|sit|stands|stand|shakes|shake|begins|begin|watches|" +
    "watch|kisses|kiss|slaps|slap|screams|scream|draws|draw|appreciates|" +
    "appreciate|tolls|toll";

  public static final String CHARACTER_BAD_FURNITURE =
    "FOOTNOTE|COPYRIGHT|ISBN|PUBLISHER|PUBLISHED|PERMISSION|LICENSE|LICENCE";

  public static final String CHARACTER_DIALOGUE_PRONOUN =
    "I|I'LL|I'M|I'D|ME|MY|MINE|YOU|YOU'LL|YOU'RE|YOUR|YOURS|WE|WE'LL|" +
    "WE'RE|OUR|OURS";

  public static final String CHARACTER_DIALOGUE_VERB =
    "IS|ARE|WAS|WERE|BE|BEEN|BEING|AM|WILL|WOULD|SHOULD|COULD|CAN|" +
    "CAN'T|NEED|NEEDS|WANT|WANTS|NOTICE|MET|PLAYED|HITS|CAME|CALL|CALLS";

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

  // Character role nouns

  public static final String ROLE_WORD =
    "GIRL|BOY|MAN|WOMAN|MOTHER|FATHER|SON|DAUGHTER|CHILD|BABY|FETUS|" +
    "FOETUS|VOICE|VOICES|CLERK|JUDGE|PRIEST|LAWYER|ATTORNEY|REPORTER|" +
    "GUARD|MATRON|HUSBAND|WIFE|COLONEL|CAPTAIN|SERGEANT|DOCTOR|NURSE|" +
    "OFFICER|INSPECTOR|DETECTIVE|PROFESSOR|TEACHER|STUDENT|WAITER|" +
    "WAITRESS|BELLBOY|JANITOR|MAID|SERVANT|KING|QUEEN|PRINCE|PRINCESS|" +
    "DUKE|DUCHESS|LORD|LADY|FIRST|SECOND|THIRD|FOURTH|YOUNG|OLD|OLDER|" +
    "ELDERLY|CHORUS|ENSEMBLE|CROWD|GROUP|OFFSTAGE|ANNOUNCER|NARRATOR|" +
    "ADDING|FILING|TELEPHONE|DEFENSE|PROSECUTION|BARBER|LOVER|" +
    "HUCKSTER|SPECTATOR|SPECTATORS|JUROR|JURY|WITNESS|POLICEMAN|POLICE|" +
    "ATTENDANT|STRANGER|CUSTOMER|WORKER|SECRETARY|STENOGRAPHER|OPERATOR|" +
    "MESSENGER|BAILIFF|USHER|PERSON|SOMEONE|SOMEBODY|" +
    "PLAYER|PERFORMER|TYPIST|BYSTANDER";

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

  // Normalization

  public static final String WHITESPACE = "\\s+";
  public static final String CASE_INSENSITIVE_FLAG = "(?i)";
  public static final String PUNCTUATION_CLASS = "\\p{Punct}";
  public static final String WHITESPACE_AROUND_SLASH = "\\s*/\\s*";
  public static final String INVISIBLE_CHARS = "[\\u200B\\u200C\\u200D\\uFEFF]";
  public static final String ZERO_WIDTH_CHARS =
    "[\\u200B\\u200C\\u200D\\uFEFF\\u00AD]";

  // Shared numeric / page-number fragments

  public static final String PAGE_NUMBER_ONLY = "^\\d{1,4}$";
  public static final String CONTAINS_PAGE_NUMBER = ".*\\d{1,4}.*";
  public static final String CONTAINS_DIGIT = ".*\\d.*";
  public static final String LINE_BREAK = "\\R";
  public static final String NEWLINE_CHAR = "\n";
  public static final String NEWLINE_RUN = "\\n{3,}";
  public static final String EQUALS = "=";

  // Session-name sanitizing (CsvExporter, ParserSessionStore)
  public static final String EXTENSION_SUFFIX = "\\.[^.]+$";
  public static final String NON_SESSION_NAME_CHAR = "[^A-Za-z0-9._-]";

  // CLI helpers (ScriptLoader, ParserPrompts, CharacterSetup, CsvExporter)
  public static final String HYPHEN_LINE_WRAP =
    "(?m)([A-Za-z]{2,})-\\h*\\n\\h*([a-z]{2,})";
  public static final String CAPS_NAME_PART = "[A-Z][A-Z .'-]*";
  public static final String CAPS_ALNUM_NAME = "[A-Z][A-Z0-9 .'-]+";
  public static final String NON_ALNUM_UPPER_QUOTE = "[^A-Z0-9']";
  public static final String DIGITS_ONLY = "[0-9]+";
  public static final String SLASH_SEPARATOR = "\\s+/\\s+";

  // Structural lines (shared: PageFurnitureDetector, ScriptPreProcess)
  // Case-insensitive so a single constant serves both lowercased and raw input.
  public static final String STRUCTURAL_CHARACTERS = "(?i)^characters?:?.*$";
  public static final String STRUCTURAL_SOUNDS = "(?i)^sounds?:?.*$";
  public static final String STRUCTURAL_SCENE = "(?i)^scene:.*$";
  public static final String STRUCTURAL_AT_RISE = "(?i)^at rise\\b.*$";
  public static final String STRUCTURAL_AT_THE_RISE = "^at the rise\\b.*$";
  public static final String STRUCTURAL_BEFORE_CURTAIN =
    "^before the curtain\\b.*$";
  public static final String STRUCTURAL_EPISODE =
    "^episode\\s+[a-z0-9ivx -]+.*$|^moment\\s*:.*$";
  public static final String STRUCTURAL_ACT = "^act\\s+[a-z0-9ivx -]+.*$";
  public static final String SCREENPLAY_SCENE_HEADING =
    "(?i)^(INT|EXT|I/E|INT/EXT|EST)\\.?\\s+.+$";
  public static final String SCREENPLAY_TRANSITION =
    "(?i)^(FADE IN|FADE OUT|CUT TO|DISSOLVE TO|BACK TO|SMASH CUT TO|MATCH CUT TO)\\b.*$";
  public static final String STRUCTURAL_LIGHTS = "^lights?\\b.*$";
  public static final String STRUCTURAL_BLACKOUT = "^blackout\\b.*$";
  public static final String STRUCTURAL_CURTAIN = "^curtain\\b.*$";

  // Page-marker tags emitted by the OCR layer (LogicalLineBuilder, ScriptPreProcess)
  public static final String PAGE_MARKER_PARSED_TEXT =
    "^<\\s*PARSED TEXT FOR PAGE:.*>$";
  public static final String PAGE_MARKER_REGION =
    "^<\\s*REGION\\s+\\d+\\s+OF\\s+\\d+.*>$";
  public static final String PAGE_MARKER_IMAGE = "^<\\s*IMAGE FOR PAGE:.*>$";

  // LogicalLineBuilder

  public static final String REPLACEMENT_CHAR = "\\uFFFD";
  public static final String BROKEN_FFFE_CHARS = "[￾￿]";
  public static final String HEADING_WITH_TRAILING_PAGE_NUMBER =
    "^([A-Z][A-Z0-9'’\\- ]{1,45}(?:\\s*/\\s*[A-Z][A-Z0-9'’\\- ]{1,45})?\\.)\\s*\\d{1,4}$";
  public static final String PUNCTUATION_RUN_ONLY =
    "^[|\\[\\]{}()_\\-–—=+~`'\".,:;\\s]{2,}$";
  public static final String ENDS_WITH_SENTENCE_PUNCTUATION =
    ".*[.!?;:][^.!?;:]*$";
  public static final String NUMBER_SUFFIX_FRAGMENT = "^\\d{1,3}[.:]?$";
  public static final String SLASH_SPEAKER_LINE =
    "^[A-Z0-9 ./'’\\-]+\\s*/\\s*[A-Z0-9 ./'’\\-]+\\.?$";

  // Name fragments
  public static final String COMMA = ",";
  public static final String NEWLINE = "\\n";
  public static final String SLASH = "/";
  public static final String NON_LETTER = "[^A-Za-z]";
  public static final String CONTAINS_UPPERCASE = ".*[A-Z].*";
  public static final String CONTAINS_LOWERCASE = ".*[a-z].*";
  public static final String CONTAINS_SMALL_NUMBER = ".*\\b\\d{1,3}\\b.*";
  public static final String TITLE_CASE_WORD = "[A-Z][a-zA-Z'’.-]+";
  public static final String ALL_CAPS_WORD = "[A-Z][A-Z'’\\-]{1,}";
  public static final String CAPS_RUN_3 = "[A-Z]{3,}";
  public static final String ROLE_HEADING_SHAPE = "[A-Za-z][A-Za-z0-9 .'’\\-]*";

  // CharacterExtractor

  public static final String ACTOR_SUFFIX = "(?i)\\s+ACTOR\\s+\\d+\\s*$";
  public static final String ARTICLE_PREFIXED_NAME =
    "(?i)^(A|AN|THE)\\s+[A-Z][A-Za-z0-9 .'’\\-]{1,50}$";
  public static final String ORDINAL_PREFIXED_NAME =
    "(?i)^(FIRST|SECOND|THIRD|FOURTH|FIFTH|SIXTH|SEVENTH|EIGHTH|NINTH|TENTH)\\s+[A-Z][A-Za-z0-9 .'’\\-]{1,50}$";
  public static final String NUMBER_WORD_SUFFIX_NAME =
    "(?i)^[A-Z][A-Za-z0-9 .'’\\-]{1,50}\\s+(ONE|TWO|THREE|FOUR|FIVE|SIX|SEVEN|EIGHT|NINE|TEN)$";
  public static final String DIRECTION_PLACE =
    ".*\\b(NEW|OLD|NORTH|SOUTH|EAST|WEST)\\s+[A-Z]{3,}\\b.*";
  public static final String ALL_CAPS_ONE_WORD = "^[A-Z]+$";
  public static final String ALL_CAPS_TWO_WORDS = "^[A-Z]+\\s+[A-Z]+$";
  public static final String ALL_CAPS_THREE_WORDS =
    "^[A-Z]+\\s+[A-Z]+\\s+[A-Z]+$";
  public static final String NON_ALNUM_UPPER = "[^A-Z0-9]";

  // Shared dialogue
  public static final String STARTS_WITH_QUOTE = "^[\"'“‘].*";
  public static final String LEADING_CONJUNCTION =
    "^(and|but|or|so|because|then)\\b.*";
  public static final String ENTER_EXIT_LINE =
    "^(enter|exit|exeunt|re-enter|reenter)\\b.*";
  public static final String CONTAINS_NUMBER_RUN = ".*\\d+.*";

  // SpeakerBlockBuilder

  public static final String SPOKEN_ADDRESS_REJECT =
    "^[A-Z][A-Z0-9'’ -]{1,40}\\.\\s*$";
  public static final String SPOKEN_ADDRESS_SHAPE =
    "^[A-Z][a-zA-Z'’\\-]{1,30}\\.\\s+\\S+.*";
  public static final String SHORT_ACTION_BEAT =
    "^(he|she|they|we|[a-z][a-z'’\\-]+(?:\\s+[a-z][a-z'’\\-]+){0,3})\\s+" +
    "(nods|waves|shrugs|smiles|laughs|cries|sighs|sits|stands|rises|turns|crosses|goes|comes|enters|exits|walks|runs|takes|puts|gets|holds|touches|kisses|whispers|stares|looks|pulls|pushes|opens|closes|eats|sips|shows|hands|helps|starts|stops|moves|throws|reads|watches|waits|points|kneels|falls|backs|leans|reaches|begins)\\b.*";
  public static final String STAGE_HEAVY_SUBJECT =
    "\\b(he|she|they|his|her|their|him|them)\\b";
  public static final String STAGE_HEAVY_ACTION =
    "\\b(looks|turns|puts|takes|opens|closes|kisses|touches|goes|comes|crosses|moves|walks|sits|stands|rises|falls|holds|pulls|pushes|reaches|leans|stares|watches|throws|carries|brings|lifts|lowers|releases|fastens|closes|clutching|spreads)\\b";
  public static final String DIALOGUE_CONJUNCTION_PRONOUN =
    "^(and|but|or|because|so)\\b.*\\b(i|i'm|i’ll|i'll|i've|you|you're|we|we're|don't|can't|won't|would|could|should|please|yes|no)\\b.*";
  public static final String DIALOGUE_PRONOUN =
    ".*\\b(i|i'm|i’ll|i'll|i've|you|you're|we|we're|don't|can't|won't|would|could|should|please|yes|no)\\b.*";
  public static final String CUE_INITIAL_DOT = ".*\\b[A-Z]\\.\\b.*";
  public static final String CUE_DASH_PATTERN =
    ".*\\b[A-Z][a-zA-Z'’ -]{1,30}\\s*[-–—]\\s*[A-Z0-9]\\b.*";
  public static final String CONTAINS_LETTER_WORD =
    ".*\\b[A-Za-z][A-Za-z'’]+\\b.*";
  public static final String LEADING_NUMBER_CAPS_LINE =
    "^\\d{1,4}\\s+[A-Z][A-Z'’ -]+$";
  public static final String NARRATIVE_NUMBER =
    "^[a-z][a-z'’\\-]{1,30}\\s+number\\s+[a-z0-9ivxlcdm]+\\.?$";
  public static final String NARRATIVE_TIME =
    ".*\\b(years ago|months ago|weeks ago|days ago|morning|afternoon|evening|night|story|remember|once|before|after|when|while|until)\\b.*";

  // FrontMatterDetector

  public static final String TITLE_CASE_LINE =
    "^[A-Z][A-Za-z'’\\-]+(?:\\s+[A-Z][A-Za-z'’\\-]+){1,8}$";
  public static final String FRONT_PUBLICATION_TERM =
    "isbn|copyright|all rights|permission|publisher|published|publishing|" +
    "press|catalogue|cataloging|manufactured|book design|cover art|" +
    "cover design|directed by|produced by|commissioned by|artistic director|" +
    "executive director|premiere|licensed|license|licence|royalty|royalties";
  public static final String FRONT_PLACE_TERM =
    "street|avenue|road|lane|drive|boulevard|suite|floor|building|city|" +
    "state|country|united states|united kingdom|canada|england";
  public static final String WEB_REF_TERM = "www\\.|\\.com|\\.org|\\.net|@";
  public static final String REVIEW_ATTRIBUTION_PAIR =
    "^[-–—~]?\\s*[a-z .'-]+,\\s*[a-z .'-]+$";
  public static final String REVIEW_ATTRIBUTION_OUTLET =
    "^[-–—~]?\\s*[a-z .'-]+\\s+(review|times|tribune|post|journal|magazine|world|weekly|daily|sun|star|news).*$";
  public static final String EPISODE_HEADING = "^EPISODE\\s+[A-Z0-9IVX -]+$";
  public static final String ACT_HEADING = "^ACT\\s+[A-Z0-9IVX -]+$";
  public static final String SCENE_HEADING = "^SCENE\\s+[A-Z0-9IVX -]+$";
  public static final String CAST_PERSON_NAME =
    "^[A-Z][a-z]+(?:\\s+[A-Z][a-zA-Z'’.-]+){0,4}\\.?$";
  public static final String CAST_CREDIT_TERM =
    "actor|actress|played by|director|understudy|voice of";
  public static final String CHARACTER_DESCRIPTION_TERM =
    "husband|wife|daughter|son|father|mother|brother|sister|child|children|" +
    "narrator|voice|pregnant|doesn't speak|does not speak|years old|" +
    "year old|inside|grown up|played by|appears as|also plays|described as";
  public static final String CREDIT_TERM =
    "directed by|artistic director|executive director|cover art|cover design|" +
    "book design|published|publisher|copyright|isbn|premiere|produced by|" +
    "commissioned by";

  // SpeakerDetector

  public static final String TRAILING_DOT_COLON = "[.:,]+$";
  public static final String CONTAINS_SENTENCE_PUNCT = ".*[?!;].*";
  public static final String SPEAKER_NAME_SHAPE =
    "[A-Za-z][A-Za-z0-9 .'’\\-/]*";
  public static final String NON_LOWER_SPACE_DASH = "[^a-z -]";
  public static final String NON_LOWER_SPACE_QUOTE_DASH = "[^a-z '-]";

  // SpeakerHeadingIndex

  public static final String HEADING_DIALOGUE_REMAINDER =
    "i|i'm|i'll|i've|you|you're|we|we're|they|he|she|it|don't|can't|won't|" +
    "would|could|should|want|know|think|feel|love|hate|need|mean|remember|" +
    "please|yes|no";
  public static final String HEADING_CAST_CREDIT_REMAINDER =
    "actor|actress|played by|understudy|director|directed by|voice of|" +
    "original cast|premiere|production|company|artistic director|" +
    "executive director";
  public static final String HEADING_DESCRIPTION_REMAINDER =
    "husband|wife|daughter|son|father|mother|brother|sister|child|children|" +
    "narrator|voice|pregnant|doesn't speak|does not speak|years old|" +
    "year old|inside|grown up|played by|appears as|also plays|described as|" +
    "counterpart|adolescence|elderly|young|old";
  public static final String HEADING_ACTION_NARRATION =
    "is|are|was|were|talks|speaks|enters|exits|attaches|straightens|shines|" +
    "makes|stopped|grew|goes|comes|looks|watches|follows|sits|stands|nods|" +
    "waves|shrugs|smiles|kisses|touches|helps|reads|puts|gets|takes|thumbs|" +
    "runs|twirls|consoles|sips|eats|whispers|demonstrates|opens|closes|" +
    "crosses|pulls|pushes|lays|carries|holds|reports|remembers|forgets|" +
    "cries|laughs|points|turns|moves|walks|kneels|rises|falls|leans|stares|" +
    "waits|listens|searches|throws|picks|drops|hands|gives|receives|places|" +
    "sets|covers|uncovers|wipes|combs|brushes|dances|sings|hums";

  // StageDetector

  public static final String ACT_PREFIX = "^ACT\\s+.*";
  public static final String SCENE_PREFIX = "^SCENE\\s+.*";
  public static final String NUMBER_THEN_CAPS =
    "^\\d{1,4}\\s+[A-Z][A-Z .'-]{2,}$";
  public static final String CAPS_THEN_NUMBER =
    "^[A-Z][A-Z .'-]{2,}\\s+\\d{1,4}$";
  public static final String NUMBERED_SPEAKER =
    "^[A-Z][A-Z .'/&-]{1,30}\\s+\\d{1,2}\\.?$";
  public static final String PARENTHETICAL_SPEAKER =
    "^[A-Z][A-Z0-9 /'.&-]{1,45}\\s*\\(.*\\)\\.?$";
  public static final String DIGITS_1_4 = "\\d{1,4}";
  public static final String TECH_CUE_START =
    "^(blackout|lights?|sound|music|curtain)\\b.*";
  public static final String ENTRANCE_EXIT_START =
    "^(enter|enters|exit|exits|exeunt|re-enter|re-enters|reenter|reenters)\\b.*";
  public static final String ENTRANCE_EXIT_ANYWHERE =
    "^.*\\b(enter|enters|exit|exits|exeunt|re-enter|re-enters|reenter|reenters)\\b.*";
  public static final String PAREN_WHOLE = "^\\([^)]*\\)\\.?$";
  public static final String BRACKET_WHOLE = "^\\[[^\\]]*\\]\\.?$";
  public static final String BRACE_WHOLE = "^\\{[^}]*\\}\\.?$";
  public static final String LOCATION_DIRECTION =
    "^(inside|outside|onstage|offstage|upstage|downstage)\\b.*";
  public static final String ARTICLE_LOCATION_COLON =
    "^(a|an|the)\\s+[a-z][a-z '-]{1,45}:.*";
  public static final String TECH_LIGHTS_SOUND = "^(lights?|sound|music)\\b.*";
  public static final String TIME_OF_DAY =
    "^(morning|afternoon|evening|night|later|silence|pause|beat)\\.?$";
  public static final String ARTICLE_SETTING_NOUN =
    "^(a|an|the)\\s+(room|office|kitchen|bedroom|living room|sitting room|dining room|street|hall|hotel|hotel room|womb|church|house|apartment|courtroom|cell|jail cell|prison cell|yard|garden|stage|window|door|stairway|stairs|landing|porch|garage|bar|restaurant|hospital|cemetery|bathroom)\\b.*";
  public static final String CONTAINS_BANG_QUESTION = ".*[?!].*";
  public static final String DIALOGUE_THE_PHRASE =
    "^(the minute|the early|the rest|the way|the thing|the one|the other|the same|the last|the first|the next)\\b.*";
  public static final String DIALOGUE_A_PHRASE =
    "^(a little|a lot|a double|a single|a good|a bad|a big|a small)\\b.*";
  public static final String DIALOGUE_FRAGMENT_PRONOUN =
    ".*\\b(i|i'm|i'll|i've|you|you're|you'll|we|we're|don't|can't|won't|would|could|should|want|know|think|feel|love|hate|need|mean|remember)\\b.*";
  public static final String OBJECT_SOUND_VERB =
    ".*\\b(rings|tolls|sounds|opens|closes|shakes)\\b.*";
  public static final String PRONOUN_START = "^(he|she|they|we|i|you)\\b.*";
  public static final String ENDS_WITH_SENTENCE = ".*[.!?]$";
  public static final String TITLE_THEN_LOWER = "^[A-Z][a-z].*";
  public static final String DIALOGUE_PRONOUN_BROAD =
    ".*\\b(i|you|we|they|he|she|me|my|your|our|their|his|her|them|us)\\b.*";
  public static final String NON_LOWER_QUOTE_DASH_RUN = "[^a-z'-]+";
  public static final String NON_ALNUM_QUOTE_SPACE_DASH = "[^A-Z0-9' -]";
  public static final String AGE_TERM =
    ".*\\b(age|aged|years old|year old)\\b.*";
  public static final String GENERAL_STAGE_VERB =
    ".*\\b(enters|exits|appears|goes|comes|walks|runs|sits|stands|looks|watches|nods|shrugs|shakes|puts|lays|takes|tolls|speaks|whispers|remains|crosses|opens|closes|holds|drops|kneels|stares|stops|listens|sings|dances|kisses|touches|twirls|consoles|cleans|straightens|waves|winces|demonstrates)\\b.*";
  public static final String TERSE_BEAT = "^(pause|beat|silence)\\.?$";
  public static final String PRONOUN_ACTION_BEAT =
    "^(he|she|they|it)\\s+(nods|shrugs|smiles|laughs|cries|sighs|waits|listens|watches|stares|turns|exits|enters|leaves)\\.?$";
  public static final String LOWER_ACTION_BEAT =
    "^[a-z][a-z' -]{1,35}\\s+(nods|shrugs|smiles|laughs|cries|sighs|waits|listens|watches|stares|turns|exits|enters|leaves)\\.?$";
  public static final String THERE_IS = "^(there is|there are|there's)\\s+.*";
  public static final String ARTICLE_SCENE_IMAGE =
    "^(a|an|the)\\s+[a-z][a-z' -]{1,45}\\s+(is|are|stands|sits|hangs|lies|waits|appears|remains)\\b.*";
  public static final String IS_SEEN_HEARD =
    ".*\\b(is seen|are seen|can be seen|is heard|are heard|can be heard)\\b.*";
  public static final String HEADER_PLAYABLE =
    "^(end of|intermission|blackout|curtain|lights?|sound|music)\\b.*";
  public static final String ACT_SCENE_EPISODE_PREFIX =
    "^(act|scene|episode)\\s+.*";
  public static final String ROMAN_NUMERAL_LINE =
    "^[ivxlcdm]+\\.?\\s+[a-z0-9' -]{2,80}$";
  public static final String DIALOGUE_SENTENCE_START =
    "^(i|i'm|i'll|i'd|ive|i've|you|you're|you'll|we|we're|we'll|they|they're|he|she)\\b.*";
  public static final String DIALOGUE_SENTENCE_VERB =
    ".*\\b(am|are|is|was|were|have|has|had|do|does|did|will|would|could|should|want|know|think|feel|love|hate|need|mean|remember)\\b.*";

  // PageFurnitureDetector

  public static final String LEADING_NAME_BLEED =
    "^\\s*\\d{1,4}\\s+[A-Z][A-Z'’\\-]+(?:\\s+[A-Z][A-Z'’\\-]+){0,4}\\s+(?=[A-Z][A-Z0-9'’\\- ]{1,45}(?:[.:]|$))";
  public static final String TRAILING_NAME_BLEED =
    "\\s+[A-Z][A-Z'’\\-]+(?:\\s+[A-Z][A-Z'’\\-]+){0,4}\\s+\\d{1,4}\\s*$";
  public static final String WRAPPED_NAME_ONLY = "^[A-Z0-9 ./'’\\-]+\\.?$";
  public static final String WRAPPED_NAME_PAREN =
    "^[A-Z0-9 ./'’\\-]+\\s*\\([^)]*\\)\\.?$";
  public static final String WRAPPED_NAME_BRACKET =
    "^[A-Z0-9 ./'’\\-]+\\s*\\[[^\\]]*\\]\\.?$";
  public static final String TECH_CUE_PAUSE =
    "^(lights?|sound|music|blackout|curtain|pause|beat|silence)\\b.*";
  public static final String ARTICLE_SETTING_SHORT =
    "^(a|an|the)\\s+(room|office|kitchen|bedroom|living room|sitting room|dining room|street|hall|hotel|hotel room|womb|church|house|apartment|courtroom|cell|yard|garden|stage|window|door)\\b.*";
  public static final String LOWER_STAGE_ACTION =
    "^[a-z][a-z'’ -]{1,45}\\s+(enters|exits|crosses|goes|comes|walks|runs|sits|stands|nods|waves|shrugs|smiles|kisses|touches|helps|reads|puts|gets|takes|twirls|consoles|sips|eats|whispers|opens|closes|carries|holds)\\b.*";
  public static final String CAPS_WORDS_THEN_NUMBER =
    "^[A-Z][A-Z'’\\-]+(?:\\s+[A-Z][A-Z'’\\-]+){0,4}\\s+\\d{1,4}$";
  public static final String NUMBER_THEN_CAPS_WORDS =
    "^\\d{1,4}\\s+[A-Z][A-Z'’\\-]+(?:\\s+[A-Z][A-Z'’\\-]+){0,4}$";
  public static final String NON_FILENAME_CHAR = "[^A-Za-z0-9_-]";
  public static final String PAGE_LABEL = "(?i)^PAGE\\s+\\d{1,4}$";
  public static final String PUBLISHER_FURNITURE_WORD =
    "ISBN|COPYRIGHT|ALL RIGHTS RESERVED|PUBLISHED|PUBLISHER|PERMISSION|RIGHTS";
  public static final String CONTAINS_DRAMA_PRICE = ".*\\bDRAMA\\s+\\$?\\d+.*";
  public static final String CAPS_CAPS_NUMBER_LINE =
    ".*\\b[A-Z]{2,}\\b.*\\b[A-Z]{2,}\\b.*\\b\\d{1,4}\\b.*";
  public static final String US_STATE_SUFFIX =
    ".*\\b[A-Z]{2,},?\\s+(NY|CA|IL|MA|TX|PA|WA|OR|CO|DC|UK|USA|US)\\b.*";

  // ScriptPreProcess

  public static final String PAGE_TITLE_NUMBER = "^.+\\s+\\d{1,4}$";
  public static final String INLINE_PAGE_HEADER =
    "\\b\\d{1,4}\\s+(?:[A-Z][A-Z.'-]*\\s+){1,8}\\d{1,4}\\b";
  public static final String PAGE_RANGE_HEADER =
    "\\b\\d{1,4}\\s+[A-Z][A-Z .'-]{2,80}\\s+\\d{1,4}\\b";
  public static final String ALL_CAPS_RUN =
    "\\b[A-Z][A-Z.'-]*(?:\\s+[A-Z][A-Z.'-]*){0,5}\\b";
  public static final String TRAILING_WHITESPACE = "\\s+$";
  public static final String SHORT_INTERJECTION = "(?i)^(no|go|oh|yes|ok|hi)$";
  public static final String NON_LETTER_QUOTE = "[^A-Za-z']";
  public static final String DOUBLE_QUOTE_MARKS = ".*['\"‘’“”].*['\"‘’“”].*";
  public static final String CONSONANT_RUN = ".*[QXZ]{2,}.*";
  public static final String NUMBER_CAPS_NUMBER =
    "^\\d{1,4}\\s+[A-Z][A-Z .'-]{3,}\\s+\\d{1,4}$";
  public static final String STRUCTURAL_EPISODE_CI = "(?i)^episode\\s+\\w+.*$";
  public static final String SCRIPT_LEADING_NAME_BLEED =
    "^\\s*\\d{1,4}\\s+[A-Z][A-Z'’\\-]+(?:\\s+[A-Z][A-Z'’\\-]+){0,3}\\s+(?=[A-Z][A-Z0-9'’\\- ]{1,45}(?:[.:]|$))";
  public static final String SCRIPT_TRAILING_NAME_BLEED =
    "\\s+[A-Z][A-Z'’\\-]+(?:\\s+[A-Z][A-Z'’\\-]+){0,3}\\s+\\d{1,4}\\s*$";
  public static final String CONTAINS_ISBN_CI = "(?i).*\\bISBN\\b.*";
  public static final String CONTAINS_WWW_CI = "(?i).*\\bWWW\\..*";
  public static final String CONTAINS_WEB_TLD_CI =
    "(?i).*\\.(COM|CO\\.UK|ORG|NET)\\b.*";
  public static final String AFTER_LAST_SPACE = ".*\\s+";
  public static final String BEFORE_FIRST_SPACE = "\\s+.*";

  // OCR

  public static final String TAB = "\t";
  public static final String NEWLINE_RUN_CHAR = "\n{3,}";
  public static final String DOUBLE_SPACE = "\\s{2,}";
  public static final String NON_LOWER_QUOTE = "[^a-z']";
  public static final String NON_CAPS_RUN = "[^A-Z]+";
  public static final String CONTAINS_VOWEL = ".*[aeiouAEIOU].*";
  public static final String CONTAINS_TITLE_WORD = ".*\\b[A-Z][a-z]{2,}\\b.*";
  public static final String CAPS_ALNUM_LINE = "^[A-Z0-9 ./'\\-]+$";
  public static final String CAPS_ALNUM_APOS_LINE = "^[A-Z0-9 ./'’\\-]+$";
  public static final String LOWER_WORD_START = "^[a-z][a-z]+\\b.*";
  public static final String PUNCT_START = "^[,.;:!?)]\\s*.*";
  public static final String SHORT_WORD_DASH_END =
    ".*\\b[a-zA-Z]{1,3}[-–—]$|.*\\b[a-zA-Z]{1,2}$";
  public static final String ENDS_SENTENCE_PUNCT = ".*[.!?\")']$.*";
  public static final String CONSONANT_RUN_4 =
    ".*[BCDFGHJKLMNPQRSTVWXYZ]{4,}.*";
  public static final String DASH_RUN = ".*[-_=]{4,}.*";
  public static final String CAPS_DASH_CAPS =
    ".*[A-Z]{1,3}[-_=]{2,}[A-Z]{1,3}.*";
  public static final String SHORT_LINE_1_8 = "^.{1,8}$";
  public static final String CONTAINS_PUNCT_DIGIT = ".*[()|;:0-9].*";
  public static final String MIXED_CASE_GARBLE =
    ".*\\b[A-Za-z]*[A-Z]{2,}[a-z]+[A-Z]+[A-Za-z]*\\b.*";
  public static final String PAGE_PREFIX_CI = "(?i)^page\\s+";
  public static final String OCR_NUMBER_THEN_CAPS =
    "^\\d{1,4}\\s+[A-Z][A-Z .'/’\\-]{2,}$";
  public static final String OCR_CAPS_THEN_NUMBER =
    "^[A-Z][A-Z .'/’\\-]{2,}\\s+\\d{1,4}$";
  public static final String AFTER_FIRST_SPACE_TO_END = "\\s+.*$";
  public static final String BEFORE_LAST_SPACE_GREEDY = "^.*\\s+";
  public static final String CONSONANT_RUN_5_MIXED =
    ".*[BCDFGHJKLMNPQRSTVWXYZbcdfghjklmnpqrstvwxyz]{5,}.*";
  public static final String PROSE_FUNCTION_WORD =
    ".*\\b(the|and|that|with|this|was|for|you|she|he|they)\\b.*";
  public static final String OCR_STAGE_HINT =
    ".*\\b(enters|exits|crosses|sits|stands|looks|nods|shakes|pause|beat|silence)\\b.*";

  // MusicDetector

  public static final String SONG_NUMBER_MARKER =
    "^#\\s*\\d{1,3}[A-Za-z]?\\b.*";
  public static final String SONG_NO_MARKER =
    "(?i)^(no\\.?|number)\\s*\\d{1,3}[A-Za-z]?\\b.*";
  public static final String MUSICAL_NUMBERS_HEADING =
    "(?i)^musical numbers?\\b.*";
  public static final String SONG_CUE_MARKER =
    "(?i)^[\\[(]?\\s*(reprise|underscore|under-score|vamp|playoff|play-off|" +
    "playout|segue|entr'?acte|exit music|incidental music|music cue)\\b.*";
  public static final String SONG_TITLE_WORD =
    "(?i).*\\b(reprise|underscore|under-score|vamp|playoff|play-off|" +
    "playout|segue|entr'?acte|entracte)\\b.*";
  public static final String SINGS_PARENTHETICAL =
    "(?i).*\\([^)]*\\b(sings?|singing|sung)\\b[^)]*\\).*";
  public static final String MUSIC_TECH_OPEN =
    "(?i)^[\\[(]?\\s*music\\s+(in|begins?|starts?|cue|under(score)?|swells?|continues?|resumes?)\\b.*";
  public static final String MUSIC_TECH_CLOSE =
    "(?i)^[\\[(]?\\s*(the\\s+)?(music|song)\\s+(out|ends?|stops?|fades?|finishes?)\\b.*";
  public static final String SONG_REGION_BOUNDARY =
    "(?i)^(act|scene|episode|intermission|end of)\\b.*";

  private RegexTerms() {}

  public static String containsAnyWord(String alternates) {
    return ".*\\b(" + alternates + ")\\b.*";
  }

  public static String containsAnyWordIgnoreCase(String alternates) {
    return "(?i).*\\b(" + alternates + ")\\b.*";
  }

  public static String startsWithAnyWord(String alternates) {
    return "^(" + alternates + ")\\b.*";
  }

  public static boolean containsPublicationOrFurniture(String text) {
    return text != null && text.matches(PUBLICATION_OR_FURNITURE_PATTERN);
  }
}
