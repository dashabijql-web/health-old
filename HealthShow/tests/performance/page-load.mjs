import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { chromium } from 'playwright';
import {
  LOGIN_CREDENTIALS,
  pruneArtifacts,
  resolveFrontendBaseUrl,
  truncate
} from '../shared/health-test-utils.mjs';

const BASE_URL = await resolveFrontendBaseUrl();
const DATA_SOURCE = process.env.API_DATA_SOURCE || 'old';
const ITERATIONS = Number.parseInt(process.env.PERF_PAGE_ITERATIONS || '3', 10);
const ARTIFACT_RETENTION = Number.parseInt(process.env.PERF_ARTIFACT_RETENTION || '10', 10);
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-');
const ARTIFACT_DIR = path.resolve(process.cwd(), 'tests', 'performance', 'artifacts', RUN_ID);
const REPORT_JSON = path.join(ARTIFACT_DIR, 'page-load.json');
const REPORT_MD = path.join(ARTIFACT_DIR, 'page-load.md');

const DEFAULT_THRESHOLDS = {
  readyMs: Number.parseInt(process.env.PERF_PAGE_READY_MS || '3500', 10),
  domContentLoadedMs: Number.parseInt(process.env.PERF_PAGE_DCL_MS || '2500', 10),
  apiP95Ms: Number.parseInt(process.env.PERF_PAGE_API_P95_MS || '1500', 10)
};

const ROUTES = [
  route('dashboard', '/#/health-monitor/dashboard', '.dm-root', { readyMs: 3500 }),
  route('real-time', '/#/health-monitor/real-time', '.rt-page,.page-container,.app-main', { readyMs: 3500 }),
  route('risk-warning', '/#/health-monitor/risk-warning', '.rw-root', { readyMs: 3500 }),
  route('alert-notifications', '/#/alert-management/notifications', '.notif-page', { readyMs: 3500 }),
  route('employee-archive', '/#/health-monitor/employee-archive', '.page-container,.ea-root,.app-main', { readyMs: 4000 }),
  // Report center cold-loads multiple charts/tables and can show small first-visit jitter.
  route('report-center', '/#/health-monitor/report-center', '.rc-page', { readyMs: 5000 })
];

const summary = {
  runId: RUN_ID,
  baseUrl: BASE_URL,
  dataSource: DATA_SOURCE,
  iterations: ITERATIONS,
  thresholds: DEFAULT_THRESHOLDS,
  startedAt: new Date().toISOString(),
  routes: []
};

await fs.mkdir(ARTIFACT_DIR, { recursive: true });
await pruneArtifacts(path.dirname(ARTIFACT_DIR), ARTIFACT_RETENTION);

function route(name, hashPath, readySelector, thresholds = {}) {
  return {
    name,
    url: `${BASE_URL}${hashPath}`,
    readySelector,
    thresholds: {
      readyMs: thresholds.readyMs || DEFAULT_THRESHOLDS.readyMs,
      domContentLoadedMs: thresholds.domContentLoadedMs || DEFAULT_THRESHOLDS.domContentLoadedMs,
      apiP95Ms: thresholds.apiP95Ms || DEFAULT_THRESHOLDS.apiP95Ms
    }
  };
}

function percentile(values, p) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.ceil((p / 100) * sorted.length) - 1;
  return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
}

async function primeDataSource(context, page) {
  await context.addCookies([{
    name: 'Health-Data-Source',
    value: DATA_SOURCE,
    url: BASE_URL,
    sameSite: 'Lax'
  }]);
  await page.setExtraHTTPHeaders({
    'X-Health-Data-Source': DATA_SOURCE
  });
}

async function login(page) {
  await page.goto(`${BASE_URL}/#/login`, { waitUntil: 'domcontentloaded' });
  const username = page.locator('input').nth(0);
  const password = page.locator('input[type="password"]').first();
  await username.fill(LOGIN_CREDENTIALS.username);
  await password.fill(LOGIN_CREDENTIALS.password);
  await page.getByRole('button', { name: /登录|登陆|Login/i }).click();
  await page.waitForURL((url) => !url.href.includes('/login'), { timeout: 15000 }).catch(() => {});
}

