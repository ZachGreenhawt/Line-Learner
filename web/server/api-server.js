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
  rm,
  stat,
  unlink,
  writeFile,
} from "node:fs/promises";
import { spawn } from "node:child_process";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = path.resolve(__dirname, "../..");
const BACKEND_DIR = path.join(ROOT_DIR, "backend");
const DATA_DIR =
  process.env.DATA_DIR ||
  process.env.RAILWAY_VOLUME_MOUNT_PATH ||
  path.join(ROOT_DIR, "web", ".data");
const UPLOAD_DIR = path.join(DATA_DIR, "uploads");
const SCRIPTS_FILE = path.join(DATA_DIR, "scripts.json");
const METRICS_FILE = path.join(DATA_DIR, "metrics.json");
const PARSER_SESSIONS_DIR = path.join(DATA_DIR, "parser_sessions");
const SESSION_TTL_MS = Number(process.env.SESSION_TTL_MS) || 60 * 60 * 1000;
const PORT = Number(process.env.PORT || process.env.API_PORT || 5174);
const HOST = process.env.HOST || "0.0.0.0";
const CORS_ORIGINS = (process.env.CORS_ORIGIN || "*")
  .split(",")
  .map((origin) => origin.trim())
  .filter(Boolean);
const CORS_ALLOW_ALL = CORS_ORIGINS.includes("*");
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
app.use((req, res, next) => {
  const origin = req.headers.origin;
  if (CORS_ALLOW_ALL) {
    res.setHeader("Access-Control-Allow-Origin", "*");
  } else if (origin && CORS_ORIGINS.includes(origin)) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
  }

  res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    res.sendStatus(204);
    return;
  }

  next();
});
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
    includeMusic: false,
  };
}

function checked(value) {
  return value === "true" || value === "on" || value === true;
}

function settingsFrom(body) {
  body = body || {};

  return {
    includeStageDir:
      checked(body.includeStageDir) ||
      checked(body.includeStageDirectionsInCue),
    caseSensitive: checked(body.caseSensitive),
    punctuation: checked(body.punctuation),
    timedMode: checked(body.timedMode),
    includeMusic:
      checked(body.includeMusic) || checked(body.includeMusicAsLines),
  };
}

function charactersFrom(value) {
  return listFrom(value);
}

function listFrom(value) {
  if (typeof value === "string") {
    value = value.split(/[,\r\n]+/);
  }

  if (!Array.isArray(value)) {
    return [];
  }

  return value.map((name) => String(name).trim()).filter(Boolean);
}

