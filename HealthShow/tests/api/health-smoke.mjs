import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import {
  assert,
  assertResultOk,
  formatLocalDate,
  isObject,
  pruneArtifacts,
  requestJson,
  resolveSession,
  truncate
} from '../shared/health-test-utils.mjs';

const RETENTION = Number.parseInt(process.env.API_ARTIFACT_RETENTION || '10', 10);
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-');
const ARTIFACT_DIR = path.resolve(process.cwd(), 'tests', 'api', 'artifacts', RUN_ID);
const REPORT_JSON = path.join(ARTIFACT_DIR, 'summary.json');
const REPORT_MD = path.join(ARTIFACT_DIR, 'summary.md');
const TODAY_KEY = formatLocalDate(new Date());
const MONTH_KEY = TODAY_KEY.slice(0, 7);
const [CURRENT_YEAR, CURRENT_MONTH] = MONTH_KEY.split('-').map(Number);

const summary = {
  runId: RUN_ID,
  month: MONTH_KEY,
  startedAt: new Date().toISOString(),
  target: null,
  checks: [],
  warnings: []
};

await fs.mkdir(ARTIFACT_DIR, { recursive: true });
await pruneArtifacts(path.dirname(ARTIFACT_DIR), RETENTION);

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
    record.status = 'failed';
    record.note = truncate(String(error), 300);
  }

  record.durationMs = Date.now() - startedAt;
  summary.checks.push(record);
  return record;
}

function pickArray(payloadData) {
  if (Array.isArray(payloadData)) return payloadData;
  if (Array.isArray(payloadData?.list)) return payloadData.list;
  if (Array.isArray(payloadData?.records)) return payloadData.records;
  return [];
}

function buildMarkdownReport() {
  const failed = summary.checks.filter((item) => item.status === 'failed').length;
  const passed = summary.checks.length - failed;

  return [
    '# Health API smoke report',
    '',
    `- run_id: ${summary.runId}`,
    `- month: ${summary.month}`,
    `- target: ${summary.target?.label || '-'} (${summary.target?.origin || '-'})`,
    `- started_at: ${summary.startedAt}`,
    `- finished_at: ${summary.finishedAt}`,
    '',
    '## Totals',
    '',
    `- passed: ${passed}`,
    `- failed: ${failed}`,
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
    ...(summary.warnings.length
      ? summary.warnings.map((item) => `- ${item}`)
      : ['- none'])
  ].join('\n');
}

const session = await resolveSession();
summary.target = session.target;
let sampleCalendarDate = TODAY_KEY;

await runCheck('auth.info', async () => {
  const result = await requestJson(session, 'GET', '/auth/info');
  assertResultOk(result);
  const data = result.payload.data;
  assert(isObject(data), 'auth info data is not object');
  assert(typeof data.name === 'string' && data.name.length > 0, 'auth info missing name');
  return data.name;
});

await runCheck('dashboard.overview', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/overview');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'dashboard overview data is not object');
});

await runCheck('dashboard.body-indicators', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/body-indicators');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'body indicators data is not object');
});

await runCheck('dashboard.warning-events', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/warning-events');
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'warning events data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.type === 'string', 'warning event missing type');
    assert(typeof first.userName === 'string', 'warning event missing userName');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('dashboard.device-activation', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/device-activation');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'device activation data is not object');
  assert(isObject(result.payload.data.stats), 'device activation stats is not object');
  assert(Array.isArray(result.payload.data.warningRates), 'device activation warningRates is not array');
});

await runCheck('dashboard.warning-counts', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/warning-counts', {
    query: { groupBy: 'day' }
  });
  assertResultOk(result);
  assert(isObject(result.payload.data), 'warning counts data is not object');
  assert(Array.isArray(result.payload.data.labels), 'warning counts labels is not array');
  assert(Array.isArray(result.payload.data.counts), 'warning counts counts is not array');
});

await runCheck('dashboard.person-counts', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/person-counts');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'person counts data is not object');
  assert(typeof result.payload.data.totalPersons === 'number', 'person counts missing totalPersons');
});

await runCheck('dashboard.dept-stats', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/dept-stats');
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'dept stats data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.name === 'string', 'dept stats missing name');
    assert(typeof first.count === 'number', 'dept stats missing count');
  }
});

