import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'

const ROOT = process.cwd()
const read = (...parts) => fs.readFileSync(path.join(ROOT, ...parts), 'utf8')
const readBackend = (...parts) => fs.readFileSync(path.join(ROOT, '..', 'HealthData', ...parts), 'utf8')

test('metric realtime queries return each person latest row', () => {
  for (const mapper of ['PressureMapper.java', 'BloodPressureMapper.java', 'BloodOxygenMapper.java']) {
    const source = readBackend('src', 'main', 'java', 'com', 'xzkj', 'health', 'mapper', mapper)
    assert.match(source, /ROW_NUMBER\(\) OVER \(PARTITION BY hr\.user_code ORDER BY hr\.record_time DESC\)/)
    assert.match(source, /latest WHERE rn = 1/)
  }
})

test('blood oxygen metrics share the same abnormal threshold and person semantics', () => {
  const mapper = readBackend('src', 'main', 'java', 'com', 'xzkj', 'health', 'mapper', 'BloodOxygenMapper.java')
  const page = read('src', 'views', 'health-monitor', 'blood-oxygen', 'index.vue')
  assert.match(mapper, /COUNT\(\*\) \* 100 \/ NULLIF\(\(SELECT COUNT\(\*\) FROM employee\), 0\)/)
  assert.match(mapper, /WHERE hr\.blood_oxygen < 95/)
  assert.match(mapper, /COUNT\(DISTINCT CASE WHEN hr\.blood_oxygen < 95 THEN hr\.user_code END\) AS low_count/)
  assert.match(mapper, /COUNT\(DISTINCT hr\.user_code\) AS total_count/)
  assert.match(page, /异常人数/)
  assert.match(page, /覆盖人员/)
  assert.match(page, /独立实时快照/)
  assert.doesNotMatch(page, /label: '检测率'/)
  assert.doesNotMatch(page, /ref="hourlyRef"/)
})

test('metric pages use top ten rankings and period-person scope cards', () => {
  for (const pageName of ['pressure', 'blood-pressure', 'blood-oxygen']) {
    const source = read('src', 'views', 'health-monitor', pageName, 'index.vue') +
      read('src', 'views', 'health-monitor', pageName, `use-${pageName}-page.ts`)
    assert.match(source, /slice\(0, 10\)/)
    assert.match(source, /覆盖人员/)
    assert.match(source, /周期内有有效/)
    assert.match(source, /独立实时快照/)
    assert.doesNotMatch(source, /ref="gaugeRef"/)
  }
})

test('pressure distribution uses comparison bars without a duplicate donut chart', () => {
  const page = read('src', 'views', 'health-monitor', 'pressure', 'index.vue')
  assert.doesNotMatch(page, /ref="distRef"/)
  assert.match(page, /ps-dist-bar-wrap/)
})

test('shared metric thresholds and mobile layout preserve action-first semantics', () => {
  const thresholds = read('src', 'constants', 'health-thresholds.js')
  const layout = read('src', 'styles', '_hm-layout.scss')
  const loader = read('src', 'views', 'health-monitor', 'metric-page', 'metric-data-loader.js')
  assert.match(thresholds, /RELAXED: 50, ELEVATED: 70, HIGH: 85/)
  assert.match(loader, /limit = 10/)
  assert.match(loader, /seen\.has\(key\)/)
  assert.match(layout, /-panel-anomaly \{ order: 1; min-height: 300px; \}/)
  assert.match(layout, /-panel-trend,[\s\S]*height: 220px;/)
})

test('dashboard heart-rate drilldown preserves period and realtime anomaly context', () => {
  const dashboard = read('src', 'views', 'health-monitor', 'dashboard', 'index.vue')
  const dashboardModel = read('src', 'views', 'health-monitor', 'dashboard', 'dashboard-view-model.js')
  const heartRatePage = read('src', 'views', 'health-monitor', 'heart-rate', 'index.vue')
  const heartRateRuntime = heartRatePage + read('src', 'views', 'health-monitor', 'heart-rate', 'use-heart-rate-page.ts')
  const realtimeApi = read('src', 'api', 'realtime.js')
  const realtimeController = readBackend('src', 'main', 'java', 'com', 'xzkj', 'health', 'controller', 'RealtimeController.java')
  const realtimeService = readBackend('src', 'main', 'java', 'com', 'xzkj', 'health', 'service', 'RealtimeService.java')

  assert.match(dashboardModel, /path: '\/health-monitor\/heart-rate',[\s\S]*period: 'day', focus: 'current-anomaly'/)
  assert.match(dashboard, /uc-overview-kpi/)
  assert.match(heartRateRuntime, /query\.focus === 'current-anomaly'/)
  assert.match(heartRateRuntime, /status: 'warning',[\s\S]*indicator: 'heartRate'/)
  assert.match(heartRateRuntime, /indicatorStates\?\.heartRate/)
  assert.match(heartRateRuntime, /scrollIntoView\(\{ behavior: 'smooth', block: 'start' \}\)/)
  assert.match(heartRatePage, /独立实时快照/)
  assert.match(realtimeApi, /indicator: params\.indicator \|\| undefined/)
  assert.match(realtimeController, /@RequestParam\(required = false\) String indicator/)
  assert.match(realtimeService, /isAbnormalState\(user\.indicatorStates\(\)\.get\(indicator\)\)/)
})

