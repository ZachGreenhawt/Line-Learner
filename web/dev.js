import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const npm = process.platform === "win32" ? "npm.cmd" : "npm";
const children = new Set();
let stopping = false;

start("api", process.execPath, ["server/api-server.js"]);
start("vite", npm, ["run", "vite"]);

function start(name, command, args) {
  const child = spawn(command, args, {
    cwd: __dirname,
    stdio: ["ignore", "pipe", "pipe"],
  });

  children.add(child);
  child.stdout.on("data", (chunk) => write(name, chunk));
  child.stderr.on("data", (chunk) => write(name, chunk));
  child.on("error", (error) => {
    console.error(`[${name}] ${error.message}`);
    stop(1);
  });
  child.on("close", (code, signal) => {
    children.delete(child);
    if (stopping) return;

    const reason = signal ? `signal ${signal}` : `status ${code}`;
    console.log(`[${name}] exited with ${reason}`);
    stop(code || 1);
  });

  return child;
}

function write(name, chunk) {
  for (const line of chunk.toString("utf8").split(/\r?\n/)) {
    if (line.trim()) console.log(`[${name}] ${line}`);
  }
}

function stop(code = 0) {
  stopping = true;
  for (const child of children) {
    child.kill("SIGTERM");
  }
  process.exit(code);
}

process.on("SIGINT", () => stop(0));
process.on("SIGTERM", () => stop(0));
