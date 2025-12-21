# Line-Learner

**Line-Learner** is a Java rehearsal tool that helps actors memorize lines by practicing **cue pickup** (the line before yours) and checking **accuracy** (typing your line). It parses a script, extracts your character’s lines, pairs each one with the most recent cue line, and runs an interactive practice session in the terminal.

## What it does
- Loads a script from a local `.txt` or `.txt` file (from `Example-Scripts/`)
- Prompts for **settings** and a **character name**
- Parses the script into aligned **cue → response** pairs
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

## Known limitations (for now)
- Speaker detection is heuristic-based (e.g., “NAME:”/“NAME.” and short all-caps names). Some scripts may require formatting tweaks.
- Stage-direction handling currently only recognizes stage direction lines that start with `(`.
- PDF support is limited to text-based PDF files

## Roadmap
- Build PDF OCR 
- More robust parsing for real-world scripts (wider stage direction formats, more speaker patterns)
- Additional practice modes (difficulty, cue-only, skipping, spaced repetition)
- Simple GUI / web UI
- Voice Processing

## Why this project
As a theatre student, I wanted a tool that focuses on the hardest part of memorization: **hearing the cue and delivering the correct line**. Building it also lets me practice clean software design (separating input, parsing, data modeling, and interaction).

## Privacy
Scripts can contain sensitive material. Line-Learner runs locally and does **not** upload scripts anywhere.