await runCheck('dashboard.pre-shift-compliance', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/pre-shift-compliance');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'pre-shift compliance data is not object');
  assert(typeof result.payload.data.preShiftRate === 'number', 'pre-shift compliance missing preShiftRate');
});

await runCheck('dashboard.daily-trend', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/daily-trend', {
    query: { days: 7 }
  });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'daily trend data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.date === 'string', 'daily trend missing date');
    assert(typeof first.heartRateRate === 'number', 'daily trend missing heartRateRate');
  }
});

await runCheck('dashboard.calendar', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/calendar', {
    query: { year: CURRENT_YEAR, month: CURRENT_MONTH }
  });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'calendar data is not array');
  sampleCalendarDate = result.payload.data.find((item) => item?.date)?.date || TODAY_KEY;
  return `${result.payload.data.length} days @ ${sampleCalendarDate}`;
});

await runCheck('dashboard.calendar.day-heart-rate', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/calendar/day-heart-rate', {
    query: { date: sampleCalendarDate }
  });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'day heart rate rank data is not array');
  return `${result.payload.data.length} rows`;
});

await runCheck('dashboard.calendar.day-blood-oxygen', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/calendar/day-blood-oxygen', {
    query: { date: sampleCalendarDate }
  });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'day blood oxygen rank data is not array');
  return `${result.payload.data.length} rows`;
});

await runCheck('dashboard.calendar.day-steps', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/calendar/day-steps', {
    query: { date: sampleCalendarDate }
  });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'day steps rank data is not array');
  return `${result.payload.data.length} rows`;
});

await runCheck('dashboard.calendar.day-warnings', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/calendar/day-warnings', {
    query: { date: sampleCalendarDate }
  });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'day warnings data is not array');
  return `${result.payload.data.length} rows`;
});

await runCheck('dashboard.mine-entry-list', async () => {
  const result = await requestJson(session, 'GET', '/dashboard/mine-entry-list', { query: { size: 20 } });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'mine entry list data is not array');
  return `${result.payload.data.length} rows`;
});

await runCheck('realtime.overview', async () => {
  const result = await requestJson(session, 'GET', '/realtime/overview');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'realtime overview data is not object');
  assert(typeof result.payload.data.avgHeartRate === 'number', 'realtime overview missing avgHeartRate');
  assert(typeof result.payload.data.todayWarningCount === 'number', 'realtime overview missing todayWarningCount');
});

await runCheck('realtime.online-users', async () => {
  const result = await requestJson(session, 'GET', '/realtime/online-users', { query: { page: 1, size: 20 } });
  assertResultOk(result);
  assert(isObject(result.payload.data), 'online users data is not object');
  assert(Array.isArray(result.payload.data.list), 'online users list is not array');
  assert(typeof result.payload.data.total === 'number', 'online users missing total');
  const list = pickArray(result.payload.data);
  return `${list.length} rows`;
});

await runCheck('realtime.statistics', async () => {
  const result = await requestJson(session, 'GET', '/realtime/statistics');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'realtime statistics data is not object');
  assert(typeof result.payload.data.onlineUsers === 'number', 'realtime statistics missing onlineUsers');
  assert(typeof result.payload.data.normalRate === 'number', 'realtime statistics missing normalRate');
});

