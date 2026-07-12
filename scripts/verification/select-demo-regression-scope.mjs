#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const defaultManifest = path.resolve(scriptDir, "../../docs/verification/demo-readiness-checks.json");

export function globToRegExp(glob) {
  let source = "^";
  for (let index = 0; index < glob.length; index += 1) {
    const character = glob[index];
    if (character === "*") {
      const isDouble = glob[index + 1] === "*";
      if (isDouble) {
        const followedBySlash = glob[index + 2] === "/";
        source += followedBySlash ? "(?:.*/)?" : ".*";
        index += followedBySlash ? 2 : 1;
      } else {
        source += "[^/]*";
      }
      continue;
    }
    if (character === "?") {
      source += "[^/]";
      continue;
    }
    source += character.replace(/[|\\{}()[\]^$+?.]/g, "\\$&");
  }
  return new RegExp(`${source}$`);
}

export function matchesAny(file, patterns) {
  const normalized = file.replaceAll("\\", "/");
  return patterns.some((pattern) => globToRegExp(pattern).test(normalized));
}

export function selectChecks(manifest, files) {
  const checksById = new Map(manifest.checks.map((check) => [check.id, check]));
  const selected = new Set();
  const matchedFiles = new Map();
  const unmatchedFiles = [];

  for (const rawFile of files) {
    const file = rawFile.replaceAll("\\", "/");
    if (!file || matchesAny(file, manifest.ignoredPaths ?? [])) continue;

    const matchedIds = new Set();
    for (const check of manifest.checks) {
      if (matchesAny(file, check.paths ?? [])) matchedIds.add(check.id);
    }
    for (const rule of manifest.globalRules ?? []) {
      if (!matchesAny(file, rule.paths ?? [])) continue;
      for (const id of rule.checks ?? []) {
        if (id === "*") {
          for (const check of manifest.checks) matchedIds.add(check.id);
        } else {
          matchedIds.add(id);
        }
      }
    }

    if (matchedIds.size === 0) {
      unmatchedFiles.push(file);
      continue;
    }
    matchedFiles.set(file, [...matchedIds].sort());
    for (const id of matchedIds) selected.add(id);
  }

  const queue = [...selected];
  while (queue.length > 0) {
    const id = queue.shift();
    const check = checksById.get(id);
    for (const requirement of check?.requires ?? []) {
      if (selected.has(requirement)) continue;
      selected.add(requirement);
      queue.push(requirement);
    }
  }

  return {
    selected: [...selected].map((id) => checksById.get(id)).filter(Boolean)
      .sort((left, right) => left.id.localeCompare(right.id)),
    matchedFiles: Object.fromEntries([...matchedFiles].sort(([left], [right]) => left.localeCompare(right))),
    unmatchedFiles: unmatchedFiles.sort(),
  };
}

function parseArgs(argv) {
  const args = { base: null, head: "HEAD", files: [], manifest: defaultManifest, json: false, strict: false, workingTree: false };
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (value === "--base") args.base = argv[++index];
    else if (value === "--head") args.head = argv[++index];
    else if (value === "--file") args.files.push(argv[++index]);
    else if (value === "--manifest") args.manifest = path.resolve(argv[++index]);
    else if (value === "--json") args.json = true;
    else if (value === "--strict") args.strict = true;
    else if (value === "--working-tree") args.workingTree = true;
    else throw new Error(`알 수 없는 인자: ${value}`);
  }
  return args;
}

function gitFiles(args) {
  const files = new Set(args.files);
  if (args.base) {
    const diff = execFileSync("git", ["diff", "--name-only", `${args.base}...${args.head}`], { encoding: "utf8" });
    for (const file of diff.split(/\r?\n/)) if (file) files.add(file);
  }
  if (args.workingTree) {
    const tracked = execFileSync("git", ["diff", "--name-only"], { encoding: "utf8" });
    const untracked = execFileSync("git", ["ls-files", "--others", "--exclude-standard"], { encoding: "utf8" });
    for (const file of `${tracked}\n${untracked}`.split(/\r?\n/)) if (file) files.add(file);
  }
  if (files.size === 0) throw new Error("--base, --working-tree 또는 --file로 변경 파일을 지정하세요.");
  return [...files].sort();
}

function renderText(result, files) {
  const lines = [
    `변경 파일: ${files.length}개`,
    `재검증 항목: ${result.selected.length}개`,
    ...result.selected.map((check) => `- ${check.id} [${check.area}] ${check.name}`),
  ];
  if (result.unmatchedFiles.length > 0) {
    lines.push("", `매핑되지 않은 파일: ${result.unmatchedFiles.length}개`, ...result.unmatchedFiles.map((file) => `- ${file}`));
  }
  return lines.join("\n");
}

const invokedDirectly = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (invokedDirectly) {
  try {
    const args = parseArgs(process.argv.slice(2));
    const manifest = JSON.parse(readFileSync(args.manifest, "utf8"));
    const files = gitFiles(args);
    const result = selectChecks(manifest, files);
    process.stdout.write(args.json ? `${JSON.stringify({ files, ...result }, null, 2)}\n` : `${renderText(result, files)}\n`);
    if (args.strict && result.unmatchedFiles.length > 0) process.exitCode = 2;
  } catch (error) {
    process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
    process.exitCode = 1;
  }
}
