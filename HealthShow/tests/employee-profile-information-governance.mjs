import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const source = (relativePath) => readFileSync(resolve(__dirname, '..', relativePath), 'utf8')

test('employee profile keeps only factual health and action surfaces', () => {
  const page = source('src/views/health-monitor/employee-profile/index.vue')

  assert.doesNotMatch(page, /HeartRateWave|实时心电图|ep-miner|miner-worker|健康风险评估|riskItems/)
  assert.match(page, /当前体征/)
  assert.doesNotMatch(page, /7日体征趋势/)
  assert.doesNotMatch(page, /近期预警轨迹/)
  assert.match(page, /联系与处置/)
  assert.match(page, /<EmployeeProfileCommandLayer/)
  assert.match(page, /每项取最近一次非空读数/)
  assert.match(page, /heartRateTime/)
})

test('employee profile keeps one actionable warning summary without duplicate status cards', () => {
  const page = source('src/views/health-monitor/employee-profile/index.vue')
  const styles = source('src/views/health-monitor/employee-profile/employee-profile.scss')
  const viewModel = source('src/views/health-monitor/employee-profile/employee-profile-view-model.js')

  assert.match(page, /@select="openWarningDetails"/)
  assert.match(page, /近30日预警明细/)
  assert.match(page, /查看全部预警/)
  assert.match(page, /openWarningIncident\(\{ \.\.\.warning, occurredAt:/)
  assert.match(styles, /:global\(\.ep-warning-dialog\.el-dialog\)/)
  assert.match(styles, /\.ep-warning-dialog \.el-dialog__header/)
  assert.match(styles, /\.ep-warning-dialog \.el-dialog__body/)
  assert.match(styles, /\.ep-warning-dialog \.el-dialog__footer/)
  assert.match(viewModel, /label: '预警闭环'/)
  assert.match(viewModel, /clickable: true/)
  assert.doesNotMatch(viewModel, /label: '实时在线'|label: '建议动作'/)
})

test('employee contact action opens the shared drawer in focused contact mode', () => {
  const layer = source('src/views/health-monitor/employee-profile/components/EmployeeProfileCommandLayer.vue')
  const drawer = source('src/views/safety-command/components/PersonDetailDrawer.vue')

  assert.match(layer, /mode="contact"/)
  assert.match(drawer, /isContactMode\.value \? '联系与处置' : '人员综合管控'/)
  assert.match(drawer, /v-if="!isContactMode" class="vital-grid"/)
  assert.match(drawer, /v-if="!isContactMode" @click="openProfile"/)
  assert.match(drawer, /发送消息/)
  assert.match(drawer, /语音播报/)
  assert.match(drawer, /应急处置/)
})

test('employee profile uses backend freshness and authoritative warning totals', () => {
  const runtime = source('src/views/health-monitor/employee-profile/employee-profile-runtime.js')
  const composable = source('src/views/health-monitor/employee-profile/use-employee-profile-page.js')

  assert.match(runtime, /data\.vitals\?\.online/)
  assert.match(runtime, /data\.vitals\?\.recordTime/)
  assert.doesNotMatch(runtime, /isOnline\.value = true/)
  assert.doesNotMatch(runtime, /lastUpdate\.value = new Date/)
  assert.match(composable, /warningTotal\.value = Number\(warnRes\.value\.data\.total\)/)
  assert.match(composable, /pendingTotal\.value = Number\(pendingRes\.value\.data\.total\)/)
  assert.match(composable, /warning7Total\.value = Number\(warn7Res\.value\.data\.total\)/)
  assert.doesNotMatch(composable, /warnings\.value\.length/)
})

test('employee profile history supports employee-scoped ranges, curves and server pagination', () => {
  const page = source('src/views/health-monitor/employee-profile/index.vue')
  const history = source('src/views/health-monitor/employee-profile/components/EmployeeHealthHistory.vue')
  const historyModel = source('src/views/health-monitor/employee-profile/employee-profile-history.ts')
  const composable = source('src/views/health-monitor/employee-profile/use-employee-profile-page.js')
  const api = source('src/api/health.js')

  assert.match(page, /<EmployeeHealthHistory/)
  assert.match(page, /:employee-code="empInfo\.empCode"/)
  assert.match(page, /:refresh-token="historyRefreshToken"/)
  assert.match(history, /type="daterange"/)
  assert.match(history, /查询/)
  assert.match(history, /refreshToken: \{ type: Number/)
  assert.match(history, /historyRequestInFlight/)
  assert.match(history, /activeMetrics = ref\(metrics\.map\(\(metric\) => metric\.key\)\)/)
  assert.doesNotMatch(history, /activeMetrics = ref\(\['heartRate', 'bloodOxygen'\]\)/)
  assert.match(history, /历史曲线/)
  assert.match(history, /明细记录/)
  assert.match(history, /getEmployeeHealthHistory\(params\)/)
  assert.match(history, /trend\.granularity === 'record' \? '原始记录'/)
  assert.match(history, /getHealthRecords\(\{[\s\S]*userCode: props\.employeeCode[\s\S]*startTime:[\s\S]*endTime:/)
  assert.match(history, /v-model:current-page="recordPage"/)
  assert.match(history, /历史数据查询范围不能超过365天/)
  assert.match(historyModel, /HISTORY_METRICS/)
  assert.match(historyModel, /trigger: 'axis'/)
  assert.match(historyModel, /axisPointer: \{[\s\S]*snap: true/)
  assert.match(historyModel, /points\.filter\(\(point\) => metrics\.some/)
  assert.match(historyModel, /filter\(\(item\) => item\.value !== null/)
  assert.match(api, /\/api\/health\/record\/history\/trend/)
  assert.match(composable, /historyRefreshToken\.value \+= 1/)
})

test('employee profile shows a live countdown aligned with its 30-second refresh', () => {
  const page = source('src/views/health-monitor/employee-profile/index.vue')
  const composable = source('src/views/health-monitor/employee-profile/use-employee-profile-page.js')

  assert.match(page, /距下次刷新 \{\{ nextRefreshSeconds \}\} 秒/)
  assert.match(composable, /const PROFILE_REFRESH_SECONDS = 30/)
  assert.match(composable, /nextRefreshSeconds\.value -= 1/)
  assert.match(composable, /nextRefreshSeconds\.value <= 0[\s\S]*refresh\(\)/)
  assert.match(composable, /useIntervalTask\([\s\S]*1000\)/)
})

test('employee profile rule guidance declares freshness, exposes actions and avoids the empty two-column layout', () => {
  const page = source('src/views/health-monitor/employee-profile/index.vue')
  const styles = source('src/views/health-monitor/employee-profile/employee-profile.scss')

  assert.match(page, /规则提示/)
  assert.match(page, /随画像每30秒刷新/)
  assert.match(page, /@click="openWarningDetails">查看预警/)
  assert.match(page, /@click="openPersonCommand">联系处置/)
  assert.match(page, /class="ep-support-grid"/)
  assert.match(styles, /\.ep-workspace \{[\s\S]*flex-direction: column/)
  assert.doesNotMatch(styles, /grid-template-columns: minmax\(0, 1\.7fr\) minmax\(290px, 0\.8fr\)/)
})