test('heart-rate period view prioritizes abnormal people and coverage over population averages', () => {
  const page = read('src', 'views', 'health-monitor', 'heart-rate', 'index.vue')
  const runtime = page + read('src', 'views', 'health-monitor', 'heart-rate', 'use-heart-rate-page.ts')
  const chart = read('src', 'views', 'health-monitor', 'heart-rate', 'heart-rate-chart.js')
  const api = read('src', 'api', 'heart-rate.js')
  const mapper = readBackend('src', 'main', 'java', 'com', 'xzkj', 'health', 'mapper', 'HeartRateMapper.java')
  const controller = readBackend('src', 'main', 'java', 'com', 'xzkj', 'health', 'controller', 'HeartRateController.java')

  assert.doesNotMatch(runtime, /label: '平均心率'/)
  assert.doesNotMatch(runtime, /getHeartRateTrend/)
  assert.match(runtime, /近30日心率异常风险/)
  assert.match(runtime, /当日心率异常风险/)
  assert.match(page, /有数据日/)
  assert.match(page, /日均覆盖/)
  assert.match(page, /coverageSummary/)
  assert.match(runtime, /loadPeriodData\(\)/)
  assert.doesNotMatch(runtime, /getRealtimeHeartRate/)
  assert.match(runtime, /overview\.value\.coveredUsers/)
  assert.match(runtime, /overview\.value\.dangerUsers/)
  assert.match(page, /独立实时快照/)
  assert.match(page, /departmentDrawerVisible/)
  assert.match(runtime, /openDepartmentDrilldown/)
  assert.match(runtime, /getHeartRateDepartmentUsers/)
  assert.match(page, /openHeaderMetric/)
  assert.match(runtime, /getHeartRatePeriodUsers/)
  assert.match(runtime, /zone: metricDrawer\.zone/)
  assert.match(runtime, /最高风险为/)
  assert.match(page, /metricDailyRows/)
  assert.match(page, /abnormalRecords/)
  assert.match(page, /totalRecords/)
  assert.match(chart, /异常人数/)
  assert.match(chart, /openDepartmentDrilldown\(row\)/)
  assert.doesNotMatch(chart, /this\.filterDept\s*=/)
  assert.match(chart, /异常率/)
  assert.match(chart, /有效覆盖/)
  assert.match(mapper, /GROUP BY CONVERT\(VARCHAR\(10\), record_time, 23\), user_code/)
  assert.match(mapper, /SUM\(CASE WHEN min_hr < 55 OR max_hr > 120 THEN 1 ELSE 0 END\) AS anomalyCount/)
  assert.match(mapper, /COUNT\(\*\) AS coveredUsers/)
  assert.match(mapper, /AS abnormalUsers/)
  assert.match(mapper, /AS dangerUsers/)
  assert.match(mapper, /getDepartmentAbnormalUsersDirect/)
  assert.match(controller, /@GetMapping\("\/department-users"\)/)
  assert.match(controller, /@GetMapping\("\/period-users"\)/)
  assert.match(api, /url: '\/heart-rate\/department-users'/)
  assert.match(api, /url: '\/heart-rate\/period-users'/)
})

