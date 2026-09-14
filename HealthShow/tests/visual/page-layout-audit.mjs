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
const API_DATA_SOURCE = process.env.API_DATA_SOURCE || 'old';
const VISUAL_VIEWPORT_PROFILE = process.env.VISUAL_VIEWPORT_PROFILE || 'default';
const VISUAL_SCROLL_AUDIT = process.env.VISUAL_SCROLL_AUDIT === '1';
const VISUAL_SCROLL_CONTAINER_LIMIT = Number.parseInt(process.env.VISUAL_SCROLL_CONTAINER_LIMIT || '1', 10);
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-');
const ARTIFACT_DIR = path.resolve(process.cwd(), 'tests', 'visual', 'artifacts', RUN_ID);
const ARTIFACT_RETENTION = Number.parseInt(process.env.VISUAL_ARTIFACT_RETENTION || '8', 10);

const DEFAULT_ROUTES = [
  { slug: 'safety-command', path: '/safety-command/index' },
  { slug: 'dashboard', path: '/health-monitor/dashboard' },
  { slug: 'workbench', path: '/health-monitor/workbench' },
  { slug: 'real-time', path: '/health-monitor/real-time' },
  { slug: 'heart-rate', path: '/health-monitor/heart-rate', canvasRoot: '.hr-root', mobileCanvasMinimum: 3 },
  { slug: 'pressure', path: '/health-monitor/pressure', canvasRoot: '.ps-root', mobileCanvasMinimum: 2 },
  { slug: 'blood-pressure', path: '/health-monitor/blood-pressure', canvasRoot: '.bp-root', mobileCanvasMinimum: 3 },
  { slug: 'blood-oxygen', path: '/health-monitor/blood-oxygen', canvasRoot: '.bo-root', mobileCanvasMinimum: 2 },
  { slug: 'risk-warning', path: '/health-monitor/risk-warning' },
  { slug: 'employee-profile', path: '/health-monitor/employee-profile' },
  { slug: 'mine-entry', path: '/health-monitor/mine-entry' },
  { slug: 'device-management', path: '/admin/device-list' },
  { slug: 'alert-notifications', path: '/alert-management/notifications' },
  { slug: 'alert-records', path: '/alert-management/records' },
  { slug: 'report-center', path: '/health-monitor/report-center' },
  { slug: 'trend-warning', path: '/health-monitor/trend-warning' },
  { slug: 'ai-chat', path: '/ai-chat/index' }
];

const VIEWPORT_PROFILES = {
  default: [
    { slug: 'desktop-1440', width: 1440, height: 900, deviceScaleFactor: 1, expectVisualViewportScale: true },
    // 2560x1600 @ 150% Windows scaling roughly maps to a 1707x1067 CSS viewport.
    { slug: 'desktop-1707', width: 1707, height: 1067, deviceScaleFactor: 1, expectVisualViewportScale: true },
    { slug: 'desktop-1920', width: 1920, height: 1080, deviceScaleFactor: 1, expectVisualViewportScale: true },
    { slug: 'mobile-390', width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true },
    { slug: 'mobile-414', width: 414, height: 896, deviceScaleFactor: 2, isMobile: true, hasTouch: true }
  ],
  'windows-desktop-matrix': [
    // 1920x1080 at 100% / 125% / 150% scaling -> 1920x1080 / 1536x864 / 1280x720 CSS viewport.
    { slug: 'fhd-100', width: 1920, height: 1080, deviceScaleFactor: 1, expectVisualViewportScale: true },
    { slug: 'fhd-125', width: 1536, height: 864, deviceScaleFactor: 1, expectVisualViewportScale: true },
    { slug: 'fhd-150', width: 1280, height: 720, deviceScaleFactor: 1, expectVisualViewportScale: true },
    // 2560x1440 at 100% / 125% / 150% scaling -> 2560x1440 / 2048x1152 / 1707x960 CSS viewport.
    { slug: 'qhd-100', width: 2560, height: 1440, deviceScaleFactor: 1, expectVisualViewportScale: true },
    { slug: 'qhd-125', width: 2048, height: 1152, deviceScaleFactor: 1, expectVisualViewportScale: true },
    { slug: 'qhd-150', width: 1707, height: 960, deviceScaleFactor: 1, expectVisualViewportScale: true }
  ]
};

const VIEWPORTS = VIEWPORT_PROFILES[VISUAL_VIEWPORT_PROFILE] || VIEWPORT_PROFILES.default;

