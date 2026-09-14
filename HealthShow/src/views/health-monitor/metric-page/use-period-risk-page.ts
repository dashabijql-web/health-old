import { computed, nextTick, reactive, ref, type ComputedRef, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  getMetricDailyRisk,
  getMetricDepartmentRisk,
  getMetricDepartmentUsers,
  getMetricPeriodUsers,
  getMetricRiskSummary
} from '@/api/period-risk'
import { fetchMetricData } from './metric-data-loader'
import type { MetricPeriod } from './use-metric-page-lifecycle'

type MetricRow = Record<string, any>

interface PeriodRiskConfig {
  path: string
  focus: string
  label: string
  activePeriod: Ref<MetricPeriod>
  periodRange: ComputedRef<{ startDate: string; endDate: string }>
  metricPeriodLabel: ComputedRef<string>
  top5Data: Ref<MetricRow[]>
  renderRiskTrend: (rows: MetricRow[]) => void
  renderRiskDepartments: (rows: MetricRow[]) => void
  startTop5Scroll: () => void
  loadPeriodSupplement?: () => void | Promise<void>
  goToPortrait: (item: MetricRow) => void
}

const createRiskSummary = () => ({
  coveredUsers: 0,
  abnormalUsers: 0,
  abnormalRecords: 0,
  totalRecords: 0,
  normalUsers: 0,
  lowUsers: 0,
  warningUsers: 0,
  dangerUsers: 0
})

export function usePeriodRiskPage(config: PeriodRiskConfig) {
  const router = useRouter()
  const riskSummary = ref(createRiskSummary())
  const dailyRiskRows = ref<MetricRow[]>([])
  const deptRiskData = ref<MetricRow[]>([])
  const riskDrawer = reactive({
    visible: false,
    kind: 'users' as 'users' | 'daily',
    key: '',
    zone: '',
    title: '',
    loading: false,
    rows: [] as MetricRow[],
    total: 0,
    page: 1,
    size: 12,
    dept: '',
    summary: null as MetricRow | null
  })

  const periodCoverageText = computed(() => {
    const rows = dailyRiskRows.value.filter((row) => Number(row.coveredUsers || 0) > 0)
    if (!rows.length) return '暂无有效覆盖数据'
    const average = Math.round(
      rows.reduce((sum, row) => sum + Number(row.coveredUsers || 0), 0) / rows.length
    )
    return `${rows.length} 个有数据日 · 日均覆盖 ${average} 人`
  })
  const riskDrawerDailyRows = computed(() => {
    return [...dailyRiskRows.value].sort((a, b) => String(b.date).localeCompare(String(a.date)))
  })

  async function switchPeriod(value: MetricPeriod) {
    if (config.activePeriod.value === value) return
    riskDrawer.visible = false
    config.activePeriod.value = value
    await Promise.allSettled([
      loadPeriodRisk(),
      config.loadPeriodSupplement?.() || Promise.resolve()
    ])
  }

  async function loadPeriodRisk() {
    const params = config.periodRange.value
    const [summary, daily, departments, top] = await Promise.all([
      fetchMetricData(() => getMetricRiskSummary(config.path, params), riskSummary.value),
      fetchMetricData(() => getMetricDailyRisk(config.path, params), []),
      fetchMetricData(() => getMetricDepartmentRisk(config.path, params), []),
      fetchMetricData(
        () => getMetricPeriodUsers(config.path, { ...params, mode: 'abnormal', page: 1, size: 10 }),
        { list: [] }
      )
    ])
    riskSummary.value = summary
    dailyRiskRows.value = daily
    deptRiskData.value = departments
    config.top5Data.value = top.list || []
    void nextTick(() => {
      config.renderRiskTrend(daily)
      config.renderRiskDepartments(departments)
      config.startTop5Scroll()
    })
  }

  function openPeriodPortrait(item: MetricRow) {
    const empCode = item.userCode || item.empCode
    const { startDate, endDate } = config.periodRange.value
    void router.push({
      path: '/health-monitor/health-portrait',
      query: {
        empCode,
        focus: `${config.focus}-abnormal`,
        period: config.activePeriod.value,
        startDate,
        endDate
      }
    })
  }

  function openDepartmentRisk(row: MetricRow) {
    Object.assign(riskDrawer, {
      visible: true,
      kind: 'users',
      key: 'department',
      zone: '',
      title: `${row.deptName} · ${config.metricPeriodLabel.value}${config.label}异常人员`,
      dept: row.deptName,
      page: 1,
      summary: null
    })
    void loadRiskDrawerUsers(1)
  }

  function openRiskMetric(key: string) {
    const titles: Record<string, string> = {
      abnormalUsers: `${config.metricPeriodLabel.value}异常人员`,
      coveredUsers: `${config.metricPeriodLabel.value}覆盖人员`,
      abnormalRecords: `${config.metricPeriodLabel.value}异常记录按日明细`,
      totalRecords: `${config.metricPeriodLabel.value}采样记录按日明细`
    }
    const daily = ['abnormalRecords', 'totalRecords'].includes(key)
    Object.assign(riskDrawer, {
      visible: true,
      kind: daily ? 'daily' : 'users',
      key,
      zone: '',
      title: titles[key] || '周期风险证据',
      dept: '',
      page: 1,
      summary: null
    })
    if (!daily) void loadRiskDrawerUsers(1)
  }

  function openRiskZone(zone: MetricRow) {
    const summary = {
      count: zone.count,
      pct: zone.pct,
      label: zone.label,
      range: zone.range,
      color: zone.color,
      period: config.metricPeriodLabel.value,
      basis: '周期内按人员最高风险等级去重'
    }
    Object.assign(riskDrawer, {
      visible: true,
      kind: 'users',
      key: 'zone',
      zone: zone.filter || zone.key,
      title: zone.key === 'normal'
        ? `${config.metricPeriodLabel.value}保持正常区间人员`
        : `${config.metricPeriodLabel.value}最高风险为${zone.label}的人员`,
      dept: '',
      page: 1,
      summary
    })
    void loadRiskDrawerUsers(1)
  }

  async function loadRiskDrawerUsers(page = riskDrawer.page) {
    riskDrawer.loading = true
    const params = { ...config.periodRange.value, page, size: riskDrawer.size }
    const data = riskDrawer.dept
      ? await fetchMetricData(
          () => getMetricDepartmentUsers(config.path, { ...params, dept: riskDrawer.dept }),
          { list: [], total: 0, page }
        )
      : await fetchMetricData(
          () => getMetricPeriodUsers(config.path, {
            ...params,
            mode: riskDrawer.key === 'abnormalUsers' ? 'abnormal' : 'covered',
            zone: riskDrawer.zone || undefined
          }),
          { list: [], total: 0, page }
        )
    const summary = riskDrawer.summary ? { ...riskDrawer.summary, count: data.total || 0 } : null
    Object.assign(riskDrawer, {
      rows: data.list || [],
      total: data.total || 0,
      page: data.page || page,
      loading: false,
      summary
    })
  }

  function openRiskDrawerPerson(item: MetricRow) {
    riskDrawer.visible = false
    if (riskDrawer.key === 'zone' && riskDrawer.zone === 'normal') config.goToPortrait(item)
    else openPeriodPortrait(item)
  }

  return {
    dailyRiskRows,
    deptRiskData,
    loadPeriodRisk,
    loadRiskDrawerUsers,
    openDepartmentRisk,
    openPeriodPortrait,
    openRiskDrawerPerson,
    openRiskMetric,
    openRiskZone,
    periodCoverageText,
    riskDrawer,
    riskDrawerDailyRows,
    riskSummary,
    switchPeriod
  }
}
