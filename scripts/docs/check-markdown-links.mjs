#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, rmSync, statSync } from 'node:fs';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import process from 'node:process';

const HELP = `Usage: node scripts/docs/check-markdown-links.mjs [options]

Options:
  --main-repo-only      Check main-repository targets without reading private gitlinks.
  --no-submodule-fetch  Do not fetch an unavailable pinned submodule commit.
  --verbose             Print the pinned commit and source used for each gitlink.
  --help                Show this help.
`;

const args = new Set(process.argv.slice(2));
if (args.has('--help')) {
  process.stdout.write(HELP);
  process.exit(0);
}

const unknownArgs = [...args].filter(
  (arg) => !['--main-repo-only', '--no-submodule-fetch', '--verbose'].includes(arg),
);
if (unknownArgs.length > 0) {
  process.stderr.write(`Unknown option: ${unknownArgs.join(', ')}\n${HELP}`);
  process.exit(2);
}

const allowSubmoduleFetch = !args.has('--no-submodule-fetch');
const mainRepoOnly = args.has('--main-repo-only');
const verbose = args.has('--verbose');

function gitEnvironment() {
  const token = process.env.DOCS_SUBMODULE_TOKEN;
  if (!token) return process.env;
  const authorization = Buffer.from(`x-access-token:${token}`, 'utf8').toString('base64');
  return {
    ...process.env,
    GIT_CONFIG_COUNT: '1',
    GIT_CONFIG_KEY_0: 'http.https://github.com/.extraheader',
    GIT_CONFIG_VALUE_0: `AUTHORIZATION: basic ${authorization}`,
  };
}

function runGit(repoRoot, gitArgs, options = {}) {
  return execFileSync('git', gitArgs, {
    cwd: repoRoot,
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
    stdio: options.quiet ? ['ignore', 'pipe', 'pipe'] : ['ignore', 'pipe', 'pipe'],
    env: gitEnvironment(),
  }).trimEnd();
}

function tryGit(repoRoot, gitArgs) {
  try {
    return { ok: true, value: runGit(repoRoot, gitArgs, { quiet: true }) };
  } catch (error) {
    return {
      ok: false,
      error: error.stderr?.toString().trim() || error.message,
    };
  }
}

const root = runGit(process.cwd(), ['rev-parse', '--show-toplevel']);
const rootPosix = root.replaceAll('\\', '/');

function toPosix(value) {
  return value.replaceAll('\\', '/');
}

function splitNul(value) {
  return value.split('\0').filter(Boolean);
}

const trackedEntries = splitNul(
  execFileSync('git', ['ls-files', '-z'], {
    cwd: root,
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  }),
).map(toPosix);
const trackedFiles = new Set(trackedEntries);
const trackedDirectories = new Set();
for (const file of trackedEntries) {
  let directory = path.posix.dirname(file);
  while (directory !== '.') {
    trackedDirectories.add(directory);
    directory = path.posix.dirname(directory);
  }
}
const trackedTopLevelSegments = new Set(
  trackedEntries.map((entry) => entry.split('/')[0]),
);
const markdownPathsByBasename = new Map();
for (const file of trackedEntries.filter((entry) => entry.toLowerCase().endsWith('.md'))) {
  const basename = path.posix.basename(file);
  const paths = markdownPathsByBasename.get(basename) ?? [];
  paths.push(file);
  markdownPathsByBasename.set(basename, paths);
}

function parseGitmodules() {
  const gitmodulesPath = path.join(root, '.gitmodules');
  if (!existsSync(gitmodulesPath)) return [];

  const result = tryGit(root, [
    'config',
    '-f',
    '.gitmodules',
    '--get-regexp',
    '^submodule\\..*\\.path$',
  ]);
  if (!result.ok || !result.value.trim()) return [];

  return result.value.split(/\r?\n/).map((line) => {
    const firstSpace = line.indexOf(' ');
    const key = line.slice(0, firstSpace);
    const modulePath = toPosix(line.slice(firstSpace + 1).trim());
    const name = key.slice('submodule.'.length, -'.path'.length);
    const url = runGit(root, [
      'config',
      '-f',
      '.gitmodules',
      '--get',
      `submodule.${name}.url`,
    ]);
    const indexLine = runGit(root, ['ls-files', '--stage', '--', modulePath]);
    const indexMatch = indexLine.match(/^160000 ([0-9a-f]{40}) 0\t/);
    const treeLine = runGit(root, ['ls-tree', 'HEAD', '--', modulePath]);
    const treeMatch = treeLine.match(/^160000 commit ([0-9a-f]{40})\t/);
    const match = indexMatch ?? treeMatch;
    if (!match) {
      throw new Error(`${modulePath} is declared as a submodule but the index and HEAD have no gitlink`);
    }
    return { name, path: modulePath, url, sha: match[1] };
  });
}

