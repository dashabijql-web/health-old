import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { getRealtimeBloodOxygen } from '@/api/blood-oxygen'
import { spo2Level } from '@/constants/health-thresholds'
import { PERIOD_OPTIONS } from '@/constants/periods'
import { fetchMetricData } from '@/views/health-monitor/metric-page/metric-data-loader'
import { exportMetricRows } from '@/views/health-monitor/metric-page/metric-export'
import { renderDepartmentRisk, renderPeriodRiskTrend } from '@/views/health-monitor/metric-page/period-risk-chart'
import { useMetricPageLifecycle, type MetricPeriod } from '@/views/health-monitor/metric-page/use-metric-page-lifecycle'
import { usePeriodRiskPage } from '@/views/health-monitor/metric-page/use-period-risk-page'
import { bloodOxygenChartMethods } from './blood-oxygen-chart'

type MetricRow = Record<string, any>

export function useBloodOxygenPage() {
  const router = useRouter()
  const activePeriod = ref<MetricPeriod>('month')
  const periodOptions = PERIOD_OPTIONS as Array<{ label: string; value: MetricPeriod }>
  const top5Data = ref<MetricRow[]>([])
  const realtimeList = ref<MetricRow[]>([])
  const anomalyExpanded = ref(false)
  const filterDept = ref('')
  const detailItem = ref<MetricRow | null>(null)
  const detailVisible = ref(false)
  const charts: Record<string, any> = {}
  const top5ScrollRef = ref<HTMLElement | null>(null)
  const deptRef = ref<HTMLElement | null>(null)
  const trendRef = ref<HTMLElement | null>(null)

  const chartPage = {
    charts,
    currentPage: 1,
    $refs: { deptRef: null as HTMLElement | null, trendRef: null as HTMLElement | null },
    get filterDept() { return filterDept.value },
    set filterDept(value: string) { filterDept.value = value }
  }

  function syncChartRefs() {
    chartPage.$refs.deptRef = deptRef.value
    chartPage.$refs.trendRef = trendRef.value
  }

  async function loadRealtime() {
    const rows = await fetchMetricData(() => getRealtimeBloodOxygen(1000), [])
    const seen = new Set<string>()
    realtimeList.value = rows.filter((row: MetricRow) => {
      const key = String(row.userCode || row.empCode || row.userName || '')
      if (!key || seen.has(key)) return false
      seen.add(key)
      return true
    })
  }

  function goToPortrait(item: MetricRow) {
    const empCode = item.userCode || item.empCode
    void router.push({
      path: '/health-monitor/health-portrait',
      query: empCode ? { empCode } : { name: item.userName }
    })
  }

  const lifecycle = useMetricPageLifecycle({
    activePeriod,
    charts,
    top5ScrollRef,
    fetchData,
    refreshData: loadRealtime
  })

  function renderRiskTrend(rows: MetricRow[]) {
    syncChartRefs()
    renderPeriodRiskTrend(chartPage, 'trendRef', rows, { bar: '#FFB84D', line: '#00d4ff' })
  }

  function renderRiskDepartments(rows: MetricRow[]) {
    syncChartRefs()
    bloodOxygenChartMethods.initDept.call(chartPage, rows)
  }

  async function fetchData() {
    await Promise.allSettled([periodRisk.loadPeriodRisk(), loadRealtime()])
  }

  const periodRisk = usePeriodRiskPage({
    path: 'blood-oxygen',
    focus: 'bloodOxygen',
    label: '血氧',
    activePeriod,
    periodRange: lifecycle.periodRange,
    metricPeriodLabel: lifecycle.metricPeriodLabel,
    top5Data,
    renderRiskTrend,
    renderRiskDepartments,
    startTop5Scroll: lifecycle.startTop5Scroll,
    goToPortrait
  })

  const headerKpis = computed(() => [
    { key: 'abnormalUsers', label: `${lifecycle.metricPeriodLabel.value}异常人数`, val: periodRisk.riskSummary.value.abnormalUsers || 0, cls: 'kpi-cyan' },
    { key: 'coveredUsers', label: `${lifecycle.metricPeriodLabel.value}覆盖人数`, val: periodRisk.riskSummary.value.coveredUsers || 0, cls: 'kpi-orange' },
    { key: 'abnormalRecords', label: `${lifecycle.metricPeriodLabel.value}异常记录`, val: periodRisk.riskSummary.value.abnormalRecords || 0, cls: 'kpi-orange' },
    { key: 'totalRecords', label: `${lifecycle.metricPeriodLabel.value}采样记录`, val: periodRisk.riskSummary.value.totalRecords || 0, cls: 'kpi-blue' }
  ])
  const trendTitle = computed(() => ({ day: '当日血氧异常风险', week: '近7日血氧异常风险', month: '近30日血氧异常风险' })[activePeriod.value])
  const top5Max = computed(() => top5Data.value.length ? Math.max(...top5Data.value.map((item) => item.abnormalCount || 0)) : 1)
  const displayedTop5 = computed(() => top5Data.value.slice(0, 10))
  const top5Title = computed(() => ({ day: '今日', week: '近7日', month: '近30日' })[activePeriod.value] + '异常频次 Top 10')
  const filteredRealtimeList = computed(() => filterDept.value ? realtimeList.value.filter((item) => item.deptName === filterDept.value) : realtimeList.value)
  const boAnomalyList = computed(() => filteredRealtimeList.value.filter((item) => item.bloodOxygen < 95))
  const displayedBoAnomalyList = computed(() => anomalyExpanded.value ? boAnomalyList.value : boAnomalyList.value.slice(0, 20))
  const boZones = computed(() => {
    const summary = periodRisk.riskSummary.value
    const total = summary.coveredUsers || 0
    const pct = (count: number) => total > 0 ? Math.round(count / total * 100) : 0
    return [
      { key: 'danger', label: '危险', range: '< 90%', count: summary.dangerUsers || 0, pct: pct(summary.dangerUsers || 0), color: '#ff5252', icon: '↓', cls: 'zone-danger' },
      { key: 'low', filter: 'warning', label: '偏低', range: '90–94%', count: summary.warningUsers || 0, pct: pct(summary.warningUsers || 0), color: '#FFB84D', icon: '↓', cls: 'zone-low' },
      { key: 'normal', label: '正常', range: '≥ 95%', count: summary.normalUsers || 0, pct: pct(summary.normalUsers || 0), color: '#52c41a', icon: 'OK', cls: 'zone-normal' }
    ]
  })

  function showDetail(item: MetricRow) {
    detailItem.value = item
    detailVisible.value = true
  }

  async function exportExcel() {
    await exportMetricRows({
      rows: periodRisk.dailyRiskRows.value,
      columns: [
        { label: '日期', key: 'date' }, { label: '覆盖人数', key: 'coveredUsers' },
        { label: '异常人数', key: 'anomalyCount' }, { label: '异常率(%)', key: 'anomalyRate' },
        { label: '异常记录', key: 'abnormalRecords' }, { label: '采样记录', key: 'totalRecords' }
      ],
      filenamePrefix: `${lifecycle.metricPeriodLabel.value}血氧风险`,
      mapRow: (row: MetricRow) => row
    })
  }

  const fmtTime = (value: unknown) => value ? dayjs(value as string).format('MM-DD HH:mm') : ''

  return {
    activePeriod, anomalyExpanded, boAnomalyList, boLevel: spo2Level, boZones,
    currentTime: lifecycle.currentTime, detailItem, detailVisible, deptRef,
    displayedBoAnomalyList, displayedTop5, exportExcel, fmtTime, headerKpis,
    metricPeriodLabel: lifecycle.metricPeriodLabel, openPeriodPortrait: periodRisk.openPeriodPortrait,
    openRiskDrawerPerson: periodRisk.openRiskDrawerPerson, openRiskMetric: periodRisk.openRiskMetric,
    openRiskZone: periodRisk.openRiskZone, pageLoading: lifecycle.pageLoading,
    periodCoverageText: periodRisk.periodCoverageText, periodOptions, riskDrawer: periodRisk.riskDrawer,
    riskDrawerDailyRows: periodRisk.riskDrawerDailyRows, riskSummary: periodRisk.riskSummary,
    loadRiskDrawerUsers: periodRisk.loadRiskDrawerUsers, showDetail, switchPeriod: periodRisk.switchPeriod,
    top5Data, top5Max, top5ScrollRef, top5Title, trendRef, trendTitle
  }
}