function selectedRoutes() {
  const filter = new Set(
    (process.env.VISUAL_ROUTES || '')
      .split(',')
      .map((value) => value.trim())
      .filter(Boolean)
  );
  if (filter.size === 0) return DEFAULT_ROUTES;
  const routes = DEFAULT_ROUTES.filter((route) => filter.has(route.slug));
  if (routes.length !== filter.size) {
    const known = new Set(routes.map((route) => route.slug));
    const unknown = Array.from(filter).filter((slug) => !known.has(slug));
    throw new Error(`Unknown VISUAL_ROUTES: ${unknown.join(', ')}`);
  }
  return routes;
}

const summary = {
  runId: RUN_ID,
  baseUrl: BASE_URL,
  dataSource: API_DATA_SOURCE,
  viewportProfile: VISUAL_VIEWPORT_PROFILE,
  scrollAudit: VISUAL_SCROLL_AUDIT,
  startedAt: new Date().toISOString(),
  routes: selectedRoutes().map((route) => route.slug),
  viewports: VIEWPORTS.map(({ slug, width, height, deviceScaleFactor }) => ({ slug, width, height, deviceScaleFactor })),
  results: [],
  issues: []
};

await fs.mkdir(ARTIFACT_DIR, { recursive: true });
await pruneArtifacts(path.dirname(ARTIFACT_DIR), ARTIFACT_RETENTION);

function issue(route, viewport, type, message, extra = {}) {
  const item = { route: route.slug, viewport: viewport.slug, type, message: truncate(message, 300), ...extra };
  summary.issues.push(item);
  return item;
}

function readPngSize(buffer) {
  const pngSignature = '89504e470d0a1a0a';
  if (buffer.subarray(0, 8).toString('hex') !== pngSignature) {
    throw new Error('screenshot is not a PNG file');
  }
  return {
    width: buffer.readUInt32BE(16),
    height: buffer.readUInt32BE(20)
  };
}

async function loginViaApi(context, page) {
  const response = await context.request.post(`${BASE_URL}/dev-api/auth/login`, { data: LOGIN_CREDENTIALS });
  if (!response.ok()) throw new Error(`login HTTP ${response.status()}`);
  const payload = await response.json();
  if (payload?.code !== 200 || !payload?.data?.token) {
    throw new Error(`login rejected: ${payload?.message || 'missing token'}`);
  }
  await context.addCookies([
    { name: 'User-Token', value: payload.data.token, url: BASE_URL },
    { name: 'satoken', value: payload.data.token, url: BASE_URL },
    { name: 'Health-Data-Source', value: API_DATA_SOURCE, url: BASE_URL }
  ]);
  await page.addInitScript((dataSource) => {
    window.localStorage.setItem('Health-Data-Source', dataSource);
  }, API_DATA_SOURCE);
}

async function waitForShell(page) {
  await Promise.race([
    page.locator('.app-main').first().waitFor({ state: 'visible', timeout: 15000 }),
    page.locator('.login-container').first().waitFor({ state: 'visible', timeout: 15000 }),
    page.locator('.wscn-http404-container').first().waitFor({ state: 'visible', timeout: 15000 })
  ]).catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 12000 }).catch(() => {});
  await page.waitForTimeout(800);
}