await runCheck('realtime.alerts', async () => {
  const result = await requestJson(session, 'GET', '/realtime/alerts', { query: { limit: 20 } });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'realtime alerts data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.userCode === 'string', 'realtime alert missing userCode');
    assert(typeof first.warningType === 'string', 'realtime alert missing warningType');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('risk-warning.overview', async () => {
  const result = await requestJson(session, 'GET', '/risk-warning/overview');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'risk overview data is not object');
  assert(typeof result.payload.data.totalWarnings === 'number', 'risk overview missing totalWarnings');
  assert(typeof result.payload.data.handledRate === 'number', 'risk overview missing handledRate');
});

await runCheck('risk-warning.list', async () => {
  const result = await requestJson(session, 'GET', '/risk-warning/list', { query: { page: 1, size: 10 } });
  assertResultOk(result);
  assert(isObject(result.payload.data), 'risk list data is not object');
  const list = pickArray(result.payload.data);
  assert(typeof result.payload.data.total === 'number', 'risk list missing total');
  const first = list[0];
  if (first) {
    assert(typeof first.userCode === 'string', 'risk list row missing userCode');
    assert(typeof first.warningType === 'string', 'risk list row missing warningType');
  }
  return `${list.length} rows`;
});

await runCheck('risk-warning.trend', async () => {
  const result = await requestJson(session, 'GET', '/risk-warning/trend', { query: { days: 30 } });
  assertResultOk(result);
  assert(isObject(result.payload.data), 'risk trend data is not object');
  assert(Array.isArray(result.payload.data.dates), 'risk trend missing dates');
  assert(isObject(result.payload.data.series), 'risk trend missing series');
});

await runCheck('risk-warning.dept-stats', async () => {
  const result = await requestJson(session, 'GET', '/risk-warning/dept-stats');
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'risk dept stats data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.deptName === 'string', 'risk dept stats row missing deptName');
    assert(typeof first.total === 'number', 'risk dept stats row missing total');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('risk-warning.type-distribution', async () => {
  const result = await requestJson(session, 'GET', '/risk-warning/type-distribution');
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'risk type distribution data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.type === 'string', 'risk type distribution row missing type');
    assert(typeof first.count === 'number', 'risk type distribution row missing count');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('pressure.overview', async () => {
  const result = await requestJson(session, 'GET', '/pressure/overview');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'pressure overview data is not object');
  assert(typeof result.payload.data.avgPressure === 'number', 'pressure overview missing avgPressure');
  assert(typeof result.payload.data.normalRate === 'number', 'pressure overview missing normalRate');
});

await runCheck('pressure.trend', async () => {
  const result = await requestJson(session, 'GET', '/pressure/trend', { query: { days: 7 } });
  assertResultOk(result);
  assert(isObject(result.payload.data), 'pressure trend data is not object');
  assert(Array.isArray(result.payload.data.dates), 'pressure trend missing dates');
  assert(Array.isArray(result.payload.data.values), 'pressure trend missing values');
});

await runCheck('pressure.distribution', async () => {
  const result = await requestJson(session, 'GET', '/pressure/distribution');
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'pressure distribution data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.name === 'string', 'pressure distribution row missing name');
    assert(typeof first.value === 'number', 'pressure distribution row missing value');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('pressure.top-users', async () => {
  const result = await requestJson(session, 'GET', '/pressure/top-users', { query: { limit: 5 } });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'pressure top users data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.userCode === 'string', 'pressure top user missing userCode');
    assert(typeof first.avgPressure === 'number', 'pressure top user missing avgPressure');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('pressure.department-stats', async () => {
  const result = await requestJson(session, 'GET', '/pressure/department-stats');
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'pressure department stats data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.deptName === 'string', 'pressure department stats row missing deptName');
    assert(typeof first.abnormalCount === 'number', 'pressure department stats row missing abnormalCount');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('pressure.realtime', async () => {
  const result = await requestJson(session, 'GET', '/pressure/realtime', { query: { limit: 20 } });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'pressure realtime data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.userCode === 'string', 'pressure realtime row missing userCode');
    assert(typeof first.pressure === 'number' || first.pressure === null, 'pressure realtime row missing pressure');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('pressure.hourly', async () => {
  const result = await requestJson(session, 'GET', '/pressure/hourly', { query: { date: TODAY_KEY } });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'pressure hourly data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.hour === 'number', 'pressure hourly row missing hour');
    assert(typeof first.avgPressure === 'number', 'pressure hourly row missing avgPressure');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('blood-pressure.overview', async () => {
  const result = await requestJson(session, 'GET', '/blood-pressure/overview');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'blood pressure overview data is not object');
  assert(typeof result.payload.data.avgSystolic === 'number', 'blood pressure overview missing avgSystolic');
  assert(typeof result.payload.data.avgDiastolic === 'number', 'blood pressure overview missing avgDiastolic');
});

await runCheck('blood-pressure.trend', async () => {
  const result = await requestJson(session, 'GET', '/blood-pressure/trend', { query: { days: 7 } });
  assertResultOk(result);
  assert(isObject(result.payload.data), 'blood pressure trend data is not object');
  assert(Array.isArray(result.payload.data.dates), 'blood pressure trend missing dates');
  assert(Array.isArray(result.payload.data.systolicValues), 'blood pressure trend missing systolicValues');
  assert(Array.isArray(result.payload.data.diastolicValues), 'blood pressure trend missing diastolicValues');
});

await runCheck('blood-pressure.distribution', async () => {
  const result = await requestJson(session, 'GET', '/blood-pressure/distribution');
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'blood pressure distribution data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.name === 'string', 'blood pressure distribution row missing name');
    assert(typeof first.value === 'number', 'blood pressure distribution row missing value');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('blood-pressure.top-users', async () => {
  const result = await requestJson(session, 'GET', '/blood-pressure/top-users', { query: { limit: 5 } });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'blood pressure top users data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.userCode === 'string', 'blood pressure top user missing userCode');
    assert(typeof first.avgSystolic === 'number', 'blood pressure top user missing avgSystolic');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('blood-pressure.department-stats', async () => {
  const result = await requestJson(session, 'GET', '/blood-pressure/department-stats');
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'blood pressure department stats data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.deptName === 'string', 'blood pressure department stats row missing deptName');
    assert(typeof first.avgDiastolic === 'number', 'blood pressure department stats row missing avgDiastolic');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('blood-pressure.realtime', async () => {
  const result = await requestJson(session, 'GET', '/blood-pressure/realtime', { query: { limit: 20 } });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'blood pressure realtime data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.userCode === 'string', 'blood pressure realtime row missing userCode');
    assert(typeof first.systolic === 'number' || first.systolic === null, 'blood pressure realtime row missing systolic');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('blood-pressure.hourly', async () => {
  const result = await requestJson(session, 'GET', '/blood-pressure/hourly', { query: { date: TODAY_KEY } });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'blood pressure hourly data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.hour === 'number', 'blood pressure hourly row missing hour');
    assert(typeof first.avgSystolic === 'number', 'blood pressure hourly row missing avgSystolic');
    assert(typeof first.avgDiastolic === 'number', 'blood pressure hourly row missing avgDiastolic');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('sleep.trend', async () => {
  const result = await requestJson(session, 'GET', '/sleep/trend', { query: { days: 7 } });
  assertResultOk(result);
  assert(isObject(result.payload.data), 'sleep trend data is not object');
  assert(Array.isArray(result.payload.data.dates), 'sleep trend missing dates');
  assert(Array.isArray(result.payload.data.avgData), 'sleep trend missing avgData');
});

await runCheck('sleep.quality-distribution', async () => {
  const result = await requestJson(session, 'GET', '/sleep/quality-distribution');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'sleep quality distribution data is not object');
  assert(typeof result.payload.data.excellent === 'number', 'sleep quality distribution missing excellent');
  assert(typeof result.payload.data.poor === 'number', 'sleep quality distribution missing poor');
});

await runCheck('sleep.page-data', async () => {
  const result = await requestJson(session, 'GET', '/sleep/page-data');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'sleep page data is not object');
  assert(isObject(result.payload.data.overview), 'sleep page data missing overview');
  assert(Array.isArray(result.payload.data.durationLegend), 'sleep page data missing durationLegend');
  assert(Array.isArray(result.payload.data.detailList), 'sleep page data missing detailList');
  assert(typeof result.payload.data.durationTotal === 'number', 'sleep page data missing durationTotal');
});

await runCheck('statistics.dept-summary', async () => {
  const result = await requestJson(session, 'GET', '/statistics/dept-summary');
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'dept summary data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.deptName === 'string', 'dept summary missing deptName');
    assert(typeof first.employeeCount === 'number', 'dept summary missing employeeCount');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('statistics.monthly-summary', async () => {
  const result = await requestJson(session, 'GET', '/statistics/monthly-summary', { query: { month: MONTH_KEY } });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'monthly summary data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.empCode === 'string', 'monthly summary missing empCode');
    assert(typeof first.recordCount === 'number', 'monthly summary missing recordCount');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('statistics.daily-counts', async () => {
  const result = await requestJson(session, 'GET', '/statistics/daily-counts', { query: { month: MONTH_KEY } });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'daily counts data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.day === 'number', 'daily counts missing day');
    assert(typeof first.count === 'number', 'daily counts missing count');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('statistics.warning-types', async () => {
  const result = await requestJson(session, 'GET', '/statistics/warning-types', { query: { month: MONTH_KEY } });
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'warning types data is not array');
  const first = result.payload.data[0];
  if (first) {
    assert(typeof first.name === 'string', 'warning types missing name');
    assert(typeof first.value === 'number', 'warning types missing value');
  }
  return `${result.payload.data.length} rows`;
});

await runCheck('trend-warning.predict', async () => {
  const result = await requestJson(session, 'GET', '/trend-warning/predict');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'trend warning predict data is not object');
  assert(isObject(result.payload.data.summary), 'trend warning summary is not object');
  assert(Array.isArray(result.payload.data.list), 'trend warning list is not array');
  const first = result.payload.data.list[0];
  if (first) {
    assert(typeof first.empCode === 'string', 'trend warning item missing empCode');
    assert(Array.isArray(first.riskMetrics), 'trend warning item missing riskMetrics');
  }
});

let sampleEmpCode = '';

await runCheck('employee.list-detail', async () => {
  const result = await requestJson(session, 'GET', '/employee/list-detail');
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'employee list detail data is not array');
  const sample = result.payload.data.find((item) => item?.empCode || item?.userCode) || null;
  sampleEmpCode = sample?.empCode || sample?.userCode || '';
  if (!sampleEmpCode) {
    summary.warnings.push('employee.list-detail returned no sample empCode; portrait smoke was skipped');
    return 'no sample employee';
  }
  return sampleEmpCode;
});

await runCheck('employee.stats', async () => {
  const result = await requestJson(session, 'GET', '/employee/stats');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'employee stats data is not object');
});

await runCheck('health-portrait.sample', async () => {
  if (!sampleEmpCode) return 'skipped';
  const result = await requestJson(session, 'GET', `/health-portrait/${encodeURIComponent(sampleEmpCode)}`);
  assertResultOk(result);
  const data = result.payload.data;
  assert(isObject(data), 'health portrait data is not object');
  assert(typeof data.empCode === 'string', 'health portrait missing empCode');
  assert(isObject(data.vitals), 'health portrait missing vitals');
  assert(isObject(data.exercise), 'health portrait missing exercise');
  assert(isObject(data.trend), 'health portrait missing trend');
  assert(Array.isArray(data.trend.dates), 'health portrait trend missing dates');
  assert(Array.isArray(data.warnings), 'health portrait warnings is not array');
  assert(Array.isArray(data.hourlyHr), 'health portrait hourlyHr is not array');
  return sampleEmpCode;
});

await runCheck('health-record.page', async () => {
  const result = await requestJson(session, 'GET', '/api/health/record/page', {
    query: { current: 1, size: 10, userCode: sampleEmpCode || undefined }
  });
  assertResultOk(result);
  assert(isObject(result.payload.data), 'health record page data is not object');
  assert(Array.isArray(result.payload.data.records), 'health record page records is not array');
  return `${result.payload.data.records.length} rows`;
});

await runCheck('device.online', async () => {
  const result = await requestJson(session, 'GET', '/api/device/online');
  assertResultOk(result);
  assert(isObject(result.payload.data), 'device online data is not object');
  assert(Array.isArray(result.payload.data.devices), 'device online devices is not array');
  return `${result.payload.data.count || result.payload.data.devices.length} devices`;
});

await runCheck('alert-config.list', async () => {
  const result = await requestJson(session, 'GET', '/alert-config/list');
  assertResultOk(result);
  assert(Array.isArray(result.payload.data), 'alert config list data is not array');
  return `${result.payload.data.length} rows`;
});

summary.finishedAt = new Date().toISOString();

await fs.writeFile(REPORT_JSON, JSON.stringify(summary, null, 2), 'utf8');
await fs.writeFile(REPORT_MD, buildMarkdownReport(), 'utf8');

const failedCount = summary.checks.filter((item) => item.status === 'failed').length;

console.log(JSON.stringify({
  artifactDir: ARTIFACT_DIR,
  target: summary.target,
  reportFile: REPORT_MD,
  checkCount: summary.checks.length,
  failedCount,
  warningCount: summary.warnings.length
}, null, 2));

if (failedCount > 0) {
  process.exitCode = 1;
}
