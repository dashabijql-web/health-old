import { chromium } from 'playwright'

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:9528'
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const page = await context.newPage()

try {
  const login = await context.request.post(`${baseUrl}/dev-api/auth/login`, {
    data: { username: 'admin', password: 'admin123' }
  })
  const payload = await login.json()
  if (payload?.code !== 200 || !payload?.data?.token) throw new Error('login failed')
  await context.addCookies([
    { name: 'User-Token', value: payload.data.token, url: baseUrl },
    { name: 'satoken', value: payload.data.token, url: baseUrl },
    { name: 'Health-Data-Source', value: 'old', url: baseUrl }
  ])

  await page.goto(`${baseUrl}/#/health-monitor/employee-archive`, { waitUntil: 'domcontentloaded' })
  const card = page.locator('.ea-card').first()
  await card.waitFor({ state: 'visible', timeout: 15000 })
  await card.click()
  await page.locator('.ep-history-chart canvas').waitFor({ state: 'visible', timeout: 15000 })

  const summaryCards = page.locator('.ep-summary-strip .hm-metric-strip__item')
  if (await summaryCards.count() !== 1) throw new Error(`expected one profile summary card, found ${await summaryCards.count()}`)
  await summaryCards.first().click()
  const warningDialog = page.getByRole('dialog', { name: '近30日预警明细' })
  await warningDialog.waitFor({ state: 'visible', timeout: 5000 })
  if (await warningDialog.locator('.ep-warning-dialog-summary > div').count() !== 4) {
    throw new Error('warning detail summary is incomplete')
  }
  await warningDialog.getByRole('button', { name: '关闭', exact: true }).click()

  await page.getByRole('button', { name: '联系与处置', exact: true }).click()
  const contactDrawer = page.getByRole('dialog', { name: /联系与处置/ })
  await contactDrawer.waitFor({ state: 'visible', timeout: 5000 })
  if (await contactDrawer.getByText('实时心电图').count() > 0 || await contactDrawer.getByText('7天趋势').count() > 0) {
    throw new Error('employee contact drawer still contains duplicate health analysis')
  }
  for (const action of ['发送消息', '语音播报', '应急处置']) {
    await contactDrawer.getByRole('button', { name: action, exact: true }).waitFor({ state: 'visible' })
  }
  await contactDrawer.locator('.el-drawer__close-btn').click()

  const countdown = page.locator('.ep-refresh-countdown')
  await countdown.waitFor({ state: 'visible', timeout: 5000 })
  const readCountdown = async () => Number((await countdown.textContent())?.match(/(\d+)\s*秒/)?.[1])
  const countdownStart = await readCountdown()
  await page.waitForTimeout(1200)
  const countdownNext = await readCountdown()
  if (!Number.isFinite(countdownStart) || countdownNext >= countdownStart) {
    throw new Error(`refresh countdown did not decrease: ${countdownStart} -> ${countdownNext}`)
  }
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await page.waitForFunction(() => document.querySelector('.ep-refresh-countdown')?.textContent?.includes('30 秒'))

  const samples = await page.locator('.ep-history-summary strong').first().textContent()
  if (!samples || Number.parseInt(samples, 10) <= 0) throw new Error(`invalid sample total: ${samples}`)

  await page.getByRole('tab', { name: '明细记录' }).click()
  await page.locator('.ep-history-table tbody tr').first().waitFor({ state: 'visible', timeout: 10000 })
  const rows = await page.locator('.ep-history-table tbody tr').count()
  const totalText = await page.locator('.ep-history-summary strong').nth(2).textContent()
  if (rows <= 0 || !totalText || Number.parseInt(totalText, 10) <= 0) {
    throw new Error(`invalid history records: rows=${rows}, total=${totalText}`)
  }

  console.log(JSON.stringify({ status: 'passed', samples, rows, total: totalText }))
} finally {
  await browser.close()
}
