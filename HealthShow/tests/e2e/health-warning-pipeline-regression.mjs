import fs from 'node:fs/promises';
import net from 'node:net';
import path from 'node:path';
import process from 'node:process';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { chromium } from 'playwright';
import {
  assert,
  assertResultOk,
  pruneArtifacts,
  requestJson,
  resolveSession,
  truncate
} from '../shared/health-test-utils.mjs';

const execFileAsync = promisify(execFile);

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
const HEALTH_WAIT_MS = Number.parseInt(process.env.WARNING_PIPELINE_HEALTH_WAIT_MS || '20000', 10);
const WARNING_WAIT_MS = Number.parseInt(process.env.WARNING_PIPELINE_WARNING_WAIT_MS || '12000', 10);
const ARTIFACT_RETENTION = Number.parseInt(process.env.PIPELINE_ARTIFACT_RETENTION || '5', 10);
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-');
const ARTIFACT_DIR = path.resolve(process.cwd(), 'tests', 'pipeline', 'artifacts', RUN_ID);
const REPORT_JSON = path.join(ARTIFACT_DIR, 'warning-summary.json');
const REPORT_MD = path.join(ARTIFACT_DIR, 'warning-summary.md');
const SCREENSHOT_PATH = path.join(ARTIFACT_DIR, 'warning-center.jpeg');
const MONTH_SUFFIX = new Date().toISOString().slice(0, 7).replace('-', '');
const HEALTH_TABLE = `health_record_${MONTH_SUFFIX}`;
const WARNING_TABLE = `warning_record_${MONTH_SUFFIX}`;
const DATA_SOURCE_HEADER = 'X-Health-Data-Source';
const DATA_SOURCE_COOKIE = 'Health-Data-Source';
const SCRIPT_TIME = new Date().toISOString().slice(0, 19).replace('T', ' ');
const REDIS_BUFFER_KEYS = [`health:buffer:${PIPELINE_DATA_SOURCE}`, 'health:buffer'];

const PROBE = {
  indicator: '体温',
  warningType: '体温异常',
  level: '高危',
  temperature: 40.5,
  temperatureStored: 405,
  heartRate: 78,
  systolic: 118,
  diastolic: 76,
  bloodOxygen: 98,
  glucose: 5.1,
  battery: 88
};

const summary = {
  runId: RUN_ID,
  status: 'passed',
  startedAt: new Date().toISOString(),
  baseUrl: '',
  target: null,
  probe: { ...PROBE },
  tables: { health: HEALTH_TABLE, warning: WARNING_TABLE },
  stages: [],
  warnings: [],
  cleanup: {
    deletedHealthRows: 0,
    deletedWarningRows: 0,
    redisTrimmed: 0
  }
};

await fs.mkdir(ARTIFACT_DIR, { recursive: true });
await pruneArtifacts(path.dirname(ARTIFACT_DIR), ARTIFACT_RETENTION);

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

assert(SQL_PASSWORD, 'SQL_PASSWORD is required for SQL-backed pipeline tests');

function stage(name, status, note = '') {
  summary.stages.push({ name, status, note, at: new Date().toISOString() });
}

function markSkipped(note) {
  summary.status = 'skipped';
  stage('warning-pipeline', 'skipped', note);
}

function normalizeWarningLevel(value) {
  return String(value ?? '').trim();
}

function classifyWarningLevel(row) {
  if (Number(row?.isHighRisk) === 1) return '高危';
  if (Number(row?.isMediumRisk) === 1) return '中危';
  if (Number(row?.isLowRisk) === 1) return '低危';
  return normalizeWarningLevel(row?.warningLevel);
}

