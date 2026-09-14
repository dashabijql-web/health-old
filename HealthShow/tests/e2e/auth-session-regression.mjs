import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { chromium } from 'playwright';
import { assert, LOGIN_CREDENTIALS, truncate } from '../shared/health-test-utils.mjs';

const BASE_URL = process.env.AUTH_AUDIT_BASE_URL || 'http://127.0.0.1:9528';
const API_BASE = `${BASE_URL}/dev-api`;
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-');
const ARTIFACT_DIR = path.resolve(process.cwd(), 'tests', 'e2e', 'artifacts', RUN_ID);
const REPORT_JSON = path.join(ARTIFACT_DIR, 'auth-summary.json');
const REPORT_MD = path.join(ARTIFACT_DIR, 'auth-summary.md');

const summary = {
  runId: RUN_ID,
  startedAt: new Date().toISOString(),
  baseUrl: BASE_URL,
  checks: []
};

await fs.mkdir(ARTIFACT_DIR, { recursive: true });

function timeoutAfter(ms, message) {
  return new Promise((_, reject) => {
    const timer = setTimeout(() => reject(new Error(message)), ms);
    timer.unref?.();
  });
}

async function cleanupWithTimeout(label, cleanup, timeoutMs = 10000) {
  try {
    await Promise.race([
      cleanup(),
      timeoutAfter(timeoutMs, `${label} cleanup timed out after ${timeoutMs}ms`)
    ]);
  } catch (error) {
    console.warn(`[auth-audit] cleanup warning ${label}: ${truncate(error?.message || String(error))}`);
  }
}

async function runCheck(name, fn) {
  console.log(`[auth-audit] start ${name}`);
  const startedAt = Date.now();
  const record = { name, status: 'passed', durationMs: 0, note: '' };
  try {
    const note = await fn();
    if (note) record.note = note;
  } catch (error) {
    record.status = 'failed';
    record.note = truncate(error?.stack || String(error), 500);
  }
  record.durationMs = Date.now() - startedAt;
  summary.checks.push(record);
  if (record.status !== 'passed') {
    console.log(`[auth-audit] fail ${name}: ${record.note}`);
    throw new Error(`${name} failed: ${record.note}`);
  }
  console.log(`[auth-audit] pass ${name}${record.note ? ` :: ${record.note}` : ''}`);
  return record;
}

async function waitForAppReady(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.locator('#app').first().waitFor({ state: 'visible', timeout: 15000 });
}

async function waitForNonLoginHash(page) {
  await page.waitForFunction(() => !window.location.hash.includes('/login'), null, { timeout: 15000 });
}

async function waitForLoginHash(page) {
  await page.waitForFunction(() => window.location.hash.includes('/login'), null, { timeout: 15000 });
}

async function loginViaApi(requestContext) {
  const response = await requestContext.post(`${API_BASE}/auth/login`, {
    data: LOGIN_CREDENTIALS
  });
  assert(response.ok(), `login HTTP ${response.status()}`);
  const payload = await response.json();
  assert(payload?.code === 200, payload?.message || 'login rejected');
  assert(payload?.data?.token, 'login response missing token');
  return payload.data.token;
}

async function bootstrapSession(page, token) {
  await page.context().addCookies([
    {
      name: 'User-Token',
      value: token,
      url: BASE_URL
    }
  ]);
  await page.goto(`${BASE_URL}/#/health-monitor/dashboard`, { waitUntil: 'domcontentloaded' });
  await waitForAppReady(page);
  await waitForNonLoginHash(page);
}

async function invalidateServerSideToken(requestContext, token) {
  const response = await requestContext.post(`${API_BASE}/auth/logout`, {
    headers: {
      satoken: token,
      cookie: `User-Token=${token}; satoken=${token}`
    }
  });
  assert(response.ok(), `logout HTTP ${response.status()}`);
}

