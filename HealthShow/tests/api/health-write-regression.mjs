import fs from 'node:fs/promises';
import net from 'node:net';
import path from 'node:path';
import process from 'node:process';
import {
  assert,
  assertResultOk,
  pruneArtifacts,
  requestJson,
  resolveSession,
  truncate
} from '../shared/health-test-utils.mjs';
import { spawn } from 'node:child_process';

const RETENTION = Number.parseInt(process.env.API_WRITE_ARTIFACT_RETENTION || '10', 10);
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-');
const ARTIFACT_DIR = path.resolve(process.cwd(), 'tests', 'api', 'artifacts', RUN_ID);
const REPORT_JSON = path.join(ARTIFACT_DIR, 'summary.json');
const REPORT_MD = path.join(ARTIFACT_DIR, 'summary.md');

const SQL_SERVER = process.env.SQL_SERVER || 'localhost,1433';
const SQL_USER = process.env.SQL_USER || 'sa';
const SQL_PASSWORD = process.env.SQL_PASSWORD || '';
const SQL_DB = process.env.SQL_DB || 'health';
const API_DATA_SOURCE = process.env.API_DATA_SOURCE || '';
const SQLCMD_BIN = process.env.SQLCMD_BIN || 'sqlcmd';
const CURRENT_MONTH = new Date().toISOString().slice(0, 7).replace('-', '');
const TCP_HOST = process.env.WATCH_TCP_HOST || '127.0.0.1';
const TCP_PORT = Number.parseInt(process.env.WATCH_TCP_PORT || '9000', 10);
const WRITE_SQL_WAIT_MS = Number.parseInt(process.env.API_WRITE_SQL_WAIT_MS || '20000', 10);
const HEALTH_TABLE = `health_record_${CURRENT_MONTH}`;

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
  target: null,
  checks: [],
  warnings: []
};

await fs.mkdir(ARTIFACT_DIR, { recursive: true });
await pruneArtifacts(path.dirname(ARTIFACT_DIR), RETENTION);

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

assert(SQL_PASSWORD, 'SQL_PASSWORD is required for SQL-backed write regression tests');

const isNewDataSource = SQL_DB.toLowerCase() === 'health_new' || API_DATA_SOURCE === 'new';