const submoduleDefinitions = parseGitmodules().sort(
  (left, right) => right.path.length - left.path.length,
);
const temporaryRepositories = [];
process.on('exit', () => {
  for (const temporaryRepository of temporaryRepositories) {
    rmSync(temporaryRepository, { recursive: true, force: true });
  }
});

function addDirectories(files) {
  const directories = new Set();
  for (const file of files) {
    let directory = path.posix.dirname(file);
    while (directory !== '.') {
      directories.add(directory);
      directory = path.posix.dirname(directory);
    }
  }
  return directories;
}

function gitObjectReader(definition) {
  const worktree = path.join(root, ...definition.path.split('/'));
  if (existsSync(path.join(worktree, '.git'))) {
    const available = tryGit(worktree, [
      'cat-file',
      '-e',
      `${definition.sha}^{commit}`,
    ]);
    if (available.ok) {
      return {
        source: 'initialized worktree',
        git: (gitArgs) => runGit(worktree, gitArgs),
      };
    }
  }

  const moduleGitDirResult = tryGit(root, [
    'rev-parse',
    '--git-path',
    `modules/${definition.path}`,
  ]);
  if (moduleGitDirResult.ok) {
    const moduleGitDir = path.resolve(root, moduleGitDirResult.value);
    if (existsSync(moduleGitDir)) {
      const available = tryGit(root, [
        `--git-dir=${moduleGitDir}`,
        'cat-file',
        '-e',
        `${definition.sha}^{commit}`,
      ]);
      if (available.ok) {
        return {
          source: 'local submodule object store',
          git: (gitArgs) => runGit(root, [`--git-dir=${moduleGitDir}`, ...gitArgs]),
        };
      }
    }
  }

  if (!allowSubmoduleFetch) {
    throw new Error(
      `${definition.path}@${definition.sha} is not available locally ` +
        '(rerun without --no-submodule-fetch or initialize the submodule)',
    );
  }

  const temporaryRoot = mkdtempSync(path.join(tmpdir(), 'careertuner-doc-links-'));
  temporaryRepositories.push(temporaryRoot);
  runGit(root, ['init', '--bare', temporaryRoot]);
  runGit(root, [
    `--git-dir=${temporaryRoot}`,
    'remote',
    'add',
    'origin',
    definition.url,
  ]);
  runGit(root, [
    `--git-dir=${temporaryRoot}`,
    'fetch',
    '--quiet',
    '--depth=1',
    '--filter=blob:none',
    '--no-tags',
    'origin',
    definition.sha,
  ]);
  return {
    source: 'temporary pinned fetch',
    git: (gitArgs) => runGit(root, [`--git-dir=${temporaryRoot}`, ...gitArgs]),
  };
}

function loadSubmoduleTree(definition) {
  const reader = gitObjectReader(definition);
  const files = new Set(
    reader
      .git(['ls-tree', '-r', '--name-only', definition.sha])
      .split(/\r?\n/)
      .filter(Boolean)
      .map(toPosix),
  );
  const directories = addDirectories(files);
  return {
    ...definition,
    source: reader.source,
    files,
    directories,
    read(innerPath) {
      return reader.git(['show', `${definition.sha}:${innerPath}`]);
    },
  };
}

