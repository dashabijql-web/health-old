import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const source = (relativePath) => readFileSync(resolve(__dirname, '..', relativePath), 'utf8')

test('unified control person search opens the shared employee health profile directly', () => {
  const dashboard = source('src/views/health-monitor/dashboard/index.vue')
  const search = source('src/views/health-monitor/dashboard/components/DashboardPersonSearch.vue')
  const actions = source('src/views/health-monitor/dashboard/dashboard-view-actions.js')
  const state = source('src/views/health-monitor/dashboard/dashboard-page-state.js')

  assert.match(dashboard, /<DashboardPersonSearch @select="goToEmployeeProfile"/)
  assert.doesNotMatch(dashboard, /<PersonDetailDrawer/)
  assert.doesNotMatch(dashboard, /import PersonDetailDrawer/)
  assert.match(search, /姓名、工号、手机号或 IMEI/)
  assert.match(search, /选中后进入完整健康画像/)
  assert.match(search, /searchEmployeesForCommand/)
  assert.match(actions, /goToEmployeeProfile\(item\)/)
  assert.match(actions, /path: '\/health-monitor\/employee-profile'/)
  assert.match(actions, /from: this\.\$route\.fullPath/)
  assert.doesNotMatch(state, /personCommandDrawer/)
})

test('person drawer keeps direct communication separate from incident-bound emergency actions', () => {
  const drawer = source('src/views/safety-command/components/PersonDetailDrawer.vue')

  assert.match(drawer, /sendWatchMessage\(resolvedImei\.value, text\)/)
  assert.match(drawer, /sendVoiceMessage\(resolvedImei\.value, voiceTemplateId\.value\)/)
  assert.match(drawer, /近期体征记录/)
  assert.match(drawer, /healthRecords\.value = records\.slice\(0, 8\)/)
  assert.match(drawer, /应急动作必须关联该人员的具体预警事件/)
  assert.match(drawer, /SOS由手表端主动发起，不能在管理端伪造/)
  assert.match(drawer, /occurredAt: warning\.occurredAt \|\| warning\.createTime \|\| warning\.time/)
})

test('employee profile opens the same command and incident drawers', () => {
  const profile = source('src/views/health-monitor/employee-profile/index.vue')
  const page = source('src/views/health-monitor/employee-profile/use-employee-profile-page.js')

  assert.match(profile, /联系与处置/)
  assert.match(profile, /<EmployeeProfileCommandLayer/)
  const layer = source('src/views/health-monitor/employee-profile/components/EmployeeProfileCommandLayer.vue')
  assert.match(layer, /<PersonDetailDrawer/)
  assert.match(layer, /<IncidentCommandDrawer/)
  assert.match(page, /function openEmergency\(event\)/)
  assert.match(page, /incidentDrawerVisible\.value = true/)
})
