import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

import {
  buildDispatchActionItems,
  buildWarningTypeData,
  getFocusWarningEvents,
  getLatestDangerEvent
} from '../src/views/health-monitor/dashboard/dashboard-summary.js'
import {
  buildDashboardDeviceCards,
  buildDashboardHeaderKpis,
  buildDashboardHealthExceptionCards,
  buildDashboardMetricCards,
  buildDashboardVitalCards
} from '../src/views/health-monitor/dashboard/dashboard-view-model.js'

const __dirname = dirname(fileURLToPath(import.meta.url))
const src = (relativePath) => readFileSync(resolve(__dirname, '..', relativePath), 'utf8')

test('getLatestDangerEvent returns only unhandled danger events', () => {
  const infoOnly = [
    { id: 1, level: 'info', handled: false },
    { id: 2, level: 'warn', handled: false }
  ]
  assert.equal(getLatestDangerEvent(infoOnly), null)

  const handledDanger = [
    { id: 3, level: 'danger', handled: true },
    { id: 4, level: 'warn', handled: false }
  ]
  assert.equal(getLatestDangerEvent(handledDanger), null)

  const danger = { id: 5, level: 'danger', handled: false }
  assert.equal(getLatestDangerEvent([{ id: 6, level: 'info', handled: false }, danger]), danger)
})

test('buildWarningTypeData falls back to warning event types', () => {
  const result = buildWarningTypeData({
    warningTypesData: [],
    warningEvents: [
      { type: '心率异常' },
      { type: '心率异常' },
      { type: '血氧偏低' }
    ]
  })

  assert.deepEqual(result.map((item) => [item.name, item.value, item.pct]), [
    ['心率异常', 2, 67],
    ['血氧偏低', 1, 33]
  ])
})

test('dashboard action counts keep strict incident semantics and deduplicate people', () => {
  const warningEvents = [
    { id: 1, userCode: 'EMP001', level: 'danger', handled: false },
    { id: 2, userCode: 'EMP001', level: 'warn', handled: false },
    { id: 3, userCode: 'EMP002', level: 'warn', handled: false }
  ]
  const actions = buildDispatchActionItems({
    warningEvents,
    focusWarningEvents: getFocusWarningEvents(warningEvents),
    kpiUnhandledHigh: 1,
    periodLabel: '近30日',
    preShiftData: { failedCount: 0 }
  })

  assert.equal(actions[0].value, '1 条')
  assert.equal(getFocusWarningEvents(warningEvents).length, 2)
})

test('dashboard header and device cards avoid invented health and device counts', () => {
  const header = buildDashboardHeaderKpis({
    kpiRealtimeOnline: 8,
    kpiRealtimeTotal: 10,
    kpiTodayWarnings: 3,
    kpiYesterdayWarnings: 2,
    kpiUnhandledHigh: 1,
    kpiUnhandledMid: 1,
    warningEvents: [],
    personCounts: { heartRate: 8 },
    deviceActivationRate: 92,
    preShiftData: { preShiftRate: 95, qualifiedCount: 19, totalToday: 20 },
    periodLabel: '当日'
  })
  const devices = buildDashboardDeviceCards({
    deviceStats: { total: 100, boundDevices: 100, warningRate: 17 },
    deviceOnline: 90,
    deviceOffline: 10,
    lowBatteryCount: null,
    dataInterrupted: { status: 'UNAVAILABLE', value: null },
    faulted: { status: 'UNAVAILABLE', value: null }
  })

  assert.equal(header.some((item) => item.label === '健康达标'), false)
  assert.equal(header.find((item) => item.label === '当前在线').val, 8)
  assert.equal(header.find((item) => item.label === '当日预警').val, 3)
  assert.equal(header.find((item) => item.label === '当日预警').sub, '↑50% 较昨2件')
  assert.equal(header.some((item) => item.label === '新增预警'), false)
  const monthlyHeader = buildDashboardHeaderKpis({
    kpiRealtimeOnline: 8,
    kpiRealtimeTotal: 10,
    kpiTodayWarnings: 300,
    kpiYesterdayWarnings: 2,
    kpiUnhandledHigh: 1,
    kpiUnhandledMid: 1,
    warningEvents: [],
    personCounts: {},
    deviceActivationRate: 92,
    preShiftData: {},
    periodLabel: '近30日'
  })
  assert.equal(monthlyHeader.find((item) => item.label === '近30日预警').val, 300)
  assert.equal(monthlyHeader.find((item) => item.label === '近30日预警').sub, '近30日累计')
  assert.equal(header.some((item) => item.label === '监测覆盖'), false)
  assert.equal(header.find((item) => item.label === '设备激活').val, '92%')
  assert.equal(devices.find((item) => item.label === '低电设备').val, '--')
  assert.equal(devices.find((item) => item.label === '数据中断').val, '--')
  assert.equal(devices.find((item) => item.label === '故障设备').val, '--')
})

