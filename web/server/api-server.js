import express from "express";
import multer from "multer";
import crypto from "node:crypto";
import { createReadStream } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  mkdir,
  readFile,
  readdir,
  stat,
  unlink,
  writeFile,
} from "node:fs/promises";
import { spawn } from "node:child_process";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = path.resolve(__dirname, "../..");
const BACKEND_DIR = path.join(ROOT_DIR, "backend");
const DATA_DIR = path.join(ROOT_DIR, "web", ".data");
const UPLOAD_DIR = path.join(DATA_DIR, "uploads");
const SCRIPTS_FILE = path.join(DATA_DIR, "scripts.json");
const PORT = Number(process.env.PORT || process.env.API_PORT || 5174);
const HOST = process.env.HOST || "0.0.0.0";
const LIST_SEPARATOR = "\u001F";

await mkdir(UPLOAD_DIR, { recursive: true });

const app = express();

const storage = multer.diskStorage({
  destination: UPLOAD_DIR,
  filename(req, file, cb) {
    const ext = path.extname(file.originalname).toLowerCase();
    const id = crypto.randomUUID();
    cb(null, `${id}${ext}`);
  },
});

const upload = multer({
  storage,
  limits: {
    fileSize: 80 * 1024 * 1024,
  },
});

app.use(express.json());
app.use(express.static(path.join(ROOT_DIR, "web", "dist")));

function scriptIdFor(file) {
  return path.parse(file.filename).name;
}

function defaultSettings() {
  return {
    includeStageDir: false,
    caseSensitive: false,
    punctuation: false,
    timedMode: false,
  };
}

function checked(value) {
  return value === "true" || value === "on" || value === true;
}

function settingsFrom(body) {
  body = body || {};

  return {
    includeStageDir: checked(body.includeStageDir),
    caseSensitive: checked(body.caseSensitive),
    punctuation: checked(body.punctuation),
    timedMode: checked(body.timedMode),
  };
}

function charactersFrom(value) {
  return listFrom(value);
}

function listFrom(value) {
  if (!Array.isArray(value)) {
    return [];
  }

  return value.map((name) => String(name).trim()).filter(Boolean);
}

async function readScripts() {
  try {
    const text = await readFile(SCRIPTS_FILE, "utf8");
    const scripts = JSON.parse(text);
    return Array.isArray(scripts) ? scripts : [];
  } catch (error) {
    if (error.code === "ENOENT") {
      return [];
    }
    throw error;
  }
}

async function writeScripts(scripts) {
  await mkdir(DATA_DIR, { recursive: true });
  await writeFile(SCRIPTS_FILE, JSON.stringify(scripts, null, 2));
}

async function saveScript(script) {
  const scripts = await existingScripts(await readScripts());
  const saved = [
    script,
    ...scripts.filter((item) => item.scriptId !== script.scriptId),
  ];

  await writeScripts(saved);
}

async function existingScripts(scripts) {
  const existing = [];

  for (const script of scripts) {
    try {
      const info = await stat(script.savedPath);
      if (info.isFile()) {
        existing.push({
          ...script,
          size: script.size || info.size,
          settings: script.settings || defaultSettings(),
        });
      }
    } catch (error) {
      if (error.code !== "ENOENT") {
        throw error;
      }
    }
  }

  existing.sort((a, b) => {
    const left = b.lastOpenedAt || b.uploadedAt || "";
    const right = a.lastOpenedAt || a.uploadedAt || "";
    return left.localeCompare(right);
  });

  return existing;
}

async function cachedScriptFor(uploaded) {
  const scripts = await existingScripts(await readScripts());

  for (const script of scripts) {
    if (
      script.fingerprint === uploaded.fingerprint &&
      !samePath(script.savedPath, uploaded.savedPath)
    ) {
      return script;
    }
  }

  return duplicateUploadFor(uploaded, scripts);
}

async function duplicateUploadFor(uploaded, scripts) {
  try {
    const uploads = await readdir(UPLOAD_DIR);
    for (const upload of uploads) {
      const savedPath = path.join(UPLOAD_DIR, upload);
      if (samePath(savedPath, uploaded.savedPath)) {
        continue;
      }

      const info = await stat(savedPath);
      if (!info.isFile() || info.size !== uploaded.size) {
        continue;
      }

      const fingerprint = await fileHash(savedPath);
      if (fingerprint !== uploaded.fingerprint) {
        continue;
      }

      const scriptId = path.parse(upload).name;
      const existing = scripts.find((script) => script.scriptId === scriptId);
      return (
        (existing && { ...existing, fingerprint }) || {
          scriptId,
          fileName: uploaded.fileName,
          savedPath,
          size: info.size,
          settings: defaultSettings(),
          fingerprint,
          uploadedAt: info.birthtime.toISOString(),
          lastOpenedAt: "",
        }
      );
    }
  } catch (error) {
    if (error.code !== "ENOENT") {
      throw error;
    }
  }

  return null;
}