const textExtensions = new Set([
  '.md',
  '.mdx',
  '.html',
  '.htm',
  '.json',
  '.json5',
  '.jsonl',
  '.yaml',
  '.yml',
  '.java',
  '.kt',
  '.kts',
  '.gradle',
  '.properties',
  '.xml',
  '.toml',
  '.ini',
  '.conf',
  '.sh',
  '.bash',
  '.zsh',
  '.ps1',
  '.bat',
  '.cmd',
  '.py',
  '.js',
  '.jsx',
  '.mjs',
  '.cjs',
  '.ts',
  '.tsx',
  '.vue',
  '.css',
  '.scss',
  '.sql',
  '.txt',
]);
const textBasenames = new Set([
  'Dockerfile',
  'Makefile',
  '.editorconfig',
]);
const excludedSegments = new Set([
  'node_modules',
  'build',
  'dist',
  'vendor',
  '.gradle',
  'graphify-out',
]);

function isScannable(file) {
  const segments = file.split('/');
  if (segments.some((segment) => excludedSegments.has(segment))) return false;
  const extension = path.posix.extname(file).toLowerCase();
  return textExtensions.has(extension) || textBasenames.has(path.posix.basename(file));
}

function lineNumberAt(text, index) {
  let line = 1;
  for (let cursor = 0; cursor < index; cursor += 1) {
    if (text.charCodeAt(cursor) === 10) line += 1;
  }
  return line;
}

