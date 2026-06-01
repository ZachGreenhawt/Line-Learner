const form = document.querySelector("#form");
const out = document.querySelector("#out");
const characters = document.querySelector("#characters");
const characterList = document.querySelector("#character-list");
const saveCharacters = document.querySelector("#save-characters");
const startLineSection = document.querySelector("#start-line-section");
const startLine = document.querySelector("#start-line");
const saveStartLine = document.querySelector("#save-start-line");
const removeLinesSection = document.querySelector("#remove-lines-section");
const removeLines = document.querySelector("#remove-lines");
const saveRemoveLines = document.querySelector("#save-remove-lines");
const parseSection = document.querySelector("#parse-section");
const targetCharacter = document.querySelector("#target-character");
const parse = document.querySelector("#parse");
const practiceSection = document.querySelector("#practice-section");
const practiceCount = document.querySelector("#practice-count");
const practiceCue = document.querySelector("#practice-cue");
const practiceAnswer = document.querySelector("#practice-answer");
const checkAnswer = document.querySelector("#check-answer");
const nextLine = document.querySelector("#next-line");
const practiceFeedback = document.querySelector("#practice-feedback");
const answerDetails = document.querySelector("#answer-details");
const expectedLine = document.querySelector("#expected-line");
let currentScript = null;
let practiceItems = [];
let practiceIndex = 0;

form.addEventListener("submit", async (e) => {
  e.preventDefault();

  out.textContent = "Uploading...";

  const scriptData = new FormData(form);

  const res = await fetch("/api/upload", {
    method: "POST",
    body: scriptData,
  });

  const data = await res.json();

  if (!data.ok) {
    out.textContent = data.error || "Upload failed.";
    return;
  }

  useScript(
    data,
    data.message || "Upload saved. Review the setup before parsing."
  );
});

saveCharacters.addEventListener("click", () => {
  if (!currentScript) return;

  currentScript.characters = cleanCharacters(characterList.value);
  characterList.value = currentScript.characters.join("\n");
  showTargetCharacters(currentScript.characters);

  out.textContent = "Characters saved.";
});

saveStartLine.addEventListener("click", () => {
  if (!currentScript) return;

  currentScript.bodyStartIndex = Number(startLine.value);

  out.textContent = "Starting line saved.";
});

saveRemoveLines.addEventListener("click", () => {
  if (!currentScript) return;

  currentScript.removeLines = cleanList(removeLines.value);
  removeLines.value = currentScript.removeLines.join("\n");

  out.textContent = "Removals saved.";
});

parse.addEventListener("click", async () => {
  if (!currentScript) return;

  currentScript.settings = settingsFromForm();
  currentScript.characters = cleanCharacters(characterList.value);
  currentScript.bodyStartIndex = Number(startLine.value);
  currentScript.removeLines = cleanList(removeLines.value);

  out.textContent = "Parsing...";

  const res = await fetch("/api/parse", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      scriptId: currentScript.scriptId,
      fileName: currentScript.fileName,
      savedPath: currentScript.savedPath,
      settings: currentScript.settings,
      characters: currentScript.characters,
      removeLines: currentScript.removeLines,
      bodyStartIndex: currentScript.bodyStartIndex,
      targetCharacter: targetCharacter.value,
    }),
  });

  const data = await res.json();
  if (data.ok) {
    startPractice(data.parsed?.items || []);
    out.textContent = "Parse complete. Practice is ready.";
    return;
  }

  out.textContent = data.error || "Parse failed.";
});

function useScript(data, message) {
  currentScript = {
    scriptId: data.scriptId,
    fileName: data.fileName,
    savedPath: data.savedPath,
    settings: data.settings || settingsFromForm(),
    characters: data.analysis.characters || [],
    bodyStartIndex: data.analysis.bodyStartIndex,
    removeLines: data.removeLines || [],
  };

  showSettings(currentScript.settings);
  characterList.value = currentScript.characters.join("\n");
  removeLines.value = currentScript.removeLines.join("\n");
  showStartLines(data.analysis.preview || [], currentScript.bodyStartIndex);
  showTargetCharacters(currentScript.characters);

  characters.hidden = false;
  startLineSection.hidden = false;
  removeLinesSection.hidden = false;
  parseSection.hidden = false;
  practiceSection.hidden = true;
  out.textContent = message;
}

