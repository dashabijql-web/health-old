import fs from 'node:fs/promises';
import net from 'node:net';
import path from 'node:path';
import process from 'node:process';
import { spawn } from 'node:child_process';
import { chromium } from 'playwright';
import {
  assert,
  assertResultOk,
  pruneArtifacts,
  requestJson,
  resolveSession,
  truncate
} from '../shared/health-test-utils.mjs';


const DEFAULT_BASE_URL = 'http://127.0.0.1:9528';
const SQLCMD_BIN = process.env.SQLCMD_BIN || 'sqlcmd';
const SQL_SERVER = process.env.SQL_SERVER || 'localhost,1433';
const SQL_USER = process.env.SQL_USER || 'sa';
const SQL_PASSWORD = process.env.SQL_PASSWORD || '';
const SQL_DB = process.env.SQL_DB || 'health';
const PIPELINE_DATA_SOURCE = process.env.PIPELINE_DATA_SOURCE || (SQL_DB === 'health_new' ? 'new' : 'old');
const TCP_HOST = process.env.WATCH_TCP_HOST || '127.0.0.1';
const TCP_PORT = Number.parseInt(process.env.WATCH_TCP_PORT || '9000', 10);
const REDIS_HOST = process.env.REDIS_HOST || '127.0.0.1';
const REDIS_PORT = Number.parseInt(process.env.REDIS_PORT || '6379', 10);
const REDIS_TIMEOUT_MS = Number.parseInt(process.env.REDIS_TIMEOUT_MS || '3000', 10);
const PIPELINE_SQL_WAIT_MS = Number.parseInt(process.env.PIPELINE_SQL_WAIT_MS || '20000', 10);
const PIPELINE_REDIS_WAIT_MS = Number.parseInt(process.env.PIPELINE_REDIS_WAIT_MS || '20000', 10);
const PIPELINE_SKIP_CLEANUP = process.env.PIPELINE_SKIP_CLEANUP === '1';
const ARTIFACT_RETENTION = Number.parseInt(process.env.PIPELINE_ARTIFACT_RETENTION || '5', 10);
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-');
const ARTIFACT_DIR = path.resolve(process.cwd(), 'tests', 'pipeline', 'artifacts', RUN_ID);
const REPORT_JSON = path.join(ARTIFACT_DIR, 'summary.json');
const REPORT_MD = path.join(ARTIFACT_DIR, 'summary.md');
const SCREENSHOT_PATH = path.join(ARTIFACT_DIR, 'employee-profile.jpeg');
const MONTH_SUFFIX = new Date().toISOString().slice(0, 7).replace('-', '');
const HEALTH_TABLE = `health_record_${MONTH_SUFFIX}`;
const REDIS_BUFFER_KEYS = ['health:buffer:old', 'health:buffer:new', 'health:buffer'];
const DATA_SOURCE_HEADER = 'X-Health-Data-Source';
const DATA_SOURCE_COOKIE = 'Health-Data-Source';

const PROBE = {
  heartRate: 77,
  systolic: 118,
  diastolic: 76,
  bloodOxygen: 98,
  temperature: 36.8,
  temperatureStored: 368,
  steps: 23456,
  calories: 987,
  rollovers: 12,
  glucose: 5.1
};

const summary = {
  runId: RUN_ID,
  startedAt: new Date().toISOString(),
  baseUrl: '',
  target: null,
  table: HEALTH_TABLE,
  probe: { ...PROBE },
  stages: [],
  warnings: [],
  cleanup: {
    deletedRows: 0,
    redisTrimmed: 0
  }
};

await fs.mkdir(ARTIFACT_DIR, { recursive: true });
await pruneArtifacts(path.dirname(ARTIFACT_DIR), ARTIFACT_RETENTION);

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}


