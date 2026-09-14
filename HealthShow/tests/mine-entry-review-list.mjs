import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const page = fs.readFileSync(
  new URL('../src/views/health-monitor/mine-entry/index.vue', import.meta.url),
  'utf8'
)

test('待复检和复检超时筛选会渲染禁入人员列表', () => {
  assert.match(
    page,
    /failList\.length\s*&&\s*\['','fail','review','overdue'\]\.includes\(filterStatus\)/
  )
  assert.match(page, /filterStatus\.value === 'review'\s*\? '待复检'/)
  assert.match(page, /filterStatus\.value === 'overdue'\s*\? '复检超时'/)
  assert.match(page, /getMineEntryList\(5000\)/)
})

test('顶部数量可以筛选名单并同步 URL', () => {
  for (const status of ['', 'pass', 'fail', 'review', 'overdue']) {
    assert.match(page, new RegExp(`@click="setStatusFilter\\('${status}'\\)"`))
  }
  assert.match(page, /class="me-kpi me-kpi-button"/)
  assert.match(page, /router\.replace\(\{ query \}\)/)
})
