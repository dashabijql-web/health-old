import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import {
  assertResultOk,
  pruneArtifacts,
  requestJson,
  resolveSession,
  truncate
} from '../shared/health-test-utils.mjs';

const DATA_SOURCE = process.env.API_DATA_SOURCE || 'old';
const ITERATIONS = Number.parseInt(process.env.PERF_API_ITERATIONS || '5', 10);
const WARMUP_ITERATIONS = Number.parseInt(process.env.PERF_API_WARMUP || '1', 10);
const ARTIFACT_RETENTION = Number.parseInt(process.env.PERF_ARTIFACT_RETENTION || '10', 10);
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-');
const ARTIFACT_DIR = path.resolve(process.cwd(), 'tests', 'performance', 'artifacts', RUN_ID);
const REPORT_JSON = path.join(ARTIFACT_DIR, 'api-latency.json');
const REPORT_MD = path.join(ARTIFACT_DIR, 'api-latency.md');

const THRESHOLDS = {
  p95Ms: Number.parseInt(process.env.PERF_API_P95_MS || '1200', 10),
  maxMs: Number.parseInt(process.env.PERF_API_MAX_MS || '2500', 10),
  failureRate: Number.parseFloat(process.env.PERF_API_FAILURE_RATE || '0')
};

const ENDPOINTS = [
  endpoint('dashboard.overview', 'GET', '/dashboard/overview', { p95Ms: 900, maxMs: 1800 }),
  endpoint('dashboard.body-indicators', 'GET', '/dashboard/body-indicators', { p95Ms: 1000, maxMs: 2200 }),
  endpoint('realtime.statistics', 'GET', '/realtime/statistics', { p95Ms: 900, maxMs: 1800 }),
  endpoint('realtime.online-users', 'GET', '/realtime/online-users', { query: { page: 1, size: 20 }, p95Ms: 1200, maxMs: 2500 }),
  endpoint('risk-warning.list', 'GET', '/risk-warning/list', { query: { page: 1, size: 10 }, p95Ms: 1200, maxMs: 2500 }),
  endpoint('employee.list-detail', 'GET', '/employee/list-detail', { p95Ms: 1500, maxMs: 3000 }),
  endpoint('employee.stats', 'GET', '/employee/stats', { p95Ms: 900, maxMs: 1800 }),
  endpoint('health-record.page', 'GET', '/api/health/record/page', { query: { current: 1, size: 10 }, p95Ms: DATA_SOURCE === 'old' ? 6500 : 1500, maxMs: DATA_SOURCE === 'old' ? 8000 : 3000 }),
  endpoint('heart-rate.overview', 'GET', '/heart-rate/overview', { p95Ms: 1200, maxMs: 2500 }),
  endpoint('blood-oxygen.overview', 'GET', '/blood-oxygen/overview', { p95Ms: 1200, maxMs: 2500 }),
  endpoint('pressure.overview', 'GET', '/pressure/overview', { p95Ms: 1200, maxMs: 2500 }),
  endpoint('blood-pressure.overview', 'GET', '/blood-pressure/overview', { p95Ms: 1200, maxMs: 2500 })
];

const summary = {
  runId: RUN_ID,
  dataSource: DATA_SOURCE,
  iterations: ITERATIONS,
  warmupIterations: WARMUP_ITERATIONS,
  thresholds: THRESHOLDS,
  startedAt: new Date().toISOString(),
  target: null,
  endpoints: []
};

await fs.mkdir(ARTIFACT_DIR, { recursive: true });
await pruneArtifacts(path.dirname(ARTIFACT_DIR), ARTIFACT_RETENTION);

function endpoint(name, method, routePath, options = {}) {
  return {
    name,
    method,
    routePath,
    query: options.query || {},
    thresholds: {
      p95Ms: options.p95Ms || THRESHOLDS.p95Ms,
      maxMs: options.maxMs || THRESHOLDS.maxMs,
      failureRate: options.failureRate ?? THRESHOLDS.failureRate
    }
  };
}

function percentile(values, p) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.ceil((p / 100) * sorted.length) - 1;
  return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
}

function median(values) {
  return percentile(values, 50);
}

async function measure(session, item, phase, iteration) {
  const startedAt = performance.now();
  try {
    const result = await requestJson(session, item.method, item.routePath, {
      query: item.query,
      dataSource: DATA_SOURCE
    });
    const durationMs = Math.round(performance.now() - startedAt);
    assertResultOk(result);
    return { phase, iteration, ok: true, durationMs, status: result.status, note: '' };
  } catch (error) {
    return {
      phase,
      iteration,
      ok: false,
      durationMs: Math.round(performance.now() - startedAt),
      status: 0,
      note: truncate(String(error), 240)
    };
  }
}

