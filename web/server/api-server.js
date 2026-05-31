import express from "express";
import multer from "multer";
import crypto from "node:crypto";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { mkdir } from "node:fs/promises";
import { spawn } from "node:child_process";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = path.resolve(__dirname, "../..");
const BACKEND_DIR = path.join(ROOT_DIR, "backend");
const DATA_DIR = path.join(ROOT_DIR, "web", ".data");
const UPLOAD_DIR = path.join(DATA_DIR, "uploads");
const PORT = Number(process.env.API_PORT || 5174);

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

function checked(value) {
  return value === "true" || value === "on" || value === true;
}

function settingsFrom(body) {
  return {
    includeStageDir: checked(body.includeStageDir),
    caseSensitive: checked(body.caseSensitive),
    punctuation: checked(body.punctuation),
    timedMode: checked(body.timedMode),
  };
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
    const analysis = await runBridge([
      "analyze",
      req.file.path,
      req.file.originalname,
    ]);

    res.json({
      ok: true,
      message: "Upload saved and analyzed.",
      scriptId,
      fileName: req.file.originalname,
      savedPath: req.file.path,
      size: req.file.size,
      settings,
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

app.use((req, res) => {
  res.status(404).json({
    ok: false,
    error: "Not found",
  });
});

app.listen(PORT, "127.0.0.1", () => {
  console.log(`API running at http://localhost:${PORT}`);
  console.log(`Backend at ${BACKEND_DIR}`);
  console.log(`Uploads at ${UPLOAD_DIR}`);
});
