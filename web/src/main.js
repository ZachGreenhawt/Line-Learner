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
const finishSession = document.querySelector("#finish-session");
let currentScript = null;
let practiceItems = [];
let practiceIndex = 0;

show(form);

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
    data.message || "Upload saved. Review the setup before parsing.",
  );
});

saveCharacters.addEventListener("click", () => {
  if (!currentScript) return;

  saveCharactersStep();
  show(startLineSection);
});

saveStartLine.addEventListener("click", () => {
  if (!currentScript) return;

  saveStartLineStep();
  show(removeLinesSection);
});

saveRemoveLines.addEventListener("click", () => {
  if (!currentScript) return;

  saveRemoveLinesStep();
  show(parseSection);
});

parse.addEventListener("click", async () => {
  if (!currentScript) return;

  currentScript.settings = settingsFromForm();
  saveCharactersStep();
  saveStartLineStep();
  saveRemoveLinesStep();

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

  out.textContent = message;
  show(characters);
}

function settingsFromForm() {
  return {
    includeStageDir: form.elements.includeStageDir.checked,
    caseSensitive: form.elements.caseSensitive.checked,
    punctuation: form.elements.punctuation.checked,
    timedMode: form.elements.timedMode.checked,
    includeMusic: form.elements.includeMusic.checked,
  };
}

function showSettings(settings) {
  form.elements.includeStageDir.checked = Boolean(settings.includeStageDir);
  form.elements.caseSensitive.checked = Boolean(settings.caseSensitive);
  form.elements.punctuation.checked = Boolean(settings.punctuation);
  form.elements.timedMode.checked = Boolean(settings.timedMode);
  form.elements.includeMusic.checked = Boolean(settings.includeMusic);
}

function show(section) {
  form.hidden = true;
  characters.hidden = true;
  startLineSection.hidden = true;
  removeLinesSection.hidden = true;
  parseSection.hidden = true;
  practiceSection.hidden = true;
  section.hidden = false;
  window.scrollTo(0, 0);
}

function saveCharactersStep() {
  currentScript.characters = cleanCharacters(characterList.value);
  characterList.value = currentScript.characters.join("\n");
  showTargetCharacters(currentScript.characters);
}

function saveStartLineStep() {
  currentScript.bodyStartIndex = Number(startLine.value);
}

function saveRemoveLinesStep() {
  currentScript.removeLines = cleanList(removeLines.value);
  removeLines.value = currentScript.removeLines.join("\n");
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
  if (practiceIndex >= practiceItems.length - 1) return;

  practiceIndex += 1;
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
  show(practiceSection);
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
  nextLine.disabled = practiceIndex >= practiceItems.length - 1;
  practiceAnswer.focus();
}

finishSession.addEventListener("click", async () => {
  const scriptId = currentScript?.scriptId;
  if (!scriptId) {
    out.textContent = "Nothing to delete.";
    return;
  }

  out.textContent = "Deleting your script and its extracted text...";
  try {
    await fetch("/api/session/end", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ scriptId }),
    });
  } catch {}

  currentScript = null;
  practiceItems = [];
  practiceIndex = 0;
  form.reset();
  show(form);
  out.textContent = "Your script and its extracted text were deleted.";
});

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