test('heart-rate abnormal user drilldown opens a period-scoped portrait record view', () => {
  const heartRatePage = read('src', 'views', 'health-monitor', 'heart-rate', 'index.vue')
  const heartRateRuntime = heartRatePage + read('src', 'views', 'health-monitor', 'heart-rate', 'use-heart-rate-page.ts')
  const portraitPage = read('src', 'views', 'personnel-management', 'health-portrait', 'index.vue')
  const portraitComposable = read('src', 'views', 'personnel-management', 'health-portrait', 'use-health-portrait-page.js')
  const portraitChart = read('src', 'views', 'personnel-management', 'health-portrait', 'health-portrait-chart.js')
  const api = read('src', 'api', 'heart-rate.js')
  const mapper = readBackend('src', 'main', 'java', 'com', 'xzkj', 'health', 'mapper', 'HeartRateMapper.java')
  const controller = readBackend('src', 'main', 'java', 'com', 'xzkj', 'health', 'controller', 'HeartRateController.java')

  assert.match(heartRateRuntime, /focus: 'heartRate-abnormal'/)
  assert.match(heartRateRuntime, /startDate,[\s\S]*endDate/)
  assert.match(portraitComposable, /'heartRate-abnormal'/)
  assert.match(portraitComposable, /loadMetricAbnormalRecords\(abnormalRecordPage\.value\)/)
  assert.match(portraitComposable, /fetchMetricAbnormalRecords\(/)
  assert.match(portraitComposable, /周期\$\{metricDrilldownConfig\.value\.label\}异常记录/)
  assert.match(portraitPage, /warningPanelTitle/)
  assert.match(portraitPage, /metricAbnormalRows/)
  assert.match(portraitPage, /abnormalRecordTotal/)
  assert.match(portraitPage, /changeAbnormalRecordPage/)
  assert.match(portraitChart, /buildHeartRateAbnormalTrendOption/)
  assert.match(api, /url: '\/heart-rate\/user-abnormal-records'/)
  assert.match(controller, /@GetMapping\("\/user-abnormal-records"\)/)
  assert.match(mapper, /getUserAbnormalRecordsDirect/)
  assert.match(mapper, /heart_rate < 55 OR heart_rate > 120/)
})

test('pressure blood-pressure and blood-oxygen share period risk evidence semantics', () => {
  const pages = ['pressure', 'blood-pressure', 'blood-oxygen']
  const api = read('src', 'api', 'period-risk.js')
  const periodRisk = read('src', 'views', 'health-monitor', 'metric-page', 'use-period-risk-page.ts')
  const drawer = read('src', 'views', 'health-monitor', 'metric-page', 'PeriodRiskDrawer.vue')
  const provider = readBackend('src', 'main', 'java', 'com', 'xzkj', 'health', 'mapper', 'provider', 'MetricPeriodRiskSqlProvider.java')

  for (const pageName of pages) {
    const page = read('src', 'views', 'health-monitor', pageName, 'index.vue') +
      read('src', 'views', 'health-monitor', pageName, `use-${pageName}-page.ts`) + periodRisk
    assert.match(page, /loadPeriodRisk\(\)/)
    assert.match(page, /riskSummary\.coveredUsers/)
    assert.match(page, /异常频次 Top 10/)
    assert.match(page, /部门异常人数/)
    assert.match(page, /独立实时快照/)
    assert.match(page, /PeriodRiskDrawer/)
    assert.match(page, /openPeriodPortrait/)
    assert.match(page, /openRiskZone\(z\)/)
    assert.doesNotMatch(page, /label: '平均压力指数'/)
    assert.doesNotMatch(page, /label: '平均收缩压'/)
    assert.doesNotMatch(page, /label: '周期平均'/)
  }

  assert.match(api, /risk-summary/)
  assert.match(api, /daily-risk/)
  assert.match(api, /department-users/)
  assert.match(periodRisk, /focus: `\$\{config\.focus\}-abnormal`/)
  assert.match(periodRisk, /async function switchPeriod\(value: MetricPeriod\)/)
  assert.match(periodRisk, /config\.loadPeriodSupplement\?\./)
  assert.match(periodRisk, /zone: riskDrawer\.zone/)
  assert.match(periodRisk, /最高风险为/)
  assert.match(drawer, /异常记录/)
  assert.match(drawer, /最近采集/)
  assert.match(drawer, /summary/)
  assert.match(provider, /case "pressure"/)
  assert.match(provider, /case "bloodPressure"/)
  assert.match(provider, /case "bloodOxygen"/)
  assert.match(provider, /GROUP BY CAST\(record_time AS date\), user_code/)
  assert.match(provider, /SUM\(CASE WHEN risk_code > 0 THEN 1 ELSE 0 END\) AS anomalyCount/)
  assert.match(provider, /maxRiskCode = #\{riskCode\}/)
  assert.doesNotMatch(provider, /COUNT\(DISTINCT/)
  assert.doesNotMatch(provider, /\(SELECT COUNT\(\*\) FROM scoped/)
})

test('health portrait supports all four metric abnormal drilldowns', () => {
  const page = read('src', 'views', 'personnel-management', 'health-portrait', 'index.vue')
  const composable = read('src', 'views', 'personnel-management', 'health-portrait', 'use-health-portrait-page.js')
  const runtime = read('src', 'views', 'personnel-management', 'health-portrait', 'health-portrait-runtime.js')
  const chart = read('src', 'views', 'personnel-management', 'health-portrait', 'health-portrait-chart.js')

  for (const focus of ['heartRate-abnormal', 'pressure-abnormal', 'bloodPressure-abnormal', 'bloodOxygen-abnormal']) {
    assert.match(composable, new RegExp(focus))
  }
  assert.match(composable, /metricDrilldownConfig/)
  assert.match(page, /metricAbnormalRows/)
  assert.match(page, /abnormalDualValue/)
  assert.match(runtime, /getMetricUserAbnormalRecords/)
  assert.match(chart, /buildMetricAbnormalTrendOption/)
})