async function sqlRaw(query) {
  const { stdout, stderr } = await execFileAsync(
    SQLCMD_BIN,
    [
      '-S',
      SQL_SERVER,
      '-U',
      SQL_USER,
      '-P',
      SQL_PASSWORD,
      '-d',
      SQL_DB,
      '-w',
      '65535',
      '-y',
      '0',
      '-Y',
      '0',
        '-Q',
      `SET NOCOUNT ON; ${query}`
    ],
    { encoding: 'utf8', windowsHide: true, timeout: 120000, maxBuffer: 20 * 1024 * 1024 }
  );

  if (stderr && stderr.trim()) {
    throw new Error(stderr.trim());
  }

  return stdout.trim();
}

function parseJsonPayload(raw) {
  const starts = ['[', '{'].map((char) => raw.indexOf(char)).filter((idx) => idx >= 0);
  assert(starts.length > 0, `sql output missing JSON payload: ${truncate(raw, 300)}`);
  return JSON.parse(raw.slice(Math.min(...starts)));
}

async function sqlJson(query) {
  const raw = await sqlRaw(`${query} FOR JSON PATH`);
  return raw ? parseJsonPayload(raw) : [];
}

async function sqlJsonObject(query) {
  const raw = await sqlRaw(`${query} FOR JSON PATH, WITHOUT_ARRAY_WRAPPER`);
  return raw ? parseJsonPayload(raw) : null;
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
      return {
        value: this.buffer.toString('utf8', bodyStart, bodyEnd),
        nextOffset: bodyEnd + 2
      };
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

async function getWarningProbeTarget() {
  const row = await sqlJsonObject(
    "SELECT TOP 1 d.id AS deviceId, d.imei, du.emp_id AS empId, e.emp_code AS empCode, e.emp_name AS empName, jt.risk_level AS riskLevel " +
    "FROM device d " +
    "JOIN device_user du ON du.device_id = d.id AND du.unbind_time IS NULL " +
    "JOIN employee e ON e.id = du.emp_id " +
    "LEFT JOIN job_type jt ON jt.id = e.job_type_id " +
    "WHERE d.imei IS NOT NULL AND LEN(d.imei) = 15 " +
    "AND NOT EXISTS (" +
    "  SELECT 1 FROM v_warning_record w " +
    "  WHERE w.user_code = e.emp_code " +
    "    AND w.indicator_name = N'体温' " +
    "    AND w.create_time >= DATEADD(MINUTE, -240, GETDATE())" +
    ") " +
    "ORDER BY d.id"
  );
  if (row?.imei && row?.empCode) return row;
  if (PIPELINE_DATA_SOURCE === 'new') {
    markSkipped('new data source has no warning probe target; empty health_new bootstrap data is expected');
    return null;
  }
  assert(row?.imei && row?.empCode, 'no warning probe target found');
  return row;
}

async function loadTemperatureConfig(riskLevel) {
  const levelsToTry = Number.isFinite(Number(riskLevel)) ? [Number(riskLevel), null] : [null];
  for (const level of levelsToTry) {
    const predicate = level === null ? 'risk_level IS NULL' : `risk_level = ${level}`;
    const row = await sqlJsonObject(
      "SELECT TOP 1 id, risk_level AS riskLevel, enabled, critical_low AS criticalLow, critical_high AS criticalHigh, " +
      "warn_mid_low AS warnMidLow, warn_mid_high AS warnMidHigh, warn_low AS warnLow, warn_high AS warnHigh " +
      "FROM alert_config " +
      `WHERE config_type = 3 AND ${predicate} ` +
      "ORDER BY id"
    );
    if (row) return row;
  }
  throw new Error(`temperature alert config not found for riskLevel=${riskLevel ?? 'default'}`);
}

function applyTemperatureProbe(config) {
  const criticalHigh = Number(config?.criticalHigh);
  assert(Number.isFinite(criticalHigh), 'temperature alert config missing criticalHigh');
  const probeTemperature = Number((criticalHigh + 0.5).toFixed(1));
  assert(probeTemperature <= 41.9, `temperature criticalHigh=${criticalHigh} leaves no safe high-risk probe`);
  PROBE.temperature = probeTemperature;
  PROBE.temperatureStored = Math.round(probeTemperature * 10);
  PROBE.level = '高危';
  summary.probe = {
    ...PROBE,
    configRiskLevel: config?.riskLevel ?? null,
    criticalHigh
  };
  return { criticalHigh, probeTemperature };
}

async function getBaseline(target) {
  const health = await sqlJsonObject(
    `SELECT ISNULL(MAX(id), 0) AS maxId, COUNT(*) AS totalCount ` +
    `FROM ${HEALTH_TABLE} WHERE user_code = '${target.empCode}'`
  );
  const warning = await sqlJsonObject(
    `SELECT ISNULL(MAX(id), 0) AS maxId, COUNT(*) AS totalCount ` +
    `FROM ${WARNING_TABLE} WHERE user_code = '${target.empCode}'`
  );
  return {
    healthMaxId: Number(health?.maxId || 0),
    warningMaxId: Number(warning?.maxId || 0),
    healthCount: Number(health?.totalCount || 0),
    warningCount: Number(warning?.totalCount || 0)
  };
}

async function queryHealthProbeRows(target, baseline) {
  return sqlJson(
    `SELECT id, user_code AS userCode, heart_rate AS heartRate, blood_oxygen AS bloodOxygen, ` +
    `blood_pressure_high AS systolic, blood_pressure_low AS diastolic, temperature, record_time AS recordTime ` +
    `FROM ${HEALTH_TABLE} ` +
    `WHERE user_code = '${target.empCode}' ` +
    `AND id > ${baseline.healthMaxId} ` +
    `AND heart_rate = ${PROBE.heartRate} ` +
    `AND blood_oxygen = ${PROBE.bloodOxygen} ` +
    `AND blood_pressure_high = ${PROBE.systolic} ` +
    `AND blood_pressure_low = ${PROBE.diastolic} ` +
    `AND CAST(ROUND(CAST(temperature AS FLOAT), 0) AS INT) = ${PROBE.temperatureStored} ` +
    `ORDER BY id`
  );
}

async function queryWarningProbeRows(target, baseline) {
  return sqlJson(
    `SELECT id, user_code AS userCode, indicator_value AS indicatorValue, is_handled AS handled, create_time AS createTime, ` +
    `CASE WHEN warning_level = N'高危' THEN 1 ELSE 0 END AS isHighRisk, ` +
    `CASE WHEN warning_level = N'中危' THEN 1 ELSE 0 END AS isMediumRisk, ` +
    `CASE WHEN warning_level = N'低危' THEN 1 ELSE 0 END AS isLowRisk ` +
    `FROM ${WARNING_TABLE} ` +
    `WHERE user_code = '${target.empCode}' ` +
    `AND id > ${baseline.warningMaxId} ` +
    `AND warning_type = N'${PROBE.warningType}' ` +
    `AND indicator_name = N'${PROBE.indicator}' ` +
    `ORDER BY id`
  );
}

async function waitForHealthRows(target, baseline) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < HEALTH_WAIT_MS) {
    const rows = await queryHealthProbeRows(target, baseline);
    if (rows.length > 0) return rows;
    await sleep(500);
  }
  return [];
}

