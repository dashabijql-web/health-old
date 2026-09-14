import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import {
  buildWarningLifecycleItem,
  buildWarningLifecycleView,
  formatDateTime,
  levelLabel,
  markWarningHandled,
  normalizeWarningLevel,
  warningLevelFilterLabel,
  warningHandledStatusLabel
} from '../src/views/alert-management/common/warning-lifecycle.js'

test('normalizeWarningLevel maps Chinese labels and numeric levels', () => {
  assert.equal(normalizeWarningLevel('高危'), 'danger')
  assert.equal(normalizeWarningLevel('危险'), 'danger')
  assert.equal(normalizeWarningLevel('中危'), 'warn')
  assert.equal(normalizeWarningLevel('预警'), 'warn')
  assert.equal(normalizeWarningLevel('提示'), 'info')
  assert.equal(normalizeWarningLevel(3), 'danger')
  assert.equal(normalizeWarningLevel(2), 'warn')
  assert.equal(normalizeWarningLevel(1), 'info')
})

test('warningLevelFilterLabel maps notification route levels to records filter labels', () => {
  assert.equal(warningLevelFilterLabel(3), '高危')
  assert.equal(warningLevelFilterLabel('danger'), '高危')
  assert.equal(warningLevelFilterLabel(2), '中危')
  assert.equal(warningLevelFilterLabel('warning'), '中危')
  assert.equal(warningLevelFilterLabel(1), '低危')
  assert.equal(warningLevelFilterLabel('info'), '低危')
  assert.equal(warningLevelFilterLabel('高危'), '高危')
  assert.equal(warningLevelFilterLabel('custom'), 'custom')
})

test('warningHandledStatusLabel keeps notifications and records pending wording aligned', () => {
  assert.equal(warningHandledStatusLabel({ handled: false }), '待处理')
  assert.equal(warningHandledStatusLabel({ isHandled: 0 }), '待处理')
  assert.equal(warningHandledStatusLabel({ handled: true }), '已处理')
  assert.equal(warningHandledStatusLabel({ isHandled: 1 }), '已处理')
})

