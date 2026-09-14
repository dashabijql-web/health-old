import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { chromium, devices } from 'playwright';
import {
  LOGIN_CREDENTIALS,
  pruneArtifacts,
  resolveFrontendBaseUrl,
  truncate
} from '../shared/health-test-utils.mjs';

const BASE_URL = await resolveFrontendBaseUrl();
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-');
const ARTIFACT_DIR = path.resolve(process.cwd(), 'tests', 'e2e', 'artifacts', RUN_ID);
const STORAGE_STATE_PATH = path.join(ARTIFACT_DIR, 'storage-state.json');
const SCREENSHOT_MODE = process.env.E2E_SCREENSHOTS || 'failures';
const ARTIFACT_RETENTION = Number.parseInt(process.env.E2E_ARTIFACT_RETENTION || '5', 10);
const ROUTE_FILTER = new Set(
  (process.env.E2E_ROUTE_FILTER || '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)
);
const SKIP_NAV_AUDITS = process.env.E2E_SKIP_NAV_AUDITS === '1' || ROUTE_FILTER.size > 0;
const SKIP_EMPLOYEE_FLOW = process.env.E2E_SKIP_EMPLOYEE_FLOW === '1' || ROUTE_FILTER.size > 0;
const SKIP_MOBILE = process.env.E2E_SKIP_MOBILE === '1' || ROUTE_FILTER.size > 0;

const DESKTOP_ROUTES = [
  { slug: 'safety-command', path: '/safety-command/index' },
  { slug: 'dashboard', path: '/health-monitor/dashboard' },
  { slug: 'workbench', path: '/health-monitor/workbench' },
  { slug: 'real-time', path: '/health-monitor/real-time' },
  { slug: 'heart-rate', path: '/health-monitor/heart-rate' },
  { slug: 'pressure', path: '/health-monitor/pressure' },
  { slug: 'blood-pressure', path: '/health-monitor/blood-pressure' },
  { slug: 'blood-oxygen', path: '/health-monitor/blood-oxygen' },
  { slug: 'sleep', path: '/health-monitor/sleep' },
  { slug: 'risk-warning', path: '/health-monitor/risk-warning' },
  { slug: 'employee-archive', path: '/health-monitor/employee-archive' },
  { slug: 'mine-entry', path: '/health-monitor/mine-entry' },
  { slug: 'report-center', path: '/health-monitor/report-center' },
  { slug: 'trend-warning', path: '/health-monitor/trend-warning' },
  { slug: 'alert-notifications', path: '/alert-management/notifications' },
  { slug: 'alert-sos', path: '/alert-management/sos' },
  { slug: 'alert-config', path: '/alert-management/config' },
  { slug: 'alert-records', path: '/alert-management/records' },
  { slug: 'device-list', path: '/admin/device-list' },
  { slug: 'user-list', path: '/admin/user-list' },
  { slug: 'role-management', path: '/admin/role' },
  { slug: 'department-management', path: '/admin/department' },
  { slug: 'job-type-management', path: '/admin/job-type' },
  { slug: 'ai-chat', path: '/ai-chat/index' }
];

const MOBILE_ROUTES = [
  { slug: 'mobile-safety-command', path: '/safety-command/index' },
  { slug: 'mobile-dashboard', path: '/health-monitor/dashboard' },
  { slug: 'mobile-workbench', path: '/health-monitor/workbench' },
  { slug: 'mobile-real-time', path: '/health-monitor/real-time' }
];

const GROUP_ENTRY_ROUTES = [
  { slug: 'command-center-entry', path: '/command-center', expectedHash: '/health-monitor/dashboard' },
  { slug: 'monitoring-center-entry', path: '/monitoring-center', expectedHash: '/health-monitor/real-time' },
  { slug: 'warning-center-entry', path: '/warning-center', expectedHash: '/alert-management/notifications' },
  { slug: 'people-center-entry', path: '/people-center', expectedHash: '/health-monitor/employee-archive' },
  { slug: 'report-ai-entry', path: '/report-ai', expectedHash: '/health-monitor/report-center' }
];

const DESKTOP_NAV_GROUPS = ['指挥中心', '监测中心', '预警中心', '人员中心', '报告与AI', '系统管理'];
const MOBILE_NAV_LABELS = ['指挥', '监测', '准入', '预警', '人员'];

const ROUTE_EXPECTATIONS = {
  dashboard: ['.dm-root'],
  'risk-warning': ['.rw-root'],
  'alert-notifications': ['.notif-page'],
  'alert-records': ['.page-container'],
  'alert-config': ['.page-container'],
  'alert-sos': ['.sos-page'],
  'report-center': ['.rc-page']
};

DESKTOP_ROUTES.push(...GROUP_ENTRY_ROUTES);

function selectRoutes(routes) {
  if (ROUTE_FILTER.size === 0) return routes;
  return routes.filter((route) => ROUTE_FILTER.has(route.slug));
}

const summary = {
  runId: RUN_ID,
  baseUrl: BASE_URL,
  startedAt: new Date().toISOString(),
  routeResults: [],
  issues: [],
  navigationAudits: []
};

let currentRoute = 'bootstrap';
let currentDevice = 'desktop';

await fs.mkdir(ARTIFACT_DIR, { recursive: true });
await pruneArtifacts(path.dirname(ARTIFACT_DIR), ARTIFACT_RETENTION);

function setScope(device, route) {
  currentDevice = device;
  currentRoute = route;
}

function pushIssue(issue) {
  summary.issues.push({
    ts: new Date().toISOString(),
    device: currentDevice,
    route: currentRoute,
    ...issue
  });
}

function attachObservers(page) {
  page.on('pageerror', (error) => {
    pushIssue({
      severity: 'error',
      type: 'pageerror',
      message: truncate(String(error))
    });
  });

  page.on('console', (msg) => {
    if (!['error', 'warning'].includes(msg.type())) return;
    pushIssue({
      severity: msg.type() === 'error' ? 'error' : 'warn',
      type: `console.${msg.type()}`,
      message: truncate(msg.text())
    });
  });

  page.on('requestfailed', (request) => {
    const failure = request.failure();
    const text = failure?.errorText || '';
    if (/ERR_ABORTED|canceled/i.test(text)) return;
    pushIssue({
      severity: 'error',
      type: 'requestfailed',
      url: truncate(request.url()),
      method: request.method(),
      message: truncate(text || 'request failed')
    });
  });

  page.on('response', (response) => {
    const status = response.status();
    if (status < 400) return;
    pushIssue({
      severity: status >= 500 ? 'error' : 'warn',
      type: 'http',
      url: truncate(response.url()),
      status,
      message: `HTTP ${status}`
    });
  });
}

async function waitForRouteShell(page, timeout = 20000) {
  await page.waitForLoadState('domcontentloaded');
  await Promise.race([
    page.locator('.app-main').first().waitFor({ state: 'visible', timeout }),
    page.locator('.login-container').first().waitFor({ state: 'visible', timeout }),
    page.locator('.wscn-http404-container').first().waitFor({ state: 'visible', timeout })
  ]).catch(() => {});

  try {
    await page.locator('.el-loading-mask').last().waitFor({ state: 'hidden', timeout: 4000 });
  } catch {
    // Some pages never show an overlay; some keep polling in the background.
  }

  await page.waitForTimeout(3200);
}

async function collectDomStats(page) {
  return page.evaluate(() => ({
    bodyTextLength: document.body?.innerText?.trim().length || 0,
    tableRows: document.querySelectorAll('.el-table__body tbody tr').length,
    canvasCount: document.querySelectorAll('canvas').length,
    svgCount: document.querySelectorAll('svg').length
  }));
}

async function saveScreenshot(page, device, slug) {
  const fileName = `${device}-${slug}.jpeg`;
  const fullPath = path.join(ARTIFACT_DIR, fileName);
  await page.screenshot({ path: fullPath, type: 'jpeg', quality: 72, fullPage: false });
  return path.relative(process.cwd(), fullPath);
}

function shouldCaptureScreenshot(status = 'passed') {
  if (SCREENSHOT_MODE === 'all') return true;
  if (SCREENSHOT_MODE === 'none') return false;
  return status === 'failed';
}

async function maybeSaveScreenshot(page, device, slug, status = 'passed') {
  if (!shouldCaptureScreenshot(status)) return '';
  return saveScreenshot(page, device, slug);
}

async function verifyExpectedSelectors(page, route, result) {
  const selectors = ROUTE_EXPECTATIONS[route.slug] || [];
  for (const selector of selectors) {
    const found = await page.locator(selector).count();
    if (found === 0) {
      result.status = result.status === 'failed' ? 'failed' : 'passed_with_issues';
      result.notes.push(`missing_selector:${selector}`);
      pushIssue({
        severity: 'error',
        type: 'selector.missing',
        message: `expected selector not found: ${selector}`
      });
    }
  }
}

async function auditDesktopNavigation(page) {
  setScope('desktop', 'desktop-navigation');
  const labels = await page.evaluate(() => {
    return Array.from(document.querySelectorAll('.sidebar-container .menu-title'))
      .map((node) => node.textContent?.trim())
      .filter(Boolean);
  });

  const missingGroups = DESKTOP_NAV_GROUPS.filter((label) => !labels.includes(label));
  if (missingGroups.length > 0) {
    pushIssue({
      severity: 'error',
      type: 'navigation.desktop.groups',
      message: `missing desktop groups: ${missingGroups.join(', ')}`
    });
  }

  summary.navigationAudits.push({
    device: 'desktop',
    type: 'sidebar-groups',
    labels,
    missing: missingGroups
  });
}

async function auditMobileNavigation(page) {
  setScope('mobile', 'mobile-navigation');
  const labels = await page.evaluate(() => {
    return Array.from(document.querySelectorAll('.mobile-bottom-nav .mbn-label'))
      .map((node) => node.textContent?.trim())
      .filter(Boolean);
  });

  const missingLabels = MOBILE_NAV_LABELS.filter((label) => !labels.includes(label));
  if (missingLabels.length > 0) {
    pushIssue({
      severity: 'error',
      type: 'navigation.mobile.labels',
      message: `missing mobile labels: ${missingLabels.join(', ')}`
    });
  }

  summary.navigationAudits.push({
    device: 'mobile',
    type: 'bottom-nav',
    labels,
    missing: missingLabels
  });
}

async function login(page) {
  setScope('desktop', 'login');
  await page.goto(`${BASE_URL}/#/login`, { waitUntil: 'domcontentloaded' });
  await page.locator('input[name="username"]').fill(LOGIN_CREDENTIALS.username);
  await page.locator('input[name="password"]').fill(LOGIN_CREDENTIALS.password);
  await page.locator('.login-form button').click();

  try {
    await page.waitForFunction(() => !window.location.hash.includes('/login'), null, { timeout: 15000 });
  } catch {
    pushIssue({
      severity: 'warn',
      type: 'login.ui_timeout',
      message: 'UI login did not leave /login in 15s, fallback to API bootstrap'
    });
    await loginViaApi(page);
  }

  await waitForRouteShell(page);
  await maybeSaveScreenshot(page, 'desktop', 'post-login');
  await page.context().storageState({ path: STORAGE_STATE_PATH });
}

async function loginViaApi(page) {
  const response = await page.context().request.post(`${BASE_URL}/dev-api/auth/login`, {
    data: LOGIN_CREDENTIALS
  });

  if (!response.ok()) {
    throw new Error(`API bootstrap login failed with HTTP ${response.status()}`);
  }

  const payload = await response.json();
  if (payload?.code !== 200 || !payload?.data?.token) {
    throw new Error(`API bootstrap login rejected: ${payload?.message || 'unknown error'}`);
  }

  await page.context().addCookies([
    {
      name: 'User-Token',
      value: payload.data.token,
      url: BASE_URL
    }
  ]);

  await page.goto(`${BASE_URL}/#/health-monitor/dashboard`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => !window.location.hash.includes('/login'), null, { timeout: 15000 });
}

async function visitRoute(page, device, route) {
  setScope(device, route.slug);
  const issueStartIndex = summary.issues.length;
  const startedAt = Date.now();
  const url = `${BASE_URL}/#${route.path}`;
  const expectedHash = route.expectedHash || route.path;

  const result = {
    device,
    slug: route.slug,
    path: route.path,
    url,
    status: 'passed',
    durationMs: 0,
    finalHash: '',
    domStats: {},
    issueCount: 0,
    issueSeverity: 'none',
    notes: [],
    screenshot: ''
  };

  try {
    await page.goto(url, { waitUntil: 'domcontentloaded' });
    await waitForRouteShell(page);
    await page.evaluate(() => window.scrollTo(0, 0));
    result.finalHash = await page.evaluate(() => window.location.hash);

    if (result.finalHash.includes('/login')) {
      result.status = 'failed';
      result.notes.push('redirected_to_login');
    }

    if (result.finalHash.includes('/404')) {
      result.status = 'failed';
      result.notes.push('redirected_to_404');
    }

    if (await page.locator('.wscn-http404-container').count()) {
      result.status = 'failed';
      result.notes.push('404_view_visible');
    }

    if (!result.finalHash.includes(expectedHash) && result.status === 'passed') {
      result.notes.push('final_hash_differs');
    }

    result.domStats = await collectDomStats(page);
    if (
      result.domStats.bodyTextLength < 20 &&
      result.domStats.tableRows === 0 &&
      result.domStats.canvasCount === 0 &&
      result.domStats.svgCount === 0
    ) {
      result.notes.push('sparse_dom');
    }

    await verifyExpectedSelectors(page, route, result);
    result.screenshot = await maybeSaveScreenshot(page, device, route.slug, result.status);
  } catch (error) {
    result.status = 'failed';
    result.notes.push(`exception:${truncate(String(error), 160)}`);
    try {
      result.screenshot = await maybeSaveScreenshot(page, device, `${route.slug}-error`, result.status);
    } catch {
      // Ignore screenshot failures after a hard navigation error.
    }
  }

  const routeIssues = summary.issues.slice(issueStartIndex);
  result.issueCount = routeIssues.length;
  if (routeIssues.some((issue) => issue.severity === 'error')) {
    result.issueSeverity = 'error';
  } else if (routeIssues.some((issue) => issue.severity === 'warn')) {
    result.issueSeverity = 'warn';
  }

  if (result.status === 'passed' && result.issueSeverity === 'error') {
    result.status = 'passed_with_issues';
  } else if (result.status === 'passed' && result.issueSeverity === 'warn') {
    result.status = 'passed_with_warnings';
  }

  result.durationMs = Date.now() - startedAt;
  summary.routeResults.push(result);
}

async function auditEmployeeProfileFlow(page) {
  setScope('desktop', 'employee-profile-flow');
  const startedAt = Date.now();
  const result = {
    device: 'desktop',
    slug: 'employee-profile-flow',
    path: '/health-monitor/employee-archive -> /health-monitor/employee-profile -> /health-monitor/workbench',
    url: `${BASE_URL}/#/health-monitor/employee-archive`,
    status: 'passed',
    durationMs: 0,
    finalHash: '',
    domStats: {},
    issueCount: 0,
    issueSeverity: 'none',
    notes: [],
    screenshot: ''
  };
  const issueStartIndex = summary.issues.length;

  try {
    const base = new URL(BASE_URL);
    await page.context().addCookies([{
      name: 'Health-Data-Source',
      value: 'old',
      domain: base.hostname,
      path: '/'
    }]);
    await page.goto(`${BASE_URL}/#/health-monitor/employee-archive`, { waitUntil: 'domcontentloaded' });
    await waitForRouteShell(page);
    const cards = page.locator('.ea-card');
    await cards.first().waitFor({ state: 'visible', timeout: 10000 });

    const firstEmp = {
      name: (await cards.nth(0).locator('.ea-name').textContent())?.trim() || '',
      code: (await cards.nth(0).locator('.ef-val.code').textContent())?.trim() || ''
    };
    const secondEmp = await (async () => {
      const count = await cards.count();
      if (count < 2) return null;
      return {
        name: (await cards.nth(1).locator('.ea-name').textContent())?.trim() || '',
        code: (await cards.nth(1).locator('.ef-val.code').textContent())?.trim() || ''
      };
    })();

    await cards.nth(0).click();
    await page.waitForFunction(() => window.location.hash.includes('/health-monitor/employee-profile'), null, { timeout: 15000 });
    await page.locator('.ep-quickbar').first().waitFor({ state: 'visible', timeout: 10000 });
    await page.locator('.ep-ai-summary').first().waitFor({ state: 'visible', timeout: 10000 });

    if (secondEmp?.code && secondEmp.code !== firstEmp.code) {
      await page.evaluate((target) => {
        const params = new URLSearchParams({ empCode: target.code, empName: target.name || target.code });
        window.location.hash = `#/health-monitor/employee-profile?${params.toString()}`;
      }, secondEmp);
      await page.waitForFunction((expectedName) => {
        const text = document.querySelector('.ep-basic-name')?.textContent?.trim();
        return text === expectedName;
      }, secondEmp.name, { timeout: 15000 });
    }

    await page.getByRole('button', { name: '月度日历' }).click();
    await page.waitForFunction(() => window.location.hash.includes('/health-monitor/workbench'), null, { timeout: 15000 });
    await page.locator('.wb-profile-btn').first().waitFor({ state: 'visible', timeout: 10000 });
    await page.locator('.wb-profile-btn').first().click();
    await page.waitForFunction(() => window.location.hash.includes('/health-monitor/employee-profile'), null, { timeout: 15000 });

    result.finalHash = await page.evaluate(() => window.location.hash);
    result.domStats = await collectDomStats(page);
    result.screenshot = await maybeSaveScreenshot(page, 'desktop', result.slug, result.status);
  } catch (error) {
    result.status = 'failed';
    result.notes.push(`exception:${truncate(String(error), 160)}`);
    try {
      result.screenshot = await maybeSaveScreenshot(page, 'desktop', `${result.slug}-error`, result.status);
    } catch {}
  }

  const flowIssues = summary.issues.slice(issueStartIndex);
  result.issueCount = flowIssues.length;
  if (flowIssues.some((issue) => issue.severity === 'error')) {
    result.issueSeverity = 'error';
  } else if (flowIssues.some((issue) => issue.severity === 'warn')) {
    result.issueSeverity = 'warn';
  }

  if (result.status === 'passed' && result.issueSeverity === 'error') {
    result.status = 'passed_with_issues';
  } else if (result.status === 'passed' && result.issueSeverity === 'warn') {
    result.status = 'passed_with_warnings';
  }

  result.durationMs = Date.now() - startedAt;
  summary.routeResults.push(result);
}

function buildMarkdownReport() {
  const totals = {
    passed: summary.routeResults.filter((item) => item.status === 'passed').length,
    warnings: summary.routeResults.filter((item) => item.status === 'passed_with_warnings').length,
    issues: summary.routeResults.filter((item) => item.status === 'passed_with_issues').length,
    failed: summary.routeResults.filter((item) => item.status === 'failed').length
  };

  const routeLines = summary.routeResults.map((item) => {
    return `| ${item.device} | ${item.slug} | ${item.status} | ${item.issueCount} | ${item.durationMs} | ${item.finalHash || '-'} | ${item.screenshot || '-'} |`;
  });

  const issueLines = summary.issues.slice(0, 120).map((issue) => {
    const status = issue.status ? ` ${issue.status}` : '';
    const url = issue.url ? ` ${issue.url}` : '';
    return `| ${issue.device} | ${issue.route} | ${issue.type} | ${issue.severity} | ${truncate(`${issue.message || ''}${status}${url}`, 180)} |`;
  });

  return [
    '# Playwright audit report',
    '',
    `- run_id: ${summary.runId}`,
    `- base_url: ${summary.baseUrl}`,
    `- started_at: ${summary.startedAt}`,
    `- finished_at: ${summary.finishedAt}`,
    '',
    '## Totals',
    '',
    `- passed: ${totals.passed}`,
    `- passed_with_warnings: ${totals.warnings}`,
    `- passed_with_issues: ${totals.issues}`,
    `- failed: ${totals.failed}`,
    `- issue_count: ${summary.issues.length}`,
    '',
    '## Navigation audits',
    '',
    ...(summary.navigationAudits.length
      ? summary.navigationAudits.map((item) => `- ${item.device}/${item.type}: labels=${item.labels.join(', ')}${item.missing.length ? ` | missing=${item.missing.join(', ')}` : ''}`)
      : ['- none']),
    '',
    '## Routes',
    '',
    '| device | route | status | issues | ms | final_hash | screenshot |',
    '| --- | --- | --- | ---: | ---: | --- | --- |',
    ...routeLines,
    '',
    '## Issues',
    '',
    '| device | route | type | severity | detail |',
    '| --- | --- | --- | --- | --- |',
    ...(issueLines.length ? issueLines : ['| - | - | - | - | none |'])
  ].join('\n');
}

async function launchBrowser() {
  for (const channel of ['msedge', 'chrome']) {
    try {
      return await chromium.launch({ headless: true, channel });
    } catch {
      // Try the next installed browser channel.
    }
  }

  return chromium.launch({ headless: true });
}

const browser = await launchBrowser();

try {
  const desktopContext = await browser.newContext({
    viewport: { width: 1440, height: 960 }
  });
  const desktopPage = await desktopContext.newPage();
  attachObservers(desktopPage);

  await login(desktopPage);
  if (!SKIP_NAV_AUDITS) {
    await auditDesktopNavigation(desktopPage);
  }

  for (const route of selectRoutes(DESKTOP_ROUTES)) {
    await visitRoute(desktopPage, 'desktop', route);
  }
  if (!SKIP_EMPLOYEE_FLOW) {
    await auditEmployeeProfileFlow(desktopPage);
  }

  await desktopContext.close();

  if (!SKIP_MOBILE) {
    const mobileContext = await browser.newContext({
      ...devices['iPhone 13'],
      storageState: STORAGE_STATE_PATH
    });
    const mobilePage = await mobileContext.newPage();
    attachObservers(mobilePage);

    for (const route of selectRoutes(MOBILE_ROUTES)) {
      await visitRoute(mobilePage, 'mobile', route);
    }
    if (!SKIP_NAV_AUDITS) {
      await auditMobileNavigation(mobilePage);
    }

    await mobileContext.close();
  }
} finally {
  await browser.close();
}

summary.finishedAt = new Date().toISOString();

await fs.writeFile(
  path.join(ARTIFACT_DIR, 'summary.json'),
  JSON.stringify(summary, null, 2),
  'utf8'
);

await fs.writeFile(
  path.join(ARTIFACT_DIR, 'summary.md'),
  buildMarkdownReport(),
  'utf8'
);

const failedRoutes = summary.routeResults.filter((item) => item.status === 'failed').length;
const issueRoutes = summary.routeResults.filter((item) => item.status === 'passed_with_issues').length;
const errorIssues = summary.issues.filter((item) => item.severity === 'error').length;

console.log(JSON.stringify({
  artifactDir: ARTIFACT_DIR,
  summaryFile: path.join(ARTIFACT_DIR, 'summary.json'),
  reportFile: path.join(ARTIFACT_DIR, 'summary.md'),
  routeCount: summary.routeResults.length,
  issueCount: summary.issues.length,
  failedRoutes,
  issueRoutes,
  errorIssues
}, null, 2));

if (failedRoutes > 0 || issueRoutes > 0 || errorIssues > 0) {
  process.exitCode = 1;
}
