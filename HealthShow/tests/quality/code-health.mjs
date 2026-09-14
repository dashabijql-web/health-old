import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

const ROOT = process.cwd();
const SRC_DIR = path.join(ROOT, 'src');
const TEST_DIR = path.join(ROOT, 'tests');
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-');
const ARTIFACT_DIR = path.resolve(ROOT, 'tests', 'quality', 'artifacts', RUN_ID);
const REPORT_JSON = path.join(ARTIFACT_DIR, 'code-health.json');
const REPORT_MD = path.join(ARTIFACT_DIR, 'code-health.md');

const THRESHOLDS = {
  maxVueLines: Number.parseInt(process.env.QUALITY_MAX_VUE_LINES || '650', 10),
  maxJsLines: Number.parseInt(process.env.QUALITY_MAX_JS_LINES || '500', 10),
  maxFunctionComplexity: Number.parseInt(process.env.QUALITY_MAX_FUNCTION_COMPLEXITY || '18', 10),
  maxFunctionLines: Number.parseInt(process.env.QUALITY_MAX_FUNCTION_LINES || '120', 10),
  maxConsoleLog: Number.parseInt(process.env.QUALITY_MAX_CONSOLE_LOG || '0', 10),
  maxVHtmlWithoutSanitize: Number.parseInt(process.env.QUALITY_MAX_VHTML_UNSANITIZED || '0', 10),
  maxTodoFixme: Number.parseInt(process.env.QUALITY_MAX_TODO_FIXME || '20', 10)
};

const IGNORE_DIRS = new Set(['node_modules', 'dist', '.git', 'coverage', 'artifacts', 'runs']);
const FILE_EXTENSIONS = new Set(['.vue', '.js', '.mjs', '.ts']);

const summary = {
  runId: RUN_ID,
  startedAt: new Date().toISOString(),
  thresholds: THRESHOLDS,
  totals: {
    files: 0,
    lines: 0,
    issues: 0,
    failedIssues: 0
  },
  files: [],
  issues: []
};

await fs.mkdir(ARTIFACT_DIR, { recursive: true });

async function walk(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true }).catch(() => []);
  const files = [];
  for (const entry of entries) {
    if (IGNORE_DIRS.has(entry.name)) continue;
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) files.push(...await walk(fullPath));
    else if (FILE_EXTENSIONS.has(path.extname(entry.name))) files.push(fullPath);
  }
  return files;
}

function rel(filePath) {
  return path.relative(ROOT, filePath).replaceAll('\\', '/');
}

function addIssue(filePath, line, severity, rule, message) {
  const issue = { file: rel(filePath), line, severity, rule, message };
  summary.issues.push(issue);
  if (severity === 'fail') summary.totals.failedIssues += 1;
}

function lineNumberAt(text, index) {
  return text.slice(0, index).split('\n').length;
}

function countComplexity(block) {
  const matches = block.match(/\b(if|for|while|case|catch)\b|&&|\|\||\?/g);
  return 1 + (matches ? matches.length : 0);
}