async function prepareRoute(page, route) {
  if (route.slug !== 'employee-profile') return;

  await page.goto(`${BASE_URL}/#/health-monitor/employee-archive`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitForShell(page);

  const cards = page.locator('.ea-card');
  await cards.first().waitFor({ state: 'visible', timeout: 15000 });
  await cards.first().click();
  await page.waitForFunction(() => window.location.hash.includes('/health-monitor/employee-profile'), null, { timeout: 15000 });
  await page.locator('.ep-vitals-panel').first().waitFor({ state: 'visible', timeout: 10000 });
  await page.locator('.ep-history-chart canvas').first().waitFor({ state: 'visible', timeout: 15000 });
  await page.waitForTimeout(600);
}

async function collectLayoutIssues(page, route, viewport) {
  return await page.evaluate(({ routeSlug, viewportSlug }) => {
    const issues = [];
    const vw = document.documentElement.clientWidth;
    const bodyWidth = Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);
    if (bodyWidth > vw + 2) {
      issues.push({ type: 'horizontal_overflow', message: `scrollWidth ${bodyWidth}px exceeds viewport ${vw}px` });
    }

    const isClippedByOverflowAncestor = (element, rect) => {
      let current = element.parentElement;
      while (current && current !== document.body) {
        const style = window.getComputedStyle(current);
        const clipsX = ['hidden', 'clip', 'auto', 'scroll'].includes(style.overflowX);
        const clipsY = ['hidden', 'clip', 'auto', 'scroll'].includes(style.overflowY);
        if (clipsX || clipsY) {
          const clipRect = current.getBoundingClientRect();
          const visibleLeft = clipsX ? Math.max(rect.left, clipRect.left) : rect.left;
          const visibleRight = clipsX ? Math.min(rect.right, clipRect.right) : rect.right;
          const visibleTop = clipsY ? Math.max(rect.top, clipRect.top) : rect.top;
          const visibleBottom = clipsY ? Math.min(rect.bottom, clipRect.bottom) : rect.bottom;
          const visibleWidth = Math.max(0, visibleRight - visibleLeft);
          const visibleHeight = Math.max(0, visibleBottom - visibleTop);
          const visibleArea = visibleWidth * visibleHeight;
          const totalArea = Math.max(1, rect.width * rect.height);
          if (visibleArea <= 1 || visibleArea / totalArea < 0.2) {
            return true;
          }
        }
        current = current.parentElement;
      }
      return false;
    };

    const isVisible = (element) => {
      const style = window.getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.visibility !== 'hidden'
        && style.display !== 'none'
        && Number(style.opacity) !== 0
        && rect.width > 0
        && rect.height > 0
        && !isClippedByOverflowAncestor(element, rect);
    };

    const textNodes = Array.from(document.body.querySelectorAll('h1,h2,h3,h4,h5,h6,p,span,a,button,label,td,th,.el-button,.el-tag'))
      .filter(isVisible)
      .map((element) => ({
        element,
        text: (element.innerText || element.textContent || '').trim().slice(0, 80),
        rect: element.getBoundingClientRect(),
        tag: element.tagName.toLowerCase()
      }))
      .filter((item) => item.text && item.rect.width > 3 && item.rect.height > 3)
      .slice(0, 220);

    const isAncestorPair = (a, b) => a.element !== b.element && (a.element.contains(b.element) || b.element.contains(a.element));
    const hasSameRoundedRect = (a, b) => ['left', 'top', 'right', 'bottom'].every((key) => Math.round(a.rect[key]) === Math.round(b.rect[key]));
    const closest = (item, selector) => item.element.closest(selector);
    const isStructuredHeaderBodyPair = (a, b) => {
      const aHeader = closest(a, '.rw-list-hd, thead, .el-table__header-wrapper');
      const bHeader = closest(b, '.rw-list-hd, thead, .el-table__header-wrapper');
      const aBody = closest(a, '.rw-list-row, tbody, .el-table__body-wrapper');
      const bBody = closest(b, '.rw-list-row, tbody, .el-table__body-wrapper');
      return (aHeader && bBody) || (bHeader && aBody);
    };
    const rectsOverlap = (a, b, pad = 4) => Math.min(a.right, b.right) - Math.max(a.left, b.left) > pad && Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top) > pad;
    const isTickerPair = (a, b) => closest(a, '.rt-ticker-wrap') && closest(b, '.rt-ticker-wrap');
    const isClippedTickerArtifact = (a, b) => {
      const aTicker = closest(a, '.rt-ticker-wrap');
      const bTicker = closest(b, '.rt-ticker-wrap');
      if (!aTicker && !bTicker) return false;
      if (aTicker && bTicker) return true;
      const tickerRect = (aTicker || bTicker).getBoundingClientRect();
      const otherRect = aTicker ? b.rect : a.rect;
      return !rectsOverlap(tickerRect, otherRect, 4);
    };
    const isMobileBottomNavArtifact = (a, b) => Boolean(closest(a, '.mobile-bottom-nav') || closest(b, '.mobile-bottom-nav'));

    for (let i = 0; i < textNodes.length; i += 1) {
      for (let j = i + 1; j < textNodes.length; j += 1) {
        if (isAncestorPair(textNodes[i], textNodes[j])) continue;
        if (isStructuredHeaderBodyPair(textNodes[i], textNodes[j])) continue;
        if (isTickerPair(textNodes[i], textNodes[j])) continue;
        if (isClippedTickerArtifact(textNodes[i], textNodes[j])) continue;
        if (isMobileBottomNavArtifact(textNodes[i], textNodes[j])) continue;
        if (textNodes[i].text === textNodes[j].text && hasSameRoundedRect(textNodes[i], textNodes[j])) continue;
        const a = textNodes[i].rect;
        const b = textNodes[j].rect;
        const xOverlap = Math.min(a.right, b.right) - Math.max(a.left, b.left);
        const yOverlap = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
        if (xOverlap > 4 && yOverlap > 4) {
          const overlapArea = xOverlap * yOverlap;
          const smallerArea = Math.min(a.width * a.height, b.width * b.height);
          if (smallerArea > 0 && overlapArea / smallerArea > 0.35) {
            issues.push({
              type: 'text_overlap',
              message: `text boxes overlap: "${textNodes[i].text}" / "${textNodes[j].text}"`,
              overlap: { x: Math.round(xOverlap), y: Math.round(yOverlap) }
            });
            if (issues.filter((entry) => entry.type === 'text_overlap').length >= 5) break;
          }
        }
      }
      if (issues.filter((entry) => entry.type === 'text_overlap').length >= 5) break;
    }

    if (vw >= 1000) {
      const charts = Array.from(document.querySelectorAll('.echarts, canvas, [data-visual-audit-chart="true"], .health-chart, .metric-chart, .trend-chart, .dashboard-chart, .rc-chart'))
        .filter(isVisible)
        .filter((element) => {
          const rect = element.getBoundingClientRect();
          const className = String(element.className || '');
          const tag = element.tagName.toLowerCase();
          if (tag === 'svg' && rect.width < 240 && rect.height < 160) return false;
          if (/icon|spark|mini|thumb|avatar|badge|dot/i.test(className)) return false;
          if (element.closest('.sparkline-wrap, .ep-trend-chart, .ep-ecg')) return false;
          return rect.width >= 120 || rect.height >= 120;
        })
        .map((element) => element.getBoundingClientRect())
        .filter((rect) => rect.width > 20 || rect.height > 20);
      charts.forEach((rect, index) => {
        if (rect.width < 240 || rect.height < 160) {
          issues.push({
            type: 'chart_container_too_small',
            message: `chart-like container #${index + 1} is ${Math.round(rect.width)}x${Math.round(rect.height)}`
          });
        }
      });
    }

    if (viewport.isMobile && route.mobileCanvasMinimum) {
      const canvases = Array.from(document.querySelectorAll(`${route.canvasRoot} canvas`))
        .map((element) => element.getBoundingClientRect())
        .filter((rect) => rect.width >= 100 && rect.height >= 100);
      if (canvases.length < route.mobileCanvasMinimum) {
        issues.push({
          type: 'mobile_chart_missing',
          message: `expected at least ${route.mobileCanvasMinimum} mobile charts, found ${canvases.length}`
        });
      }
    }

    const actionButtons = Array.from(document.querySelectorAll('button,.el-button,[role="button"]')).filter(isVisible);
    if (actionButtons.length === 0) {
      issues.push({ type: 'no_primary_action_visible', message: 'no visible button/action found on page' });
    }

    const clippedTables = Array.from(document.querySelectorAll('.el-table__body-wrapper,.el-table,.el-table__inner-wrapper'))
      .filter(isVisible)
      .filter((element) => {
        const scrollHost = element.closest('.rt-tbl-wrap');
        if (!scrollHost) return true;
        const hostRect = scrollHost.getBoundingClientRect();
        const hostStyle = window.getComputedStyle(scrollHost);
        const canScrollX = ['auto', 'scroll'].includes(hostStyle.overflowX) && scrollHost.scrollWidth > scrollHost.clientWidth + 2;
        return !(canScrollX && hostRect.left >= -2 && hostRect.right <= vw + 2);
      })
      .map((element) => element.getBoundingClientRect())
      .filter((rect) => rect.right > vw + 2 || rect.left < -2);
    clippedTables.forEach((rect) => {
      issues.push({
        type: 'table_clipped_outside_viewport',
        message: `table wrapper outside viewport: left=${Math.round(rect.left)}, right=${Math.round(rect.right)}, viewport=${vw}`
      });
    });

    if (routeSlug === 'dashboard' && vw >= 1200) {
      const vitalCards = Array.from(document.querySelectorAll('.dm-vital-card'))
        .filter(isVisible)
        .map((element) => {
          const rect = element.getBoundingClientRect();
          const labelRect = element.querySelector('.dm-vital-label')?.getBoundingClientRect() ?? null;
          const valueRect = element.querySelector('.dm-vital-val')?.getBoundingClientRect() ?? null;
          return {
            text: (element.innerText || element.textContent || '').replace(/\s+/g, ' ').trim(),
            width: rect.width,
            labelWidth: labelRect?.width ?? 0,
            labelHeight: labelRect?.height ?? 0,
            valueWidth: valueRect?.width ?? 0,
            valueHeight: valueRect?.height ?? 0
          };
        });

      const collapsedVitalCards = vitalCards.filter((item) =>
        item.width < 108
        || (item.labelWidth > 0 && item.labelWidth < 28 && item.labelHeight > 34)
        || (item.valueWidth > 0 && item.valueWidth < 24 && item.valueHeight > 18)
      );

      collapsedVitalCards.slice(0, 3).forEach((item) => {
        issues.push({
          type: 'dashboard_vital_card_collapsed',
          message: `vital card is too narrow for desktop reading: "${item.text}" (${Math.round(item.width)}px wide, label ${Math.round(item.labelWidth)}x${Math.round(item.labelHeight)}, value ${Math.round(item.valueWidth)}x${Math.round(item.valueHeight)})`
        });
      });
    }

    return issues.map((entry) => ({ route: routeSlug, viewport: viewportSlug, ...entry }));
  }, { routeSlug: route.slug, viewportSlug: viewport.slug });
}

