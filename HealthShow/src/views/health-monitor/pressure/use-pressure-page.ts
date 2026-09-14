import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { getPressureDistribution, getPressureRealtime } from '@/api/pressure'
import { PERIOD_OPTIONS } from '@/constants/periods'
import { fetchMetricData } from '@/views/health-monitor/metric-page/metric-data-loader'
import { exportMetricRows } from '@/views/health-monitor/metric-page/metric-export'
import { renderDepartmentRisk, renderPeriodRiskTrend } from '@/views/health-monitor/metric-page/period-risk-chart'
import { useMetricPageLifecycle, type MetricPeriod } from '@/views/health-monitor/metric-page/use-metric-page-lifecycle'
import { usePeriodRiskPage } from '@/views/health-monitor/metric-page/use-period-risk-page'

type MetricRow = Record<string, any>

export function usePressurePage() {
  const router = useRouter()
  const activePeriod = ref<MetricPeriod>('month')
  const periodOptions = PERIOD_OPTIONS as Array<{ label: string; value: MetricPeriod }>
  const top5Data = ref<MetricRow[]>([])
  const distLegend = ref<MetricRow[]>([])
  const realtimeList = ref<MetricRow[]>([])
  const filterDept = ref('')
  const anomalyExpanded = ref(false)
  const charts: Record<string, any> = {}
  const top5ScrollRef = ref<HTMLElement | null>(null)
  const deptRef = ref<HTMLElement | null>(null)
  const hourlyRef = ref<HTMLElement | null>(null)

  async function fetchData() {
    await Promise.allSettled([periodRisk.loadPeriodRisk(), loadDist(), loadRealtime()])
  }

  async function loadDist() {
    const { startDate, endDate } = lifecycle.periodRange.value
    const rows = await fetchMetricData(() => getPressureDistribution(startDate, endDate), [])
    distLegend.value = (rows || []).filter((item: MetricRow) => item.name && item.value > 0)
  }

  async function loadRealtime() {
    const rows = await fetchMetricData(() => getPressureRealtime(1000), [])
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

  const renderRiskTrend = (rows: MetricRow[]) => {
    renderPeriodRiskTrend(
      { charts, $refs: { hourlyRef: hourlyRef.value } },
      'hourlyRef',
      rows,
      { bar: '#FFB84D', line: '#00d4ff' }
    )
  }
  const renderRiskDepartments = (rows: MetricRow[]) => {
    renderDepartmentRisk(
      {
        charts,
        $refs: { deptRef: deptRef.value },
        openDepartmentRisk: (row: MetricRow) => periodRisk.openDepartmentRisk(row)
      },
      'deptRef',
      rows,
      { bar: '#fb923c' }
    )
  }

  const periodRisk = usePeriodRiskPage({
    path: 'pressure',
    focus: 'pressure',
    label: '压力',
    activePeriod,
    periodRange: lifecycle.periodRange,
    metricPeriodLabel: lifecycle.metricPeriodLabel,
    top5Data,
    renderRiskTrend,
    renderRiskDepartments,
    startTop5Scroll: lifecycle.startTop5Scroll,
    loadPeriodSupplement: loadDist,
    goToPortrait
  })

  const headerKpis = computed(() => [
    { key: 'abnormalUsers', label: `${lifecycle.metricPeriodLabel.value}异常人数`, val: periodRisk.riskSummary.value.abnormalUsers || 0, cls: 'kpi-orange' },
    { key: 'coveredUsers', label: `${lifecycle.metricPeriodLabel.value}覆盖人数`, val: periodRisk.riskSummary.value.coveredUsers || 0, cls: 'kpi-green' },
    { key: 'abnormalRecords', label: `${lifecycle.metricPeriodLabel.value}异常记录`, val: periodRisk.riskSummary.value.abnormalRecords || 0, cls: 'kpi-yellow' },
    { key: 'totalRecords', label: `${lifecycle.metricPeriodLabel.value}采样记录`, val: periodRisk.riskSummary.value.totalRecords || 0, cls: 'kpi-red' }
  ])
  const trendTitle = computed(() => ({
    day: '当日压力异常风险',
    week: '近7日压力异常风险',
    month: '近30日压力异常风险'
  })[activePeriod.value])
  const top5Max = computed(() => top5Data.value.length
    ? Math.max(...top5Data.value.map((item) => item.abnormalCount || 0))
    : 1)
  const displayedTop5 = computed(() => top5Data.value.slice(0, 10))
  const top5Title = computed(() => ({ day: '今日', week: '近7日', month: '近30日' })[activePeriod.value] + '异常频次 Top 10')
  const filteredRealtimeList = computed(() => filterDept.value
    ? realtimeList.value.filter((item) => item.deptName === filterDept.value)
    : realtimeList.value)
  const psAnomalyList = computed(() => filteredRealtimeList.value.filter((item) => item.pressure >= 70))
  const displayedAnomalyList = computed(() => anomalyExpanded.value
    ? psAnomalyList.value
    : psAnomalyList.value.slice(0, 20))
  const psZones = computed(() => {
    const summary = periodRisk.riskSummary.value
    const total = summary.coveredUsers || 0
    const pct = (count: number) => total > 0 ? Math.round(count / total * 100) : 0
    return [
      { key: 'normal', label: '正常', range: '< 70', count: summary.normalUsers || 0, pct: pct(summary.normalUsers || 0), color: '#52c41a', icon: 'OK', cls: 'zone-normal' },
      { key: 'elevated', filter: 'warning', label: '偏高', range: '70–84', count: summary.warningUsers || 0, pct: pct(summary.warningUsers || 0), color: '#FFB84D', icon: '!', cls: 'zone-elevated' },
      { key: 'high', filter: 'danger', label: '高压', range: '≥ 85', count: summary.dangerUsers || 0, pct: pct(summary.dangerUsers || 0), color: '#ff5252', icon: 'ALERT', cls: 'zone-high' }
    ]
  })

  async function exportExcel() {
    await exportMetricRows({
      rows: periodRisk.dailyRiskRows.value,
      columns: [
        { label: '日期', key: 'date' },
        { label: '覆盖人数', key: 'coveredUsers' },
        { label: '异常人数', key: 'anomalyCount' },
        { label: '异常率(%)', key: 'anomalyRate' },
        { label: '异常记录', key: 'abnormalRecords' },
        { label: '采样记录', key: 'totalRecords' }
      ],
      filenamePrefix: `${lifecycle.metricPeriodLabel.value}压力风险`,
      mapRow: (row: MetricRow) => row
    })
  }

  const fmtTime = (value: unknown) => value ? dayjs(value as string).format('MM-DD HH:mm') : ''
  const top5BarColor = (value: number) => value >= 85
    ? 'linear-gradient(90deg, #ff5252, #b91c1c)'
    : value >= 70
      ? 'linear-gradient(90deg, #FFB84D, #d97706)'
      : value >= 50
        ? 'linear-gradient(90deg, #52c41a, #166534)'
        : 'linear-gradient(90deg, #4FC3F7, #0284c7)'
  const top5ValColor = (value: number) => value >= 85 ? '#ff5252' : value >= 70 ? '#FFB84D' : value >= 50 ? '#52c41a' : '#4FC3F7'

  return {
    activePeriod,
    anomalyExpanded,
    currentTime: lifecycle.currentTime,
    deptRef,
    displayedAnomalyList,
    displayedTop5,
    distLegend,
    exportExcel,
    fmtTime,
    headerKpis,
    hourlyRef,
    metricPeriodLabel: lifecycle.metricPeriodLabel,
    openPeriodPortrait: periodRisk.openPeriodPortrait,
    openRiskDrawerPerson: periodRisk.openRiskDrawerPerson,
    openRiskMetric: periodRisk.openRiskMetric,
    openRiskZone: periodRisk.openRiskZone,
    pageLoading: lifecycle.pageLoading,
    periodCoverageText: periodRisk.periodCoverageText,
    periodOptions,
    psAnomalyList,
    psZones,
    riskDrawer: periodRisk.riskDrawer,
    riskDrawerDailyRows: periodRisk.riskDrawerDailyRows,
    riskSummary: periodRisk.riskSummary,
    loadRiskDrawerUsers: periodRisk.loadRiskDrawerUsers,
    switchPeriod: periodRisk.switchPeriod,
    top5BarColor,
    top5Data,
    top5Max,
    top5ScrollRef,
    top5Title,
    top5ValColor,
    trendTitle
  }
}
