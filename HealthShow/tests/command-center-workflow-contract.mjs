import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const source = (relativePath) => readFileSync(resolve(__dirname, '..', relativePath), 'utf8')

test('dashboard and safety-command reuse the same incident drawer with timestamp-safe context', () => {
  const dashboard = source('src/views/health-monitor/dashboard/index.vue')
  const safetyCommand = source('src/views/safety-command/index.vue')
  const drawer = source('src/views/safety-command/components/IncidentCommandDrawer.vue')
  const workflow = source('src/views/health-monitor/dashboard/dashboard-command-workflow.js')

  assert.match(dashboard, /<IncidentCommandDrawer/)
  assert.match(dashboard, /source-page="dashboard"/)
  assert.match(workflow, /openCommandIncident\(event\)/)
  assert.match(workflow, /occurredAt: query\.occurredAt/)
  assert.match(safetyCommand, /source-page="safety-command"/)
  assert.match(safetyCommand, /return-available="route\.query\.from === 'dashboard'"/)
  assert.match(drawer, /getCommandCenterIncident\(props\.event\.id, props\.event\.occurredAt\)/)
  assert.match(drawer, /getCommandCenterIncidentTimeline\(props\.event\.id, props\.event\.occurredAt\)/)
})

test('operational queues use persisted review and device states', () => {
  const dashboard = source('src/views/health-monitor/dashboard/index.vue') +
    source('src/views/health-monitor/dashboard/use-dashboard-page.ts')
  const workflow = source('src/views/health-monitor/dashboard/dashboard-command-workflow.js')
  const mineEntry = source('src/views/health-monitor/mine-entry/index.vue')

  assert.match(dashboard, /buildDashboardAdmissionQueueItems/)
  assert.match(workflow, /label: '待复检'/)
  assert.match(workflow, /label: '复检超时'/)
  assert.match(workflow, /query: \{ status: 'review', from: 'dashboard' \}/)
  assert.match(workflow, /query: \{ status: 'overdue', from: 'dashboard' \}/)
  assert.doesNotMatch(dashboard, /设备例外队列/)
  assert.doesNotMatch(workflow, /buildDashboardDeviceExceptionItems/)
  assert.match(workflow, /label: '今日待办'/)
  assert.match(workflow, /label: '已超时事件'/)
  assert.match(workflow, /label: '未分派事件'/)
  assert.doesNotMatch(workflow, /label: '全量待办'/)
  assert.doesNotMatch(workflow, /label: '设备干预'/)
  assert.match(workflow, /query: \{ status: 'fail', from: 'dashboard' \}/)
  assert.match(mineEntry, /const filterStatus = ref\(normalizeMineEntryStatus\(route\.query\.status\)\)/)
  assert.match(mineEntry, /watch\(\(\) => route\.query\.status/)
  assert.match(mineEntry, /getPreShiftReviews/)
  assert.match(mineEntry, /applyReviewAction/)
})

test('the command pages retain same-path rollback switches', () => {
  const appRoutes = source('src/router/app-routes.mjs')
  const healthRoutes = source('src/router/health-monitor.mjs')

  assert.match(appRoutes, /VITE_SAFETY_COMMAND_V2 \?\? 'true'\) !== 'false'/)
  assert.match(appRoutes, /legacy-20260601\/index\.vue/)
  assert.match(healthRoutes, /VITE_UNIFIED_CONTROL_V2 \?\? 'true'\) !== 'false'/)
  assert.match(healthRoutes, /dashboard\/legacy-20260601\/index\.vue/)
})