function redactSecrets(value) {
  return String(value)
    .replace(/(-P\s+)([^\s]+)/gi, '$1[REDACTED]')
    .replace(SQL_PASSWORD ? new RegExp(SQL_PASSWORD.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g') : /$a/, '[REDACTED]');
}

async function runSqlcmd(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(SQLCMD_BIN, args, {
      cwd: process.cwd(),
      windowsHide: true,
      shell: false
    });
    let stdout = '';
    let stderr = '';
    const timer = setTimeout(() => {
      child.kill();
      reject(new Error('sqlcmd timeout after 120000ms'));
    }, 120000);
    child.stdout?.on('data', (chunk) => { stdout += chunk.toString('utf8'); });
    child.stderr?.on('data', (chunk) => { stderr += chunk.toString('utf8'); });
    child.on('error', (error) => {
      clearTimeout(timer);
      reject(error);
    });
    child.on('close', (code, signal) => {
      clearTimeout(timer);
      resolve({ code, signal, stdout, stderr });
    });
  });
}

assert(SQL_PASSWORD, 'SQL_PASSWORD is required for SQL-backed pipeline tests');

function stage(name, status, note = '') {
  summary.stages.push({ name, status, note, at: new Date().toISOString() });
}

function normalizeTemp(value) {
  if (value === null || value === undefined || value === '') return null;
  const n = Number(value);
  if (!Number.isFinite(n)) return null;
  return Number((n > 100 ? n / 10 : n).toFixed(1));
}

async function sqlRaw(query) {
  const result = await runSqlcmd([
    '-S', SQL_SERVER,
    '-U', SQL_USER,
    '-P', SQL_PASSWORD,
    '-d', SQL_DB,
    '-r', '1',
    '-w', '65535',
    '-y', '0',
    '-Y', '0',
    '-Q',
    `SET NOCOUNT ON; ${query}`
  ]);
  const combinedOutput = `${result.stdout || ''}\n${result.stderr || ''}`.trim();
  if (result.code !== 0 || /Msg \d+,/i.test(combinedOutput) || /错误|error/i.test(result.stderr || '')) {
    throw new Error(redactSecrets([
      `sqlcmd exitCode=${result.code}`,
      result.signal ? `signal=${result.signal}` : '',
      result.stderr ? `stderr: ${result.stderr}` : '',
      result.stdout ? `stdout: ${result.stdout}` : ''
    ].filter(Boolean).join('\n')));
  }

  return result.stdout.trim();
}


function parseJsonPayload(raw) {
  const start = Math.min(
    ...['[', '{']
      .map((char) => raw.indexOf(char))
      .filter((index) => index >= 0)
  );
  if (!Number.isFinite(start)) {
    throw new Error(`sql output missing JSON payload: ${truncate(raw, 300)}`);
  }
  return JSON.parse(raw.slice(start));
}

async function sqlJson(query) {
  const raw = await sqlRaw(`${query} FOR JSON PATH`);
  return raw ? parseJsonPayload(raw) : [];
}

async function sqlJsonObject(query) {
  const raw = await sqlRaw(`${query} FOR JSON PATH, WITHOUT_ARRAY_WRAPPER`);
  return raw ? parseJsonPayload(raw) : null;
}

async function getProbeTarget() {
  const row = await sqlJsonObject(
    "SELECT TOP 1 d.id AS deviceId, d.imei, du.emp_id AS empId, e.emp_code AS empCode, e.emp_name AS empName " +
    "FROM device d " +
    "JOIN device_user du ON du.device_id = d.id AND du.unbind_time IS NULL " +
    "JOIN employee e ON e.id = du.emp_id " +
    "WHERE d.imei IS NOT NULL AND LEN(d.imei) = 15 " +
    "ORDER BY d.id"
  );
  if (row?.imei && row?.empCode) return row;
  if (PIPELINE_DATA_SOURCE === 'new') {
    stage('probe.target', 'skipped', 'no bound probe target found in new data source');
    return null;
  }
  assert(row?.imei && row?.empCode, 'no bound probe target found');
  return row;
}

async function getBaseline(target) {
  const row = await sqlJsonObject(
    `SELECT ISNULL(MAX(id), 0) AS maxId, COUNT(*) AS totalCount ` +
    `FROM ${HEALTH_TABLE} WHERE user_code = '${target.empCode}'`
  );
  return {
    maxId: Number(row?.maxId || 0),
    totalCount: Number(row?.totalCount || 0)
  };
}

