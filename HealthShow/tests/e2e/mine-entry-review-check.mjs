import assert from 'node:assert/strict'
import { chromium } from 'playwright'

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:9528'
const browser = await chromium.launch({ headless: true })

try {
  const loginContext = await browser.newContext()
  const loginResponse = await loginContext.request.post(`${baseUrl}/dev-api/auth/login`, {
    data: { username: 'admin', password: 'admin123' }
  })
  const login = await loginResponse.json()
  assert.equal(login.code, 200, 'test login should succeed')
  assert.ok(login.data?.token, 'test login should return a token')
  await loginContext.close()

  const results = []
  for (const viewport of [{ name: 'desktop', width: 1440, height: 900 }, { name: 'mobile', width: 390, height: 844 }]) {
    const context = await browser.newContext({ viewport })
    await context.addCookies([
      { name: 'User-Token', value: login.data.token, url: baseUrl },
      { name: 'satoken', value: login.data.token, url: baseUrl },
      { name: 'Health-Data-Source', value: 'old', url: baseUrl }
    ])
    const page = await context.newPage()
    await page.addInitScript(() => window.localStorage.setItem('Health-Data-Source', 'old'))
    await page.goto(`${baseUrl}/#/health-monitor/mine-entry?status=review&from=dashboard`, {
      waitUntil: 'domcontentloaded'
    })

    const pendingKpi = page.locator('.me-kpi').filter({ hasText: '待复检' }).locator('.me-kpi-n')
    await pendingKpi.waitFor({ state: 'visible', timeout: 20000 })
    const reviewGroup = page.locator('.me-group-hd').filter({ hasText: '待复检' })
    await reviewGroup.waitFor({ state: 'visible', timeout: 20000 })
    await page.locator('.el-loading-mask').waitFor({ state: 'hidden', timeout: 20000 }).catch(() => {})
    await page.waitForTimeout(200)
    const visibleState = await page.evaluate(() => {
      const kpi = Array.from(document.querySelectorAll('.me-kpi'))
        .find((item) => item.textContent?.includes('待复检'))
      const group = Array.from(document.querySelectorAll('.me-group-hd'))
        .find((item) => item.textContent?.includes('待复检'))
      return {
        pending: Number.parseInt(kpi?.querySelector('.me-kpi-n')?.textContent || '0', 10),
        groupCount: Number.parseInt(group?.textContent?.match(/（(\d+) 人）/)?.[1] || '0', 10),
        cards: document.querySelectorAll('.me-card-fail').length
      }
    })
    const { pending, groupCount, cards } = visibleState
    assert.ok(pending > 0, `${viewport.name}: expected authoritative pending review count`)
    assert.ok(cards > 0, `${viewport.name}: pending reviews must render concrete review cards`)
    assert.equal(cards, groupCount, `${viewport.name}: review group count and rendered cards should agree`)
    assert.ok(cards >= pending, `${viewport.name}: review queue must not be truncated below the authoritative count`)

    const clickKpi = async (label, status) => {
      const kpi = page.locator('.me-kpi-button').filter({ hasText: label })
      await kpi.click()
      await page.waitForFunction(
        (expected) => new URLSearchParams(window.location.hash.split('?')[1] || '').get('status') === expected,
        status || null
      )
      assert.equal(await kpi.getAttribute('aria-pressed'), 'true', `${viewport.name}: ${label} should be selected`)
      const selected = await page.locator('.me-toolbar-select').nth(1).locator('.el-select__placeholder').textContent()
      assert.ok(selected?.includes(status === 'pass' ? '准入' : status === 'fail' ? '禁入' : status === 'review' ? '待复检' : '全部'))
    }
    await clickKpi('准入通过', 'pass')
    assert.ok(await page.locator('.el-table__body tbody tr').count() > 0, `${viewport.name}: pass filter should show rows`)
    await clickKpi('禁止入井', 'fail')
    assert.ok(await page.locator('.me-card-fail').count() > 0, `${viewport.name}: fail filter should show cards`)
    await clickKpi('待复检', 'review')
    assert.ok(await page.locator('.me-card-fail').count() > 0, `${viewport.name}: review filter should show cards`)
    await clickKpi('今日检测', '')
    assert.ok(await page.locator('.me-card-fail').count() > 0, `${viewport.name}: all filter should restore cards`)
    results.push({ viewport: viewport.name, pending, groupCount, cards })
    await context.close()
  }

  console.log(JSON.stringify({ status: 'passed', results }))
} finally {
  await browser.close()
}
