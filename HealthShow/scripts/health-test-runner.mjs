import { spawn } from 'node:child_process'
import fs from 'node:fs/promises'
import net from 'node:net'
import path from 'node:path'
import process from 'node:process'
import { chromium } from 'playwright'

const ROOT = process.cwd()
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-')
const ARTIFACT_DIR = path.resolve(ROOT, 'tests', 'runs', RUN_ID)
const SUMMARY_PATH = path.join(ARTIFACT_DIR, 'health-test-runner-summary.json')

const args = process.argv.slice(2)
const profile = args[0] || 'fast'
const options = parseOptions(args.slice(1))
const requestedDataSource = options.source || process.env.API_DATA_SOURCE || 'old'

if (requestedDataSource !== 'old') {
  console.error(`health-old only supports --source old (received: ${requestedDataSource})`)
  process.exit(2)
}

const dataSource = 'old'
const expectNonEmpty = '1'

const PROFILES = {
  fast: {
    description: 'Pure Node tests and source-guard tests; no backend/browser/database required.',
    preflight: 'fast',
    commands: [
      command('npm', ['run', 'test:warning-semantics'], { env: { TZ: 'UTC' } }),
      command('npm', ['run', 'test:realtime-detail']),
      command('npm', ['run', 'test:employee-profile-governance']),
      command('npm', ['run', 'test:report-center-export']),
      command('npm', ['run', 'test:ai-chat-layout']),
      command('npm', ['run', 'test:visual-audit-summary']),
      command('npm', ['run', 'test:performance-triage-summary'])
    ]
  },
  frontend: {
    description: 'Frontend confidence gate: fast tests + structure audit + production build.',
    preflight: 'frontend',
    commands: [
      command('npm', ['run', 'test:warning-semantics'], { env: { TZ: 'UTC' } }),
      command('npm', ['run', 'test:employee-profile-governance']),
      command('npm', ['run', 'audit:structure']),
      command('npm', ['run', 'build'])
    ]
  },
  integration: {
    description: 'Frontend/backend integration gate; requires running frontend and backend services.',
    preflight: 'integration',
    commands: [
      command('npm', ['run', 'audit:api']),
      command('npm', ['run', 'audit:data:old']),
      command('npm', ['run', 'audit:auth'])
    ]
  },
  perf: {
    description: 'Performance gate: API latency and browser page-load budgets; requires running frontend/backend services.',
    preflight: 'integration',
    commands: [
      command('npm', ['run', 'audit:perf:old'])
    ]
  },
  quality: {
    description: 'Maintainability and product-quality gate: code-health + UX/competitor benchmark; no backend required.',
    preflight: 'frontend',
    commands: [
      command('npm', ['run', 'audit:quality:code']),
      command('npm', ['run', 'audit:product'])
    ]
  },
  full: {
    description: 'Full HealthShow gate; requires frontend, backend, browser and pipeline services.',
    preflight: 'full',
    commands: [
      command('npm', ['run', 'test:frontend']),
      command('npm', ['run', 'test:quality']),
      command('npm', ['run', 'audit:api']),
      command('npm', ['run', 'audit:data:old']),
      command('npm', ['run', 'audit:auth']),
      command('npm', ['run', 'audit:e2e']),
      command('npm', ['run', 'audit:perf:old']),
      command('npm', ['run', 'audit:pipeline']),
      command('npm', ['run', 'audit:pipeline-warning']),
      command('npm', ['run', 'audit:write'])
    ]
  }
}

if (!PROFILES[profile]) {
  console.error(`Unknown profile: ${profile}`)
  console.error(`Available profiles: ${Object.keys(PROFILES).join(', ')}`)
  process.exit(2)
}

await fs.mkdir(ARTIFACT_DIR, { recursive: true })
const summary = {
  runId: RUN_ID,
  profile,
  description: PROFILES[profile].description,
  dataSource,
  expectNonEmpty: expectNonEmpty === '1',
  startedAt: new Date().toISOString(),
  status: 'passed',
  preflight: [],
  commands: [],
  artifactDir: ARTIFACT_DIR
}

console.log(`[health-test-runner] profile=${profile}`)
console.log(`[health-test-runner] dataSource=${dataSource} expectNonEmpty=${expectNonEmpty}`)
console.log(`[health-test-runner] artifacts=${ARTIFACT_DIR}`)

const preflight = await runPreflight(PROFILES[profile].preflight)
summary.preflight = preflight
printPreflight(preflight)
const blocked = preflight.filter((item) => item.required && !item.ok)
if (blocked.length > 0) {
  summary.status = 'blocked'
  summary.finishedAt = new Date().toISOString()
  await writeSummary(summary)
  console.error(`[health-test-runner] BLOCKED: ${blocked.map((item) => item.name).join(', ')}`)
  process.exit(3)
}

for (const step of PROFILES[profile].commands) {
  const record = await runCommand(step)
  summary.commands.push(record)
  if (record.exitCode !== 0) {
    summary.status = 'failed'
    summary.finishedAt = new Date().toISOString()
    await writeSummary(summary)
    console.error(`[health-test-runner] FAILED: ${record.name} exit=${record.exitCode}`)
    process.exit(record.exitCode || 1)
  }
}

summary.finishedAt = new Date().toISOString()
await writeSummary(summary)
console.log(`[health-test-runner] PASS ${profile}`)
console.log(`[health-test-runner] summary=${SUMMARY_PATH}`)

function parseOptions(values) {
  const result = {}
  for (let i = 0; i < values.length; i += 1) {
    const arg = values[i]
    if (arg === '--source') result.source = values[++i]
    if (arg === '--expect-non-empty') result.expectNonEmpty = values[++i]
  }
  return result
}

