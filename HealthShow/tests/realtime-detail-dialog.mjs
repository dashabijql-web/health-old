import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import {
  formatRealtimeTime,
  getMetricClass,
  getRealtimeStatusLabel,
  normalizeRealtimeUsersResponse
} from '../src/views/health-monitor/real-time/realtime-helpers.js'

const detailDialogSource = readFileSync('src/views/health-monitor/real-time/components/RealtimeDetailDialog.vue', 'utf8')
const tableSource = readFileSync('src/views/health-monitor/real-time/components/RealtimeUserTable.vue', 'utf8')
const headerSource = readFileSync('src/views/health-monitor/real-time/components/RealtimeHeader.vue', 'utf8')
const runtimeSource = readFileSync('src/views/health-monitor/real-time/use-realtime-page.ts', 'utf8')
const indexSource = readFileSync('src/views/health-monitor/real-time/index.vue', 'utf8')

test('real-time page response keeps server totals, summary and stale state', () => {
  const normalized = normalizeRealtimeUsersResponse({
    list: [{ userCode: 'EMP001', status: 'warning' }],
    total: 1000,
    page: 2,
    size: 20,
    stale: true,
    refreshedAt: '2026-07-15T10:00:00',
    summary: { onlineCount: 1000, warningCount: 41, staleCount: 7, noDataCount: 3 },
    departments: ['综采一队'],
    warningPreview: [{ userCode: 'EMP001' }]
  })

  assert.equal(normalized.list.length, 1)
  assert.equal(normalized.total, 1000)
  assert.equal(normalized.summary.warningCount, 41)
  assert.equal(normalized.stale, true)
  assert.deepEqual(normalized.departments, ['综采一队'])
  assert.equal(normalized.warningPreview.length, 1)
})

test('real-time indicator rendering trusts backend evaluation state', () => {
  const user = {
    heartRate: 78,
    indicatorStates: { heartRate: 'danger' }
  }
  assert.equal(getMetricClass(user, 'heartRate'), 'c-danger')
  assert.equal(getRealtimeStatusLabel('no_data'), '待补')
  assert.equal(formatRealtimeTime('2026-07-14T03:30:12+08:00'), '07-14 03:30')
})

test('real-time page exposes reliable refresh and server-side paging controls', () => {
  assert.match(headerSource, /最后|更新|refreshLabel/, 'header should expose data refresh time')
  assert.doesNotMatch(headerSource, /ticker|跑马|实时预警/, 'duplicated warning ticker should be removed')
  assert.match(runtimeSource, /page: currentPage\.value/, 'runtime should request the active server page')
  assert.match(runtimeSource, /status: searchForm\.value\.status/, 'status filter should be sent to the server')
  assert.match(runtimeSource, /refreshError/, 'refresh failures must be visible')
  assert.match(indexSource, /onlineUsers\.total/, 'pagination and count should use the server total')
  assert.match(indexSource, /goToPage/, 'mobile and desktop should share explicit pagination')
})

test('real-time detail and mobile cards keep actionable context without low-value fields', () => {
  assert.match(detailDialogSource, /#footer/, 'detail dialog should expose an explicit footer action')
  assert.match(detailDialogSource, /type="primary" autofocus aria-label="关闭实时体征详情"/, 'close action should receive initial focus')
  assert.match(detailDialogSource, /语音提醒/, 'single-watch voice action should not be labeled broadcast')
  assert.doesNotMatch(detailDialogSource, /性别 \/ 年龄|卡路里/, 'low-value demographics and calories should not occupy realtime detail cards')
  assert.match(tableSource, /warningReasons\.slice\(0, 2\)/, 'mobile cards should show the actual warning reasons')
  assert.match(tableSource, /@keydown\.enter\.prevent="\$emit\('showUserDetail', row\)"/, 'mobile cards keep Enter detail access')
  assert.match(tableSource, /@keydown\.space\.prevent="\$emit\('showUserDetail', row\)"/, 'mobile cards keep Space detail access')
})