async function collectRenderMetrics(page) {
  return await page.evaluate(() => ({
    innerWidth: window.innerWidth,
    innerHeight: window.innerHeight,
    devicePixelRatio: window.devicePixelRatio,
    visualViewportScale: window.visualViewport?.scale ?? null
  }));
}

async function captureScrollEvidence(page, route, viewport) {
  const scrollArtifacts = { pageScrollStates: [], scrollContainers: [] };

  const pageHost = await page.evaluate(() => {
    const candidates = [
      document.scrollingElement,
      document.querySelector('.app-main'),
      document.querySelector('.main-container'),
      document.querySelector('.app-container'),
      document.querySelector('.dm-outer'),
      document.querySelector('.rw-root'),
      document.querySelector('.ep-page'),
      document.querySelector('.ea-page')
    ].filter(Boolean);

    const seen = new Set();
    const isScrollable = (element) => {
      if (!element) return false;
      const rect = element.getBoundingClientRect();
      return element.scrollHeight > element.clientHeight + 80
        && element.clientHeight > 260
        && rect.width > window.innerWidth * 0.5;
    };

    for (const candidate of candidates) {
      if (seen.has(candidate)) continue;
      seen.add(candidate);
      if (!isScrollable(candidate)) continue;
      candidate.setAttribute('data-visual-page-scroll-host', '1');
      return {
        selector: '[data-visual-page-scroll-host="1"]',
        maxScrollTop: Math.max(0, candidate.scrollHeight - candidate.clientHeight),
        clientHeight: candidate.clientHeight
      };
    }

    return null;
  });

  if (pageHost?.maxScrollTop > 80) {
    const scrollStops = [
      { slug: 'page-mid', ratio: 0.5 },
      { slug: 'page-bottom', ratio: 1 }
    ];
    for (const stop of scrollStops) {
      const nextTop = Math.round(pageHost.maxScrollTop * stop.ratio);
      await page.evaluate(({ selector, nextTop }) => {
        const host = document.querySelector(selector);
        if (host) host.scrollTop = nextTop;
      }, { selector: pageHost.selector, nextTop });
      await page.waitForTimeout(250);
      const screenshotName = `${route.slug}-${viewport.slug}-${stop.slug}.png`;
      const screenshotPath = path.join(ARTIFACT_DIR, screenshotName);
      await page.screenshot({ path: screenshotPath, fullPage: false });
      scrollArtifacts.pageScrollStates.push({
        slug: stop.slug,
        scrollTop: nextTop,
        screenshot: screenshotPath
      });
    }

    await page.evaluate(({ selector }) => {
      const host = document.querySelector(selector);
      if (host) host.scrollTop = 0;
    }, { selector: pageHost.selector });
    await page.waitForTimeout(120);
  }

  const scrollContainers = await page.evaluate((limit) => {
    const previous = Array.from(document.querySelectorAll('[data-visual-scroll-capture-id]'));
    previous.forEach((element) => element.removeAttribute('data-visual-scroll-capture-id'));

    const isVisible = (element) => {
      const style = window.getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.visibility !== 'hidden'
        && style.display !== 'none'
        && Number(style.opacity) !== 0
        && rect.width > 0
        && rect.height > 0;
    };

    const describe = (element) => {
      const testId = element.getAttribute('data-testid');
      if (testId) return `[data-testid="${testId}"]`;
      if (element.id) return `#${element.id}`;
      const classNames = Array.from(element.classList || []).filter(Boolean).slice(0, 2);
      if (classNames.length) return `.${classNames.join('.')}`;
      return element.tagName.toLowerCase();
    };

    const candidates = Array.from(document.body.querySelectorAll('*'))
      .filter(isVisible)
      .map((element) => {
        const style = window.getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        const canScrollY = ['auto', 'scroll'].includes(style.overflowY) || ['auto', 'scroll'].includes(style.overflow);
        return {
          element,
          rect,
          area: rect.width * rect.height,
          canScrollY,
          scrollHeight: element.scrollHeight,
          clientHeight: element.clientHeight
        };
      })
      .filter((item) =>
        item.canScrollY
        && item.scrollHeight > item.clientHeight + 80
        && item.clientHeight >= 120
        && item.rect.width >= 180
        && item.area >= 60000
        && !(item.rect.height > window.innerHeight * 0.8 && item.rect.width > window.innerWidth * 0.8)
      )
      .sort((a, b) => b.area - a.area);

    const selected = [];
    for (const item of candidates) {
      if (selected.some((current) => current.element.contains(item.element) || item.element.contains(current.element))) continue;
      selected.push(item);
      if (selected.length >= limit) break;
    }

    return selected.map((item, index) => {
      const id = `scroll-${index + 1}`;
      item.element.setAttribute('data-visual-scroll-capture-id', id);
      return {
        id,
        selector: `[data-visual-scroll-capture-id="${id}"]`,
        descriptor: describe(item.element),
        maxScrollTop: Math.max(0, item.element.scrollHeight - item.element.clientHeight)
      };
    });
  }, VISUAL_SCROLL_CONTAINER_LIMIT);

  for (const container of scrollContainers) {
    const locator = page.locator(container.selector);
    if ((await locator.count()) !== 1) continue;

    await page.evaluate(({ selector }) => {
      const element = document.querySelector(selector);
      if (element) element.scrollTop = 0;
    }, { selector: container.selector });
    await page.waitForTimeout(150);

    const topName = `${route.slug}-${viewport.slug}-${container.id}-container-top.png`;
    const topPath = path.join(ARTIFACT_DIR, topName);
    await locator.screenshot({ path: topPath });

    await page.evaluate(({ selector, nextTop }) => {
      const element = document.querySelector(selector);
      if (element) element.scrollTop = nextTop;
    }, { selector: container.selector, nextTop: container.maxScrollTop });
    await page.waitForTimeout(180);

    const bottomName = `${route.slug}-${viewport.slug}-${container.id}-container-bottom.png`;
    const bottomPath = path.join(ARTIFACT_DIR, bottomName);
    await locator.screenshot({ path: bottomPath });

    scrollArtifacts.scrollContainers.push({
      descriptor: container.descriptor,
      topScreenshot: topPath,
      bottomScreenshot: bottomPath
    });

    await page.evaluate(({ selector }) => {
      const element = document.querySelector(selector);
      if (element) element.scrollTop = 0;
    }, { selector: container.selector });
  }

  return scrollArtifacts;
}

