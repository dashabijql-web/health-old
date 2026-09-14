import fs from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'

import { LOGIN_CREDENTIALS, resolveFrontendBaseUrl } from '../shared/health-test-utils.mjs'

const baseUrl = await resolveFrontendBaseUrl()
const outputDir = path.resolve('tests/visual/artifacts/dashboard-full-page-latest')
const metricsPath = path.join(outputDir, 'dashboard-full-page-metrics.json')
const viewports = [
  { slug: 'desktop-1440', width: 1440, height: 900 },
  { slug: 'desktop-1707', width: 1707, height: 1067 },
  { slug: 'desktop-1920', width: 1920, height: 1080 }
]

await fs.mkdir(outputDir, { recursive: true })

const browser = await chromium.launch({ headless: true })
const results = []
const failures = []

try {
  for (const viewport of viewports) {
    const context = await browser.newContext({ viewport: { width: viewport.width, height: viewport.height }, deviceScaleFactor: 1 })
    const page = await context.newPage()
    const response = await context.request.post(`${baseUrl}/dev-api/auth/login`, { data: LOGIN_CREDENTIALS })
    const payload = await response.json()
    if (!response.ok() || payload?.code !== 200 || !payload?.data?.token) {
      throw new Error(payload?.message || `login failed with HTTP ${response.status()}`)
    }

    await context.addCookies([
      { name: 'User-Token', value: payload.data.token, url: baseUrl },
      { name: 'satoken', value: payload.data.token, url: baseUrl },
      { name: 'Health-Data-Source', value: 'old', url: baseUrl }
    ])
    await page.addInitScript(() => window.localStorage.setItem('Health-Data-Source', 'old'))
    await page.goto(`${baseUrl}/#/health-monitor/dashboard`, { waitUntil: 'domcontentloaded', timeout: 30000 })
    await page.locator('.db-control-system').waitFor({ state: 'visible', timeout: 15000 })
    await page.waitForLoadState('networkidle', { timeout: 12000 }).catch(() => {})
    await page.waitForTimeout(1200)

    const metrics = await page.evaluate(() => {
    const describe = (element) => {
      if (!element) return null
      const rect = element.getBoundingClientRect()
      const title = element.querySelector('.db-track-title')?.textContent?.trim() || element.className
      return {
        title,
        className: String(element.className),
        top: Math.round(rect.top + window.scrollY),
        bottom: Math.round(rect.bottom + window.scrollY),
        width: Math.round(rect.width),
        height: Math.round(rect.height),
        scrollHeight: element.scrollHeight,
        clientHeight: element.clientHeight
      }
    }

    const leftPanels = Array.from(document.querySelectorAll('.db-col-health > .db-panel')).map(describe)
    const decisionPanels = Array.from(document.querySelectorAll('.db-col-decision > .db-panel')).map(describe)
    const addGaps = (panels) => panels.map((panel, index) => ({
      ...panel,
      gapAfter: index < panels.length - 1 ? panels[index + 1].top - panel.bottom : null
    }))

    const healthPanel = document.querySelector('.db-health-snapshot')
    const healthGrid = healthPanel?.querySelector('.dm-vitals-grid')
    const devicePanel = document.querySelector('.db-col-device')
    const deviceCards = devicePanel?.querySelector('.dm-device-cards')
    const contentWidthRatio = (content, panel) => {
      if (!content || !panel) return null
      return Number((content.getBoundingClientRect().width / panel.getBoundingClientRect().width).toFixed(3))
    }

    return {
      viewport: { width: window.innerWidth, height: window.innerHeight },
      document: {
        bodyScrollHeight: document.body.scrollHeight,
        documentScrollHeight: document.documentElement.scrollHeight,
        scrollingElementScrollHeight: document.scrollingElement?.scrollHeight || null
      },
      mainGrid: describe(document.querySelector('.db-main-grid')),
      deviceBand: describe(document.querySelector('.db-device-band')),
      closureLane: describe(document.querySelector('.db-closure-lane')),
      closureWorkspace: describe(document.querySelector('.db-closure-workspace')),
      closureAssist: describe(document.querySelector('.db-closure-assist')),
      dutyConsoleCount: document.querySelectorAll('.db-duty-console').length,
      analysisBand: describe(document.querySelector('.db-analysis-band')),
      analysisSidebarColumns: (() => {
        const sidebar = document.querySelector('.db-analysis-sidebar')
        return sidebar ? getComputedStyle(sidebar).gridTemplateColumns.split(' ').length : 0
      })(),
      healthPanel: {
        ...describe(healthPanel),
        cardCount: healthGrid?.children.length || 0,
        gridColumns: healthGrid ? getComputedStyle(healthGrid).gridTemplateColumns.split(' ').length : 0,
        contentWidthRatio: contentWidthRatio(healthGrid, healthPanel)
      },
      devicePanel: {
        ...describe(devicePanel),
        cardCount: deviceCards?.children.length || 0,
        gridColumns: deviceCards ? getComputedStyle(deviceCards).gridTemplateColumns.split(' ').length : 0,
        contentWidthRatio: contentWidthRatio(deviceCards, devicePanel)
      },
      leftPanels: addGaps(leftPanels),
      decisionPanels: addGaps(decisionPanels),
      topColumnHeightDelta: Math.abs(leftPanels[0].bottom - decisionPanels[0].bottom)
    }
    })

    const screenshotPath = path.join(outputDir, `dashboard-full-page-${viewport.width}.png`)
    await page.screenshot({ path: screenshotPath, fullPage: true })
    results.push({ viewport, screenshotPath, metrics })
    if (metrics.topColumnHeightDelta > 24) {
      failures.push(`${viewport.slug} dashboard top columns differ by ${metrics.topColumnHeightDelta}px`)
    }
    if (metrics.document.documentScrollHeight > 5000) {
      failures.push(`${viewport.slug} dashboard is abnormally tall at ${metrics.document.documentScrollHeight}px`)
    }
    if (metrics.healthPanel.gridColumns !== 3) {
      failures.push(`${viewport.slug} health snapshot uses ${metrics.healthPanel.gridColumns} columns instead of 3`)
    }
    if (!metrics.closureWorkspace || !metrics.closureAssist || metrics.dutyConsoleCount !== 0) {
      failures.push(`${viewport.slug} dashboard did not merge duty support into the closure workspace`)
    }
    if (metrics.devicePanel.contentWidthRatio < 0.9) {
      failures.push(`${viewport.slug} device cards only use ${metrics.devicePanel.contentWidthRatio} of panel width`)
    }
    if (metrics.analysisSidebarColumns !== 3) {
      failures.push(`${viewport.slug} analysis sidebar uses ${metrics.analysisSidebarColumns} columns instead of 3`)
    }
    await context.close()
  }

  await fs.writeFile(metricsPath, JSON.stringify(results, null, 2))
  console.log(JSON.stringify({ metricsPath, results }, null, 2))
  if (failures.length) throw new Error(failures.join('; '))
} finally {
  await browser.close()
}