function scanFunctions(filePath, text) {
  const patterns = [
    { re: /(?:async\s+)?function\s+([A-Za-z0-9_$]+)?\s*\([^)]*\)\s*\{/g, nameGroup: 1, kind: 'function' },
    { re: /(?:const|let|var)\s+([A-Za-z0-9_$]+)\s*=\s*(?:async\s*)?\([^)]*\)\s*=>\s*\{/g, nameGroup: 1, kind: 'arrow' },
    { re: /(?:const|let|var)\s+([A-Za-z0-9_$]+)\s*=\s*(?:async\s*)?[A-Za-z0-9_$]+\s*=>\s*\{/g, nameGroup: 1, kind: 'arrow' },
    { re: /(?:^|[,{\n])\s*(?:async\s+)?([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*\{/gm, nameGroup: 1, kind: 'object-method' }
  ];
  const seen = new Set();
  const ignoredNames = new Set(['if', 'for', 'while', 'switch', 'catch', 'function']);
  for (const pattern of patterns) {
    for (const match of text.matchAll(pattern.re)) {
      const name = match[pattern.nameGroup] || '<anonymous>';
      if (ignoredNames.has(name)) continue;
      const openIndexInMatch = match[0].lastIndexOf('{');
      const start = match.index + openIndexInMatch;
      const key = `${start}:${name}`;
      if (seen.has(key)) continue;
      seen.add(key);
      const end = findMatchingBrace(text, start);
      if (end <= start) continue;
      const block = text.slice(start, end + 1);
      const lines = block.split('\n').length;
      const complexity = countComplexity(block);
      const line = lineNumberAt(text, match.index);
      if (lines > THRESHOLDS.maxFunctionLines) {
        addIssue(filePath, line, 'warn', 'function-too-long', `${name} has ${lines} lines > ${THRESHOLDS.maxFunctionLines}`);
      }
      if (complexity > THRESHOLDS.maxFunctionComplexity) {
        addIssue(filePath, line, 'warn', 'function-complexity', `${name} complexity ${complexity} > ${THRESHOLDS.maxFunctionComplexity}`);
      }
    }
  }
}

function findMatchingBrace(text, openIndex) {
  let depth = 0;
  let quote = '';
  let escaped = false;
  for (let i = openIndex; i < text.length; i += 1) {
    const ch = text[i];
    if (quote) {
      if (escaped) escaped = false;
      else if (ch === '\\') escaped = true;
      else if (ch === quote) quote = '';
      continue;
    }
    if (ch === '"' || ch === "'" || ch === '`') {
      quote = ch;
      continue;
    }
    if (ch === '{') depth += 1;
    if (ch === '}') {
      depth -= 1;
      if (depth === 0) return i;
    }
  }
  return -1;
}

function scanFile(filePath, text) {
  const lines = text.split(/\r?\n/);
  const extension = path.extname(filePath);
  summary.totals.files += 1;
  summary.totals.lines += lines.length;
  summary.files.push({ file: rel(filePath), lines: lines.length });

  const maxLines = extension === '.vue' ? THRESHOLDS.maxVueLines : THRESHOLDS.maxJsLines;
  if (lines.length > maxLines) {
    addIssue(filePath, 1, 'warn', 'file-too-large', `${lines.length} lines > ${maxLines}`);
  }

  lines.forEach((lineText, index) => {
    const lineNo = index + 1;
    if (/console\.log\s*\(/.test(lineText) && rel(filePath).startsWith('src/')) addIssue(filePath, lineNo, 'fail', 'console-log', 'console.log left in source');
    if (/TODO|FIXME/i.test(lineText)) addIssue(filePath, lineNo, 'warn', 'todo-fixme', 'TODO/FIXME marker');
    if (/\bsetInterval\s*\(/.test(lineText)) addIssue(filePath, lineNo, 'warn', 'set-interval', 'setInterval requires cleanup proof');
    if (/\bsetTimeout\s*\(/.test(lineText)) addIssue(filePath, lineNo, 'warn', 'set-timeout', 'setTimeout should have lifecycle/cleanup consideration');
    if (/\beval\s*\(|new Function\s*\(/.test(lineText)) addIssue(filePath, lineNo, 'fail', 'dynamic-code-execution', 'eval/new Function is forbidden');
    if (/v-html\s*=/.test(lineText)) {
      const nearby = lines.slice(Math.max(0, index - 30), Math.min(lines.length, index + 31)).join('\n');
      if (!/DOMPurify|sanitize|marked|purify/i.test(nearby)) {
        addIssue(filePath, lineNo, process.env.QUALITY_STRICT_SECURITY === '1' ? 'fail' : 'warn', 'v-html-unsanitized', 'v-html without nearby sanitizer/purifier evidence');
      } else {
        addIssue(filePath, lineNo, 'warn', 'v-html-review', 'v-html present; sanitizer evidence found nearby but requires review');
      }
    }
  });

  scanFunctions(filePath, text);
}

function buildMarkdownReport() {
  const topFiles = [...summary.files].sort((a, b) => b.lines - a.lines).slice(0, 20);
  return [
    '# HealthShow code-health report',
    '',
    `- run_id: ${summary.runId}`,
    `- started_at: ${summary.startedAt}`,
    `- finished_at: ${summary.finishedAt}`,
    `- files: ${summary.totals.files}`,
    `- lines: ${summary.totals.lines}`,
    `- issues: ${summary.totals.issues}`,
    `- failed_issues: ${summary.totals.failedIssues}`,
    '',
    '## Top large files',
    '',
    '| file | lines |',
    '| --- | ---: |',
    ...topFiles.map((item) => `| ${item.file} | ${item.lines} |`),
    '',
    '## Issues',
    '',
    '| severity | rule | file | line | message |',
    '| --- | --- | --- | ---: | --- |',
    ...summary.issues.map((item) => `| ${item.severity} | ${item.rule} | ${item.file} | ${item.line} | ${item.message} |`)
  ].join('\n');
}

const files = [...await walk(SRC_DIR), ...await walk(path.join(TEST_DIR, 'api')), ...await walk(path.join(TEST_DIR, 'e2e')), ...await walk(path.join(TEST_DIR, 'shared')), ...await walk(path.join(TEST_DIR, 'performance'))];
for (const filePath of files) {
  const text = await fs.readFile(filePath, 'utf8');
  scanFile(filePath, text);
}

summary.totals.issues = summary.issues.length;
summary.finishedAt = new Date().toISOString();
await fs.writeFile(REPORT_JSON, JSON.stringify(summary, null, 2), 'utf8');
await fs.writeFile(REPORT_MD, buildMarkdownReport(), 'utf8');

console.log(JSON.stringify({
  artifactDir: ARTIFACT_DIR,
  reportFile: REPORT_MD,
  files: summary.totals.files,
  issues: summary.totals.issues,
  failedIssues: summary.totals.failedIssues
}, null, 2));


const consoleLogIssues = summary.issues.filter((item) => item.rule === 'console-log').length;
const unsanitizedHtmlIssues = summary.issues.filter((item) => item.rule === 'v-html-unsanitized' && item.severity === 'fail').length;
const dynamicCodeIssues = summary.issues.filter((item) => item.rule === 'dynamic-code-execution').length;
const hardFailures = dynamicCodeIssues + Math.max(0, consoleLogIssues - THRESHOLDS.maxConsoleLog) + Math.max(0, unsanitizedHtmlIssues - THRESHOLDS.maxVHtmlWithoutSanitize);
if (hardFailures > 0) process.exitCode = 1;
