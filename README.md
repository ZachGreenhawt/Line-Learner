# Line-Learner

**Line-Learner** is a Java rehearsal tool that helps actors memorize lines by practicing **cue pickup** (the line before yours) and checking **accuracy** (typing your line). It parses a script, extracts your character’s lines, pairs each one with the most recent cue line, and runs an interactive practice session in the terminal.

**Line-Learner** is a Java theatre script parser and rehearsal tool focused on cue pickup: hearing the line before yours and responding with the correct line. It parses scripts, extracts character dialogue, builds cue/response pairs, and runs an interactive terminal practice session.

## What it does

- Loads `.txt` and `.pdf` scripts
- Uses native PDF text when reliable
- Falls back to OCR for scanned or messy PDFs
- Detects characters and speaker headings
- Handles numbered speakers like `CLERK 1` (A character in Machinal)
- Handles slash speakers like `TELEPHONE GIRL / CLERK 2` (A character in my cut script of Machinal)
- Detects stage directions, front matter, and repeated page furniture
- Preserves uncertain dialogue as `UNKNOWN`
- Exports parser diagnostics as CSV files
- Builds cue/response pairs
- Runs an interactive terminal practice session

## Parser diagnostics

Every parse writes CSV files for review:

- `parser_output_pairs.csv`
- `parser_characters.csv`
- `parser_logical_lines.csv`
- `parser_heading_index.csv`
- `parser_blocks.csv`
- `parser_turns.csv`
- `parser_suspicious_turns.csv`
- `parser_health.csv`

These files help compare parser behavior across real scripts and make debugging easier.

## Project structure

### App and practice

- `Main.java` – program entry point
- `Settings.java` – practice settings
- `PracticeSession.java` – terminal rehearsal session
- `ParsedScript.java` – parsed cue/line container

### Script loading and extraction

- `ScriptLoader.java` – TXT/PDF script loading
- `PdfTextExtractor.java` – native PDF and OCR extraction
- `HybridTextExtraction.java` – native-vs-OCR decision layer
- `TextExtractionQualityScorer.java` – extraction quality scoring

### OCR pipeline

- `ImagePreprocessor.java`
- `OrientationResolver.java`
- `PageRegionExtractor.java`
- `OcrCandidate.java`
- `OcrCandidateScorer.java`
- `OcrResult.java`
- `OcrRunProfile.java`
- `OcrSearchTier.java`
- `PatternKey.java`
- `DocumentLearningCache.java`
- `ReadingOrderResolver.java`
- `TrialKey.java`
- `TrialStats.java`

### Parser pipeline

- `ScriptPreProcess.java`
- `LogicalLineBuilder.java`
- `FrontMatterDetector.java`
- `PageFurnitureDetector.java`
- `FurnitureCandidateResolver.java`
- `SpeakerDetector.java`
- `SpeakerHeadingIndex.java`
- `SpeakerBlockBuilder.java`
- `StageDetector.java`
- `TurnBuilder.java`
- `CuePairBuilder.java`
- `ParseModels.java`
- `TextNormalizer.java`

### Persistence and diagnostics

- `ParserSessionStore.java` – stores extracted text, characters, and aliases
- `CharacterExtractor.java`
- `CharacterSetup.java`
- `ParserPrompts.java`
- `CsvExporter.java`
- `OcrDiagnosticsExporter.java`

## Current development focus

The parser is being tested on real scripts with difficult formatting. This includes expressionist writing, narration heavy writing, numbered speakers, slash speakers, front matter, and OCR noise.

### Current Priorities

- Improve block assembly and continuation handling

- Reduce false stage/prose classification

- Strengthen front matter/body start detection

- Keep parser output conservative (i.e., preserve uncertain text rather than guessing wrong speakers)

- Eventually build a more user friendly UI

## Known Limitations

- Script Parsing is heuristic-based and may still need manual review

- OCR quality depends heavily on scan quality

- Prose heavy scripts and unusual formatting can still create `UNKNOWN` turns

- Some parser diagnostics are intended for development rather than end users

## Privacy

Scripts can contain sensitive material. Line-Learner runs locally and does **not** upload scripts anywhere.

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

On Windows, replace `:` with `;` in the classpath.

## Why this project

As a theatre student, I wanted a tool that focuses on the hardest part of memorization: hearing the cue and responding with the correct line. The project grew from a simple rehearsal utility into a larger system for parsing real scripts, handling messy PDFs and OCR output, and preserving uncertain text conservatively instead of guessing incorrectly.