function samePath(left, right) {
  return path.resolve(left) === path.resolve(right);
}

function fileHash(file) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash("sha256");
    const stream = createReadStream(file);

    stream.on("data", (chunk) => hash.update(chunk));
    stream.on("error", reject);
    stream.on("end", () => resolve(hash.digest("hex")));
  });
}

async function deleteUpload(file) {
  try {
    await unlink(file);
  } catch (error) {
    if (error.code !== "ENOENT") {
      throw error;
    }
  }
}

function runBridge(args) {
  return new Promise((resolve, reject) => {
    const classPath = [
      path.join(ROOT_DIR, "web", ".bridge-build"),
      path.join(BACKEND_DIR, "lib", "*"),
    ].join(path.delimiter);

    const child = spawn("java", [
      "-cp",
      classPath,
      "web.server.bridge",
      ...args,
    ]);

    let stdout = "";
    let stderr = "";

    child.stdout.on("data", (chunk) => {
      stdout += chunk.toString("utf8");
    });

    child.stderr.on("data", (chunk) => {
      stderr += chunk.toString("utf8");
    });

    child.on("error", reject);

    child.on("close", (code) => {
      if (code !== 0) {
        reject(new Error(stderr || `Bridge exited with status ${code}`));
        return;
      }

      const text = stdout.trim();
      const json = text.split("\n").filter(Boolean).at(-1);

      if (!json) {
        reject(new Error(stderr || "Bridge returned no output."));
        return;
      }

      try {
        resolve(JSON.parse(json));
      } catch {
        reject(new Error(`Bridge returned invalid JSON: ${text}`));
      }
    });
  });
}

app.post("/api/upload", upload.single("user-file"), async (req, res) => {
  if (!req.file) {
    res.status(400).json({
      ok: false,
      error: "Please choose a script file.",
    });
    return;
  }

  const scriptId = scriptIdFor(req.file);
  const settings = settingsFrom(req.body);

  try {
    const now = new Date().toISOString();
    const uploaded = {
      scriptId,
      fileName: req.file.originalname,
      savedPath: req.file.path,
      size: req.file.size,
      settings,
      fingerprint: await fileHash(req.file.path),
      uploadedAt: now,
      lastOpenedAt: now,
    };
    const cached = await cachedScriptFor(uploaded);
    const script =
      cached ?
        {
          ...cached,
          fileName: cached.fileName || uploaded.fileName,
          settings,
          lastOpenedAt: now,
        }
      : uploaded;

    if (cached) {
      await deleteUpload(req.file.path);
    }

    const analysis = await runBridge([
      "analyze",
      script.savedPath,
      script.fileName,
    ]);

    await saveScript(script);

    res.json({
      ok: true,
      cached: Boolean(cached),
      message:
        cached ?
          "Cached upload used. Review the setup before parsing."
        : "Upload saved. Review the setup before parsing.",
      scriptId: script.scriptId,
      fileName: script.fileName,
      savedPath: script.savedPath,
      size: script.size,
      settings: script.settings,
      analysis,
    });
  } catch (error) {
    res.status(500).json({
      ok: false,
      error: error.message || "Bridge failed.",
      scriptId,
      fileName: req.file.originalname,
      savedPath: req.file.path,
      size: req.file.size,
      settings,
    });
  }
});

app.post("/api/parse", async (req, res) => {
  const settings = settingsFrom(req.body.settings);
  const characters = charactersFrom(req.body.characters);
  const removeLines = listFrom(req.body.removeLines);

  try {
    const parsed = await runBridge([
      "parse",
      req.body.savedPath,
      req.body.fileName || "",
      req.body.targetCharacter || "",
      String(req.body.bodyStartIndex ?? -1),
      String(settings.includeStageDir),
      String(settings.caseSensitive),
      String(settings.punctuation),
      String(settings.timedMode),
      characters.join(LIST_SEPARATOR),
      removeLines.join(LIST_SEPARATOR),
    ]);

    res.json({
      ok: true,
      scriptId: req.body.scriptId,
      fileName: req.body.fileName,
      settings,
      characters,
      removeLines,
      parsed,
    });
  } catch (error) {
    res.status(500).json({
      ok: false,
      error: error.message || "Parse failed.",
    });
  }
});

app.use((req, res) => {
  res.status(404).json({
    ok: false,
    error: "Not found",
  });
});

const server = app.listen(PORT, HOST, () => {
  console.log(`API running at http://${HOST}:${PORT}`);
  console.log(`Backend at ${BACKEND_DIR}`);
  console.log(`Uploads at ${UPLOAD_DIR}`);
});

process.on("SIGTERM", () => server.close());
process.on("SIGINT", () => server.close());
