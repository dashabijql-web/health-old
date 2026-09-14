import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import {
  assertResultOk,
  buildAuthHeaders,
  formatLocalDate,
  pruneArtifacts,
  resolveSession,
  truncate
} from '../shared/health-test-utils.mjs';

const DATA_SOURCE = process.env.API_DATA_SOURCE || 'old';
const EXPECT_NON_EMPTY = process.env.API_EXPECT_NON_EMPTY
  ? process.env.API_EXPECT_NON_EMPTY === '1'
  : DATA_SOURCE === 'old';
const RETENTION = Number.parseInt(process.env.API_ARTIFACT_RETENTION || '10', 10);
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-');
const ARTIFACT_DIR = path.resolve(process.cwd(), 'tests', 'api', 'artifacts', RUN_ID);
const REPORT_JSON = path.join(ARTIFACT_DIR, 'data-density.json');
const REPORT_MD = path.join(ARTIFACT_DIR, 'data-density.md');
const TODAY_KEY = formatLocalDate(new Date());
const MONTH_KEY = TODAY_KEY.slice(0, 7);

const CHECKS = [
  metric('dashboard.overview.heartRate', '/dashboard/overview', (data) => data?.heartRate),
  metric('dashboard.overview.bloodOxygen', '/dashboard/overview', (data) => data?.bloodOxygen),
  metric('dashboard.body.avgHeartRate', '/dashboard/body-indicators', (data) => data?.avgHeartRate),
  metric('dashboard.body.avgBloodOxygen', '/dashboard/body-indicators', (data) => data?.avgBloodOxygen),
  metric('realtime.online.total', '/realtime/online-users?page=1&size=20', (data) => data?.total),
  metric('realtime.statistics.todayRecords', '/realtime/statistics', (data) => data?.todayRecords),
  metric('risk-warning.total', '/risk-warning/list?page=1&size=10', (data) => data?.total),
  metric('heart-rate.totalCount', '/heart-rate/overview', (data) => data?.totalCount),
  metric('pressure.totalCount', '/pressure/overview', (data) => data?.totalCount),
  metric('blood-pressure.totalCount', '/blood-pressure/overview', (data) => data?.totalCount),
  metric('blood-oxygen.totalCount', '/blood-oxygen/overview', (data) => data?.totalCount),
  arrayLength('pressure.hourly', `/pressure/hourly?date=${TODAY_KEY}`),
  arrayLength('blood-pressure.hourly', `/blood-pressure/hourly?date=${TODAY_KEY}`),
  arrayLength('blood-oxygen.hourly', `/blood-oxygen/hourly?startDate=${TODAY_KEY}&endDate=${TODAY_KEY}`),
  arrayLength('sleep.detailList', '/sleep/page-data', (data) => data?.detailList),
  arrayLength('statistics.monthly-summary', `/statistics/monthly-summary?month=${MONTH_KEY}`),
  arrayLength('trend-warning.list', '/trend-warning/predict', (data) => data?.list),
  arrayLength('employee.list-detail', '/employee/list-detail'),
  metric('employee.stats.total', '/employee/stats', (data) => data?.total),
  metric('health-record.total', '/api/health/record/page?current=1&size=10', (data) => data?.total),
  metric('device.online.count', '/api/device/online', (data) => data?.count)
];

const summary = {
  runId: RUN_ID,
  dataSource: DATA_SOURCE,
  expectNonEmpty: EXPECT_NON_EMPTY,
  today: TODAY_KEY,
  month: MONTH_KEY,
  startedAt: new Date().toISOString(),
  target: null,
  checks: []
};

await fs.mkdir(ARTIFACT_DIR, { recursive: true });
await pruneArtifacts(path.dirname(ARTIFACT_DIR), RETENTION);

function asNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function metric(name, routePath, pick) {
  return {
    name,
    routePath,
    measure: (data) => asNumber(pick(data)),
    note: (value) => `value=${value}`
  };
}

function arrayLength(name, routePath, pick = (data) => data) {
  return {
    name,
    routePath,
    measure: (data) => {
      const value = pick(data);
      return Array.isArray(value) ? value.length : 0;
    },
    note: (value) => `rows=${value}`
  };
}

async function requestJsonWithDataSource(session, routePath) {
  const response = await fetch(`${session.target.origin}${session.target.apiPrefix}${routePath}`, {
    headers: {
      ...buildAuthHeaders(session),
      cookie: `User-Token=${session.token}; satoken=${session.token}; Health-Data-Source=${DATA_SOURCE}`,
      'X-Health-Data-Source': DATA_SOURCE
    }
  });
  const text = await response.text();
  let payload = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = null;
  }
  return {
    status: response.status,
    source: response.headers.get('x-health-data-source') || '',
    payload
  };
}

async function runCheck(session, check) {
  const startedAt = Date.now();
  const record = {
    name: check.name,
    routePath: check.routePath,
    status: 'passed',
    durationMs: 0,
    source: '',
    value: 0,
    note: ''
  };

  try {
    const result = await requestJsonWithDataSource(session, check.routePath);
    record.source = result.source;
    assertResultOk(result);
    record.value = check.measure(result.payload.data);
    record.note = check.note(record.value);
    if (record.value <= 0) {
      if (EXPECT_NON_EMPTY) {
        throw new Error(`${check.name} is empty under source=${DATA_SOURCE}`);
      }
      record.status = 'warning';
      record.note = `${check.name} is empty under source=${DATA_SOURCE}`;
    }
  } catch (error) {
    record.status = 'failed';
    record.note = truncate(String(error), 300);
  }

  record.durationMs = Date.now() - startedAt;
  summary.checks.push(record);
}

function buildMarkdownReport() {
  const failed = summary.checks.filter((item) => item.status === 'failed').length;
  const warnings = summary.checks.filter((item) => item.status === 'warning').length;
  const passed = summary.checks.length - failed - warnings;
  return [
    '# Health data-density report',
    '',
    `- run_id: ${summary.runId}`,
    `- target: ${summary.target?.label || '-'} (${summary.target?.origin || '-'})`,
    `- data_source: ${summary.dataSource}`,
    `- expect_non_empty: ${summary.expectNonEmpty}`,
    `- today: ${summary.today}`,
    `- month: ${summary.month}`,
    `- started_at: ${summary.startedAt}`,
    `- finished_at: ${summary.finishedAt}`,
    '',
    '## Totals',
    '',
    `- passed: ${passed}`,
    `- warnings: ${warnings}`,
    `- failed: ${failed}`,
    '',
    '## Checks',
    '',
    '| check | status | source | value | ms | note |',
    '| --- | --- | --- | ---: | ---: | --- |',
    ...summary.checks.map((item) => `| ${item.name} | ${item.status} | ${item.source || '-'} | ${item.value} | ${item.durationMs} | ${item.note || '-'} |`)
  ].join('\n');
}

const session = await resolveSession();
summary.target = session.target;

for (const check of CHECKS) {
  await runCheck(session, check);
}

summary.finishedAt = new Date().toISOString();
await fs.writeFile(REPORT_JSON, JSON.stringify(summary, null, 2), 'utf8');
await fs.writeFile(REPORT_MD, buildMarkdownReport(), 'utf8');

const failedCount = summary.checks.filter((item) => item.status === 'failed').length;
const warningCount = summary.checks.filter((item) => item.status === 'warning').length;

console.log(JSON.stringify({
  artifactDir: ARTIFACT_DIR,
  reportFile: REPORT_MD,
  checkCount: summary.checks.length,
  failedCount,
  warningCount
}, null, 2));

if (failedCount > 0) {
  process.exitCode = 1;
}