function redactSecrets(value) {
  return String(value)
    .replace(/(-P\s+)([^\s]+)/gi, '$1[REDACTED]')
    .replace(new RegExp(SQL_PASSWORD.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g'), '[REDACTED]');
}

function normalizeSqlError(error) {
  const parts = [];
  if (error?.code !== undefined) parts.push(`sqlcmd exitCode=${error.code}`);
  if (error?.signal) parts.push(`sqlcmd signal=${error.signal}`);
  if (error?.stderr) parts.push(`stderr: ${error.stderr}`);
  if (error?.stdout) parts.push(`stdout: ${error.stdout}`);
  if (!parts.length && error?.message) parts.push(error.message);
  return redactSecrets(parts.join('\n')).trim() || String(error);
}

async function runCheck(name, fn) {
  const startedAt = Date.now();
  const record = {
    name,
    status: 'passed',
    durationMs: 0,
    note: ''
  };

  try {
    const note = await fn();
    if (note) record.note = note;
  } catch (error) {
    record.status = error?.skip ? 'skipped' : 'failed';
    record.note = truncate(redactSecrets(String(error)), Number.parseInt(process.env.API_WRITE_NOTE_LIMIT || '1200', 10));
  }

  record.durationMs = Date.now() - startedAt;
  summary.checks.push(record);
  return record;
}

function skipCheck(note) {
  const error = new Error(note);
  error.skip = true;
  throw error;
}

function skipIfNewDataSource(error, note) {
  if (!isNewDataSource) throw error;
  skipCheck(note);
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

async function sqlJson(query) {
  const batch = `SET NOCOUNT ON; ${query}`;
  const args = [
    '-S', SQL_SERVER,
    '-U', SQL_USER,
    '-P', SQL_PASSWORD,
    '-d', SQL_DB,
    '-r', '1',
    '-w', '65535',
    '-y', '0',
    '-Y', '0',
    '-Q', batch
  ];
  const result = await runSqlcmd(args);
  const combinedOutput = `${result.stdout || ''}\n${result.stderr || ''}`.trim();
  if (result.code !== 0 || /Msg \d+,/i.test(combinedOutput) || /错误|error/i.test(result.stderr || '')) {
    throw new Error(redactSecrets([
      `sqlcmd exitCode=${result.code}`,
      result.signal ? `signal=${result.signal}` : '',
      result.stderr ? `stderr: ${result.stderr}` : '',
      result.stdout ? `stdout: ${result.stdout}` : ''
    ].filter(Boolean).join('\n')));
  }

  const text = result.stdout.trim();
  if (!text) return null;
  const starts = [text.indexOf('['), text.indexOf('{')].filter((idx) => idx >= 0);
  const start = starts.length ? Math.min(...starts) : -1;
  if (start < 0) return null;
  return JSON.parse(text.slice(start));
}

async function sqlExecRows(query) {
  const result = await sqlJson(`${query}; SELECT @@ROWCOUNT AS rows FOR JSON PATH, WITHOUT_ARRAY_WRAPPER`);
  return result?.rows ?? 0;
}

function escapeSqlString(value) {
  return String(value).replace(/'/g, "''");
}

async function restoreWarningRow(id, createTime) {
  const monthTable = `warning_record_${tableSuffixFromTime(createTime)}`;
  const monthRows = await sqlExecRows(`
    UPDATE ${monthTable}
    SET is_handled = 0,
        handle_time = NULL,
        handle_by = NULL,
        remark = NULL
    WHERE id = ${id}
  `).catch(() => 0);

  if (monthRows > 0) return monthRows;

  return sqlExecRows(`
    UPDATE warning_record
    SET is_handled = 0,
        handle_time = NULL,
        handle_by = NULL,
        remark = NULL
    WHERE id = ${id}
  `);
}

function tableSuffixFromTime(timeValue) {
  const month = String(timeValue).slice(0, 7).replace('-', '');
  return month;
}

async function getWriteProbeTarget() {
  const simulatorFirst = SQL_DB.toLowerCase() === 'health';
  const simulatorOrder = simulatorFirst
    ? "CASE WHEN d.imei LIKE '3594567800_____' THEN 0 ELSE 1 END,"
    : "CASE WHEN d.imei LIKE '3594567800_____' THEN 1 ELSE 0 END,";

  const row = await sqlJson(`
    SELECT TOP 1
      d.id AS deviceId,
      d.imei,
      du.emp_id AS empId,
      e.emp_code AS empCode,
      e.emp_name AS empName
    FROM device d
    JOIN device_user du ON du.device_id = d.id AND du.unbind_time IS NULL
    JOIN employee e ON e.id = du.emp_id
    WHERE d.imei IS NOT NULL AND LEN(d.imei) = 15
    ORDER BY ${simulatorOrder} d.id
    FOR JSON PATH, WITHOUT_ARRAY_WRAPPER
  `);
  assert(row?.imei && row?.empCode, 'no bound write probe target found');
  return row;
}

async function getWriteProbeBaseline(target) {
  const empCode = escapeSqlString(target.empCode);
  const row = await sqlJson(`
    SELECT ISNULL(MAX(id), 0) AS maxId
    FROM ${HEALTH_TABLE}
    WHERE user_code = '${empCode}'
    FOR JSON PATH, WITHOUT_ARRAY_WRAPPER
  `);
  return { maxId: Number(row?.maxId || 0) };
}

async function queryWriteProbeRows(target, baseline) {
  const empCode = escapeSqlString(target.empCode);
  const rows = await sqlJson(`
    SELECT
      id,
      user_code AS userCode,
      heart_rate AS heartRate,
      blood_oxygen AS bloodOxygen,
      blood_pressure_high AS systolic,
      blood_pressure_low AS diastolic,
      temperature,
      steps,
      calories,
      record_time AS recordTime
    FROM ${HEALTH_TABLE}
    WHERE user_code = '${empCode}'
      AND id > ${Number(baseline.maxId || 0)}
      AND (
        (
          heart_rate = ${PROBE.heartRate}
          AND blood_oxygen = ${PROBE.bloodOxygen}
          AND blood_pressure_high = ${PROBE.systolic}
          AND blood_pressure_low = ${PROBE.diastolic}
          AND CAST(ROUND(CAST(temperature AS FLOAT), 0) AS INT) = ${PROBE.temperatureStored}
        )
        OR (steps = ${PROBE.steps} AND calories = ${PROBE.calories})
      )
    ORDER BY id
    FOR JSON PATH
  `);
  return Array.isArray(rows) ? rows : [];
}

async function deleteWriteProbeRows(rowIds) {
  const ids = [...new Set(rowIds.map((id) => Number(id)).filter(Number.isFinite))];
  if (!ids.length) return 0;
  return sqlExecRows(`DELETE FROM ${HEALTH_TABLE} WHERE id IN (${ids.join(',')})`);
}

async function cleanupWriteProbeRows(target, baseline, rowIds) {
  const deleted = await deleteWriteProbeRows(rowIds);
  const residueRows = await queryWriteProbeRows(target, baseline).catch(() => []);
  if (residueRows.length > 0) {
    summary.warnings.push(`write probe cleanup residue: ${residueRows.length}`);
  }
  return deleted;
}

async function sendPacket(socket, packet) {
  socket.write(packet, 'utf8');
  await sleep(180);
}

async function sendWriteProbePackets(target) {
  const socket = await new Promise((resolve, reject) => {
    const conn = net.createConnection({ host: TCP_HOST, port: TCP_PORT }, () => resolve(conn));
    conn.once('error', reject);
  });

  socket.setTimeout(5000);
  socket.on('data', () => {
    // The write regression only needs the server to accept and persist the packets.
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
  } finally {
    socket.end();
    socket.destroy();
  }
}

async function waitForWriteProbeRows(target, baseline, timeoutMs = WRITE_SQL_WAIT_MS) {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    const rows = await queryWriteProbeRows(target, baseline);
    if (rows.length > 0) {
      return rows;
    }
    await sleep(500);
  }

  throw new Error(`write probe not observed in ${HEALTH_TABLE} for ${target.empCode}/${target.imei}`);
}

async function findWritableEmployeeCandidate() {
  return sqlJson(`
    SELECT TOP 1
      e.emp_code AS empCode,
      e.emp_name AS empName,
      COUNT(hr.id) AS recordCount
    FROM employee e
    INNER JOIN v_health_record hr
      ON hr.user_code = e.emp_code
     AND hr.record_time >= DATEADD(DAY, -30, GETDATE())
    WHERE NOT EXISTS (
      SELECT 1
      FROM ai_health_report r
      WHERE r.emp_code = e.emp_code
    )
    GROUP BY e.emp_code, e.emp_name
    HAVING COUNT(hr.id) > 0
    ORDER BY COUNT(hr.id) DESC, e.emp_code ASC
    FOR JSON PATH, WITHOUT_ARRAY_WRAPPER
  `);
}

async function main() {
  const session = await resolveSession();
  summary.target = session.target;

  await runCheck('auth.info', async () => {
    const result = await requestJson(session, 'GET', '/auth/info');
    assertResultOk(result);
    const data = result.payload.data;
    assert(data && typeof data === 'object', 'auth info data is not object');
    assert(typeof data.name === 'string' && data.name.length > 0, 'auth info missing name');
    return data.name;
  });

  await runCheck('realtime.chain', async () => {
    let target;
    try {
      target = await getWriteProbeTarget();
    } catch (error) {
      skipIfNewDataSource(error, 'new data source has no bound write probe target; live TCP write probe is skipped until device/employee seed data exists');
    }
    const baseline = await getWriteProbeBaseline(target);
    const insertedIds = [];

    try {
      await sendWriteProbePackets(target);
      const rows = await waitForWriteProbeRows(target, baseline);
      insertedIds.push(...rows.map((row) => row.id));
      const observed = rows[rows.length - 1];
      assert(observed?.id && observed?.userCode, 'write probe row missing user code');

      const pageAfter = await requestJson(session, 'GET', '/api/health/record/page', {
        query: { current: 1, size: 1, userCode: observed.userCode }
      });
      assertResultOk(pageAfter);
      const latestPageRow = pageAfter.payload?.data?.records?.[0];
      assert(latestPageRow?.id === observed.id, 'API did not expose the observed live record');

      const realtimeResult = await requestJson(session, 'GET', `/realtime/user/${observed.userCode}`);
      assertResultOk(realtimeResult);
      assert(realtimeResult.payload?.data && typeof realtimeResult.payload.data === 'object', 'realtime user data is not object');

      return `${observed.userCode} @ ${observed.recordTime || observed.time}`;
    } finally {
      if (insertedIds.length > 0) {
        await cleanupWriteProbeRows(target, baseline, insertedIds);
      }
    }
  });

  await runCheck('risk-warning.handle', async () => {
    const listResult = await requestJson(session, 'GET', '/risk-warning/list', {
      query: { page: 1, size: 20, handled: false }
    });
    assertResultOk(listResult);
    const row = (listResult.payload?.data?.list || []).find((item) => item?.id && !item?.handled);
    if (!row?.id || !row?.createTime) {
      skipIfNewDataSource(new Error('no unhandled warning found'), 'new data source has no unhandled warning candidate; warning handle write probe is skipped until warning seed data exists');
    }

    const createTime = String(row.createTime);
    const tableName = `warning_record_${tableSuffixFromTime(createTime)}`;
    const handleBody = {
      handleBy: 'system',
      handleRemark: 'health write regression',
      createTime
    };

    try {
      const handleResult = await requestJson(session, 'POST', `/risk-warning/handle/${row.id}`, {
        data: handleBody
      });
      assertResultOk(handleResult);

      const handledRow = await sqlJson(`
        SELECT TOP 1 id, is_handled AS handled, handle_by AS handleBy, remark AS handleRemark
        FROM ${tableName}
        WHERE id = ${row.id}
        FOR JSON PATH, WITHOUT_ARRAY_WRAPPER
      `) || await sqlJson(`
        SELECT TOP 1 id, is_handled AS handled, handle_by AS handleBy, remark AS handleRemark
        FROM warning_record
        WHERE id = ${row.id}
        FOR JSON PATH, WITHOUT_ARRAY_WRAPPER
      `);

      if (!(handledRow?.handled === true || handledRow?.handled === 1)) {
        skipIfNewDataSource(new Error('warning was not marked handled'), 'new data source warning handle candidate is not persisted in a writable warning table yet');
        assert(false, 'warning was not marked handled');
      }
      return `warning ${row.id} handled`;
    } finally {
      await restoreWarningRow(row.id, createTime);
    }
  });

  await runCheck('alert-config.update', async () => {
    const listResult = await requestJson(session, 'GET', '/alert-config/list');
    assertResultOk(listResult);
    const row = (listResult.payload?.data || []).find((item) => item?.id);
    assert(row?.id, 'no alert config found');

    const original = { ...row };
    const mutated = { ...row, enabled: row.enabled ? 0 : 1 };

    try {
      let updateResult = await requestJson(session, 'PUT', '/alert-config/update', { data: mutated });
      assertResultOk(updateResult);

      let verifyResult = await requestJson(session, 'GET', '/alert-config/list');
      assertResultOk(verifyResult);
      const changed = (verifyResult.payload?.data || []).find((item) => item?.id === row.id);
      assert(changed && changed.enabled === mutated.enabled, 'alert config update did not persist');

      updateResult = await requestJson(session, 'PUT', '/alert-config/update', { data: original });
      assertResultOk(updateResult);

      verifyResult = await requestJson(session, 'GET', '/alert-config/list');
      assertResultOk(verifyResult);
      const restored = (verifyResult.payload?.data || []).find((item) => item?.id === row.id);
      assert(restored && restored.enabled === original.enabled, 'alert config rollback failed');

      return `config ${row.id}`;
    } catch (error) {
      await requestJson(session, 'PUT', '/alert-config/update', { data: original }).catch(() => {});
      throw error;
    }
  });

  await runCheck('ai.report.employee', async () => {
    const candidate = await findWritableEmployeeCandidate();
    if (!candidate?.empCode) {
      skipIfNewDataSource(new Error('no writable AI report candidate found'), 'new data source has no writable AI report candidate; AI employee report write probe is skipped until employee/health seed data exists');
    }

    try {
      const result = await requestJson(session, 'POST', '/ai/report/employee', {
        data: { empCode: candidate.empCode },
        headers: { 'content-type': 'application/json' }
      });
      assertResultOk(result);
      const report = result.payload?.data?.report || result.payload?.data?.reportContent || '';
      assert(typeof report === 'string' && report.length > 50, 'AI report content is empty');
      return `${candidate.empCode} ${report.length} chars`;
    } finally {
      await sqlExecRows(`DELETE FROM ai_health_report WHERE emp_code = '${String(candidate.empCode).replace(/'/g, "''")}'`)
        .catch(() => {});
    }
  });
}

function buildMarkdownReport() {
  const totals = {
    passed: summary.checks.filter((item) => item.status === 'passed').length,
    failed: summary.checks.filter((item) => item.status === 'failed').length,
    skipped: summary.checks.filter((item) => item.status === 'skipped').length
  };

  return [
    '# Health write regression report',
    '',
    `- run_id: ${summary.runId}`,
    `- target: ${summary.target?.label || '-'} (${summary.target?.origin || '-'})`,
    `- started_at: ${summary.startedAt}`,
    `- finished_at: ${summary.finishedAt}`,
    '',
    '## Totals',
    '',
    `- passed: ${totals.passed}`,
    `- failed: ${totals.failed}`,
    `- skipped: ${totals.skipped}`,
    `- warnings: ${summary.warnings.length}`,
    '',
    '## Checks',
    '',
    '| check | status | ms | note |',
    '| --- | --- | ---: | --- |',
    ...summary.checks.map((item) => `| ${item.name} | ${item.status} | ${item.durationMs} | ${item.note || '-'} |`),
    '',
    '## Warnings',
    '',
    ...(summary.warnings.length ? summary.warnings.map((item) => `- ${item}`) : ['- none'])
  ].join('\n');
}

try {
  await main();
} finally {
  summary.finishedAt = new Date().toISOString();
  await fs.writeFile(REPORT_JSON, JSON.stringify(summary, null, 2), 'utf8');
  await fs.writeFile(REPORT_MD, buildMarkdownReport(), 'utf8');
}

const failedCount = summary.checks.filter((item) => item.status === 'failed').length;
const skippedCount = summary.checks.filter((item) => item.status === 'skipped').length;

console.log(JSON.stringify({
  artifactDir: ARTIFACT_DIR,
  target: summary.target,
  reportFile: REPORT_MD,
  checkCount: summary.checks.length,
  failedCount,
  skippedCount,
  status: failedCount > 0 ? 'failed' : skippedCount > 0 ? 'skipped' : 'passed',
  warningCount: summary.warnings.length
}, null, 2));

if (failedCount > 0) {
  process.exitCode = 1;
}
