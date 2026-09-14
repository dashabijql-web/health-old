import assert from 'node:assert/strict'
import { chromium } from 'playwright'

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://127.0.0.1:9528'
const SCREENSHOT_PREFIX = process.env.SCREENSHOT_PREFIX || '/tmp/dashboard-warning-dark'

const browser = await chromium.launch({ headless: true })
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const loginResponse = await context.request.post(`${FRONTEND_URL}/dev-api/auth/login`, {
    data: { username: 'admin', password: 'admin123' }
  })
  assert.ok(loginResponse.ok(), 'test login request should succeed')
  const login = await loginResponse.json()
  assert.equal(login.code, 200, 'test login should succeed')
  assert.ok(login.data?.token, 'test login should return a token')
  await context.addCookies([
    { name: 'User-Token', value: login.data.token, url: FRONTEND_URL },
    { name: 'satoken', value: login.data.token, url: FRONTEND_URL },
    { name: 'Health-Data-Source', value: 'old', url: FRONTEND_URL }
  ])

  const page = await context.newPage()
  await page.addInitScript(() => window.localStorage.setItem('Health-Data-Source', 'old'))
  await page.goto(`${FRONTEND_URL}/#/health-monitor/dashboard`, { waitUntil: 'networkidle' })
  await page.locator('.dm-event').first().waitFor({ state: 'visible', timeout: 20000 })

  const backgrounds = {}
  await page.locator('.dm-event').first().evaluate((element) => element.click())
  const curveDialog = page.locator('.dm-warn-curve-dialog')
  await curveDialog.waitFor({ state: 'visible' })
  backgrounds.curve = await curveDialog.evaluate((element) => getComputedStyle(element).backgroundColor)
  assert.equal(backgrounds.curve, 'rgb(8, 12, 32)')
  await page.waitForTimeout(350)
  await page.screenshot({ path: `${SCREENSHOT_PREFIX}-curve.png`, fullPage: false })
  await curveDialog.locator('.el-dialog__headerbtn').click()

  await page.locator('.dm-event').first().getByRole('button', { name: '指挥处置' }).evaluate((element) => element.click())
  const commandDrawer = page.locator('.incident-command-drawer')
  await commandDrawer.waitFor({ state: 'visible' })
  backgrounds.command = await commandDrawer.evaluate((element) => getComputedStyle(element).backgroundColor)
  assert.equal(backgrounds.command, 'rgb(7, 20, 38)')
  await page.waitForTimeout(350)
  await page.screenshot({ path: `${SCREENSHOT_PREFIX}-command.png`, fullPage: false })
  const resolveButton = commandDrawer.getByRole('button', { name: '完成处理' })
  if (await resolveButton.count()) {
    await resolveButton.click()
    const confirmDialog = page.locator('.incident-command-confirm')
    await confirmDialog.waitFor({ state: 'visible' })
    backgrounds.confirm = await confirmDialog.evaluate((element) => getComputedStyle(element).backgroundColor)
    assert.equal(backgrounds.confirm, 'rgb(8, 26, 48)')
    const confirmBox = await confirmDialog.boundingBox()
    assert.ok(confirmBox, 'confirmation dialog should have a measurable box')
    assert.ok(Math.abs(confirmBox.x + confirmBox.width / 2 - 720) < 40, 'confirmation dialog should be centered horizontally')
    assert.ok(Math.abs(confirmBox.y + confirmBox.height / 2 - 450) < 40, 'confirmation dialog should be centered vertically')
    await page.waitForTimeout(350)
    await page.screenshot({ path: `${SCREENSHOT_PREFIX}-confirm.png`, fullPage: false })
    await confirmDialog.getByRole('button', { name: '取消' }).click()
  }
  await commandDrawer.locator('.el-drawer__close-btn').click()
  await commandDrawer.waitFor({ state: 'hidden' })
  await page.waitForTimeout(350)

  const warningList = page.locator('.dm-event-list').first()
  await warningList.evaluate((element) => { element.scrollTop = 0 })
  await warningList.hover()
  await page.waitForTimeout(150)
  await page.locator('.dm-event').first().getByRole('button', { name: '处理', exact: true }).evaluate((element) => element.click())
  const handleDialog = page.locator('.dm-handle-dialog')
  await handleDialog.waitFor({ state: 'visible' })
  backgrounds.handle = await handleDialog.evaluate((element) => getComputedStyle(element).backgroundColor)
  assert.equal(backgrounds.handle, 'rgb(8, 12, 32)')
  await page.waitForTimeout(350)
  await page.screenshot({ path: `${SCREENSHOT_PREFIX}-handle.png`, fullPage: false })
  console.log(JSON.stringify({
    status: 'passed',
    screenshots: ['curve', 'command', 'confirm', 'handle'].map((surface) => `${SCREENSHOT_PREFIX}-${surface}.png`),
    backgrounds
  }))
} finally {
  await browser.close()
}