test('alert management pages use shared pending handled wording', () => {
  const files = [
    'src/views/alert-management/notifications/index.vue',
    'src/views/alert-management/records/index.vue'
  ]

  for (const file of files) {
    const source = readFileSync(file, 'utf8')
    assert.doesNotMatch(source, /label="未处理"|['`]未处理['`]/, file)
  }
})

test('alert management records reuse shared display level labels', () => {
  const recordsSource = readFileSync('src/views/alert-management/records/index.vue', 'utf8')

  assert.match(recordsSource, /import \{[^}]*levelLabel[^}]*\} from '\.\.\/common\/warning-lifecycle'/)
  assert.doesNotMatch(recordsSource, /const levelLabel\s*=/)
})

test('alert records page exposes shared SLA clock fields in table, mobile card, and detail drawer', () => {
  const source = readFileSync('src/views/alert-management/records/index.vue', 'utf8')

  assert.match(source, /label="SLA"/)
  assert.match(source, /row\.slaClockLabel/)
  assert.match(source, /row\.slaClockText/)
  assert.match(source, /detailRow\.slaClockLabel/)
  assert.match(source, /detailRow\.slaClockText/)
  assert.match(source, /records-sla-clock/)
})

test('notification records jump preserves lifecycle filters as route query', () => {
  const notificationSource = readFileSync('src/views/alert-management/notifications/index.vue', 'utf8')
  const recordsSource = readFileSync('src/views/alert-management/records/index.vue', 'utf8')

  assert.match(notificationSource, /path:\s*'\/alert-management\/records'/)
  assert.match(notificationSource, /warningLevel:\s*filter\.level/)
  assert.match(notificationSource, /handleStatus:\s*filter\.handled\s*\?\s*'handled'\s*:\s*'unhandled'/)
  assert.match(notificationSource, /keyword:\s*filter\.userCode/)

  assert.match(recordsSource, /warningLevelFilterLabel/)
  assert.match(recordsSource, /searchForm\.warningLevel\s*=\s*warningLevelFilterLabel\(query\.warningLevel\)/)
  assert.match(recordsSource, /searchForm\.handleStatus\s*=\s*filters\.handleStatus\s*\|\|\s*''/)
  assert.match(recordsSource, /searchForm\.keyword\s*=\s*filters\.keyword\s*\|\|\s*filters\.userCode\s*\|\|\s*''/)
})

test('buildWarningLifecycleView derives deadline and overdue seconds from createTime', () => {
  const createTime = '2026-05-08T10:00:00+08:00'
  const now = Date.parse('2026-05-08T10:35:00+08:00')
  const view = buildWarningLifecycleView({ createTime }, { now, slaMinutes: 30, warningWindowMinutes: 10 })

  assert.equal(view.slaMinutes, 30)
  assert.equal(view.slaStatus, 'overdue')
  assert.equal(view.overdueSeconds, 300)
  assert.equal(view.remainingSeconds, 0)
  assert.equal(view.slaDeadline, formatDateTime(Date.parse(createTime) + 30 * 60 * 1000))
})

test('buildWarningLifecycleView marks handled warnings as handled instead of overdue', () => {
  const createTime = '2026-05-08T10:00:00+08:00'
  const now = Date.parse('2026-05-08T11:00:00+08:00')
  const view = buildWarningLifecycleView({ createTime, handled: true }, { now, slaMinutes: 30, warningWindowMinutes: 10 })

  assert.equal(view.slaStatus, 'handled')
  assert.equal(view.slaStatusText, '已处理')
  assert.equal(view.remainingSeconds, 0)
  assert.equal(view.overdueSeconds, 0)
})

test('buildWarningLifecycleView marks near-deadline warnings as warning', () => {
  const createTime = '2026-05-08T10:00:00Z'
  const now = Date.parse('2026-05-08T10:22:00Z')
  const view = buildWarningLifecycleView({ createTime }, { now, slaMinutes: 30, warningWindowMinutes: 10 })

  assert.equal(view.slaStatus, 'warning')
  assert.equal(view.remainingSeconds, 480)
  assert.equal(view.overdueSeconds, 0)
})

test('buildWarningLifecycleView exposes operator-facing SLA clock text', () => {
  const createTime = '2026-05-08T10:00:00Z'

  const nearDeadline = buildWarningLifecycleView(
    { createTime },
    { now: Date.parse('2026-05-08T10:22:00Z'), slaMinutes: 30, warningWindowMinutes: 10 }
  )
  assert.equal(nearDeadline.slaClockLabel, '剩余')
  assert.equal(nearDeadline.slaClockText, '8分钟')

  const overdue = buildWarningLifecycleView(
    { createTime },
    { now: Date.parse('2026-05-08T10:35:00Z'), slaMinutes: 30, warningWindowMinutes: 10 }
  )
  assert.equal(overdue.slaClockLabel, '超时')
  assert.equal(overdue.slaClockText, '5分钟')
})

test('buildWarningLifecycleItem normalizes isHandled and level display fields', () => {
  const createTime = '2026-05-08T10:00:00+08:00'
  const now = Date.parse('2026-05-08T11:00:00+08:00')
  const item = buildWarningLifecycleItem({ warningLevel: '高危', createTime, isHandled: 1 }, { now, slaMinutes: 30 })

  assert.equal(item.handled, true)
  assert.equal(item.warningLevelClass, 'badge-danger')
  assert.equal(item.warningLevelLabel, '危险')
  assert.equal(levelLabel('高危'), '危险')
  assert.equal(levelLabel('中危'), '预警')
  assert.equal(levelLabel('低危'), '提示')
  assert.equal(item.slaStatus, 'handled')
  assert.equal(item.slaStatusText, '已处理')
})

test('markWarningHandled refreshes stale overdue SLA fields after local handling', () => {
  const item = {
    id: 1,
    handled: false,
    isHandled: 0,
    slaStatus: 'overdue',
    slaStatusText: '已超时',
    overdueSeconds: 600,
    remainingSeconds: 0
  }

  const result = markWarningHandled(item)

  assert.equal(result, item)
  assert.equal(item.handled, true)
  assert.equal(item.isHandled, true)
  assert.equal(item.slaStatus, 'handled')
  assert.equal(item.slaStatusText, '已处理')
  assert.equal(item.overdueSeconds, 0)
  assert.equal(item.remainingSeconds, 0)
})