function maskMarkdownCode(text) {
  const lines = text.split(/(?<=\n)/);
  let fence = null;
  return lines
    .map((line) => {
      const fenceMatch = line.match(/^\s*(```+|~~~+)/);
      if (fenceMatch) {
        const marker = fenceMatch[1][0];
        fence = fence === marker ? null : fence ?? marker;
        return line.replace(/[^\r\n]/g, ' ');
      }
      if (fence) return line.replace(/[^\r\n]/g, ' ');
      return line.replace(/`[^`\r\n]*`/g, (value) => ' '.repeat(value.length));
    })
    .join('');
}

function extractMarkdownDestinations(text) {
  const masked = maskMarkdownCode(text);
  const references = [];

  for (let index = 0; index < masked.length - 1; index += 1) {
    if (masked[index] !== ']' || masked[index + 1] !== '(') continue;
    if (masked.lastIndexOf('[', index) < masked.lastIndexOf('\n', index)) continue;
    let cursor = index + 2;
    while (/\s/.test(masked[cursor] ?? '')) cursor += 1;
    const start = cursor;
    let destination = '';
    if (masked[cursor] === '<') {
      const end = masked.indexOf('>', cursor + 1);
      if (end === -1) continue;
      destination = text.slice(cursor + 1, end);
    } else {
      let depth = 0;
      let escaped = false;
      for (; cursor < masked.length; cursor += 1) {
        const character = masked[cursor];
        if (escaped) {
          escaped = false;
          continue;
        }
        if (character === '\\') {
          escaped = true;
          continue;
        }
        if (character === '(') depth += 1;
        if (character === ')') {
          if (depth === 0) break;
          depth -= 1;
        }
        if (/\s/.test(character) && depth === 0) break;
      }
      destination = text.slice(start, cursor);
    }
    if (destination) {
      references.push({
        raw: destination,
        index: start,
        kind: 'markdown-link',
      });
    }
  }

  const definitionPattern = /^\s*\[[^\]]+\]:\s*(?:<([^>]+)>|(\S+))/gm;
  for (const match of masked.matchAll(definitionPattern)) {
    const raw = match[1] ?? match[2];
    const offset = match[0].indexOf(raw);
    references.push({
      raw,
      index: match.index + Math.max(offset, 0),
      kind: 'markdown-reference',
    });
  }

  const htmlPattern = /<[a-z][\w:-]*\b[^>]+?(?:href|src)\s*=\s*["']([^"']+)["'][^>]*>/gi;
  for (const match of masked.matchAll(htmlPattern)) {
    const raw = match[1];
    references.push({
      raw,
      index: match.index + match[0].indexOf(raw),
      kind: 'html-attribute',
    });
  }

  return references;
}

function extractBareMarkdownPaths(text) {
  const references = [];
  const tokenPattern =
    /(^|[\s"'`([{<=,:])((?:\.{1,2}\/|\/)?(?:[\p{L}\p{N}_.-]+\/)*[\p{L}\p{N}_.-]+\.md(?:#[^\s"'`\])}>;,]+)?)/gmu;
  for (const match of text.matchAll(tokenPattern)) {
    const raw = match[2];
    const index = match.index + match[1].length;
    const lineStart = text.lastIndexOf('\n', index) + 1;
    const lineEnd = text.indexOf('\n', index);
    const line = text.slice(lineStart, lineEnd === -1 ? text.length : lineEnd);
    if (line.includes('docs-link-check: ignore')) continue;
    const tokenStart = Math.max(
      text.lastIndexOf(' ', index - 1),
      text.lastIndexOf('\n', index - 1),
      text.lastIndexOf('\t', index - 1),
    );
    if (text.slice(tokenStart + 1, index).includes('://')) continue;
    references.push({ raw, index, kind: 'markdown-path' });
  }

  const quotedPattern = /(["'`])((?:\.{1,2}\/|\/)?[^"'`\r\n]*?\.md(?:#[^"'`\s]+)?)\1/g;
  for (const match of text.matchAll(quotedPattern)) {
    const raw = match[2].trim();
    if (!raw || raw.includes('://')) continue;
    const index = match.index + 1 + match[2].indexOf(raw);
    const lineStart = text.lastIndexOf('\n', index) + 1;
    const lineEnd = text.indexOf('\n', index);
    const line = text.slice(lineStart, lineEnd === -1 ? text.length : lineEnd);
    if (line.includes('docs-link-check: ignore')) continue;
    references.push({ raw, index, kind: 'quoted-markdown-path' });
  }
  return references;
}

function isExternal(destination) {
  return (
    /^[a-z][a-z0-9+.-]*:/i.test(destination) ||
    destination.startsWith('//')
  );
}

function safeDecode(value) {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function splitDestination(destination) {
  const hashIndex = destination.indexOf('#');
  const queryIndex = destination.indexOf('?');
  const pathEnd = [hashIndex, queryIndex]
    .filter((index) => index >= 0)
    .reduce((minimum, index) => Math.min(minimum, index), destination.length);
  return {
    path: safeDecode(destination.slice(0, pathEnd)).replaceAll('\\', '/'),
    fragment:
      hashIndex >= 0
        ? safeDecode(destination.slice(hashIndex + 1, queryIndex > hashIndex ? queryIndex : undefined))
        : '',
  };
}

function findGitlink(repoPath) {
  return submoduleDefinitions.find(
    (definition) =>
      repoPath === definition.path || repoPath.startsWith(`${definition.path}/`),
  );
}

function knownMainPath(repoPath) {
  return trackedFiles.has(repoPath) || trackedDirectories.has(repoPath) || Boolean(findGitlink(repoPath));
}

function normalizeRepoPath(repoPath) {
  const normalized = path.posix.normalize(repoPath);
  return normalized.length > 1 ? normalized.replace(/\/+$/, '') : normalized;
}

function isRepositoryMarkdownPath(source, raw) {
  const destination = splitDestination(raw);
  const rawPath = destination.path;
  if (!rawPath || /[*{}]|\.\.\.|%0\d/i.test(rawPath)) return false;

  const normalizedWithoutDot = normalizeRepoPath(rawPath.replace(/^\.\//, ''));
  const firstSegment = normalizedWithoutDot.split('/')[0];
  if (trackedTopLevelSegments.has(firstSegment)) return true;

  const relativeCandidate = normalizeRepoPath(
    path.posix.join(path.posix.dirname(source), rawPath),
  );
  if (knownMainPath(relativeCandidate)) return true;

  if (!rawPath.includes('/')) {
    return (markdownPathsByBasename.get(rawPath)?.length ?? 0) === 1;
  }
  return false;
}

function resolveRepoPath(source, rawPath, kind) {
  if (!rawPath) return source;
  if (rawPath.startsWith('/')) {
    const rootCandidate = normalizeRepoPath(rawPath.slice(1));
    return knownMainPath(rootCandidate) ? rootCandidate : null;
  }

  if (kind === 'markdown-path' || kind === 'quoted-markdown-path') {
    const rootCandidate = normalizeRepoPath(rawPath.replace(/^\.\//, ''));
    const firstSegment = rootCandidate.split('/')[0];
    if (!rawPath.startsWith('../') && trackedTopLevelSegments.has(firstSegment)) {
      return rootCandidate;
    }
    if (!rawPath.includes('/')) {
      const basenameMatches = markdownPathsByBasename.get(rawPath) ?? [];
      if (basenameMatches.length === 1) return basenameMatches[0];
    }
  }
  return normalizeRepoPath(path.posix.join(path.posix.dirname(source), rawPath));
}

function githubSlug(value) {
  return value
    .replace(/<[^>]*>/g, '')
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/[*_~`]/g, '')
    .trim()
    .toLowerCase()
    .replace(/[\p{P}\p{S}]/gu, (character) =>
      character === '-' || character === '_' ? character : '',
    )
    .replace(/\s+/g, '-');
}

function markdownAnchors(content) {
  const anchors = new Set();
  const counts = new Map();
  let fence = null;
  for (const line of content.split(/\r?\n/)) {
    const fenceMatch = line.match(/^\s*(```+|~~~+)/);
    if (fenceMatch) {
      const marker = fenceMatch[1][0];
      fence = fence === marker ? null : fence ?? marker;
      continue;
    }
    if (fence) continue;
    const heading = line.match(/^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$/);
    if (!heading) continue;
    const base = githubSlug(heading[1]);
    const count = counts.get(base) ?? 0;
    counts.set(base, count + 1);
    anchors.add(count === 0 ? base : `${base}-${count}`);
  }
  for (const match of content.matchAll(/<[^>]+\s(?:id|name)=["']([^"']+)["']/gi)) {
    anchors.add(match[1]);
  }
  return anchors;
}

const scannableFiles = trackedEntries.filter((file) => {
  const absolute = path.join(root, ...file.split('/'));
  return isScannable(file) && existsSync(absolute) && statSync(absolute).isFile();
});
const references = [];
let externalSkipped = 0;
for (const source of scannableFiles) {
  const absolute = path.join(root, ...source.split('/'));
  const text = readFileSync(absolute, 'utf8');
  if (text.includes('\0')) continue;
  const extension = path.posix.extname(source).toLowerCase();
  const extracted = [
    ...(['.md', '.html', '.htm'].includes(extension)
      ? extractMarkdownDestinations(text)
      : []),
    ...extractBareMarkdownPaths(text),
  ];
  const seen = new Set();
  for (const reference of extracted) {
    const raw = reference.raw.trim();
    if (!raw) continue;
    if (isExternal(raw)) {
      externalSkipped += 1;
      continue;
    }
    if (
      (reference.kind === 'markdown-path' || reference.kind === 'quoted-markdown-path') &&
      !isRepositoryMarkdownPath(source, raw)
    ) {
      continue;
    }
    const line = lineNumberAt(text, reference.index);
    const key = `${line}\0${raw}`;
    if (seen.has(key)) continue;
    seen.add(key);
    references.push({ ...reference, raw, source, line });
  }
}

const referencedGitlinks = new Set();
for (const reference of references) {
  const destination = splitDestination(reference.raw);
  const repoPath = resolveRepoPath(
    reference.source,
    destination.path,
    reference.kind,
  );
  if (!repoPath) continue;
  const gitlink = findGitlink(repoPath);
  if (gitlink) referencedGitlinks.add(gitlink.path);
}

const submoduleTrees = new Map();
const setupErrors = [];
for (const definition of submoduleDefinitions) {
  if (mainRepoOnly) continue;
  if (!referencedGitlinks.has(definition.path)) continue;
  try {
    const tree = loadSubmoduleTree(definition);
    submoduleTrees.set(definition.path, tree);
    if (verbose) {
      process.stdout.write(
        `gitlink ${tree.path}@${tree.sha} (${tree.source}, ${tree.files.size} entries)\n`,
      );
    }
  } catch (error) {
    setupErrors.push({
      source: '.gitmodules',
      line: 1,
      kind: 'gitlink',
      raw: definition.path,
      target: `${definition.path}@${definition.sha}`,
      reason: error.message,
    });
  }
}

const anchorCache = new Map();
function anchorsForMain(target) {
  if (!anchorCache.has(target)) {
    const absolute = path.join(root, ...target.split('/'));
    anchorCache.set(target, markdownAnchors(readFileSync(absolute, 'utf8')));
  }
  return anchorCache.get(target);
}

function anchorsForSubmodule(tree, innerPath) {
  const cacheKey = `${tree.path}@${tree.sha}:${innerPath}`;
  if (!anchorCache.has(cacheKey)) {
    anchorCache.set(cacheKey, markdownAnchors(tree.read(innerPath)));
  }
  return anchorCache.get(cacheKey);
}

const errors = [...setupErrors];
let checked = 0;
let anchorChecks = 0;
let webRootSkipped = 0;
let gitlinkReferencesSkipped = 0;
for (const reference of references) {
  const destination = splitDestination(reference.raw);
  const target = resolveRepoPath(
    reference.source,
    destination.path,
    reference.kind,
  );
  if (target === null) {
    webRootSkipped += 1;
    continue;
  }
  if (target === '..' || target.startsWith('../')) {
    errors.push({
      ...reference,
      target,
      reason: 'path escapes the repository root',
    });
    continue;
  }

  const definition = findGitlink(target);
  if (definition && mainRepoOnly) {
    gitlinkReferencesSkipped += 1;
    continue;
  }

  checked += 1;
  if (definition) {
    const tree = submoduleTrees.get(definition.path);
    if (!tree) continue;
    const innerPath = target === definition.path ? '' : target.slice(definition.path.length + 1);
    const exists =
      innerPath === '' || tree.files.has(innerPath) || tree.directories.has(innerPath);
    if (!exists) {
      errors.push({
        ...reference,
        target: `${target} @ ${definition.sha}`,
        reason: 'target is absent from the pinned submodule tree',
      });
      continue;
    }
    if (destination.fragment && innerPath.toLowerCase().endsWith('.md')) {
      anchorChecks += 1;
      if (!anchorsForSubmodule(tree, innerPath).has(destination.fragment)) {
        errors.push({
          ...reference,
          target: `${target}#${destination.fragment} @ ${definition.sha}`,
          reason: 'Markdown anchor does not exist',
        });
      }
    }
    continue;
  }

  const exists = trackedFiles.has(target) || trackedDirectories.has(target);
  if (!exists) {
    errors.push({
      ...reference,
      target,
      reason: 'target is not tracked by the main repository',
    });
    continue;
  }
  if (
    destination.fragment &&
    trackedFiles.has(target) &&
    target.toLowerCase().endsWith('.md')
  ) {
    anchorChecks += 1;
    if (!anchorsForMain(target).has(destination.fragment)) {
      errors.push({
        ...reference,
        target: `${target}#${destination.fragment}`,
        reason: 'Markdown anchor does not exist',
      });
    }
  }
}

errors.sort(
  (left, right) =>
    left.source.localeCompare(right.source) || left.line - right.line || left.raw.localeCompare(right.raw),
);

process.stdout.write('CareerTuner documentation link check\n');
process.stdout.write(`- repository: ${rootPosix}\n`);
process.stdout.write(`- tracked text files scanned: ${scannableFiles.length}\n`);
process.stdout.write(
  `- local references checked: ${checked} (${anchorChecks} Markdown anchors)\n`,
);
process.stdout.write(`- external URLs skipped without network access: ${externalSkipped}\n`);
process.stdout.write(`- web-root routes skipped: ${webRootSkipped}\n`);
process.stdout.write(`- gitlink references skipped: ${gitlinkReferencesSkipped}\n`);
process.stdout.write(`- pinned gitlinks inspected: ${submoduleTrees.size}\n`);

if (errors.length > 0) {
  process.stderr.write(`\nFound ${errors.length} documentation link error(s):\n`);
  for (const error of errors) {
    process.stderr.write(
      `ERROR ${error.source}:${error.line} [${error.kind}] ${error.raw}` +
        ` -> ${error.target} (${error.reason})\n`,
    );
  }
  process.exitCode = 1;
} else {
  process.stdout.write('PASS: all checked local documentation references resolve.\n');
}