async function runEndpoint(session, item) {
  const samples = [];
  for (let i = 1; i <= WARMUP_ITERATIONS; i += 1) {
    await measure(session, item, 'warmup', i);
  }
  for (let i = 1; i <= ITERATIONS; i += 1) {
    samples.push(await measure(session, item, 'measure', i));
  }

  const okSamples = samples.filter((sample) => sample.ok);
  const durations = okSamples.map((sample) => sample.durationMs);
  const failures = samples.filter((sample) => !sample.ok);
  const failureRate = samples.length ? failures.length / samples.length : 1;
  const stats = {
    count: samples.length,
    okCount: okSamples.length,
    failureCount: failures.length,
    failureRate,
    minMs: durations.length ? Math.min(...durations) : 0,
    p50Ms: median(durations),
    p95Ms: percentile(durations, 95),
    maxMs: durations.length ? Math.max(...durations) : 0
  };
  const violations = [];
  if (failureRate > item.thresholds.failureRate) violations.push(`failureRate ${failureRate.toFixed(2)} > ${item.thresholds.failureRate}`);
  if (stats.p95Ms > item.thresholds.p95Ms) violations.push(`p95 ${stats.p95Ms}ms > ${item.thresholds.p95Ms}ms`);
  if (stats.maxMs > item.thresholds.maxMs) violations.push(`max ${stats.maxMs}ms > ${item.thresholds.maxMs}ms`);

  return {
    name: item.name,
    method: item.method,
    routePath: item.routePath,
    query: item.query,
    thresholds: item.thresholds,
    status: violations.length ? 'failed' : 'passed',
    stats,
    violations,
    samples
  };
}

function buildPerformanceTriage(endpoints) {
  const ranked = endpoints
    .map((item) => {
      const p95Usage = item.thresholds.p95Ms > 0 ? item.stats.p95Ms / item.thresholds.p95Ms : 0;
      const maxUsage = item.thresholds.maxMs > 0 ? item.stats.maxMs / item.thresholds.maxMs : 0;
      const utilization = Math.max(p95Usage, maxUsage);
      return {
        name: item.name,
        status: item.status,
        p95Ms: item.stats.p95Ms,
        maxMs: item.stats.maxMs,
        p95ThresholdMs: item.thresholds.p95Ms,
        maxThresholdMs: item.thresholds.maxMs,
        utilization,
        headroomPercent: Math.max(0, Math.round((1 - utilization) * 100))
      };
    })
    .sort((a, b) => b.utilization - a.utilization || b.p95Ms - a.p95Ms);

  return {
    slowestEndpoints: ranked.slice(0, 5),
    outlierCandidates: ranked.filter((item) => item.status === 'failed' || item.utilization >= 0.8)
  };
}

function buildMarkdownReport() {
  const failed = summary.endpoints.filter((item) => item.status === 'failed').length;
  return [
    '# HealthShow API performance report',
    '',
    `- run_id: ${summary.runId}`,
    `- data_source: ${summary.dataSource}`,
    `- target: ${summary.target?.label || '-'} (${summary.target?.origin || '-'})`,
    `- iterations: ${summary.iterations}`,
    `- warmup_iterations: ${summary.warmupIterations}`,
    `- started_at: ${summary.startedAt}`,
    `- finished_at: ${summary.finishedAt}`,
    `- failed_endpoints: ${failed}`,
    '',
    '| endpoint | status | p50 | p95 | max | failures | threshold |',
    '| --- | --- | ---: | ---: | ---: | ---: | --- |',
    ...summary.endpoints.map((item) => `| ${item.name} | ${item.status} | ${item.stats.p50Ms}ms | ${item.stats.p95Ms}ms | ${item.stats.maxMs}ms | ${item.stats.failureCount}/${item.stats.count} | p95<=${item.thresholds.p95Ms}ms max<=${item.thresholds.maxMs}ms |`),
    '',
    '## Slowest endpoints / threshold utilization',
    '',
    '| endpoint | status | p95 | max | utilization | headroom |',
    '| --- | --- | ---: | ---: | ---: | ---: |',
    ...(summary.performanceTriage?.slowestEndpoints || []).map((item) => `| ${item.name} | ${item.status} | ${item.p95Ms}ms/${item.p95ThresholdMs}ms | ${item.maxMs}ms/${item.maxThresholdMs}ms | ${Math.round(item.utilization * 100)}% | ${item.headroomPercent}% |`),
    '',
    '## Outlier candidates',
    '',
    ...(summary.performanceTriage?.outlierCandidates?.length
      ? summary.performanceTriage.outlierCandidates.map((item) => `- ${item.name}: utilization=${Math.round(item.utilization * 100)}%, p95=${item.p95Ms}ms/${item.p95ThresholdMs}ms, max=${item.maxMs}ms/${item.maxThresholdMs}ms`)
      : ['- none; no endpoint reached 80% of its p95/max threshold.']),
    '',
    '## Violations',
    '',
    ...summary.endpoints.flatMap((item) => item.violations.length ? item.violations.map((violation) => `- ${item.name}: ${violation}`) : [])
  ].join('\n');
}

const session = await resolveSession();
summary.target = session.target;

for (const item of ENDPOINTS) {
  summary.endpoints.push(await runEndpoint(session, item));
}
summary.performanceTriage = buildPerformanceTriage(summary.endpoints);

summary.finishedAt = new Date().toISOString();
await fs.writeFile(REPORT_JSON, JSON.stringify(summary, null, 2), 'utf8');
await fs.writeFile(REPORT_MD, buildMarkdownReport(), 'utf8');

const failedCount = summary.endpoints.filter((item) => item.status === 'failed').length;
console.log(JSON.stringify({
  artifactDir: ARTIFACT_DIR,
  reportFile: REPORT_MD,
  endpointCount: summary.endpoints.length,
  failedCount
}, null, 2));

if (failedCount > 0) process.exitCode = 1;