function settingsFromForm() {
  return {
    includeStageDir: form.elements.includeStageDir.checked,
    caseSensitive: form.elements.caseSensitive.checked,
    punctuation: form.elements.punctuation.checked,
    timedMode: form.elements.timedMode.checked,
  };
}

function showSettings(settings) {
  form.elements.includeStageDir.checked = Boolean(settings.includeStageDir);
  form.elements.caseSensitive.checked = Boolean(settings.caseSensitive);
  form.elements.punctuation.checked = Boolean(settings.punctuation);
  form.elements.timedMode.checked = Boolean(settings.timedMode);
}

checkAnswer.addEventListener("click", () => {
  const item = practiceItems[practiceIndex];
  if (!item) return;

  const guess = practiceAnswer.value;
  if (!guess.trim()) {
    practiceFeedback.textContent = "Type the line first.";
    return;
  }

  if (answerKey(guess) === answerKey(item.line)) {
    practiceFeedback.textContent = "Correct.";
    answerDetails.open = false;
    return;
  }

  practiceFeedback.textContent = "Not quite.";
  expectedLine.textContent = item.line;
});

nextLine.addEventListener("click", () => {
  if (!practiceItems.length) return;

  practiceIndex = (practiceIndex + 1) % practiceItems.length;
  showPracticeItem();
});

function showStartLines(lines, bodyStartIndex) {
  startLine.replaceChildren();

  for (const line of lines) {
    const option = document.createElement("option");
    option.value = line.index;
    option.textContent = `Line ${line.lineNumber}: ${line.text}`;
    option.selected = line.index === bodyStartIndex;
    startLine.append(option);
  }
}

function showTargetCharacters(names) {
  const selected = targetCharacter.value;
  targetCharacter.replaceChildren();

  for (const name of names) {
    const option = document.createElement("option");
    option.value = name;
    option.textContent = name;
    option.selected = name === selected;
    targetCharacter.append(option);
  }
}

function cleanCharacters(text) {
  return cleanList(text).map((name) => name.toUpperCase());
}

function cleanList(text) {
  const seen = new Set();
  const values = [];

  for (const line of text.split(/[,\r\n]+/)) {
    const value = line.trim().replace(/\s+/g, " ");
    const key = value.toUpperCase();
    if (!value || seen.has(key)) continue;

    seen.add(key);
    values.push(value);
  }

  return values;
}

function startPractice(items) {
  practiceItems = Array.isArray(items) ? items : [];
  practiceIndex = 0;
  practiceSection.hidden = false;
  showPracticeItem();
}

function showPracticeItem() {
  const item = practiceItems[practiceIndex];
  practiceAnswer.value = "";
  practiceFeedback.textContent = "";
  answerDetails.open = false;

  if (!item) {
    practiceCount.textContent = "No practice lines found.";
    practiceCue.textContent = "";
    expectedLine.textContent = "";
    checkAnswer.disabled = true;
    nextLine.disabled = true;
    return;
  }

  practiceCount.textContent = `${practiceIndex + 1} of ${practiceItems.length}`;
  practiceCue.textContent = item.cue;
  expectedLine.textContent = item.line;
  checkAnswer.disabled = false;
  nextLine.disabled = practiceItems.length < 2;
  practiceAnswer.focus();
}

function answerKey(text) {
  const settings = currentScript?.settings || {};
  let value = String(text).trim().replace(/\s+/g, " ");

  if (!settings.punctuation) {
    value = value.replace(/[^\p{L}\p{N}\s]/gu, "");
  }

  if (!settings.caseSensitive) {
    value = value.toLowerCase();
  }

  return value.trim();
}
