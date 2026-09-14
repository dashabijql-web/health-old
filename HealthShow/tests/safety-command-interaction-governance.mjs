import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const source = (relativePath) => readFileSync(resolve(__dirname, '..', relativePath), 'utf8')

test('safety command statistics drive filters or records instead of generic info dialogs', () => {
  const page = source('src/views/safety-command/index.vue')

  assert.match(page, /applyEventFilter/)
  assert.match(page, /filteredEvents/)
  assert.match(page, /path: '\/alert-management\/records'/)
  assert.match(page, /action\.key === 'today-new'/)
  assert.doesNotMatch(page, /showInfoDialog|<SafetyCommandDialogs/)
  assert.match(page, /item\.key === 'freshness'/)
})

test('unconfigured global actions are disabled and incident-scoped actions remain in the incident drawer', () => {
  const page = source('src/views/safety-command/index.vue')
  const incidentDrawer = source('src/views/safety-command/components/IncidentCommandDrawer.vue')
  const riskPanel = source('src/views/safety-command/components/RiskPersonPanel.vue')

  assert.match(page, /呼叫 · 未配置/)
  assert.match(page, /广播 · 未配置/)
  assert.match(page, /撤离 · 未配置/)
  assert.doesNotMatch(riskPanel, /全部呼叫|callAll/)
  assert.match(incidentDrawer, /runExternal\('call'\)/)
  assert.match(incidentDrawer, /runExternal\('broadcast'\)/)
  assert.match(incidentDrawer, /runExternal\('evacuate'\)/)
})

test('department nodes open a scoped incident drawer without health-rate filler', () => {
  const page = source('src/views/safety-command/index.vue')
  const drawer = source('src/views/safety-command/components/DepartmentIncidentDrawer.vue')

  assert.match(page, /<DepartmentIncidentDrawer/)
  assert.match(drawer, /部门预警来自今日统计/)
  assert.match(drawer, /当前已加载未闭环预警/)
  assert.match(drawer, /未分派/)
  assert.match(drawer, /已超时/)
  assert.match(drawer, /show-event/)
  assert.match(drawer, /handle-event/)
  assert.match(drawer, /show-profile/)
  assert.match(drawer, /健康画像/)
  assert.match(page, /path: '\/health-monitor\/employee-profile'/)
  assert.match(page, /empCode: event\.userCode/)
  assert.match(page, /queuedDepartmentEvent/)
  assert.match(page, /window\.setTimeout/)
  assert.match(page, /incidentDrawerVisible,/)
  assert.match(source('src/views/safety-command/components/SafetyCommandSupportGrid.vue'), /:title="eventListTitle"/)
  assert.doesNotMatch(drawer, /在线\/总数|健康率|AI 部门健康分析/)
  assert.match(drawer, /\.department-incident-drawer\.el-drawer/)
  assert.match(drawer, /\.department-incident-drawer \.el-drawer__header/)
  assert.match(drawer, /\.department-incident-drawer \.el-drawer__body/)
  assert.match(drawer, /background: #071426/)
})