async function queryProbeRows(target, baseline) {
  return sqlJson(
    `SELECT id, user_code AS userCode, heart_rate AS heartRate, blood_oxygen AS bloodOxygen, ` +
    `blood_pressure_high AS systolic, blood_pressure_low AS diastolic, temperature, steps, calories, record_time AS recordTime ` +
    `FROM ${HEALTH_TABLE} ` +
    `WHERE user_code = '${target.empCode}' AND id > ${baseline.maxId} AND (` +
    `(heart_rate = ${PROBE.heartRate} AND blood_oxygen = ${PROBE.bloodOxygen} ` +
    `AND blood_pressure_high = ${PROBE.systolic} AND blood_pressure_low = ${PROBE.diastolic} ` +
    `AND CAST(ROUND(CAST(temperature AS FLOAT), 0) AS INT) = ${PROBE.temperatureStored}) ` +
    `OR (steps = ${PROBE.steps} AND calories = ${PROBE.calories})` +
    `) ORDER BY id`
  );
}

async function deleteProbeRows(rowIds) {
  if (!rowIds.length) return 0;
  const row = await sqlJsonObject(
    `DELETE FROM ${HEALTH_TABLE} WHERE id IN (${rowIds.join(',')}); SELECT @@ROWCOUNT AS deletedRows`
  );
  return Number(row?.deletedRows || 0);
}

async function cleanupProbeRows(target, baseline, rowIds) {
  let deletedRows = 0;
  let pendingIds = [...new Set(rowIds.map((rowId) => Number(rowId)).filter(Number.isFinite))];

  for (let attempt = 0; attempt < 3; attempt += 1) {
    if (pendingIds.length > 0) {
      deletedRows += await deleteProbeRows(pendingIds);
    }

    await sleep(300);
    const residueRows = await queryProbeRows(target, baseline);
    pendingIds = [...new Set(residueRows.map((row) => Number(row.id)).filter(Number.isFinite))];
    if (pendingIds.length === 0) {
      return { deletedRows, residue: 0 };
    }
  }

  return { deletedRows, residue: pendingIds.length };
}

class RedisSocketClient {
  constructor(sock) {
    this.sock = sock;
    this.buffer = Buffer.alloc(0);
    this.waiters = [];
    this.closed = false;
    this.sock.on('data', (chunk) => {
      this.buffer = Buffer.concat([this.buffer, chunk]);
      this.flushWaiters();
    });
    this.sock.on('close', () => {
      this.closed = true;
      this.flushWaiters();
    });
    this.sock.on('error', () => {
      this.closed = true;
      this.flushWaiters();
    });
  }

  static async connect(host, port, timeoutMs = 3000) {
    const sock = net.createConnection({ host, port });
    await Promise.race([
      new Promise((resolve, reject) => {
        sock.once('connect', resolve);
        sock.once('error', reject);
      }),
      new Promise((_, reject) => setTimeout(() => reject(new Error('redis connect timeout')), timeoutMs))
    ]);
    sock.setTimeout(timeoutMs);
    return new RedisSocketClient(sock);
  }

  close() {
    this.sock.destroy();
  }

  flushWaiters() {
    const waiters = this.waiters.splice(0, this.waiters.length);
    for (const waiter of waiters) waiter();
  }

  async waitForData() {
    if (this.buffer.length > 0 || this.closed) return;
    await new Promise((resolve) => this.waiters.push(resolve));
  }

  async execute(...parts) {
    const chunks = [Buffer.from(`*${parts.length}\r\n`, 'utf8')];
    for (const part of parts) {
      const raw = Buffer.from(String(part), 'utf8');
      chunks.push(Buffer.from(`$${raw.length}\r\n`, 'utf8'));
      chunks.push(raw);
      chunks.push(Buffer.from('\r\n', 'utf8'));
    }
    this.sock.write(Buffer.concat(chunks));
    return this.readReply();
  }

  async readReply() {
    while (true) {
      const parsed = await this.tryParseReply(0);
      if (parsed) {
        this.buffer = this.buffer.subarray(parsed.nextOffset);
        return parsed.value;
      }
      await this.waitForData();
      if (this.closed && this.buffer.length === 0) {
        throw new Error('redis connection closed');
      }
    }
  }