test('dashboard command panels prioritize actionable exceptions', () => {
  const viewSource = src('src/views/health-monitor/dashboard/index.vue')
  const viewModelSource = src('src/views/health-monitor/dashboard/dashboard-view-model.js')
  const devicePanelSource = src('src/views/health-monitor/dashboard/components/DashboardDevicePanel.vue')
  const styleSource = src('src/views/health-monitor/dashboard/dashboard.scss')
  const vitalCards = buildDashboardVitalCards({
    bodyIndicators: {
      avgHeartRate: 82,
      avgBloodOxygen: 96,
      avgPressure: 64,
      avgTemperature: 36.8,
      avgBloodPressureHigh: 128,
      avgBloodPressureLow: 82
    }
  })
  const exceptions = buildDashboardHealthExceptionCards({
    vitalCards,
    warningEvents: [
      { type: '心率异常', userCode: 'EMP001', handled: false },
      { type: '心率异常', userCode: 'EMP001', handled: false },
      { type: '血氧偏低', userCode: 'EMP002', handled: false },
      { type: '血压偏高', userCode: 'EMP003', handled: false },
      { type: '舒张压偏高', userCode: 'EMP004', handled: false }
    ]
  })

  assert.deepEqual(
    exceptions.filter((item) => item.exceptionCount > 0).map(({ metricKey, exceptionCount }) => [metricKey, exceptionCount]),
    [['heartRate', 1], ['bloodOxygen', 1], ['bloodPressureHigh', 1], ['bloodPressureLow', 1]]
  )
  const realtimeExceptions = buildDashboardHealthExceptionCards({
    vitalCards,
    warningEvents: [],
    healthSnapshot: {
      metrics: [
        {
          key: 'heartRate', label: '心率', unit: 'bpm', average: 78.5,
          minimum: 52, maximum: 128, p95: 112, coveredUsers: 20,
          abnormalUsers: 3, abnormalRate: 15
        },
        {
          key: 'bloodPressureHigh', label: '高压', unit: 'mmHg', average: 124,
          minimum: 92, maximum: 181, p95: 145, coveredUsers: 18,
          abnormalUsers: 2, abnormalRate: 11.1
        },
        {
          key: 'bloodPressureLow', label: '低压', unit: 'mmHg', average: 78,
          minimum: 58, maximum: 96, p95: 90, coveredUsers: 18,
          abnormalUsers: 1, abnormalRate: 5.6
        }
      ]
    }
  })
  const realtimeHeartRate = realtimeExceptions.find(item => item.metricKey === 'heartRate')
  assert.equal(realtimeHeartRate.val, '3/20')
  assert.equal(realtimeHeartRate.unit, '异常/覆盖')
  assert.equal(realtimeHeartRate.tag, '群体均值 78.5bpm')
  assert.match(realtimeHeartRate.exceptionText, /范围 52-128bpm · 覆盖 20 人/)
  const realtimeSystolic = realtimeExceptions.find(item => item.metricKey === 'bloodPressureHigh')
  assert.equal(realtimeSystolic.val, '2/18')
  assert.equal(realtimeSystolic.unit, '异常/覆盖')
  assert.equal(realtimeSystolic.tag, '群体均值 124mmHg')
  const realtimeDiastolic = realtimeExceptions.find(item => item.metricKey === 'bloodPressureLow')
  assert.equal(realtimeDiastolic.val, '1/18')
  assert.equal(realtimeDiastolic.unit, '异常/覆盖')
  assert.equal(realtimeDiastolic.tag, '群体均值 78mmHg')
  assert.doesNotMatch(viewSource, /监测覆盖与数据质量|全项覆盖/)
  assert.doesNotMatch(viewSource, /warningEvents\.filter\(e=>e\.handled\)\.length/)
  assert.match(viewSource, /健康异常快照/)
  assert.match(viewSource, /v\.exceptionText/)
  assert.doesNotMatch(viewSource, /近30日检测人数/)
  assert.match(viewModelSource, /filter\(event => !event\.handled && warningEventMatchesMetric\(event, metricKey\)\)/)
  assert.match(devicePanelSource, /设备运行概览/)
  assert.doesNotMatch(viewSource, /设备例外队列/)
  assert.match(styleSource, /\.unified-control-page \.uc-workspace[\s\S]*grid-template-columns:/)
  assert.match(styleSource, /\.unified-control-page \.uc-device-summary[\s\S]*grid-template-columns: repeat\(3, 1fr\)/)
  assert.match(styleSource, /\.unified-control-page \.uc-device-table[\s\S]*grid-template-columns: 1fr 42px 32px/)
  assert.match(styleSource, /\.unified-control-page \.uc-exception-grid[\s\S]*grid-template-columns:/)
  assert.match(styleSource, /\.unified-control-page \.uc-focus-row[\s\S]*grid-template-columns:/)
  assert.match(styleSource, /@media \(max-width: 780px\)[\s\S]*\.unified-control-page \.uc-workspace[\s\S]*flex-direction: column/)
  assert.match(viewSource, /uc-devices-panel/)
  const dispatchPanelSource = src('src/views/health-monitor/dashboard/components/DashboardDispatchPanel.vue')
  assert.match(viewSource, /uc-live-panel/)
  assert.match(viewSource, /uc-focus-panel/)
  assert.doesNotMatch(viewSource, /值班决策面板/)
  assert.doesNotMatch(dispatchPanelSource, /待处理闭环队列/)
  assert.doesNotMatch(dispatchPanelSource, /dm-dispatch-queue-list/)
  assert.match(dispatchPanelSource, /\.dm-dispatch-focus-list[\s\S]*grid-template-columns: 1fr/)
  assert.doesNotMatch(dispatchPanelSource, /<section class="dm-dispatch-assist">/)
  assert.match(styleSource, /\.unified-control-page \.uc-right-rail[\s\S]*display: grid/)
  assert.match(styleSource, /\.unified-control-page \.uc-right-rail[\s\S]*display: flex/)
})