async function measureRoute(page, item, iteration) {
  const apiDurations = [];
  const failedRequests = [];
  const responseListener = async (response) => {
    const request = response.request();
    const url = response.url();
    if (!url.includes('/dev-api/') && !url.includes('/health/')) return;
    const timing = request.timing();
    const start = timing.startTime || 0;
    const end = timing.responseEnd || timing.responseHeadersEnd || 0;
    if (end > start) apiDurations.push(Math.round(end - start));
    if (response.status() >= 400) failedRequests.push(`${response.status()} ${truncate(url, 120)}`);
  };
  page.on('response', responseListener);

  const startedAt = performance.now();
  let status = 'passed';
  const notes = [];
  let domContentLoadedMs = 0;
  let readyMs = 0;

  try {
    await page.goto(item.url, { waitUntil: 'domcontentloaded', timeout: 30000 });
    domContentLoadedMs = Math.round(performance.now() - startedAt);
    await Promise.race(
      item.readySelector.split(',').map((selector) => page.locator(selector.trim()).first().waitFor({ state: 'visible', timeout: 25000 }))
    );
    await page.locator('.el-loading-mask').last().waitFor({ state: 'hidden', timeout: 3000 }).catch(() => {});
    readyMs = Math.round(performance.now() - startedAt);
    await page.waitForTimeout(500);
  } catch (error) {
    status = 'failed';
    notes.push(truncate(String(error), 260));
    readyMs = Math.round(performance.now() - startedAt);
  } finally {
    page.off('response', responseListener);
  }

  const apiP95Ms = percentile(apiDurations, 95);
  if (domContentLoadedMs > item.thresholds.domContentLoadedMs) {
    status = 'failed';
    notes.push(`domContentLoaded ${domContentLoadedMs}ms > ${item.thresholds.domContentLoadedMs}ms`);
  }
  if (readyMs > item.thresholds.readyMs) {
    status = 'failed';
    notes.push(`ready ${readyMs}ms > ${item.thresholds.readyMs}ms`);
  }
  if (apiP95Ms > item.thresholds.apiP95Ms) {
    status = 'failed';
    notes.push(`api p95 ${apiP95Ms}ms > ${item.thresholds.apiP95Ms}ms`);
  }
  if (failedRequests.length > 0) {
    status = 'failed';
    notes.push(`failed requests: ${failedRequests.slice(0, 3).join(' | ')}`);
  }

  return {
    iteration,
    status,
    domContentLoadedMs,
    readyMs,
    apiCount: apiDurations.length,
    apiP95Ms,
    apiMaxMs: apiDurations.length ? Math.max(...apiDurations) : 0,
    notes
  };
}

async function runRoute(browser, item) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  const page = await context.newPage();
  await primeDataSource(context, page);
  await login(page);
  const samples = [];
  for (let i = 1; i <= ITERATIONS; i += 1) {
    samples.push(await measureRoute(page, item, i));
  }
  await context.close();

  const readyValues = samples.filter((sample) => sample.status === 'passed').map((sample) => sample.readyMs);
  const dclValues = samples.filter((sample) => sample.status === 'passed').map((sample) => sample.domContentLoadedMs);
  const apiP95Values = samples.filter((sample) => sample.status === 'passed').map((sample) => sample.apiP95Ms);
  const stats = {
    count: samples.length,
    failedCount: samples.filter((sample) => sample.status === 'failed').length,
    readyP50Ms: percentile(readyValues, 50),
    readyP95Ms: percentile(readyValues, 95),
    domContentLoadedP95Ms: percentile(dclValues, 95),
    apiP95Ms: percentile(apiP95Values, 95)
  };

  return {
    name: item.name,
    url: item.url,
    readySelector: item.readySelector,
    thresholds: item.thresholds,
    status: stats.failedCount > 0 ? 'failed' : 'passed',
    stats,
    samples
  };
}

function buildMarkdownReport() {
  const failed = summary.routes.filter((item) => item.status === 'failed').length;
  return [
    '# HealthShow page-load performance report',
    '',
    `- run_id: ${summary.runId}`,
    `- base_url: ${summary.baseUrl}`,
    `- data_source: ${summary.dataSource}`,
    `- iterations: ${summary.iterations}`,
    `- started_at: ${summary.startedAt}`,
    `- finished_at: ${summary.finishedAt}`,
    `- failed_routes: ${failed}`,
    '',
    '| route | status | ready p50 | ready p95 | DCL p95 | API p95 | failures |',
    '| --- | --- | ---: | ---: | ---: | ---: | ---: |',
    ...summary.routes.map((item) => `| ${item.name} | ${item.status} | ${item.stats.readyP50Ms}ms | ${item.stats.readyP95Ms}ms | ${item.stats.domContentLoadedP95Ms}ms | ${item.stats.apiP95Ms}ms | ${item.stats.failedCount}/${item.stats.count} |`)
  ].join('\n');
}

const browser = await chromium.launch({ headless: true });
try {
  for (const item of ROUTES) {
    summary.routes.push(await runRoute(browser, item));
  }
} finally {
  await browser.close();
}

summary.finishedAt = new Date().toISOString();
await fs.writeFile(REPORT_JSON, JSON.stringify(summary, null, 2), 'utf8');
await fs.writeFile(REPORT_MD, buildMarkdownReport(), 'utf8');

const failedCount = summary.routes.filter((item) => item.status === 'failed').length;
console.log(JSON.stringify({
  artifactDir: ARTIFACT_DIR,
  reportFile: REPORT_MD,
  routeCount: summary.routes.length,
  failedCount
}, null, 2));

if (failedCount > 0) process.exitCode = 1;
