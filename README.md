# Line-Learner

**Line-Learner** is a local theatre script parser and rehearsal tool focused on cue pickup: hearing the line before yours and responding with the correct line.

It can run as a terminal Java app or through a simple local web UI. Scripts stay on your machine.

## What It Does

- Loads `.txt` and `.pdf` scripts
- Uses native PDF text when reliable
- Falls back to OCR for scanned or messy PDFs
- Caches extracted PDF text so repeat uploads are faster
- Detects characters and speaker headings
- Lets you add/remove detected characters before parsing
- Lets you choose the starting line before parsing
- Lets you remove repeated headers, footers, OCR junk, or other phrases before parsing
- Builds cue/response pairs for a selected character
- Runs line practice by showing a cue and asking for the next line
- Exports parser diagnostics as CSV files from the terminal app

## Web App

The web app provides the main workflow without needing to use the terminal prompts:

1. Upload a `.txt` or `.pdf` script
2. Review and edit the detected character list
3. Pick the starting line
4. Optionally enter repeated text to remove before parsing
5. Choose your character
6. Parse the script
7. Practice from cue to line

Uploaded scripts are saved in `web/.data/uploads/`. The server fingerprints uploads by file contents, and if the same file is uploaded multiple times, it reuses the saved copy and cached extraction rather than treating it as a new script

### Run The Web App

From `web/`:

```bash
npm install
npm run dev
```

The Vite app runs on `http://localhost:5173`, and the API server runs on `http://localhost:5174`.

If the bridge classes need to be rebuilt manually:

```bash
javac -encoding UTF-8 -cp "backend/lib/*" -d web/.bridge-build $(find backend/src -name "*.java") web/server/bridge.java
```

On Windows, replace `:` with `;`

## Terminal App

The terminal app still supports the original full prompt flow:

- choose settings
- load a script
- optionally remove repeated phrases
- review/edit characters
- choose the body start
- choose your character
- run practice
- retry missed lines

### Run The Terminal App

From the project root:

```bash
javac -encoding UTF-8 -cp "backend/lib/*" -d out $(find backend/src -name "*.java")
java -cp "out:backend/lib/*" app.Main
```

On Windows replace `:` with `;`

## Project Structure

```text
backend/
  src/
    app/        terminal entry point
    ocr/        PDF native/OCR extraction
    parser/     script cleanup, character detection, cue parsing
    practice/   terminal practice session
    util/       shared text and regex helpers
  lib/          Java dependencies

web/
  server/       Express API and Java bridge
  src/          browser JavaScript and basic CSS
  .data/        local uploads and upload cache metadata
```

## Parser Diagnostics

The terminal parser writes CSV files for review:

- `parser_output_pairs.csv`
- `parser_characters.csv`
- `parser_logical_lines.csv`
- `parser_heading_index.csv`
- `parser_blocks.csv`
- `parser_turns.csv`
- `parser_suspicious_turns.csv`
- `parser_health.csv`

These files help compare parser behavior across real scripts and make debugging easier

## Current Development Focus

The parser is being tested on real scripts with difficult formatting, including expressionist writing, narration heavy writing, numbered speakers, slash speakers, front matter, running headers, and OCR noise.

Current priorities:

- Keep the web flow simple and understandable
- Improve block assembly and continuation handling
- Reduce false stage/prose classification
- Strengthen missed stage/prose classification
- Strengthen front matter/body start detection

## Known Limitations

- Script parsing is heuristic based and may still need manual review
- OCR quality depends heavily on scan quality
- Prose heavy scripts and unusual formatting may still create `UNKNOWN` turns
- Some parser diagnostics are intended for development rather than end users

## Privacy

Scripts can contain sensitive material. Line-Learner runs locally and does **not** upload scripts to any external service.

## Why This Project

As a theatre student, I wanted a tool that focuses on the hardest part of memorization: hearing the cue and responding with the correct line. The project grew from a simple rehearsal utility into a larger system for parsing real scripts, handling messy PDFs and OCR output, and preserving uncertain text conservatively instead of guessing incorrectly.
