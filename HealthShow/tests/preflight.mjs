import { spawn } from 'node:child_process';
import fs from 'node:fs/promises';
import net from 'node:net';
import { chromium } from 'playwright';

const mode = process.argv[2] || 'e2e';
const checks = [];
const BLOCKED_EXIT_CODE = 3;

function addCheck(name, ok, detail, hint = '') {
  checks.push({ name, ok, detail, hint });
}

function connectPort(port, host = '127.0.0.1', timeout = 2000) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ port, host });

    const finish = (ok, detail) => {
      socket.removeAllListeners();
      socket.destroy();
      resolve({ ok, detail });
    };

    socket.setTimeout(timeout, () => finish(false, `timeout ${host}:${port}`));
    socket.on('connect', () => finish(true, `${host}:${port}`));
    socket.on('error', (error) => finish(false, error.message));
  });
}

async function checkFrontend() {
  try {
    const response = await fetch('http://127.0.0.1:9528/', { method: 'GET' });
    addCheck('frontend-dev-server', response.ok, `GET / => HTTP ${response.status}`, '先执行 `npm run dev`');
  } catch (error) {
    addCheck('frontend-dev-server', false, error.message, '先执行 `npm run dev`');
  }
}

async function checkBackend() {
  const result = await connectPort(8080);
  addCheck('backend-8080', result.ok, result.detail, '先执行 `mvn spring-boot:run -f D:/Health/HealthData/pom.xml`');
}

async function checkPlaywrightBrowser() {
  try {
    const executable = chromium.executablePath();
    await fs.access(executable);
    addCheck('playwright-browser', true, executable);
  } catch (error) {
    addCheck('playwright-browser', false, error.message, '先执行 `npm run audit:e2e:install`');
  }
}

async function checkTcp9000() {
  const result = await connectPort(9000);
  addCheck('tcp-9000', result.ok, result.detail, '先启动 HealthData 后端，它会监听 TCP 9000');
}

async function checkRedis() {
  const result = await connectPort(6379);
  addCheck('redis-6379', result.ok, result.detail, '先启动 Redis');
}

function parseSqlServerPort(sqlServer) {
  const parts = String(sqlServer || '').split(',');
  const port = Number.parseInt(parts[1] || '', 10);
  return Number.isFinite(port) ? port : 1433;
}

async function checkCommand(name, command, args = [], hint = '') {
  const result = await new Promise((resolve) => {
    try {
      const child = spawn(command, args, {
        stdio: 'ignore',
        shell: process.platform === 'win32'
      });
      child.on('error', (error) => resolve({ ok: false, detail: error.message }));
      child.on('close', (exitCode) => resolve({ ok: exitCode === 0, detail: `${command} ${args.join(' ')}`.trim() }));
    } catch (error) {
      resolve({ ok: false, detail: error.message });
    }
  });
  addCheck(name, result.ok, result.detail, hint);
}

async function checkSql() {
  addCheck('SQL_PASSWORD', Boolean(process.env.SQL_PASSWORD), process.env.SQL_PASSWORD ? 'set' : 'missing', 'pipeline 写入/回归测试需要 SQL_PASSWORD');
  await checkCommand('sqlcmd', process.env.SQLCMD_BIN || 'sqlcmd', ['-?'], '先安装 sqlcmd 或设置 SQLCMD_BIN');
  const result = await connectPort(parseSqlServerPort(process.env.SQL_SERVER || 'localhost,1433'));
  addCheck('sql-server', result.ok, result.detail, '启动 SQL Server');
}

await checkPlaywrightBrowser();
await checkFrontend();
await checkBackend();

if (mode === 'pipeline' || mode === 'all') {
  await checkTcp9000();
  await checkRedis();
  await checkSql();
}

const failed = checks.filter((check) => !check.ok);

for (const check of checks) {
  const prefix = check.ok ? 'OK     ' : 'BLOCKED';
  console.log(`${prefix} ${check.name}: ${check.detail}`);
  if (!check.ok && check.hint) {
    console.log(`        hint: ${check.hint}`);
  }
}

if (failed.length > 0) {
  console.error(`[preflight] BLOCKED: ${failed.map((check) => check.name).join(', ')}`);
  process.exit(BLOCKED_EXIT_CODE);
}