  async tryParseReply(offset) {
    if (this.buffer.length <= offset) return null;
    const prefix = String.fromCharCode(this.buffer[offset]);
    if (prefix === '+' || prefix === '-' || prefix === ':') {
      const lineEnd = this.buffer.indexOf('\r\n', offset);
      if (lineEnd === -1) return null;
      const line = this.buffer.toString('utf8', offset + 1, lineEnd);
      if (prefix === '-') throw new Error(line);
      if (prefix === ':') return { value: Number.parseInt(line, 10), nextOffset: lineEnd + 2 };
      return { value: line, nextOffset: lineEnd + 2 };
    }

    if (prefix === '$') {
      const lineEnd = this.buffer.indexOf('\r\n', offset);
      if (lineEnd === -1) return null;
      const length = Number.parseInt(this.buffer.toString('utf8', offset + 1, lineEnd), 10);
      if (length === -1) return { value: null, nextOffset: lineEnd + 2 };
      const bodyStart = lineEnd + 2;
      const bodyEnd = bodyStart + length;
      if (this.buffer.length < bodyEnd + 2) return null;
      const value = this.buffer.toString('utf8', bodyStart, bodyEnd);
      return { value, nextOffset: bodyEnd + 2 };
    }

    if (prefix === '*') {
      const lineEnd = this.buffer.indexOf('\r\n', offset);
      if (lineEnd === -1) return null;
      const count = Number.parseInt(this.buffer.toString('utf8', offset + 1, lineEnd), 10);
      if (count === -1) return { value: null, nextOffset: lineEnd + 2 };
      let nextOffset = lineEnd + 2;
      const values = [];
      for (let i = 0; i < count; i += 1) {
        const child = await this.tryParseReply(nextOffset);
        if (!child) return null;
        values.push(child.value);
        nextOffset = child.nextOffset;
      }
      return { value: values, nextOffset };
    }

    throw new Error(`unsupported redis reply prefix: ${prefix}`);
  }
}

async function connectRedisWithRetry(host, port, timeoutMs, attempts = 5, delayMs = 1000) {
  let lastError = null;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      return await RedisSocketClient.connect(host, port, timeoutMs);
    } catch (error) {
      lastError = error;
      if (attempt < attempts) {
        await sleep(delayMs);
      }
    }
  }
  throw lastError || new Error('redis connect failed');
}

async function findProbePayloads(redisClient, target) {
  const matched = [];

  for (const key of REDIS_BUFFER_KEYS) {
    const items = await redisClient.execute('LRANGE', key, '0', '-1').catch(() => []);
    const rows = Array.isArray(items) ? items.filter((item) => typeof item === 'string') : [];
    for (const row of rows) {
      const matchUser = row.includes(`"userCode":"${target.empCode}"`);
      const matchVitals =
        row.includes(`"heartRate":${PROBE.heartRate}`) &&
        row.includes(`"bloodOxygen":${PROBE.bloodOxygen}`);
      const matchSteps = row.includes(`"steps":${PROBE.steps}`) && row.includes(`"calories":${PROBE.calories}`);
      if (matchUser && (matchVitals || matchSteps)) {
        matched.push({ key, payload: row });
      }
    }
  }

  return matched;
}

async function observeRedis(redisClient, target) {
  const startedAt = Date.now();
  const matchedPayloads = new Map();
  while (Date.now() - startedAt < PIPELINE_REDIS_WAIT_MS) {
    const payloads = await findProbePayloads(redisClient, target);
    for (const item of payloads) matchedPayloads.set(`${item.key}::${item.payload}`, item);
    if (matchedPayloads.size > 0) {
      return { matched: true, payloads: [...matchedPayloads.values()] };
    }
    await sleep(150);
  }
  return { matched: false, payloads: [...matchedPayloads.values()] };
}

async function cleanupRedisPayloads(redisClient, payloads) {
  let removed = 0;
  for (const payload of payloads) {
    try {
      const delta = await redisClient.execute('LREM', payload.key, '0', payload.payload);
      removed += Number(delta || 0);
    } catch {
      // Best-effort cleanup.
    }
  }
  return removed;
}