async function auditRoute(browser, route, viewport) {
  const contextOptions = viewport.isMobile
    ? {
        ...devices['iPhone 13'],
        viewport: { width: viewport.width, height: viewport.height },
        deviceScaleFactor: viewport.deviceScaleFactor ?? 2,
        isMobile: true,
        hasTouch: true
      }
    : {
        viewport: { width: viewport.width, height: viewport.height },
        deviceScaleFactor: viewport.deviceScaleFactor ?? 1
      };
  const context = await browser.newContext(contextOptions);
  const page = await context.newPage();
  const result = { route: route.slug, path: route.path, viewport: viewport.slug, status: 'passed', screenshot: null, issues: [] };
  try {
    await loginViaApi(context, page);
    if (route.slug === 'employee-profile') {
      await prepareRoute(page, route);
    } else {
      await page.goto(`${BASE_URL}/#${route.path}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
      await waitForShell(page);
    }
    result.finalHash = await page.evaluate(() => window.location.hash);
    if (result.finalHash.includes('/login')) {
      result.issues.push(issue(route, viewport, 'redirected_to_login', 'route redirected to login'));
    }
    if (result.finalHash.includes('/404')) {
      result.issues.push(issue(route, viewport, 'not_found', 'route rendered 404'));
    }
    result.renderMetrics = await collectRenderMetrics(page);
    if (result.renderMetrics.innerWidth !== viewport.width || result.renderMetrics.innerHeight !== viewport.height) {
      result.issues.push(issue(
        route,
        viewport,
        'viewport_mismatch',
        `runtime viewport ${result.renderMetrics.innerWidth}x${result.renderMetrics.innerHeight} does not match expected ${viewport.width}x${viewport.height}`,
        { renderMetrics: result.renderMetrics }
      ));
    }
    const expectedDpr = viewport.deviceScaleFactor ?? 1;
    if (result.renderMetrics.devicePixelRatio !== expectedDpr) {
      result.issues.push(issue(
        route,
        viewport,
        'device_pixel_ratio_mismatch',
        `devicePixelRatio ${result.renderMetrics.devicePixelRatio} does not match expected ${expectedDpr}`,
        { renderMetrics: result.renderMetrics }
      ));
    }
    if (viewport.expectVisualViewportScale && result.renderMetrics.visualViewportScale !== null && result.renderMetrics.visualViewportScale !== 1) {
      result.issues.push(issue(
        route,
        viewport,
        'visual_viewport_scale_mismatch',
        `visualViewport.scale ${result.renderMetrics.visualViewportScale} should be 1 or null for reliable fixed-size desktop captures`,
        { renderMetrics: result.renderMetrics }
      ));
    }
    const layoutIssues = await collectLayoutIssues(page, route, viewport);
    for (const found of layoutIssues) {
      result.issues.push(issue(route, viewport, found.type, found.message, found));
    }
    const screenshotName = `${route.slug}-${viewport.slug}.png`;
    result.screenshot = path.join(ARTIFACT_DIR, screenshotName);
    await page.screenshot({ path: result.screenshot, fullPage: false });
    const screenshotBuffer = await fs.readFile(result.screenshot);
    result.screenshotDimensions = readPngSize(screenshotBuffer);
    const expectedScreenshotWidth = viewport.width * (viewport.deviceScaleFactor ?? 1);
    const expectedScreenshotHeight = viewport.height * (viewport.deviceScaleFactor ?? 1);
    if (
      result.screenshotDimensions.width !== expectedScreenshotWidth
      || result.screenshotDimensions.height !== expectedScreenshotHeight
    ) {
      result.issues.push(issue(
        route,
        viewport,
        'screenshot_size_mismatch',
        `png size ${result.screenshotDimensions.width}x${result.screenshotDimensions.height} does not match expected ${expectedScreenshotWidth}x${expectedScreenshotHeight}`,
        { screenshotDimensions: result.screenshotDimensions }
      ));
    }
    if (VISUAL_SCROLL_AUDIT && !viewport.isMobile) {
      result.scrollArtifacts = await captureScrollEvidence(page, route, viewport);
    }
    if (result.issues.length > 0) result.status = 'failed';
  } catch (error) {
    result.status = 'failed';
    result.issues.push(issue(route, viewport, 'audit_error', error.message || String(error)));
  } finally {
    await context.close();
  }
  summary.results.push(result);
}

const browser = await chromium.launch({ headless: true });
try {
  for (const route of selectedRoutes()) {
    for (const viewport of VIEWPORTS) {
      await auditRoute(browser, route, viewport);
    }
  }
} finally {
  await browser.close();
}

summary.finishedAt = new Date().toISOString();
summary.failedRoutes = new Set(summary.results.filter((result) => result.status !== 'passed').map((result) => result.route)).size;
summary.failedChecks = summary.results.filter((result) => result.status !== 'passed').length;
summary.status = summary.failedChecks === 0 ? 'passed' : 'failed';
summary.routeSummaries = summary.routes.map((routeSlug) => {
  const routeResults = summary.results.filter((result) => result.route === routeSlug);
  const routeIssues = summary.issues.filter((item) => item.route === routeSlug);
  return {
    route: routeSlug,
    status: routeResults.every((result) => result.status === 'passed') ? 'passed' : 'failed',
    passedViewports: routeResults.filter((result) => result.status === 'passed').length,
    failedViewports: routeResults.filter((result) => result.status !== 'passed').length,
    issueCount: routeIssues.length,
    rerunCommand: `node scripts/with-env.mjs VISUAL_ROUTES=${routeSlug} -- npm run audit:visual`,
    screenshots: routeResults.map((result) => ({
      viewport: result.viewport,
      status: result.status,
      file: result.screenshot ? path.basename(result.screenshot) : null,
      scrollArtifacts: result.scrollArtifacts || null
    }))
  };
});

summary.issueTypeSummaries = Array.from(
  summary.issues.reduce((counts, item) => {
    const current = counts.get(item.type) || { type: item.type, count: 0, routes: new Set(), viewports: new Set() };
    current.count += 1;
    current.routes.add(item.route);
    current.viewports.add(item.viewport);
    counts.set(item.type, current);
    return counts;
  }, new Map()).values()
).map((item) => ({
  type: item.type,
  count: item.count,
  routes: Array.from(item.routes).sort(),
  viewports: Array.from(item.viewports).sort()
})).sort((a, b) => b.count - a.count || a.type.localeCompare(b.type));

const jsonPath = path.join(ARTIFACT_DIR, 'layout-summary.json');
const mdPath = path.join(ARTIFACT_DIR, 'layout-summary.md');
await fs.writeFile(jsonPath, JSON.stringify(summary, null, 2));

const lines = [
  `# Visual Layout Audit ${RUN_ID}`,
  '',
  `- status: ${summary.status}`,
  `- base_url: ${BASE_URL}`,
  `- data_source: ${API_DATA_SOURCE}`,
  `- viewport_profile: ${VISUAL_VIEWPORT_PROFILE}`,
  `- scroll_audit: ${VISUAL_SCROLL_AUDIT}`,
  `- failed_routes: ${summary.failedRoutes}`,
  `- failed_checks: ${summary.failedChecks}`,
  `- artifact_dir: ${ARTIFACT_DIR}`,
  `- rerun_all: npm run audit:visual`,
  `- rerun_one_route: node scripts/with-env.mjs VISUAL_ROUTES=<route-slug> -- npm run audit:visual`,
  '',
  '## Route Summary',
  '',
  '| route | status | passed viewports | failed viewports | issues | rerun | screenshots |',
  '| --- | --- | ---: | ---: | ---: | --- | --- |',
  ...summary.routeSummaries.map((route) => {
    const screenshots = route.screenshots
      .map((screenshot) => `${screenshot.viewport}:${screenshot.file || '-'}`)
      .join(', ');
    return `| ${route.route} | ${route.status} | ${route.passedViewports} | ${route.failedViewports} | ${route.issueCount} | \`${route.rerunCommand}\` | ${screenshots} |`;
  }),
  '',
  '## Issue Type Summary',
  '',
  ...(summary.issueTypeSummaries.length > 0
    ? [
        '| issue type | count | routes | viewports |',
        '| --- | ---: | --- | --- |',
        ...summary.issueTypeSummaries.map((item) => `| ${item.type} | ${item.count} | ${item.routes.join(', ')} | ${item.viewports.join(', ')} |`)
      ]
    : ['- none']),
  '',
  '## Route / Viewport Results',
  '',
  ...summary.results.map((result) => `- ${result.status === 'passed' ? 'PASS' : 'FAIL'} ${result.route} ${result.viewport} screenshot=${path.basename(result.screenshot || '')}`)
];

if (VISUAL_SCROLL_AUDIT) {
  lines.push('', '## Scroll Evidence', '');
  const scrollLines = summary.results.flatMap((result) => {
    const items = [];
    for (const state of result.scrollArtifacts?.pageScrollStates || []) {
      items.push(`- ${result.route} ${result.viewport} ${state.slug}: ${path.basename(state.screenshot)}`);
    }
    for (const container of result.scrollArtifacts?.scrollContainers || []) {
      items.push(`- ${result.route} ${result.viewport} largest scroll container ${container.descriptor}: top=${path.basename(container.topScreenshot)} bottom=${path.basename(container.bottomScreenshot)}`);
    }
    return items;
  });
  if (scrollLines.length > 0) {
    lines.push(...scrollLines);
  } else {
    lines.push('- none');
  }
}

if (summary.issues.length > 0) {
  lines.push('', '## Issues', '');
  for (const item of summary.issues) {
    lines.push(`- ${item.route} ${item.viewport} ${item.type}: ${item.message}`);
  }
}

await fs.writeFile(mdPath, `${lines.join('\n')}\n`);
console.log(`VISUAL_AUDIT_${summary.status.toUpperCase()} failedRoutes=${summary.failedRoutes} artifactDir=${ARTIFACT_DIR}`);
if (summary.status !== 'passed') process.exitCode = 1;