async function waitForWarningRows(target, baseline) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < WARNING_WAIT_MS) {
    const rows = await queryWarningProbeRows(target, baseline);
    if (rows.length > 0) return rows;
    await sleep(500);
  }
  return [];
}

async function findProbePayloads(redisClient, target) {
  const matched = [];
  for (const key of REDIS_BUFFER_KEYS) {
    const items = await redisClient.execute('LRANGE', key, '0', '-1').catch(() => []);
    const rows = Array.isArray(items) ? items.filter((item) => typeof item === 'string') : [];
    for (const row of rows) {
      if (
        row.includes(`"userCode":"${target.empCode}"`) &&
        row.includes(`"temperature":${PROBE.temperatureStored}`) &&
        row.includes(`"heartRate":${PROBE.heartRate}`) &&
        row.includes(`"bloodOxygen":${PROBE.bloodOxygen}`)
      ) {
        matched.push({ key, payload: row });
      }
    }
  }
  return matched;
}

async function observeRedisPayloads(redisClient, target) {
  const startedAt = Date.now();
  const payloads = new Map();
  while (Date.now() - startedAt < 6000) {
    const matches = await findProbePayloads(redisClient, target);
    for (const match of matches) payloads.set(`${match.key}::${match.payload}`, match);
    if (payloads.size > 0) return [...payloads];
    await sleep(150);
  }
  return [...payloads];
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

async function sendWarningProbe(target) {
  const socket = await new Promise((resolve, reject) => {
    const conn = net.createConnection({ host: TCP_HOST, port: TCP_PORT }, () => resolve(conn));
    conn.once('error', reject);
  });

  socket.setTimeout(5000);
  socket.on('data', () => {
    // Server responses are optional here.
  });

  try {
    await sendPacket(socket, `IW*AP00*${target.imei}#`);
    await sleep(1200);
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

async function deleteHealthRows(rowIds) {
  if (!rowIds.length) return 0;
  const row = await sqlJsonObject(
    `DELETE FROM ${HEALTH_TABLE} WHERE id IN (${rowIds.join(',')}); SELECT @@ROWCOUNT AS deletedRows`
  );
  return Number(row?.deletedRows || 0);
}

async function deleteWarningRows(rowIds) {
  if (!rowIds.length) return 0;
  const row = await sqlJsonObject(
    `DELETE FROM ${WARNING_TABLE} WHERE id IN (${rowIds.join(',')}); SELECT @@ROWCOUNT AS deletedRows`
  );
  return Number(row?.deletedRows || 0);
}

async function verifyWarningsHandled(rowIds) {
  if (!rowIds.length) return [];
  return sqlJson(
    `SELECT id, is_handled AS handled, handle_by AS handleBy, remark AS handleRemark ` +
    `FROM ${WARNING_TABLE} ` +
    `WHERE id IN (${rowIds.join(',')}) ` +
    `ORDER BY id`
  );
}

async function verifyNotificationsPage(session, target, displayName) {
  const browser = await launchBrowser();
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
    const page = await context.newPage();
    await bootstrapLogin(page, session);
    await page.goto(`${session.target.origin}/#/alert-management/notifications`, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('.notif-page', { timeout: 20000 });

    const searchInput = page.locator('input[placeholder="搜索员工工号"]').first();
    await searchInput.fill(target.empCode);
    await searchInput.evaluate((el) => {
      el.dispatchEvent(new Event('change', { bubbles: true }));
      el.dispatchEvent(new Event('blur', { bubbles: true }));
    });
    await page.getByRole('button', { name: '刷新' }).click();

    const probeText = PROBE.temperature.toFixed(1);
    const warningItem = page
      .locator('.notif-item')
      .filter({ hasText: probeText })
      .first();
    await warningItem.waitFor({ state: 'visible', timeout: 20000 });

    const snapshotBefore = await warningItem.evaluate((item) => {
      return {
        text: item.innerText?.trim() || '',
        handled: item.classList.contains('is-handled') || false
      };
    });

    const handleButton = warningItem.getByRole('button', { name: '处理' });
    await handleButton.click();

    await page.waitForFunction(
      ({ probeText }) => {
        const items = [...document.querySelectorAll('.notif-item')];
        const match = items.find((item) => {
          const text = item.innerText || '';
          return text.includes(probeText);
        });
        if (!match) return false;
        return match.classList.contains('is-handled') || (match.innerText || '').includes('已处理');
      },
      { probeText },
      { timeout: 20000 }
    );

    await page.screenshot({ path: SCREENSHOT_PATH, type: 'jpeg', quality: 72 });

    const snapshotAfter = await warningItem.evaluate((item) => {
      return {
        text: item.innerText?.trim() || '',
        handled: item.classList.contains('is-handled') || false
      };
    });

    await context.close();
    return { before: snapshotBefore, after: snapshotAfter };
  } finally {
    await browser.close();
  }
}

async function launchBrowser() {
  for (const channel of ['msedge', 'chrome']) {
    try {
      return await chromium.launch({ headless: true, channel });
    } catch {
      // Try next installed browser.
    }
  }
  return chromium.launch({ headless: true });
}

async function bootstrapLogin(page, session) {
  await page.context().addCookies([
    { name: 'User-Token', value: session.token, url: session.target.origin },
    { name: 'satoken', value: session.token, url: session.target.origin },
    { name: DATA_SOURCE_COOKIE, value: PIPELINE_DATA_SOURCE, url: session.target.origin }
  ]);
}

function buildMarkdownReport() {
  return [
    '# Health warning pipeline regression report',
    '',
    `- run_id: ${summary.runId}`,
    `- started_at: ${summary.startedAt}`,
    `- finished_at: ${summary.finishedAt}`,
    `- base_url: ${summary.baseUrl}`,
    `- target: ${summary.target ? `${summary.target.empCode} / ${summary.target.imei}` : '-'}`,
    `- health_table: ${summary.tables.health}`,
    `- warning_table: ${summary.tables.warning}`,
    '',
    '## Stages',
    '',
    '| stage | status | note |',
    '| --- | --- | --- |',
    ...summary.stages.map((item) => `| ${item.name} | ${item.status} | ${item.note || '-'} |`),
    '',
    '## Cleanup',
    '',
    `- deleted_health_rows: ${summary.cleanup.deletedHealthRows}`,
    `- deleted_warning_rows: ${summary.cleanup.deletedWarningRows}`,
    `- redis_trimmed: ${summary.cleanup.redisTrimmed}`,
    '',
    '## Warnings',
    '',
    ...(summary.warnings.length ? summary.warnings.map((item) => `- ${item}`) : ['- none'])
  ].join('\n');
}

let redisClient = null;
let redisPayloads = [];
let healthRowIds = [];
let warningRowIds = [];
let fatalError = null;

try {
  const session = await resolveSession();
  summary.baseUrl = session.target.origin;
  stage('resolve-session', 'passed', `${session.target.label} (${session.target.origin})`);

  const target = await getWarningProbeTarget();
  summary.target = target;
  if (!target) {
    summary.warnings.push('warning pipeline skipped for new data source because no bound warning probe target exists');
  } else {
    stage('pick-target', 'passed', `${target.empCode} / ${target.imei} / risk=${target.riskLevel ?? 'default'}`);

    const temperatureConfig = await loadTemperatureConfig(target.riskLevel);
    const appliedProbe = applyTemperatureProbe(temperatureConfig);
    stage(
      'probe-config',
      'passed',
      `criticalHigh=${appliedProbe.criticalHigh}, probe=${appliedProbe.probeTemperature}, configRisk=${temperatureConfig.riskLevel ?? 'default'}`
    );

    const baseline = await getBaseline(target);
    stage('baseline', 'passed', `health_max=${baseline.healthMaxId}, warning_max=${baseline.warningMaxId}`);

    redisClient = await connectRedisWithRetry(REDIS_HOST, REDIS_PORT, REDIS_TIMEOUT_MS);
    stage('redis-connect', 'passed', `${REDIS_HOST}:${REDIS_PORT}`);

    await sendWarningProbe(target);
    stage('tcp-warning-probe', 'passed', `${TCP_HOST}:${TCP_PORT} <- ${target.imei}`);

    redisPayloads = await observeRedisPayloads(redisClient, target);
    if (redisPayloads.length > 0) {
      stage('redis-buffer', 'passed', `${redisPayloads.length} payload(s) observed in ${[...new Set(redisPayloads.map((item) => item.key))].join(', ')}`);
    } else {
      stage('redis-buffer', 'passed', 'no payload observed before flush');
    }

    const healthRows = await waitForHealthRows(target, baseline);
    assert(healthRows.length > 0, `no health probe rows found in ${HEALTH_TABLE}`);
    healthRowIds = [...new Set(healthRows.map((row) => Number(row.id)).filter(Number.isFinite))];
    stage('sql.health-record', 'passed', `rows=${healthRowIds.join(',')}`);

    const warningRows = await waitForWarningRows(target, baseline);
    assert(warningRows.length > 0, `no warning probe rows found in ${WARNING_TABLE}`);
    warningRowIds = [...new Set(warningRows.map((row) => Number(row.id)).filter(Number.isFinite))];
    const matchedLevels = [...new Set(warningRows.map((row) => classifyWarningLevel(row)).filter(Boolean))];
    assert(
      warningRows.some((row) => classifyWarningLevel(row) === normalizeWarningLevel(PROBE.level)),
      `warning level mismatch: expected=${PROBE.level}, actual=${matchedLevels.join(',') || 'none'}`
    );
    stage('sql.warning-record', 'passed', `rows=${warningRowIds.join(',')} / level=${matchedLevels.join(',')}`);

    const warningList = await requestJson(session, 'GET', '/risk-warning/list', {
      query: { page: 1, size: 20, handled: false, userCode: target.empCode },
      dataSource: PIPELINE_DATA_SOURCE
    });
    assertResultOk(warningList);
    const warningItems = warningList.payload?.data?.list || warningList.payload?.data?.records || [];
    const apiRow = warningItems.find((item) => warningRowIds.includes(Number(item.id)));
    assert(apiRow, 'risk-warning list missing probe warning');
    stage('api.risk-warning-list', 'passed', `${apiRow.warningType || apiRow.indicatorName} ${apiRow.warningValue || apiRow.indicatorValue || ''}`.trim());

    const pageState = await verifyNotificationsPage(session, target, apiRow.userName || apiRow.empName || target.empCode);
    stage(
      'page.notifications.handle',
      'passed',
      `${truncate(pageState.before.text, 80)} -> ${pageState.after.handled ? '已处理' : '未处理'}`
    );

    const handledRows = await verifyWarningsHandled(warningRowIds);
    assert(handledRows.length === warningRowIds.length, 'handled warning rows could not be reloaded from SQL');
    assert(
      handledRows.every((row) => row.handled === true || row.handled === 1),
      `warning rows not marked handled: ${handledRows.map((row) => `${row.id}:${row.handled}`).join(', ')}`
    );
    stage(
      'sql.warning-handled',
      'passed',
      handledRows.map((row) => `${row.id}:${row.handleBy || 'unknown'}`).join(', ')
    );
  }
} catch (error) {
  fatalError = error;
  summary.status = 'failed';
  stage('warning-pipeline', 'failed', truncate(String(error), 320));
} finally {
  if (redisClient && redisPayloads.length > 0) {
    summary.cleanup.redisTrimmed = await cleanupRedisPayloads(redisClient, redisPayloads);
  }
  if (redisClient) {
    redisClient.close();
  }
  if (warningRowIds.length > 0) {
    summary.cleanup.deletedWarningRows = await deleteWarningRows(warningRowIds);
  }
  if (healthRowIds.length > 0) {
    summary.cleanup.deletedHealthRows = await deleteHealthRows(healthRowIds);
  }

  summary.finishedAt = new Date().toISOString();
  await fs.writeFile(REPORT_JSON, JSON.stringify(summary, null, 2), 'utf8');
  await fs.writeFile(REPORT_MD, buildMarkdownReport(), 'utf8');
}

const failedStages = summary.stages.filter((item) => item.status === 'failed').length;
const skippedStages = summary.stages.filter((item) => item.status === 'skipped').length;

console.log(JSON.stringify({
  artifactDir: ARTIFACT_DIR,
  reportFile: REPORT_MD,
  stageCount: summary.stages.length,
  failedStages,
  skippedStages,
  warnings: summary.warnings.length,
  status: summary.status,
  cleanup: summary.cleanup
}, null, 2));

if (fatalError) {
  console.error(truncate(String(fatalError), 400));
}

if (failedStages > 0) {
  process.exitCode = 1;
}