function safeFileName(name, fallback = "script.txt") {
  const base = path
    .basename(name || fallback)
    .replace(/[^A-Za-z0-9._ -]/g, "_");
  return base || fallback;
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

function emptyMetrics() {
  return {
    startedAt: new Date().toISOString(),
    updatedAt: "",
    counts: {},
    days: {},
  };
}

function mergeMetrics(saved) {
  return {
    ...emptyMetrics(),
    ...(saved && typeof saved === "object" ? saved : {}),
    counts:
      saved && typeof saved.counts === "object" && saved.counts ?
        saved.counts
      : {},
    days:
      saved && typeof saved.days === "object" && saved.days ? saved.days : {},
  };
}

async function readMetrics() {
  try {
    return mergeMetrics(JSON.parse(await readFile(METRICS_FILE, "utf8")));
  } catch (error) {
    if (error.code === "ENOENT") {
      return emptyMetrics();
    }
    console.warn("[metrics] could not read saved metrics", {
      reason: error.message,
    });
    return emptyMetrics();
  }
}

let metrics = await readMetrics();
let metricsWrite = Promise.resolve();

function saveMetrics() {
  metricsWrite = metricsWrite
    .catch(() => {})
    .then(async () => {
      await mkdir(DATA_DIR, { recursive: true });
      await writeFile(METRICS_FILE, JSON.stringify(metrics, null, 2));
    });

  metricsWrite.catch((error) => {
    console.error("[metrics] could not save metrics", {
      reason: error.message,
    });
  });

  return metricsWrite;
}

async function recordMetric(name, amount = 1) {
  const key = String(name || "")
    .trim()
    .replace(/[^A-Za-z0-9_.:-]/g, "_")
    .slice(0, 80);

  if (!key) {
    return;
  }

  const count = Number.isFinite(amount) ? amount : 1;
  const day = new Date().toISOString().slice(0, 10);
  metrics.updatedAt = new Date().toISOString();
  metrics.counts[key] = (metrics.counts[key] || 0) + count;
  metrics.days[day] = metrics.days[day] || {};
  metrics.days[day][key] = (metrics.days[day][key] || 0) + count;
  await saveMetrics();
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

async function deleteSession(scriptId) {
  if (!scriptId) {
    return;
  }

  try {
    const uploads = await readdir(UPLOAD_DIR);
    for (const file of uploads) {
      if (path.parse(file).name === scriptId) {
        await deleteUpload(path.join(UPLOAD_DIR, file));
      }
    }
  } catch (error) {
    if (error.code !== "ENOENT") {
      throw error;
    }
  }

  await rm(path.join(PARSER_SESSIONS_DIR, scriptId), {
    recursive: true,
    force: true,
  });

  const scripts = await readScripts();
  const remaining = scripts.filter((script) => script.scriptId !== scriptId);
  if (remaining.length !== scripts.length) {
    await writeScripts(remaining);
  }
}

async function touchScript(scriptId) {
  if (!scriptId) {
    return;
  }
  try {
    const scripts = await readScripts();
    let changed = false;
    for (const script of scripts) {
      if (script.scriptId === scriptId) {
        script.lastOpenedAt = new Date().toISOString();
        changed = true;
      }
    }
    if (changed) {
      await writeScripts(scripts);
    }
  } catch {}
}
async function cleanupExpiredSessions() {
  const now = Date.now();
  const live = new Set();

  try {
    const scripts = await readScripts();
    for (const script of scripts) {
      const stamp = Date.parse(script.lastOpenedAt || script.uploadedAt || "");
      if (stamp && now - stamp > SESSION_TTL_MS) {
        await deleteSession(script.scriptId);
      } else {
        live.add(script.scriptId);
      }
    }
  } catch {}

  await sweepOrphans(UPLOAD_DIR, now, live, (name) => path.parse(name).name);
  await sweepOrphans(PARSER_SESSIONS_DIR, now, live, (name) => name);
}

async function sweepOrphans(dir, now, live, idOf) {
  let entries;
  try {
    entries = await readdir(dir);
  } catch {
    return;
  }

  for (const name of entries) {
    if (live.has(idOf(name))) {
      continue;
    }
    const target = path.join(dir, name);
    try {
      const info = await stat(target);
      if (now - info.mtimeMs > SESSION_TTL_MS) {
        await rm(target, { recursive: true, force: true });
      }
    } catch {}
  }
}

function runBridge(args, { scriptId } = {}) {
  return new Promise((resolve, reject) => {
    const classPath = [
      path.join(ROOT_DIR, "web", ".bridge-build"),
      path.join(BACKEND_DIR, "lib", "*"),
    ].join(path.delimiter);

    const jvm = ["-Xmx600m", `-Dll.sessionRoot=${PARSER_SESSIONS_DIR}`];
    if (scriptId) {
      jvm.push(`-Dll.sessionId=${scriptId}`);
    }

    const child = spawn("java", [
      ...jvm,
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

    child.on("close", (code, signal) => {
      if (code !== 0) {
        const status = signal ? `signal ${signal}` : `status ${code}`;
        reject(new Error(stderr || `Bridge exited with ${status}`));
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

async function analyzeFile(file, body) {
  const scriptId = scriptIdFor(file);
  const settings = settingsFrom(body);

  const now = new Date().toISOString();
  const uploaded = {
    scriptId,
    fileName: file.originalname,
    savedPath: file.path,
    size: file.size,
    settings,
    fingerprint: await fileHash(file.path),
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
    await deleteUpload(file.path);
  }

  await saveScript(script);

  const analysis = await runBridge(
    ["analyze", script.savedPath, script.fileName],
    { scriptId: script.scriptId },
  );

  await saveScript(script);
  await recordMetric(cached ? "cached_script_used" : "script_uploaded");

  return {
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
  };
}

function sendMissingScript(res) {
  res.status(400).json({
    ok: false,
    error: "Please choose a script file.",
  });
}

app.get("/api/health", async (req, res) => {
  try {
    const backend = await stat(path.join(BACKEND_DIR, "src"));
    res.json({
      ok: true,
      service: "line-learner-api",
      backendFound: backend.isDirectory(),
    });
  } catch {
    res.status(500).json({
      ok: false,
      error: "Backend folder was not found.",
    });
  }
});

app.post("/api/upload", upload.single("user-file"), async (req, res) => {
  if (!req.file) {
    sendMissingScript(res);
    return;
  }

  const scriptId = scriptIdFor(req.file);
  const settings = settingsFrom(req.body);

  try {
    const result = await analyzeFile(req.file, req.body);
    res.json(result);
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

app.post("/api/analyze", upload.single("script"), async (req, res) => {
  if (!req.file) {
    sendMissingScript(res);
    return;
  }

  try {
    const result = await analyzeFile(req.file, req.body);
    res.json({
      ...result.analysis,
      scriptId: result.scriptId,
      fileName: result.fileName,
      savedPath: result.savedPath,
      size: result.size,
      settings: result.settings,
      cached: result.cached,
      analysis: result.analysis,
    });
  } catch (error) {
    res.status(500).json({
      ok: false,
      error: error.message || "Bridge failed.",
    });
  }
});

app.post("/api/analyze-text", async (req, res) => {
  const text = String(req.body?.text || "");
  if (!text.trim()) {
    res.status(400).json({
      ok: false,
      error: "Paste script text before parsing.",
    });
    return;
  }

  const requestedName = safeFileName(req.body.fileName, "Pasted Script.txt");
  const fileName =
    requestedName.toLowerCase().endsWith(".txt") ?
      requestedName
    : `${requestedName}.txt`;
  const filename = `${crypto.randomUUID()}.txt`;
  const filePath = path.join(UPLOAD_DIR, filename);

  try {
    await writeFile(filePath, text, "utf8");
    const result = await analyzeFile(
      {
        filename,
        originalname: fileName,
        path: filePath,
        size: Buffer.byteLength(text),
      },
      req.body,
    );
    res.json({
      ...result.analysis,
      scriptId: result.scriptId,
      fileName: result.fileName,
      savedPath: result.savedPath,
      size: result.size,
      settings: result.settings,
      cached: result.cached,
      analysis: result.analysis,
    });
  } catch (error) {
    await deleteUpload(filePath);
    res.status(500).json({
      ok: false,
      error: error.message || "Bridge failed.",
    });
  }
});

async function scriptFrom(body) {
  if (body.savedPath) {
    return {
      scriptId: body.scriptId,
      fileName: body.fileName || "",
      savedPath: body.savedPath,
    };
  }

  const scripts = await existingScripts(await readScripts());
  const script = scripts.find((item) => item.scriptId === body.scriptId);
  if (!script) {
    return null;
  }

  return script;
}

app.post("/api/parse", async (req, res) => {
  const settings = settingsFrom(req.body.settings);
  const characters = charactersFrom(req.body.characters);
  const removeLines = listFrom(req.body.removeLines || req.body.cleanup);

  try {
    const script = await scriptFrom(req.body);
    if (!script) {
      res.status(404).json({
        ok: false,
        error:
          "That uploaded script could not be found. Upload it again to continue.",
      });
      return;
    }

    const parsed = await runBridge(
      [
        "parse",
        script.savedPath,
        script.fileName || "",
        req.body.targetCharacter || "",
        String(req.body.bodyStartIndex ?? -1),
        String(settings.includeStageDir),
        String(settings.caseSensitive),
        String(settings.punctuation),
        String(settings.timedMode),
        String(settings.includeMusic),
        characters.join(LIST_SEPARATOR),
        removeLines.join(LIST_SEPARATOR),
      ],
      { scriptId: script.scriptId },
    );

    await touchScript(script.scriptId);
    await recordMetric("script_parsed");
    if (parsed.total) {
      await recordMetric("practice_lines_created", Number(parsed.total));
    }

    res.json({
      ...parsed,
      ok: true,
      scriptId: script.scriptId,
      fileName: script.fileName,
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

app.post(
  "/api/session/end",
  express.json({ type: "*/*" }),
  async (req, res) => {
    const scriptId = String((req.body && req.body.scriptId) || "").trim();
    if (!scriptId) {
      res.status(400).json({ ok: false, error: "Missing scriptId." });
      return;
    }

    try {
      await deleteSession(scriptId);
      await recordMetric("session_ended");
      res.json({ ok: true });
    } catch (error) {
      res.status(500).json({
        ok: false,
        error: error.message || "Could not end the session.",
      });
    }
  },
);

const RESEND_API_KEY = process.env.RESEND_API_KEY || "";
const FEEDBACK_FROM =
  process.env.FEEDBACK_FROM || "Your Script <noreply@yourscript.app>";
const FEEDBACK_IMPACT_TO =
  process.env.FEEDBACK_IMPACT_TO || "impact@yourscript.app";
const FEEDBACK_DEBUG_TO =
  process.env.FEEDBACK_DEBUG_TO || "debug@yourscript.app";
const PARSER_ISSUES_FROM = process.env.PARSER_ISSUES_FROM || FEEDBACK_FROM;
const PARSER_ISSUES_TO = process.env.PARSER_ISSUES_TO || FEEDBACK_DEBUG_TO;

function cleanText(value, max = 8000) {
  if (typeof value !== "string") return "";
  return value.replace(/\r\n?/g, "\n").trim().slice(0, max);
}

function cleanEmail(value) {
  return cleanText(value, 254).toLowerCase();
}

function validEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function feedbackSubject(kind) {
  if (kind === "error") return "[Your Script] Parser issue";
  if (kind === "story") return "[Your Script] A success story";
  return "[Your Script] Feedback";
}

function feedbackTo(kind) {
  return kind === "error" ? FEEDBACK_DEBUG_TO : FEEDBACK_IMPACT_TO;
}

function feedbackBody(body, kind) {
  const note = cleanText(body.note, 5000);
  const senderEmail = cleanEmail(body.senderEmail);
  const from = cleanText(body.from, 80);
  const diagnostics = cleanText(body.diagnostics, 12000);
  const error = body.error && typeof body.error === "object" ? body.error : {};

  const lines = [];

  if (kind === "error") {
    lines.push(
      "Something didn't parse right. Details below:",
      "",
      "What went wrong:",
      note || "(no note provided)",
      "",
      "-- debug info --",
      `message: ${cleanText(error.message, 500) || "(none)"}`,
      `where:   ${cleanText(error.context, 200) || from || "(unknown)"}`,
    );
  } else if (kind === "story") {
    lines.push(
      "Sharing a win with Your Script:",
      "",
      note || "(no note provided)",
    );
  } else {
    lines.push("Your note:", "", note || "(no note provided)");
  }

  if (diagnostics) {
    lines.push("", "-- safe parser diagnostics --", diagnostics);
  }

  lines.push(
    "",
    "-- request context --",
    `reply to: ${senderEmail}`,
    `from:    ${from || "(unknown)"}`,
    `time:    ${cleanText(body.at, 80) || new Date().toISOString()}`,
    `url:     ${cleanText(body.url, 400)}`,
    `browser: ${cleanText(body.ua, 500)}`,
  );

  return lines.join("\n");
}

function resendMessage(status) {
  if (status === 401) return "Resend API key is invalid.";
  if (status === 403) return "Resend rejected the sender or domain settings.";
  if (status === 422) return "Resend rejected the email settings.";
  return "Feedback could not be sent right now.";
}

function safeErrorLabel(error) {
  if (!error) return "unknown";
  if (typeof error.statusCode === "number")
    return `resend_status_${error.statusCode}`;
  if (typeof error.status === "number") return `resend_status_${error.status}`;
  if (error.code) return String(error.code).slice(0, 80);
  if (error.name) return String(error.name).slice(0, 80);
  return "send_failed";
}

async function sendEmail({ to, from, subject, text, replyTo = "" }) {
  if (!RESEND_API_KEY) {
    const error = new Error("Email send failed.");
    error.code = "missing_resend_key";
    error.publicMessage = "RESEND_API_KEY is not configured.";
    throw error;
  }

  const payload = {
    from,
    to: Array.isArray(to) ? to : [to],
    subject,
    text,
  };

  if (replyTo) {
    payload.reply_to = replyTo;
  }

  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${RESEND_API_KEY}`,
      "Content-Type": "application/json",
      "User-Agent": "Your Script API",
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const error = new Error("Email send failed.");
    error.statusCode = response.status;
    error.publicMessage = resendMessage(response.status);
    throw error;
  }

  return response.json().catch(() => ({}));
}


const ISSUE_LABELS = {
  wrong_speaker: "Wrong speaker",
  stage_direction: "Stage direction issue",
  dialogue: "Missed dialogue",
  lyric: "Lyric issue",
  music_cue: "Music cue issue",
  split_block: "Needs split",
  merge_block: "Needs merge",
  exclude_line: "Should exclude",
};

function issueKind(issue) {
  return cleanText(issue?.kind, 60) || "note";
}

function issueLabel(issue) {
  const kind = issueKind(issue);
  return ISSUE_LABELS[kind] || kind.replace(/_/g, " ");
}

function cueType(mask) {
  const cue = cleanText(mask, 160);
  if (!cue) return "missing cue";
  if (cue.includes("STARTS THE SCENE")) return "scene-start cue";
  if (cue.startsWith("UNKNOWN:")) return "unknown-speaker cue";
  if (cue.startsWith("[REVIEW CUE")) return "review cue";
  return "speaker cue";
}

function valueScore(issue) {
  const kind = issueKind(issue);
  let score = 20;
  const reasons = [];

  if (["wrong_speaker", "split_block", "merge_block"].includes(kind)) {
    score += 35;
    reasons.push("changes parser structure");
  }
  if (["lyric", "music_cue"].includes(kind)) {
    score += 30;
    reasons.push("musical-theatre classifier signal");
  }
  if (["stage_direction", "dialogue", "exclude_line"].includes(kind)) {
    score += 25;
    reasons.push("block classification signal");
  }
  if (cleanText(issue?.after?.expectedSpeakerMask, 80)) {
    score += 20;
    reasons.push("includes corrected speaker");
  }
  if (cleanText(issue?.formatting?.shape, 160)) {
    score += 10;
    reasons.push("includes formatting shape");
  }

  const label =
    score >= 75 ? "very high" : score >= 55 ? "high" : score >= 35 ? "medium" : "low";
  return { score, label, reasons };
}

function likelyCauses(issue) {
  const kind = issueKind(issue);
  const b = issue.block || {};
  const type = cueType(b.cueMask);
  const causes = [];

  if (kind === "wrong_speaker") {
    causes.push("SpeakerHeadingIndex may have missed or misread a nearby heading.");
    causes.push("SpeakerBlockBuilder may have carried dialogue into the previous speaker block.");
    causes.push("CuePairBuilder may have paired the target line with the wrong previous turn.");
    if (type === "scene-start cue") {
      causes.push("Scene-start cue means no previous non-target cue was found before this target line.");
    }
  } else if (kind === "stage_direction") {
    causes.push("StageDetector or source-only block rules likely classified spoken text as action.");
    causes.push("Check parenthetical, indentation, and action-verb rules around this block.");
  } else if (kind === "dialogue") {
    causes.push("Dialogue may have been dropped as stage/source-only text or page furniture.");
    causes.push("Check line continuation and bare-turn detection around this block.");
  } else if (kind === "lyric") {
    causes.push("MusicDetector may have missed lyric shape, song boundary, or ensemble context.");
  } else if (kind === "music_cue") {
    causes.push("Music cue may have been treated as stage direction or dialogue.");
  } else if (kind === "split_block") {
    causes.push("A speaker change or stage boundary may be embedded inside one parsed block.");
  } else if (kind === "merge_block") {
    causes.push("A wrapped dialogue line may have been split into separate blocks.");
  } else if (kind === "exclude_line") {
    causes.push("Page furniture, running header, or cleanup removal may not have matched this line.");
  }

  return causes;
}

function missingSignals(issue) {
  const missing = [];
  const kind = issueKind(issue);

  if (kind === "wrong_speaker" && !cleanText(issue?.after?.expectedSpeakerMask, 80)) {
    missing.push("correct speaker");
  }
  if (!cleanText(issue?.before?.classification, 80)) {
    missing.push("parser block classification");
  }
  if (!cleanText(issue?.before?.confidence, 80)) {
    missing.push("parser confidence");
  }
  if (!cleanText(issue?.before?.rules, 240)) {
    missing.push("rules triggered/rejected");
  }
  if (!issue?.neighbors) {
    missing.push("previous/next parsed blocks");
  }
  if (!issue?.formatting?.indent && !issue?.formatting?.shape) {
    missing.push("formatting pattern");
  }

  return missing;
}

function patternKey(issue) {
  const kind = issueKind(issue);
  const b = issue.block || {};
  const shape = cleanText(issue.formatting?.shape, 120) || "shape_unknown";
  const cue = cueType(b.cueMask).replace(/\s+/g, "_");
  const music = issue.settings?.includeMusicAsLines ? "music_on" : "music_off";
  return [kind, cue, shape, music].join(" | ");
}

function settingsLine(settings = {}) {
  const onoff = (value) => (value ? "on" : "off");
  return [
    `stage-in-cue ${onoff(settings.includeStageDirectionsInCue)}`,
    `case ${onoff(settings.caseSensitive)}`,
    `punctuation ${onoff(settings.punctuation)}`,
    `timed ${onoff(settings.timedMode)}`,
    `music ${onoff(settings.includeMusicAsLines)}`,
  ].join(", ");
}

function parserReportBody(issues) {
  const first = issues[0] || {};
  const run = first.parseRun || {};
  const values = issues.map(valueScore);
  const top = values.reduce(
    (best, value) => (value.score > best.score ? value : best),
    { score: 0, label: "low", reasons: [] },
  );
  const kindCounts = {};

  for (const issue of issues) {
    const kind = issueKind(issue);
    kindCounts[kind] = (kindCounts[kind] || 0) + 1;
  }

  const lines = [
    `Parser correction report: ${issues.length} issue(s)`,
    "",
    "SESSION",
    `- Priority: ${top.label} (${top.score})`,
    `- Main failure types: ${Object.entries(kindCounts)
      .map(([kind, count]) => `${kind} x${count}`)
      .join(", ")}`,
    `- Input: ${cleanText(run.input?.kind, 80) || "unknown"} (~${cleanText(String(run.input?.sizeKB || "?"), 20)} KB)`,
    `- Run: ${run.practiceLines || 0} practice lines, ${run.turns || 0} turns, ~${run.sourceLines || 0} source lines`,
    `- Body start line: ${run.bodyStartLine || "?"}`,
    `- Settings: ${settingsLine(first.settings || {})}`,
    `- Parser version: ${cleanText(first.parserVersion, 40) || "web-client"}`,
    "",
    "WHY THIS IS USEFUL",
    "- Treat each issue as parser output vs user correction.",
    "- Use the pattern key to group repeated failures across sessions.",
    "- Missing signals below are the next fields to add if this report is not enough.",
  ];

  issues.forEach((issue, i) => {
    const b = issue.block || {};
    const ctx = issue.context || {};
    const before = issue.before || {};
    const after = issue.after || {};
    const value = values[i];
    const note = cleanText(issue.noteMask, 500);
    const expectedSpeaker = cleanText(after.expectedSpeakerMask, 80);
    const missing = missingSignals(issue);

    lines.push(
      "",
      `ISSUE ${i + 1}: ${issueLabel(issue)} (${value.label}, ${value.score})`,
      `- Location: practice line ${b.index || ctx.line || "?"}, ${cleanText(ctx.round, 40) || "run"}, ${cleanText(ctx.mode, 40) || "mode unknown"}`,
      `- Pattern key: ${patternKey(issue)}`,
      "",
      "Parser output",
      `- Classification: ${cleanText(before.classification, 80) || "practice_pair"}`,
      `- Speaker shown/assigned: ${cleanText(before.speaker || b.characterMask, 80) || "unknown"}`,
      `- Cue type: ${cueType(b.cueMask)}`,
      `- Cue mask: \"${cleanText(b.cueMask, 160)}\" (${b.cueW || 0} words)`,
      `- Line mask: \"${cleanText(b.lineMask, 160)}\" (${b.lineW || 0} words)`,
      `- Formatting: ${cleanText(issue.formatting?.shape, 180) || "not captured"}`,
      "",
      "User correction",
      `- Requested change: ${issueKind(issue)}`,
      `- Correct speaker: ${expectedSpeaker || "not provided"}`,
      `- Note: ${note || "none"}`,
      "",
      "Likely parser areas to inspect",
      ...likelyCauses(issue).map((cause) => `- ${cause}`),
      "",
      "Next debug steps",
      `- Reproduce with body start line ${run.bodyStartLine || "?"} and settings above.`,
      "- Inspect the affected turn plus the previous and next parsed turns.",
      "- Check whether source cleanup, page furniture, OCR heading recovery, or music detection changed this block.",
      "- If repeated, add a small edgecase file matching the pattern key.",
    );

    if (value.reasons.length) {
      lines.push("", "Value reasons", ...value.reasons.map((reason) => `- ${reason}`));
    }
    if (missing.length) {
      lines.push("", "Missing high-value data", ...missing.map((item) => `- ${item}`));
    }
  });

  lines.push("", "Privacy: script text is masked; use source line numbers and pattern keys for reproduction.");
  lines.push("Your Script parser intelligence report");
  return lines.join("\n");
}

app.post("/api/parser-report", async (req, res) => {
  const issues = Array.isArray(req.body?.issues) ? req.body.issues.slice(0, 50) : [];
  if (!issues.length) {
    return res.status(400).json({ ok: false, error: "No parser issues to report." });
  }

  try {
    await sendEmail({
      from: PARSER_ISSUES_FROM,
      to: PARSER_ISSUES_TO,
      subject: `[Your Script] Parser issues — ${issues.length} from one session`,
      text: parserReportBody(issues),
    });
    await recordMetric("parser_report_sent");
    await recordMetric("parser_issues_reported", issues.length);
    res.json({ ok: true, recorded: issues.length });
  } catch (error) {
    console.error("[parser-report] send failed", {
      reason: safeErrorLabel(error),
    });
    res.status(502).json({
      ok: false,
      error: error.publicMessage || "Couldn't send the parser report right now.",
    });
  }
});

app.post("/api/feedback", async (req, res) => {
  const body = req.body || {};
  const kind = ["error", "story"].includes(body.kind) ? body.kind : "general";
  const note = cleanText(body.note, 5000);
  const senderEmail = cleanEmail(body.senderEmail);

  if (!validEmail(senderEmail)) {
    return res.status(400).json({
      ok: false,
      error: "Enter a valid email before sending.",
    });
  }

  if (!note && kind !== "error") {
    return res.status(400).json({
      ok: false,
      error: "Write a short note before sending.",
    });
  }

  try {
    const result = await sendEmail({
      from: FEEDBACK_FROM,
      to: feedbackTo(kind),
      subject: feedbackSubject(kind),
      text: feedbackBody({ ...body, note, senderEmail }, kind),
      replyTo: senderEmail,
    });

    await recordMetric(`feedback_${kind}`);
    res.json({ ok: true });
  } catch (error) {
    console.error("[feedback] send failed", {
      reason: safeErrorLabel(error),
    });

    res.status(500).json({
      ok: false,
      error: "Feedback could not be sent right now.",
    });
  }
});

app.post("/api/event", async (req, res) => {
  try {
    await recordMetric(req.body?.event || "event");
  } catch {}
  res.sendStatus(204);
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
  console.log(`Metrics at ${METRICS_FILE}`);
});

cleanupExpiredSessions().catch(() => {});
const sweepTimer = setInterval(
  () => cleanupExpiredSessions().catch(() => {}),
  Math.min(SESSION_TTL_MS, 10 * 60 * 1000),
);
sweepTimer.unref();

async function shutdown() {
  try {
    await saveMetrics();
  } catch {}
  server.close();
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
