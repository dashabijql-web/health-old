import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  getDailyAnomalyHeartRate,
  getHeartRateDepartmentUsers,
  getHeartRateDeptStats,
  getHeartRateOverview,
  getHeartRatePeriodUsers,
  getHeartRateTopUsers
} from '@/api/heart-rate'
import { getOnlineUsers, getRealtimeHealthSnapshot } from '@/api/realtime'
import { PERIOD_OPTIONS } from '@/constants/periods'
import { useIntervalTask } from '@/composables/useIntervalTask'
import { useScrollLoop } from '@/composables/useScrollLoop'
import { useTimeoutTask } from '@/composables/useTimeoutTask'
import { fetchMetricData } from '@/views/health-monitor/metric-page/metric-data-loader'
import { exportMetricRows } from '@/views/health-monitor/metric-page/metric-export'
import { heartRateChartMethods } from './heart-rate-chart'

type Period = 'day' | 'week' | 'month'
type AnyRow = Record<string, any>

interface HeartRateOverview {
  avgHeartRate: number
  minHeartRate: number
  maxHeartRate: number
  detectionRate: number
  abnormalCount: number
  totalCount: number
  coveredUsers: number
  abnormalUsers: number
  lowUsers: number
  normalUsers: number
  elevatedUsers: number
  dangerUsers: number
}

const createOverview = (): HeartRateOverview => ({
  avgHeartRate: 0,
  minHeartRate: 0,
  maxHeartRate: 0,
  detectionRate: 0,
  abnormalCount: 0,
  totalCount: 0,
  coveredUsers: 0,
  abnormalUsers: 0,
  lowUsers: 0,
  normalUsers: 0,
  elevatedUsers: 0,
  dangerUsers: 0
})

