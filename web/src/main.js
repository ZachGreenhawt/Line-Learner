const form = document.querySelector("#form");
const out = document.querySelector("#out");
const characters = document.querySelector("#characters");
const characterList = document.querySelector("#character-list");
const saveCharacters = document.querySelector("#save-characters");
const startLineSection = document.querySelector("#start-line-section");
const startLine = document.querySelector("#start-line");
const saveStartLine = document.querySelector("#save-start-line");
const parseSection = document.querySelector("#parse-section");
const targetCharacter = document.querySelector("#target-character");
const parse = document.querySelector("#parse");
let currentScript = null;

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
    out.textContent = data.err || "Upload failed";
    return;
  }

  currentScript = {
    scriptId: data.scriptId,
    fileName: data.fileName,
    savedPath: data.savedPath,
    settings: data.settings,
    characters: data.analysis.characters || [],
    bodyStartIndex: data.analysis.bodyStartIndex,
  };

  characterList.value = currentScript.characters.join("\n");
  showStartLines(data.analysis.preview || [], currentScript.bodyStartIndex);
  showTargetCharacters(currentScript.characters);

  characters.hidden = false;
  startLineSection.hidden = false;
  parseSection.hidden = false;
  out.textContent = JSON.stringify(data, null, 2);
});

saveCharacters.addEventListener("click", () => {
  if (!currentScript) return;

  currentScript.characters = cleanCharacters(characterList.value);
  characterList.value = currentScript.characters.join("\n");
  showTargetCharacters(currentScript.characters);

  out.textContent = JSON.stringify(
    {
      ok: true,
      message: "Characters saved.",
      scriptId: currentScript.scriptId,
      fileName: currentScript.fileName,
      settings: currentScript.settings,
      bodyStartIndex: currentScript.bodyStartIndex,
      characters: currentScript.characters,
    },
    null,
    2
  );
});

saveStartLine.addEventListener("click", () => {
  if (!currentScript) return;

  currentScript.bodyStartIndex = Number(startLine.value);

  out.textContent = JSON.stringify(
    {
      ok: true,
      message: "Starting line saved.",
      scriptId: currentScript.scriptId,
      fileName: currentScript.fileName,
      settings: currentScript.settings,
      bodyStartIndex: currentScript.bodyStartIndex,
      characters: currentScript.characters,
    },
    null,
    2
  );
});

parse.addEventListener("click", async () => {
  if (!currentScript) return;

  currentScript.characters = cleanCharacters(characterList.value);
  currentScript.bodyStartIndex = Number(startLine.value);

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
      bodyStartIndex: currentScript.bodyStartIndex,
      targetCharacter: targetCharacter.value,
    }),
  });

  const data = await res.json();
  out.textContent = JSON.stringify(data, null, 2);
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
  const seen = new Set();
  const names = [];

  for (const line of text.split(/\r?\n/)) {
    const name = line.trim().replace(/\s+/g, " ").toUpperCase();
    if (!name || seen.has(name)) continue;

    seen.add(name);
    names.push(name);
  }

  return names;
}
