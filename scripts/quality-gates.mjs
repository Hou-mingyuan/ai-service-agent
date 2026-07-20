import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const ignoredBinaryExtensions = new Set([
  ".gif",
  ".ico",
  ".jpeg",
  ".jpg",
  ".jar",
  ".pdf",
  ".png",
  ".webp",
  ".zip",
]);

const secretPatterns = [
  ["OpenAI-style API key", /\bsk-[A-Za-z0-9_-]{20,}\b/g],
  ["AWS access key", /\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/g],
  ["GitHub token", /\bgh[pousr]_[A-Za-z0-9]{20,}\b/g],
  ["GitLab token", /\bglpat-[A-Za-z0-9_-]{20,}\b/g],
  ["Slack token", /\bxox[baprs]-[A-Za-z0-9-]{20,}\b/g],
  ["private key", /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/g],
];

const legacyPorts = [80 * 101, 80 * 101 + 1, 5000 + 173, 18000 + 84, 18000 + 85];
const legacyEndpoint = `/ws/${"agent"}`;
const repositoryFiles = execFileSync(
  "git",
  ["ls-files", "--cached", "--others", "--exclude-standard", "-z"],
  { cwd: root, encoding: "utf8", maxBuffer: 32 * 1024 * 1024 },
)
  .split("\0")
  .filter(Boolean)
  .filter((relative) => fs.existsSync(path.join(root, relative)));
const textFiles = repositoryFiles.filter(
  (relative) => !ignoredBinaryExtensions.has(path.extname(relative).toLowerCase()),
);

function lineNumber(text, index) {
  return text.slice(0, index).split("\n").length;
}

const findings = [];

for (const relativePath of textFiles) {
  const absolute = path.join(root, relativePath);
  const buffer = fs.readFileSync(absolute);
  if (buffer.includes(0)) continue;
  const text = buffer.toString("utf8");
  const relative = relativePath.replaceAll("\\", "/");

  for (const [label, pattern] of secretPatterns) {
    pattern.lastIndex = 0;
    for (const match of text.matchAll(pattern)) {
      findings.push(`${relative}:${lineNumber(text, match.index)}: possible ${label}`);
    }
  }

  const isGeneratedReport = relative.startsWith("docs/lighthouse-") || relative === "frontend/package-lock.json";
  if (isGeneratedReport || relative === "scripts/quality-gates.mjs") continue;

  for (const port of legacyPorts) {
    const pattern = new RegExp(`(^|[^0-9])${port}([^0-9]|$)`, "g");
    for (const match of text.matchAll(pattern)) {
      findings.push(`${relative}:${lineNumber(text, match.index)}: legacy port ${port}`);
    }
  }
  const endpointIndex = text.indexOf(legacyEndpoint);
  if (endpointIndex >= 0) {
    findings.push(`${relative}:${lineNumber(text, endpointIndex)}: legacy WebSocket endpoint`);
  }
}

if (findings.length > 0) {
  console.error("Repository quality gates FAILED:");
  for (const finding of findings) console.error(`- ${finding}`);
  process.exit(1);
}

console.log(
  `Repository quality gates PASSED: ${textFiles.length} text candidates checked; no high-risk secret signatures or legacy service endpoints.`,
);