export function useHeartRatePage() {
  const route = useRoute()
  const router = useRouter()
  const pageLoading = ref(false)
  const currentTime = ref('')
  const overview = ref<HeartRateOverview>(createOverview())
  const top5Data = ref<AnyRow[]>([])
  const dailyRiskRows = ref<AnyRow[]>([])
  const realtimeSnapshot = ref<AnyRow | null>(null)
  const snapshotAnomalyRows = ref<AnyRow[]>([])
  const snapshotAnomalyTotal = ref(0)
  const snapshotAnomalyPage = ref(1)
  const snapshotAnomalySize = 20
  const snapshotListStale = ref(false)
  const isDashboardDrilldown = ref(false)
  const activePeriod = ref<Period>('month')
  const periodOptions = PERIOD_OPTIONS as Array<{ label: string; value: Period }>
  const charts: Record<string, any> = {}
  const detailItem = ref<AnyRow | null>(null)
  const detailVisible = ref(false)
  const departmentDrawerVisible = ref(false)
  const departmentDetail = reactive({
    deptName: '',
    lowCount: 0,
    highCount: 0,
    abnormalCount: 0,
    list: [] as AnyRow[],
    total: 0,
    page: 1,
    size: 12,
    loading: false
  })
  const metricDrawer = reactive({
    visible: false,
    key: '',
    zone: '',
    summary: null as AnyRow | null,
    list: [] as AnyRow[],
    total: 0,
    page: 1,
    size: 12,
    loading: false
  })
  const _top5Paused = ref(false)
  const top5ScrollRef = ref<HTMLElement | null>(null)
  const deptRef = ref<HTMLElement | null>(null)
  const trendRef = ref<HTMLElement | null>(null)
  const currentAnomalyPanel = ref<HTMLElement | null>(null)
  let pageReady = false

  const metricPeriodLabel = computed(() => ({
    day: '当日',
    week: '近7日',
    month: '近30日'
  })[activePeriod.value])
  const periodRange = computed(() => {
    const today = dayjs().format('YYYY-MM-DD')
    if (activePeriod.value === 'day') return { startDate: today, endDate: today }
    if (activePeriod.value === 'week') {
      return { startDate: dayjs().subtract(6, 'day').format('YYYY-MM-DD'), endDate: today }
    }
    return { startDate: dayjs().subtract(29, 'day').format('YYYY-MM-DD'), endDate: today }
  })
  const headerKpis = computed(() => [
    { key: 'abnormalUsers', label: `${metricPeriodLabel.value}异常人数`, val: (overview.value.abnormalUsers || 0).toLocaleString(), cls: 'kpi-orange' },
    { key: 'coveredUsers', label: `${metricPeriodLabel.value}覆盖人数`, val: (overview.value.coveredUsers || 0).toLocaleString(), cls: 'kpi-cyan' },
    { key: 'abnormalRecords', label: `${metricPeriodLabel.value}异常记录`, val: (overview.value.abnormalCount || 0).toLocaleString(), cls: 'kpi-red' },
    { key: 'totalRecords', label: `${metricPeriodLabel.value}采样记录`, val: (overview.value.totalCount || 0).toLocaleString(), cls: 'kpi-blue' }
  ])
  const trendTitle = computed(() => ({
    day: '当日心率异常风险',
    week: '近7日心率异常风险',
    month: '近30日心率异常风险'
  })[activePeriod.value])
  const coverageSummary = computed(() => {
    const covered = dailyRiskRows.value.filter((row) => Number(row.coveredUsers || 0) > 0)
    const avgCovered = covered.length
      ? Math.round(covered.reduce((sum, row) => sum + Number(row.coveredUsers || 0), 0) / covered.length)
      : 0
    const periodDays = { day: 1, week: 7, month: 30 }[activePeriod.value]
    return { days: covered.length, avgCovered, periodDays }
  })
  const trendScopeText = computed(() => {
    if (!coverageSummary.value.days) return '暂无有效覆盖数据'
    return `${coverageSummary.value.days} 个有数据日 · 日均覆盖 ${coverageSummary.value.avgCovered} 人`
  })
  const top5Max = computed(() => top5Data.value.length
    ? Math.max(...top5Data.value.map((item) => Number(item.count || 0)))
    : 1)
  const displayedTop5 = computed(() => top5Data.value.slice(0, 10))
  const anomalyList = computed(() => snapshotAnomalyRows.value)
  const heartRateSnapshotMetric = computed(() => {
    return (realtimeSnapshot.value?.metrics || []).find((metric: AnyRow) => metric.key === 'heartRate') || null
  })
  const anomalyScopeText = computed(() => {
    const snapshot = realtimeSnapshot.value
    if (!snapshot) return '实时快照加载中'
    return `窗口 ${snapshot.onlineWindowMinutes} 分钟 · 新鲜度 ${snapshot.freshnessMinutes} 分钟 · 全员覆盖 ${heartRateSnapshotMetric.value?.coveredUsers || 0} 人`
  })
  const anomalyEmptyText = computed(() => {
    if (snapshotListStale.value) return '实时数据刷新失败，当前没有可用的心率异常名单'
    if (realtimeSnapshot.value?.status === 'STALE') return '当前窗口只有过期数据，暂无新鲜心率异常'
    if (realtimeSnapshot.value?.status === 'NO_DATA') return '当前窗口暂无心率数据'
    return '当前窗口无异常心率人员'
  })
  const hrZones = computed(() => {
    const total = overview.value.coveredUsers || 0
    const pct = (count: number) => total > 0 ? Math.round(count / total * 100) : 0
    return [
      { key: 'low', label: '偏低', range: '< 55 bpm', count: overview.value.lowUsers || 0, pct: pct(overview.value.lowUsers || 0), color: '#4FC3F7', icon: '↓', cls: 'zone-low' },
      { key: 'normal', label: '正常', range: '55–120 bpm', count: overview.value.normalUsers || 0, pct: pct(overview.value.normalUsers || 0), color: '#52c41a', icon: 'OK', cls: 'zone-normal' },
      { key: 'elevated', filter: 'warning', label: '偏高', range: '121–150 bpm', count: overview.value.elevatedUsers || 0, pct: pct(overview.value.elevatedUsers || 0), color: '#FFB84D', icon: '↑', cls: 'zone-elevated' },
      { key: 'danger', label: '危险', range: '> 150 bpm', count: overview.value.dangerUsers || 0, pct: pct(overview.value.dangerUsers || 0), color: '#ff5252', icon: 'ALERT', cls: 'zone-danger' }
    ]
  })
  const departmentDrawerTitle = computed(() => {
    return `${departmentDetail.deptName || '部门'} · ${metricPeriodLabel.value}心率异常人员`
  })
  const metricDrawerTitle = computed(() => {
    if (metricDrawer.summary) {
      return metricDrawer.zone === 'normal'
        ? `${metricPeriodLabel.value}保持正常区间人员`
        : `${metricPeriodLabel.value}最高风险为${metricDrawer.summary.label}的人员`
    }
    return {
      abnormalUsers: `${metricPeriodLabel.value}异常人员`,
      coveredUsers: `${metricPeriodLabel.value}覆盖人员`,
      coverageDays: `${metricPeriodLabel.value}每日覆盖明细`,
      abnormalRecords: `${metricPeriodLabel.value}异常记录按日明细`,
      totalRecords: `${metricPeriodLabel.value}采样记录按日明细`
    }[metricDrawer.key] || '周期指标明细'
  })
  const metricDrawerKind = computed(() => {
    return ['abnormalUsers', 'coveredUsers', 'zone'].includes(metricDrawer.key) ? 'users' : 'daily'
  })
  const metricDailyRows = computed(() => {
    return [...dailyRiskRows.value].sort((a, b) => String(b.date).localeCompare(String(a.date)))
  })

  const chartContext: AnyRow = {
    charts,
    get $refs() {
      return { deptRef: deptRef.value, trendRef: trendRef.value }
    },
    openDepartmentDrilldown: (row: AnyRow) => openDepartmentDrilldown(row)
  }
  const initDept = (rows: AnyRow[]) => heartRateChartMethods.initDept.call(chartContext, rows)
  const renderDailyRiskTrend = (rows: AnyRow[]) => heartRateChartMethods.renderDailyRiskTrend.call(chartContext, rows)

  const top5Scroll = useScrollLoop({
    getElement: () => top5ScrollRef.value,
    intervalMs: 80,
    step: 1,
    endPauseMs: 1500,
    shouldScroll: () => !_top5Paused.value
  })
  const tickClock = () => {
    currentTime.value = dayjs().format('YYYY年MM月DD日 HH:mm:ss')
  }
  const { start: startClock } = useIntervalTask(tickClock, 1000)
  const { start: startRefresh, stop: stopRefresh } = useIntervalTask(() => fetchData(), 30000)
  const { start: scheduleResize } = useTimeoutTask(() => {
    void nextTick(() => Object.values(charts).forEach((chart) => chart?.resize?.()))
  }, 200)

  function applyRouteContext(query: AnyRow = {}, refresh = false) {
    const routePeriod = Array.isArray(query.period) ? query.period[0] : query.period
    const nextPeriod = ['day', 'week', 'month'].includes(routePeriod) ? routePeriod as Period : activePeriod.value
    const periodChanged = nextPeriod !== activePeriod.value
    activePeriod.value = nextPeriod
    isDashboardDrilldown.value = query.focus === 'current-anomaly'
    if (refresh && periodChanged) void fetchData()
    if (isDashboardDrilldown.value) focusCurrentAnomalyPanel()
  }

  function focusCurrentAnomalyPanel() {
    void nextTick(() => {
      currentAnomalyPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }

  async function exportExcel() {
    await exportMetricRows({
      rows: dailyRiskRows.value,
      columns: [
        { label: '日期', key: 'date' },
        { label: '覆盖人数', key: 'coveredUsers' },
        { label: '异常人数', key: 'anomalyCount' },
        { label: '异常率(%)', key: 'anomalyRate' },
        { label: '偏低人数', key: 'lowCount' },
        { label: '偏高人数', key: 'highCount' }
      ],
      filenamePrefix: `${metricPeriodLabel.value}心率风险`,
      mapRow: (row: AnyRow) => ({
        date: row.date || '--',
        coveredUsers: row.coveredUsers || 0,
        anomalyCount: row.anomalyCount || 0,
        anomalyRate: row.anomalyRate || 0,
        lowCount: row.lowCount || 0,
        highCount: row.highCount || 0
      })
    })
  }

  async function fetchData() {
    await Promise.allSettled([loadPeriodData(), loadSnapshotAnomalies()])
  }

  async function loadPeriodData() {
    await Promise.allSettled([loadOverview(), loadTopUsers(), loadDept(), loadRiskTrend()])
  }

  function switchPeriod(value: Period) {
    if (activePeriod.value === value) return
    departmentDrawerVisible.value = false
    metricDrawer.visible = false
    activePeriod.value = value
    void loadPeriodData()
  }

  async function loadOverview() {
    const { startDate, endDate } = periodRange.value
    overview.value = await fetchMetricData(() => getHeartRateOverview(startDate, endDate), overview.value)
  }

  async function loadTopUsers() {
    const { startDate, endDate } = periodRange.value
    top5Data.value = await fetchMetricData(() => getHeartRateTopUsers(10, startDate, endDate), [])
    void nextTick(top5Scroll.start)
  }

  async function loadDept() {
    const { startDate, endDate } = periodRange.value
    const rows = await fetchMetricData(() => getHeartRateDeptStats(startDate, endDate), [])
    void nextTick(() => initDept(rows))
  }

  async function loadRiskTrend() {
    const { startDate, endDate } = periodRange.value
    const rows = await fetchMetricData(() => getDailyAnomalyHeartRate(startDate, endDate), [])
    dailyRiskRows.value = rows
    void nextTick(() => renderDailyRiskTrend(rows))
  }

  async function loadSnapshotAnomalies() {
    const [snapshotResult, usersResult] = await Promise.allSettled([
      getRealtimeHealthSnapshot(),
      getOnlineUsers({
        page: snapshotAnomalyPage.value,
        size: snapshotAnomalySize,
        status: 'warning',
        indicator: 'heartRate'
      })
    ])
    if (snapshotResult.status === 'fulfilled' && snapshotResult.value?.code === 200) {
      realtimeSnapshot.value = snapshotResult.value.data || null
    }
    if (usersResult.status === 'fulfilled' && usersResult.value?.code === 200) {
      const page = usersResult.value.data || {}
      snapshotAnomalyRows.value = (page.list || []).map((row: AnyRow) => ({
        ...row,
        empCode: row.userCode,
        gender: row.gender === 1 ? '男' : row.gender === 2 ? '女' : '--',
        recordTime: row.lastUpdate,
        heartRateState: row.indicatorStates?.heartRate || 'warning'
      }))
      snapshotAnomalyTotal.value = page.total || 0
      snapshotAnomalyPage.value = page.page || snapshotAnomalyPage.value
      snapshotListStale.value = Boolean(page.stale)
    } else {
      snapshotAnomalyRows.value = []
      snapshotAnomalyTotal.value = 0
      snapshotListStale.value = true
    }
    if (isDashboardDrilldown.value) focusCurrentAnomalyPanel()
  }

  function changeSnapshotAnomalyPage(page: number) {
    snapshotAnomalyPage.value = page
    void loadSnapshotAnomalies()
  }

  async function openDepartmentDrilldown(item: AnyRow) {
    Object.assign(departmentDetail, {
      deptName: item.deptName,
      lowCount: item.lowCount || 0,
      highCount: item.highCount || 0,
      abnormalCount: (item.lowCount || 0) + (item.highCount || 0),
      list: [],
      total: 0,
      page: 1
    })
    departmentDrawerVisible.value = true
    await loadDepartmentUsers(1)
  }

  async function loadDepartmentUsers(page = departmentDetail.page) {
    if (!departmentDetail.deptName) return
    departmentDetail.loading = true
    const { startDate, endDate } = periodRange.value
    try {
      const response = await getHeartRateDepartmentUsers({
        dept: departmentDetail.deptName,
        startDate,
        endDate,
        page,
        size: departmentDetail.size
      })
      const data = response?.code === 200 ? response.data : null
      departmentDetail.list = data?.list || []
      departmentDetail.total = data?.total || 0
      departmentDetail.page = data?.page || page
    } finally {
      departmentDetail.loading = false
    }
  }

  const changeDepartmentPage = (page: number) => void loadDepartmentUsers(page)
  const openDepartmentUser = (item: AnyRow) => {
    departmentDrawerVisible.value = false
    openHeartRatePortrait(item)
  }
  const closeDepartmentDrilldown = () => charts.dept?.dispatchAction({ type: 'downplay' })

  async function openHeaderMetric(key: string) {
    Object.assign(metricDrawer, {
      visible: true,
      key,
      zone: '',
      summary: null,
      list: [],
      total: 0,
      page: 1
    })
    if (['abnormalUsers', 'coveredUsers'].includes(key)) await loadMetricUsers(1)
  }

  const openScopeDetail = () => void openHeaderMetric('coverageDays')
  const openZoneDetail = (zone: AnyRow) => {
    Object.assign(metricDrawer, {
      visible: true,
      key: 'zone',
      zone: zone.filter || zone.key,
      summary: zone,
      list: [],
      total: 0,
      page: 1
    })
    void loadMetricUsers(1)
  }

  async function loadMetricUsers(page = metricDrawer.page) {
    metricDrawer.loading = true
    const { startDate, endDate } = periodRange.value
    try {
      const response = await getHeartRatePeriodUsers({
        mode: metricDrawer.key === 'abnormalUsers' ? 'abnormal' : 'covered',
        zone: metricDrawer.zone || undefined,
        startDate,
        endDate,
        page,
        size: metricDrawer.size
      })
      const data = response?.code === 200 ? response.data : null
      metricDrawer.list = data?.list || []
      metricDrawer.total = data?.total || 0
      metricDrawer.page = data?.page || page
      if (metricDrawer.summary) metricDrawer.summary = { ...metricDrawer.summary, count: data?.total || 0 }
    } finally {
      metricDrawer.loading = false
    }
  }

  const changeMetricPage = (page: number) => void loadMetricUsers(page)
  const openMetricUser = (item: AnyRow) => {
    metricDrawer.visible = false
    if (metricDrawer.key === 'abnormalUsers' || (metricDrawer.key === 'zone' && metricDrawer.zone !== 'normal')) {
      openHeartRatePortrait(item)
    } else {
      goToPortrait(item)
    }
  }

  function openHeartRatePortrait(item: AnyRow) {
    const empCode = item.userCode || item.empCode
    const { startDate, endDate } = periodRange.value
    void router.push({
      path: '/health-monitor/health-portrait',
      query: {
        empCode,
        focus: 'heartRate-abnormal',
        period: activePeriod.value,
        startDate,
        endDate
      }
    })
  }

  function goToPortrait(item: AnyRow) {
    const empCode = item.userCode || item.empCode
    void router.push({
      path: '/health-monitor/health-portrait',
      query: empCode ? { empCode } : { name: item.userName }
    })
  }

  const showDetail = (item: AnyRow) => {
    detailItem.value = item
    detailVisible.value = true
  }
  const fmtTime = (value: unknown) => value ? dayjs(value as string).format('MM-DD HH:mm') : ''
  const handleResize = () => scheduleResize()
  const handleVisibility = () => {
    if (document.hidden) {
      stopRefresh()
      return
    }
    void fetchData()
    startRefresh()
  }

  watch(() => route.query, (query) => applyRouteContext(query, pageReady), { deep: true })

  onMounted(async () => {
    applyRouteContext(route.query, false)
    pageReady = true
    tickClock()
    startClock()
    pageLoading.value = true
    try {
      await fetchData()
    } finally {
      pageLoading.value = false
    }
    void nextTick(top5Scroll.start)
    startRefresh()
    window.addEventListener('resize', handleResize)
    document.addEventListener('visibilitychange', handleVisibility)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    document.removeEventListener('visibilitychange', handleVisibility)
    Object.values(charts).forEach((chart) => chart?.dispose?.())
  })

  return {
    _top5Paused,
    activePeriod,
    anomalyEmptyText,
    anomalyList,
    anomalyScopeText,
    changeDepartmentPage,
    changeMetricPage,
    changeSnapshotAnomalyPage,
    closeDepartmentDrilldown,
    coverageSummary,
    currentAnomalyPanel,
    currentTime,
    dailyRiskRows,
    departmentDetail,
    departmentDrawerTitle,
    departmentDrawerVisible,
    deptRef,
    detailItem,
    detailVisible,
    displayedTop5,
    exportExcel,
    fmtTime,
    goToPortrait,
    headerKpis,
    hrZones,
    isDashboardDrilldown,
    metricDailyRows,
    metricDrawer,
    metricDrawerKind,
    metricDrawerTitle,
    metricPeriodLabel,
    openDepartmentUser,
    openHeaderMetric,
    openHeartRatePortrait,
    openMetricUser,
    openScopeDetail,
    openZoneDetail,
    overview,
    pageLoading,
    periodOptions,
    periodRange,
    showDetail,
    snapshotAnomalyPage,
    snapshotAnomalySize,
    snapshotAnomalyTotal,
    switchPeriod,
    top5Data,
    top5Max,
    top5ScrollRef,
    trendRef,
    trendScopeText,
    trendTitle
  }
}