async function sendPacket(socket, packet) {
  socket.write(packet, 'utf8');
  await sleep(180);
}

async function sendProbePackets(target) {
  const socket = await new Promise((resolve, reject) => {
    const conn = net.createConnection({ host: TCP_HOST, port: TCP_PORT }, () => resolve(conn));
    conn.once('error', reject);
  });

  socket.setTimeout(5000);
  socket.on('data', () => {
    // Server responses are optional for this regression; ignore the body.
  });

  try {
    await sendPacket(socket, `IW*AP00*${target.imei}#`);
    await sleep(1200);
    await sendPacket(socket, `IW*AP03*1,${PROBE.steps},${PROBE.rollovers},${PROBE.calories}#`);
    await sleep(1100);
    await sendPacket(
      socket,
      `IW*APHP*${PROBE.heartRate},${PROBE.systolic},${PROBE.diastolic},${PROBE.bloodOxygen},${PROBE.glucose},${PROBE.temperature},,,,,,,#`
    );
    await sleep(1100);
    await sendPacket(
      socket,
      `IW*APHP*${PROBE.heartRate},${PROBE.systolic},${PROBE.diastolic},${PROBE.bloodOxygen},${PROBE.glucose},${PROBE.temperature},,,,,,,#`
    );
    await sleep(300);
  } finally {
    socket.end();
    socket.destroy();
  }
}

async function waitForSqlRows(target, baseline) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < PIPELINE_SQL_WAIT_MS) {
    const rows = await queryProbeRows(target, baseline);
    if (rows.length >= 2) {
      return rows;
    }
    await sleep(500);
  }
  return [];
}

function findProbeRow(rows, predicate) {
  return rows.find((row) => predicate(row)) || null;
}

async function launchBrowser() {
  for (const channel of ['msedge', 'chrome']) {
    try {
      return await chromium.launch({ headless: true, channel });
    } catch {
      // Try the next browser channel.
    }
  }
  return chromium.launch({ headless: true });
}

async function loginViaApi(page, session) {
  await page.context().addCookies([
    {
      name: 'User-Token',
      value: session.token,
      url: session.target.origin
    },
    {
      name: 'satoken',
      value: session.token,
      url: session.target.origin
    },
    {
      name: DATA_SOURCE_COOKIE,
      value: PIPELINE_DATA_SOURCE,
      url: session.target.origin
    }
  ]);
}

async function verifyEmployeeProfilePage(session, target) {
  const browser = await launchBrowser();
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
    const page = await context.newPage();
    await loginViaApi(page, session);
    const route = `${session.target.origin}/#/health-monitor/employee-profile?empCode=${encodeURIComponent(target.empCode)}&empName=${encodeURIComponent(target.empCode)}`;
    await page.goto(route, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('.ep-page', { timeout: 20000 });
    await page.waitForFunction(
      (probe) => {
        const hr = document.querySelector('.ep-vital-card.hr .ep-vc-val')?.textContent || '';
        const spo2 = document.querySelector('.ep-vital-card.spo2 .ep-vc-val')?.textContent || '';
        const temp = document.querySelector('.ep-vital-card.temp .ep-vc-val')?.textContent || '';
        return hr.includes(String(probe.heartRate)) &&
          spo2.includes(String(probe.bloodOxygen)) &&
          temp.includes(probe.temperature.toFixed(1));
      },
      PROBE,
      { timeout: 20000 }
    );
    await page.screenshot({ path: SCREENSHOT_PATH, type: 'jpeg', quality: 72 });

    const snapshot = await page.evaluate(() => ({
      hr: document.querySelector('.ep-vital-card.hr .ep-vc-val')?.textContent?.trim() || '',
      spo2: document.querySelector('.ep-vital-card.spo2 .ep-vc-val')?.textContent?.trim() || '',
      temp: document.querySelector('.ep-vital-card.temp .ep-vc-val')?.textContent?.trim() || '',
      bp: document.querySelector('.ep-vital-card.steps')?.textContent?.trim() || '',
      profileCode: document.querySelector('.ep-bi .code')?.textContent?.trim() || ''
    }));

    await context.close();
    return snapshot;
  } finally {
    await browser.close();
  }
}

