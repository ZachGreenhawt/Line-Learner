# Line-Learner

**Line-Learner** is a Java rehearsal tool that helps actors memorize lines by practicing **cue pickup** (the line before yours) and checking **accuracy** (typing your line). It parses a script, extracts your character’s lines, pairs each one with the most recent cue line, and runs an interactive practice session in the terminal.

## What it does
- Loads a script from a local `.txt` or `.pdf` file (from `Example-Scripts/`)
- Prompts for **settings** and a **character name**
- Parses the script into aligned **cue --> response** pairs
- Runs an interactive practice session and shows accuracy
- Offers a post-practice menu:
  - **Try again** (same parsed script)
  - **Retry only missed lines**
  - **Quit**

## Settings (current)
When you start the program, you can choose:
- **Include stage directions in cue lines** (only stage directions that appear on their own line and start with `(`)
- **Case sensitive** vs. case-insensitive checking
- **Keep punctuation** vs. ignore punctuation when checking answers
- **Timed mode** (shows time per line + total session time)

## Supported script formats (v0.5)
Line-Learner currently recognizes character dialogue in these patterns:

### 1) Name + colon
```text
CHARACTER: Spoken text here
```

### 2) Name + period
```text
CHARACTER. Spoken text here
```

### 3) Name on its own line (followed by dialogue)
```text
CHARACTER
Spoken text here
```

### Continuation lines
If a line does **not** look like a new character name, it is treated as a continuation of the current speaker’s dialogue and appended to the previous line.

### Stage directions
- A stage direction line that **starts with `(`** is treated as a stage direction.
- If stage-direction mode is **enabled**, stage directions are appended to the current cue line (as long as it is not currently your character’s turn).
- Inline parentheticals like `Hello (whispering) there` are removed from dialogue before parsing.

## How it works
1. `ScriptToString` reads the selected script file into a single `String`.
2. `ScriptParser` walks through the script line-by-line and builds two aligned lists:
   - `cueLines[i]` = the cue line before your line `i`
   - `charLines[i]` = your expected line `i`
3. These lists are packaged into a `ParsedScript` object (clean data model).
4. `PracticeSession` iterates through `ParsedScript` and prompts the user using settings from `Settings`.

## Project structure
- `Main.java` – program entry point (settings → load → parse → practice)
- `Settings.java` – interactive settings menu
- `ScriptToString.java` – file reading
- `ScriptParser.java` – script parser
- `ParsedScript.java` – container for parsed cue/line pairs
- `PracticeSession.java` – interactive rehearsal loop + retry menu
- `Example-Scripts/` – sample scripts for testing and demos

## Run locally
1. Clone:
   ```bash
   git clone https://github.com/ZachGreenhawt/Line-Learner.git
   cd Line-Learner
   ```
2. Compile:
   ```bash
   javac *.java
   ```
3. Run:
   ```bash
   java Main
   ```

## Known limitations (pre-v1)
The following issues are actively being addressed and are planned to be resolved **before v1 is released**:
- **Character introductions** (first appearance before a speaking line) can occasionally be misclassified
- **Page numbers, headers, and footers** may be interpreted as dialogue or speaker names in some scripts
- **Stage directions** beyond simple parenthetical lines (e.g., italic-only directions, action lines like “DANIEL crosses”) are still being normalized
- Speaker detection remains heuristic based and may require minor formatting adjustments for unusual scripts.
- PDF support is currently functionally limited to text-based PDFs; OCR support is still in active development.

## Roadmap
### Before v1
- Finalize robust handling for character introductions, page numbers, and non-dialogue artifacts
- Expand stage-direction detection (action lines, italic-only directions, under-speaker parentheticals)
- Stabilize OCR-based PDF ingestion
- Harden speaker detection for real world, inconsistently formatted scripts

### After v1
- **Web-based GUI** for Line-Learner (browser-based rehearsal interface)
- Additional practice modes (difficulty levels, cue-only, skipping, spaced repetition)
- Improved accuracy analytics and session history
- Optional voice-based practice and speech comparison

## Why this project
As a theatre student, I wanted a tool that focuses on the hardest part of memorization: **hearing the cue and delivering the correct line**. Building it also lets me practice clean software design (separating input, parsing, data modeling, and interaction).

## Privacy
Scripts can contain sensitive material. Line-Learner runs locally and does **not** upload scripts anywhere.