function command(bin, cmdArgs, opts = {}) {
  return {
    name: [bin, ...cmdArgs].join(' '),
    bin,
    args: cmdArgs,
    env: opts.env || {}
  }
}

async function runPreflight(kind) {
  const checks = []
  checks.push(checkEnv('node', process.version, true))
  checks.push(checkEnv('cwd', ROOT, true))
  checks.push(checkEnv('data-source', dataSource, true))
  checks.push(checkEnv('expect-non-empty', expectNonEmpty, true))

  if (kind === 'fast') return checks

  checks.push(await checkFile('package-lock', path.join(ROOT, 'package-lock.json'), true))

  if (kind === 'frontend') return checks

  checks.push(await checkTcp('backend-8080', 8080, true, '启动后端：mvn spring-boot:run -f D:/Health/HealthData/pom.xml'))
  checks.push(await checkHttp('frontend-http', 'http://127.0.0.1:9528/', true, { method: 'GET' }, '启动前端：cd D:/Health/HealthShow && npm run dev'))
  checks.push(await checkBrowser('playwright-browser', true, '先执行 `npm run audit:e2e:install`'))

  if (kind === 'integration') return checks

  checks.push(await checkEnvVar('SQL_PASSWORD', process.env.SQL_PASSWORD || '', true, 'pipeline 写入/回归测试需要 SQL_PASSWORD'))
  checks.push(await checkCommand('sqlcmd', process.env.SQLCMD_BIN || 'sqlcmd', ['-?'], true, '先安装 sqlcmd 或设置 SQLCMD_BIN'))
  checks.push(await checkTcp('sql-server', parseSqlServerPort(process.env.SQL_SERVER || 'localhost,1433'), true, '启动 SQL Server'))
  checks.push(await checkTcp('tcp-9000', 9000, true, '启动 HealthData 后端 TCP 监听'))
  checks.push(await checkTcp('redis-6379', 6379, true, '启动 Redis 后再跑 pipeline/full'))
  return checks
}

function checkEnv(name, detail, required) {
  return { name, ok: Boolean(detail), required, detail: String(detail), hint: '' }
}

async function checkFile(name, filePath, required) {
  try {
    await fs.access(filePath)
    return { name, ok: true, required, detail: filePath, hint: '' }
  } catch (error) {
    return { name, ok: false, required, detail: error.message, hint: `missing ${filePath}` }
  }
}

function checkEnvVar(name, value, required, hint) {
  return { name, ok: Boolean(value), required, detail: Boolean(value) ? 'set' : 'missing', hint }
}

function parseSqlServerPort(sqlServer) {
  const parts = String(sqlServer).split(',')
  const maybePort = Number.parseInt(parts[1] || '', 10)
  return Number.isFinite(maybePort) ? maybePort : 1433
}

async function checkCommand(name, command, args = [], required, hint) {
  try {
    const child = spawn(command, args, {
      cwd: ROOT,
      shell: process.platform === 'win32',
      stdio: 'ignore'
    })
    const result = await new Promise((resolve) => {
      child.on('error', (error) => resolve({ ok: false, detail: error.message }))
      child.on('close', (exitCode) => resolve({ ok: exitCode === 0, detail: `${command} ${args.join(' ')}`.trim() }))
    })
    return { name, ok: result.ok, required, detail: result.detail, hint }
  } catch (error) {
    return { name, ok: false, required, detail: error.message, hint }
  }
}

async function checkBrowser(name, required, hint) {
  try {
    const executable = chromium.executablePath()
    await fs.access(executable)
    return { name, ok: true, required, detail: executable, hint }
  } catch (error) {
    return { name, ok: false, required, detail: error.message, hint }
  }
}

async function checkHttp(name, url, required, fetchOptions, hint) {
  try {
    const response = await fetch(url, fetchOptions)
    return { name, ok: response.ok, required, detail: `HTTP ${response.status} ${url}`, hint }
  } catch (error) {
    return { name, ok: false, required, detail: error.message, hint }
  }
}

async function checkTcp(name, port, required, hint) {
  const result = await new Promise((resolve) => {
    const socket = net.createConnection({ host: '127.0.0.1', port })
    const finish = (ok, detail) => {
      socket.removeAllListeners()
      socket.destroy()
      resolve({ ok, detail })
    }
    socket.setTimeout(2000, () => finish(false, `timeout 127.0.0.1:${port}`))
    socket.on('connect', () => finish(true, `127.0.0.1:${port}`))
    socket.on('error', (error) => finish(false, error.message))
  })
  return { name, ok: result.ok, required, detail: result.detail, hint }
}

function printPreflight(checks) {
  for (const check of checks) {
    const status = check.ok ? 'OK     ' : check.required ? 'BLOCKED' : 'WARN   '
    console.log(`${status} ${check.name}: ${check.detail}`)
    if (!check.ok && check.hint) console.log(`        hint: ${check.hint}`)
  }
}

function runCommand(step) {
  return new Promise((resolve) => {
    console.log(`[health-test-runner] RUN ${step.name}`)
    const startedAt = Date.now()
    const child = spawn(step.bin, step.args, {
      cwd: ROOT,
      stdio: 'inherit',
      shell: process.platform === 'win32',
      env: {
        ...process.env,
        API_DATA_SOURCE: dataSource,
        API_EXPECT_NON_EMPTY: expectNonEmpty,
        PIPELINE_DATA_SOURCE: dataSource,
        SQL_DB: 'health',
        ...step.env
      }
    })
    child.on('close', (code) => {
      resolve({
        name: step.name,
        exitCode: code,
        durationMs: Date.now() - startedAt
      })
    })
  })
}

async function writeSummary(data) {
  await fs.writeFile(SUMMARY_PATH, `${JSON.stringify(data, null, 2)}\n`, 'utf8')
}
