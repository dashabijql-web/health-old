import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const source = (relativePath) => readFileSync(resolve(__dirname, '..', relativePath), 'utf8')

test('command pages use the shared incident API rather than independent warning lists', () => {
  const safetyRuntime = source('src/views/safety-command/safety-command-runtime.js')
  const dashboardRuntime = source('src/views/health-monitor/dashboard/dashboard-runtime-data.js')

  assert.match(safetyRuntime, /getCommandCenterIncidents\(\{ scope: 'today', status: 'OPEN'/)
  assert.match(dashboardRuntime, /getCommandCenterIncidents\(\{[\s\S]*scope: 'today',[\s\S]*status: 'ALL'[\s\S]*size: 50/)
  assert.doesNotMatch(dashboardRuntime, /size: 200/)
  assert.match(dashboardRuntime, /res\.data\.items\.map\(mapDashboardWarningEvent\)/)
})

test('dashboard command totals come from the server summary instead of the first incident page', () => {
  const api = source('src/api/command-center.js')
  const runtime = source('src/views/health-monitor/dashboard/dashboard-runtime.js')
  const workflow = source('src/views/health-monitor/dashboard/dashboard-command-workflow.js')

  assert.match(api, /url: '\/command-center\/dashboard-summary'/)
  assert.match(api, /getCommandCenterDashboardSummary\(params\)/)
  assert.match(runtime, /fetchCommandCenterDashboardSummary/)
  assert.match(runtime, /summary\.warning\?\.periodNew/)
  assert.match(runtime, /summary\.warning\?\.criticalPending/)
  assert.match(workflow, /warningSummary\?\.pendingTotal/)
  assert.match(workflow, /warningSummary\?\.unassignedTotal/)
  assert.match(workflow, /warningSummary\?\.overdueTotal/)
  assert.doesNotMatch(workflow, /warningEvents\.filter/)
})

test('incident resolution uses the timestamp-safe locator and reloads server state', () => {
  const safetyDialog = source('src/views/safety-command/components/EventHandleDialog.vue')
  const dashboardDetail = source('src/views/health-monitor/dashboard/dashboard-detail-methods.js')

  assert.match(safetyDialog, /resolveCommandCenterIncident\(props\.event\.id/)
  assert.match(safetyDialog, /occurredAt: props\.event\.occurredAt/)
  assert.doesNotMatch(safetyDialog, /handleBy:\s*form\.handleType/)
  assert.match(dashboardDetail, /resolveCommandCenterIncident\(event\.id/)
  assert.match(dashboardDetail, /await this\.fetchWarningEvents\(\)/)
})

test('dashboard keeps the incident locator while linking to command-center detail', () => {
  const actions = source('src/views/health-monitor/dashboard/dashboard-view-actions.js')
  const safetyPage = source('src/views/safety-command/index.vue')

  assert.match(actions, /warningId: String\(event\.id\)/)
  assert.match(actions, /occurredAt: event\.occurredAt/)
  assert.match(safetyPage, /getCommandCenterIncident\(warningId, occurredAt\)/)
})

test('incident detail keeps unavailable operational data explicit', () => {
  const viewModel = source('src/views/safety-command/safety-command-view-model.js')
  const dialogs = source('src/views/safety-command/components/SafetyCommandDialogs.vue')

  assert.match(viewModel, /\? '未接入定位'/)
  assert.match(viewModel, /record\.owner\?\.status === 'UNASSIGNED' \? '未分派'/)
  assert.match(viewModel, /record\.sla\?\.configured \? record\.sla\.deadlineAt : '未配置'/)
  assert.match(dialogs, /责任人/)
  assert.match(dialogs, /处置时限/)
})