function buildMarkdownReport() {
  return [
    '# Auth Session Regression',
    '',
    `- run_id: ${summary.runId}`,
    `- base_url: ${summary.baseUrl}`,
    `- started_at: ${summary.startedAt}`,
    `- finished_at: ${summary.finishedAt}`,
    '',
    '| check | status | ms | note |',
    '| --- | --- | ---: | --- |',
    ...summary.checks.map(item => `| ${item.name} | ${item.status} | ${item.durationMs} | ${item.note || '-'} |`)
  ].join('\n');
}

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ baseURL: BASE_URL });
const page = await context.newPage();

try {
  await runCheck('login.ui.prefilled-admin', async () => {
    await page.goto(`${BASE_URL}/#/login`, { waitUntil: 'domcontentloaded' });
    await waitForAppReady(page);
    const username = await page.locator('input[name="username"]').inputValue();
    const password = await page.locator('input[name="password"]').inputValue();
    assert(username === LOGIN_CREDENTIALS.username, `username input should default to ${LOGIN_CREDENTIALS.username}`);
    assert(password === LOGIN_CREDENTIALS.password, 'password input should match configured test password');
    return `login form is prefilled with ${LOGIN_CREDENTIALS.username}/<configured-password>`;
  });

  await runCheck('login.ui.submit', async () => {
    await page.locator('input[name="username"]').fill(LOGIN_CREDENTIALS.username);
    await page.locator('input[name="password"]').fill(LOGIN_CREDENTIALS.password);
    await page.locator('.login-form button').click();
    await waitForNonLoginHash(page);
    await page.locator('.navbar').waitFor({ state: 'visible', timeout: 15000 });
    return await page.evaluate(() => window.location.hash);
  });

  await runCheck('auth.refresh.restore', async () => {
    await page.reload({ waitUntil: 'domcontentloaded' });
    await waitForAppReady(page);
    await waitForNonLoginHash(page);
    await page.locator('.navbar').waitFor({ state: 'visible', timeout: 15000 });
    return await page.evaluate(() => window.location.hash);
  });

  await runCheck('auth.logout.ui', async () => {
    await page.locator('.avatar-wrapper').click();
    await page.locator('.user-dropdown').getByText('退出').click();
    await waitForLoginHash(page);
    const cookies = await context.cookies(BASE_URL);
    const userToken = cookies.find(cookie => cookie.name === 'User-Token');
    assert(!userToken, 'User-Token cookie should be removed after logout');
    return 'logout cleared cookie and returned to login';
  });

  let token = await loginViaApi(context.request);
  await runCheck('auth.bootstrap.after-logout', async () => {
    await bootstrapSession(page, token);
    return await page.evaluate(() => window.location.hash);
  });

  await runCheck('auth.concurrent-401-redirects-to-login', async () => {
    await invalidateServerSideToken(context.request, token);
    const result = await page.evaluate(async () => {
      const [{ default: request }] = await Promise.all([
        import('/src/utils/request.js')
      ]);

      await Promise.allSettled(
        Array.from({ length: 4 }, () => request({ url: '/dashboard/overview', method: 'get' }))
      );
      await new Promise(resolve => setTimeout(resolve, 1500));

      return {
        hash: window.location.hash
      };
    });

    assert(result.hash.includes('/login'), `expected login redirect, got ${result.hash}`);
    const cookies = await context.cookies(BASE_URL);
    const userToken = cookies.find(cookie => cookie.name === 'User-Token');
    assert(!userToken, 'User-Token cookie should be removed after concurrent 401 requests');
    return `hash=${result.hash}, cookieCleared=true`;
  });
} finally {
  summary.finishedAt = new Date().toISOString();
  await fs.writeFile(REPORT_JSON, JSON.stringify(summary, null, 2), 'utf8');
  await fs.writeFile(REPORT_MD, buildMarkdownReport(), 'utf8');
  await cleanupWithTimeout('playwright-context', () => context.close());
  await cleanupWithTimeout('playwright-browser', () => browser.close());
}

console.log(JSON.stringify({
  artifactDir: ARTIFACT_DIR,
  reportFile: REPORT_MD,
  checkCount: summary.checks.length,
  failedCount: summary.checks.filter(item => item.status !== 'passed').length
}, null, 2));

process.exit(0);
