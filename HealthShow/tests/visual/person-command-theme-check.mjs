import assert from 'node:assert/strict'
import { chromium } from 'playwright'

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://127.0.0.1:9528'
const SCREENSHOT_PATH = process.env.SCREENSHOT_PATH || '/tmp/person-command-dark-drawer.png'

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
  await page.addInitScript(() => {
    window.localStorage.setItem('Health-Data-Source', 'old')
  })
  await page.goto(`${FRONTEND_URL}/#/health-monitor/dashboard`, { waitUntil: 'networkidle' })
  await Promise.race([
    page.locator('.app-main').first().waitFor({ state: 'visible', timeout: 15000 }),
    page.locator('.login-container').first().waitFor({ state: 'visible', timeout: 15000 })
  ]).catch(() => {})
  await page.waitForTimeout(800)

  const searchControl = page.locator('.person-command-search .el-select').first()
  if (await searchControl.count() === 0) {
    await page.screenshot({ path: '/tmp/person-command-theme-navigation-failure.png', fullPage: false })
    throw new Error(`person search unavailable at ${page.url()}`)
  }

  await searchControl.click()
  const search = page.locator('.person-command-search .el-select__input').first()
  await search.fill('陈斌')
  await page.waitForTimeout(800)
  await search.press('ArrowDown')
  await search.press('Enter')

  const drawer = page.locator('.person-drawer')
  await drawer.waitFor({ state: 'visible' })
  await page.waitForTimeout(800)

  const theme = await drawer.evaluate((element) => {
    const body = element.querySelector('.el-drawer__body')
    const header = element.querySelector('.el-drawer__header')
    return {
      drawerBackground: getComputedStyle(element).backgroundColor,
      bodyBackground: body ? getComputedStyle(body).backgroundColor : '',
      headerBackground: header ? getComputedStyle(header).backgroundColor : ''
    }
  })

  assert.equal(theme.drawerBackground, 'rgb(7, 20, 38)')
  assert.equal(theme.bodyBackground, 'rgb(7, 20, 38)')
  assert.equal(theme.headerBackground, 'rgb(8, 26, 48)')

  await page.getByRole('button', { name: '发送消息' }).click()
  const messageDialog = page.locator('.person-command-dialog')
  await messageDialog.waitFor({ state: 'visible' })
  theme.dialogBackground = await messageDialog.evaluate((element) => getComputedStyle(element).backgroundColor)
  assert.equal(theme.dialogBackground, 'rgb(8, 26, 48)')
  await messageDialog.getByRole('button', { name: '取消' }).click()

  await page.screenshot({ path: SCREENSHOT_PATH, fullPage: false })
  console.log(JSON.stringify({ status: 'passed', screenshot: SCREENSHOT_PATH, theme }))
} finally {
  await browser.close()
}
