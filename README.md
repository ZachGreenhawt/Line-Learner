# Line-Learner

Line-Learner is a Java rehearsal tool that helps actors memorize lines by practicing **cue pickup** (the line before yours) and checking **accuracy** (typing your line exactly). It parses a script, extracts your character’s lines, pairs each one with the most recent cue line, and runs an interactive practice session in the terminal.

## Features (current)
- Loads a script from a local file
- Prompts for a character name
- Builds cue --> response pairs (cue line + your line)
- Runs an interactive practice session and tracks progress
- Clean data model: parsing outputs are encapsulated in a `ParsedScript` object

## How it works
1. `ScriptStore` reads the script and parses dialogue into aligned lists:
   - `cueLines[i]` = the cue before line `i`
   - `charLines[i]` = your character’s expected line `i`
2. The parsed results are packaged into a `ParsedScript` object to keep parsing separate from practice logic.
3. `PracticeSession` iterates through `ParsedScript` and prompts the user to type each line.

## Project structure
- `Main.java` – program entry point (loads script, starts practice)
- `ScriptStore.java` – file reading + parsing
- `ParsedScript.java` – container for parsed cue/line pairs (encapsulation)
- `PracticeSession.java` – interactive rehearsal loop
- `Example-Scripts/` – sample scripts for testing and demos

## Script format (v0)
This version expects character dialogue lines in a format like:

CHARACTER: spoken text here
(stage directions are ignored)
Continuation lines (non-character lines) are treated as part of the current speaker’s line.

## Run locally
1. Clone:
   ```bash
   git clone https://github.com/ZachGreenhawt/Line-Learner.git
   cd Line-Learner
2. Compile: 
    javac *.java
3. Run: 
    java Main

## Roadmap
- Improve parsing for real scripts (NAME. / NAME: / multi-line dialogue)
- Add options: retry line, skip line, review missed lines
- PDF support
- Simple GUI

## Why this project
As a theatre student, I have always wanted a tool that focuses on the hardest part of memorization: Hearing the cue and delivering the correct line under pressure. This project is also a way to practice clean software design.

## Notes
Scripts will commonly contain sensitive material; this is why Line-Learner runs locally and does not upload scripts anywhere. 