function buildMarkdownReport() {
  return [
    '# Health pipeline regression report',
    '',
    `- run_id: ${summary.runId}`,
    `- started_at: ${summary.startedAt}`,
    `- finished_at: ${summary.finishedAt}`,
    `- base_url: ${summary.baseUrl}`,
    `- target: ${summary.target ? `${summary.target.empCode} / ${summary.target.imei}` : '-'}`,
    `- health_table: ${summary.table}`,
    '',
    '## Stages',
    '',
    '| stage | status | note |',
    '| --- | --- | --- |',
    ...summary.stages.map((item) => `| ${item.name} | ${item.status} | ${item.note || '-'} |`),
    '',
    '## Cleanup',
    '',
    `- deleted_rows: ${summary.cleanup.deletedRows}`,
    `- redis_trimmed: ${summary.cleanup.redisTrimmed}`,
    '',
    '## Warnings',
    '',
    ...(summary.warnings.length ? summary.warnings.map((item) => `- ${item}`) : ['- none'])
  ].join('\n');
}

let redisClient = null;
let redisPayloads = [];
let cleanupRowIds = [];
let baselineSnapshot = null;
let fatalError = null;

try {
  const session = await resolveSession();
  summary.baseUrl = session.target.origin;
  stage('resolve-session', 'passed', `${session.target.label} (${session.target.origin})`);

  const target = await getProbeTarget();
  summary.target = target;
  if (!target) {
    stage('pipeline', 'skipped', 'new data source has no bound probe target; write-through TCP probe is old-source only until seed data exists');
  } else {
    stage('pick-target', 'passed', `${target.empCode} / ${target.imei}`);

    baselineSnapshot = await getBaseline(target);
  stage('baseline', 'passed', `max_id=${baselineSnapshot.maxId}, count=${baselineSnapshot.totalCount}`);

  redisClient = await connectRedisWithRetry(REDIS_HOST, REDIS_PORT, REDIS_TIMEOUT_MS);
  stage('redis-connect', 'passed', `${REDIS_HOST}:${REDIS_PORT}`);

  await sendProbePackets(target);
  stage('tcp-probe', 'passed', `${TCP_HOST}:${TCP_PORT} <- ${target.imei}`);

  const redisObservation = await observeRedis(redisClient, target);
  redisPayloads = redisObservation.payloads;
  if (redisObservation.matched) {
    stage('redis-buffer', 'passed', `${redisPayloads.length} payload(s) observed in ${[...new Set(redisPayloads.map((item) => item.key))].join(', ')}`);
  } else {
    stage('redis-buffer', 'passed', 'no payload observed before flush');
  }

  const probeRows = await waitForSqlRows(target, baselineSnapshot);
  assert(probeRows.length >= 2, `expected probe rows in ${HEALTH_TABLE}, found ${probeRows.length}`);

  const vitalsRow = findProbeRow(
    probeRows,
    (row) =>
      Number(row.heartRate) === PROBE.heartRate &&
      Number(row.bloodOxygen) === PROBE.bloodOxygen &&
      Number(row.systolic) === PROBE.systolic &&
      Number(row.diastolic) === PROBE.diastolic &&
      normalizeTemp(row.temperature) === PROBE.temperature
  );
  const movementRow = findProbeRow(
    probeRows,
    (row) => Number(row.steps) === PROBE.steps && Number(row.calories) === PROBE.calories
  );

  assert(vitalsRow, 'vitals probe row not found in SQL');
  assert(movementRow, 'heartbeat probe row not found in SQL');
  cleanupRowIds = [...new Set(probeRows.map((row) => Number(row.id)).filter(Number.isFinite))];
  stage('sql-flush', 'passed', `rows=${cleanupRowIds.join(',')}`);

  const healthRecords = await requestJson(session, 'GET', '/api/health/record/page', {
    query: { current: 1, size: 20, userCode: target.empCode },
    dataSource: PIPELINE_DATA_SOURCE
  });
  assertResultOk(healthRecords);
  const recordList = Array.isArray(healthRecords.payload?.data?.records) ? healthRecords.payload.data.records : [];
  assert(recordList.some((row) => cleanupRowIds.includes(Number(row.id))), 'health record page missing probe rows');
  stage('api.health-record-page', 'passed', `${recordList.length} rows`);

  const portrait = await requestJson(session, 'GET', `/health-portrait/${encodeURIComponent(target.empCode)}`, { dataSource: PIPELINE_DATA_SOURCE });
  assertResultOk(portrait);
  const portraitVitals = portrait.payload?.data?.vitals || {};
  assert(Number(portraitVitals.heartRate) === PROBE.heartRate, 'health portrait heartRate mismatch');
  assert(Number(portraitVitals.bloodOxygen) === PROBE.bloodOxygen, 'health portrait bloodOxygen mismatch');
  assert(normalizeTemp(portraitVitals.temperature) === PROBE.temperature, 'health portrait temperature mismatch');
  stage(
    'api.health-portrait',
    'passed',
    `hr=${portraitVitals.heartRate}, spo2=${portraitVitals.bloodOxygen}, temp=${normalizeTemp(portraitVitals.temperature)}`
  );

  const realtime = await requestJson(session, 'GET', `/realtime/user/${encodeURIComponent(target.empCode)}`, { dataSource: PIPELINE_DATA_SOURCE });
  assertResultOk(realtime);
  const realtimeData = realtime.payload?.data || {};
  assert(Number(realtimeData.heartRate) === PROBE.heartRate, 'realtime user heartRate mismatch');
  assert(Number(realtimeData.bloodOxygen) === PROBE.bloodOxygen, 'realtime user bloodOxygen mismatch');
  assert(normalizeTemp(realtimeData.temperature) === PROBE.temperature, 'realtime user temperature mismatch');
  stage(
    'api.realtime-user',
    'passed',
    `hr=${realtimeData.heartRate}, spo2=${realtimeData.bloodOxygen}, temp=${normalizeTemp(realtimeData.temperature)}`
  );

  const pageSnapshot = await verifyEmployeeProfilePage(session, target);
  stage(
    'page.employee-profile',
    'passed',
    `${pageSnapshot.hr} | ${pageSnapshot.spo2} | ${pageSnapshot.temp}`
  );
  }
} catch (error) {
  fatalError = error;
  stage('pipeline', 'failed', truncate(String(error), 320));
} finally {
  if (PIPELINE_SKIP_CLEANUP) {
    summary.warnings.push('cleanup skipped because PIPELINE_SKIP_CLEANUP=1');
  } else if (redisClient && redisPayloads.length > 0) {
    summary.cleanup.redisTrimmed = await cleanupRedisPayloads(redisClient, redisPayloads);
  }
  if (redisClient) {
    redisClient.close();
  }

  if (!PIPELINE_SKIP_CLEANUP && cleanupRowIds.length > 0) {
    const cleanupResult = baselineSnapshot
      ? await cleanupProbeRows(summary.target, baselineSnapshot, cleanupRowIds)
      : { deletedRows: await deleteProbeRows(cleanupRowIds), residue: 0 };
    summary.cleanup.deletedRows = cleanupResult.deletedRows;
    const residue = baselineSnapshot ? cleanupResult.residue : 0;
    if (residue > 0) {
      summary.warnings.push(`probe residue still present after cleanup: ${residue}`);
    }
  }

  summary.finishedAt = new Date().toISOString();
  await fs.writeFile(REPORT_JSON, JSON.stringify(summary, null, 2), 'utf8');
  await fs.writeFile(REPORT_MD, buildMarkdownReport(), 'utf8');
}

const failedStages = summary.stages.filter((item) => item.status === 'failed').length;

console.log(JSON.stringify({
  artifactDir: ARTIFACT_DIR,
  reportFile: REPORT_MD,
  stageCount: summary.stages.length,
  failedStages,
  warnings: summary.warnings.length,
  cleanup: summary.cleanup
}, null, 2));

if (fatalError) {
  console.error(truncate(String(fatalError), 400));
}

if (failedStages > 0) {
  process.exitCode = 1;
}
