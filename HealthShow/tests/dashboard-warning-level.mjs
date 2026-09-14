import test from 'node:test'
import assert from 'node:assert/strict'

import {
  dashboardWarningLevelBadgeClass,
  dashboardWarningLevelDrawerClass,
  dashboardWarningLevelEventClass,
  dashboardWarningLevelLabel,
  dashboardWarningLevelMarkerColor,
  dashboardWarningLevelTagType
} from '../src/views/health-monitor/dashboard/dashboard-warning-level.js'

test('dashboard warning level helpers distinguish danger, warn and info', () => {
  assert.equal(dashboardWarningLevelLabel('danger'), '危险')
  assert.equal(dashboardWarningLevelLabel('warn'), '预警')
  assert.equal(dashboardWarningLevelLabel('info'), '提示')

  assert.equal(dashboardWarningLevelBadgeClass('danger'), 'badge-danger')
  assert.equal(dashboardWarningLevelBadgeClass('warn'), 'badge-warn')
  assert.equal(dashboardWarningLevelBadgeClass('info'), 'badge-info')

  assert.equal(dashboardWarningLevelEventClass('danger'), 'ev-danger')
  assert.equal(dashboardWarningLevelEventClass('warn'), 'ev-warn')
  assert.equal(dashboardWarningLevelEventClass('info'), 'ev-info')

  assert.equal(dashboardWarningLevelMarkerColor('danger'), '#ff3b3b')
  assert.equal(dashboardWarningLevelMarkerColor('warn'), '#ff8c00')
  assert.equal(dashboardWarningLevelMarkerColor('info'), '#00c8ff')

  assert.equal(dashboardWarningLevelTagType('danger'), 'danger')
  assert.equal(dashboardWarningLevelTagType('warn'), 'warning')
  assert.equal(dashboardWarningLevelTagType('info'), 'info')

  assert.equal(dashboardWarningLevelDrawerClass('danger'), 'lv-danger')
  assert.equal(dashboardWarningLevelDrawerClass('warn'), 'lv-warn')
  assert.equal(dashboardWarningLevelDrawerClass('info'), 'lv-info')
})
