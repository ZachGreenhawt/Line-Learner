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
const DATA_DIR = path.join(ROOT_DIR, "web", ".data");
const UPLOAD_DIR = path.join(DATA_DIR, "uploads");
const SCRIPTS_FILE = path.join(DATA_DIR, "scripts.json");
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


function parserReportBody(issues) {
  const first = issues[0] || {};
  const run = first.parseRun || {};
  const s = first.settings || {};
  const onoff = (value) => (value ? "on" : "off");
  const lines = [
    `Parser problems flagged in one practice session: ${issues.length} note(s).`,
    "",
    "RUN",
    `  source lines: ~${run.sourceLines || 0}`,
    `  practice lines: ${run.practiceLines || 0} across ${run.turns || 0} turns`,
    `  body start line: ${run.bodyStartLine || "?"}`,
    `  settings: stage-in-cue ${onoff(s.includeStageDirectionsInCue)} · case ${onoff(s.caseSensitive)} · punctuation ${onoff(s.punctuation)} · timed ${onoff(s.timedMode)} · music ${onoff(s.includeMusicAsLines)}`,
    `  parser version: ${cleanText(first.parserVersion, 40) || "web-client"}`,
    "",
    "ISSUES (masked, IP-safe)",
  ];

  issues.forEach((issue, i) => {
    const b = issue.block || {};
    const ctx = issue.context || {};
    lines.push(
      `  ${i + 1}) ${cleanText(issue.kind, 40) || "note"} · line ${b.index || ctx.line || "?"} · ${cleanText(ctx.round, 40) || "run"}`,
      `     cue  "${cleanText(b.cueMask, 120)}" (${b.cueW || 0} words)`,
      `     line "${cleanText(b.lineMask, 120)}" (${b.lineW || 0} words)`,
    );
    const note = cleanText(issue.noteMask, 500);
    if (note) lines.push(`     note: ${note}`);
    const shape = cleanText(issue.formatting?.shape, 160);
    if (shape) lines.push(`     shape: ${shape}`);
  });

  lines.push("", "— Your Script (per-session parser report)");
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

app.post("/api/event", (req, res) => {
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
});

cleanupExpiredSessions().catch(() => {});
const sweepTimer = setInterval(
  () => cleanupExpiredSessions().catch(() => {}),
  Math.min(SESSION_TTL_MS, 10 * 60 * 1000),
);
sweepTimer.unref();

process.on("SIGTERM", () => server.close());
process.on("SIGINT", () => server.close());
