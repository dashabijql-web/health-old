import {
  getMetricDailyRisk,
  getMetricDepartmentRisk,
  getMetricDepartmentUsers,
  getMetricPeriodUsers,
  getMetricRiskSummary
} from '@/api/period-risk'
import { fetchMetricData } from './metric-data-loader'

export function createPeriodRiskMixin(config) {
  return {
    components: { PeriodRiskDrawer: () => import('./PeriodRiskDrawer.vue') },
    data() {
      return {
        riskSummary: { coveredUsers: 0, abnormalUsers: 0, abnormalRecords: 0, totalRecords: 0, normalUsers: 0, lowUsers: 0, warningUsers: 0, dangerUsers: 0 },
        dailyRiskRows: [],
        deptRiskData: [],
        riskDrawer: { visible: false, kind: 'users', key: '', zone: '', title: '', loading: false, rows: [], total: 0, page: 1, size: 12, dept: '', summary: null }
      }
    },
    computed: {
      periodCoverageText() {
        const rows = this.dailyRiskRows.filter(row => Number(row.coveredUsers || 0) > 0)
        if (!rows.length) return '暂无有效覆盖数据'
        const average = Math.round(rows.reduce((sum, row) => sum + Number(row.coveredUsers || 0), 0) / rows.length)
        return `${rows.length} 个有数据日 · 日均覆盖 ${average} 人`
      },
      riskDrawerDailyRows() {
        return [...this.dailyRiskRows].sort((a, b) => String(b.date).localeCompare(String(a.date)))
      }
    },
    methods: {
      async switchPeriod(value) {
        if (this.activePeriod === value) return
        this.riskDrawer.visible = false
        this.activePeriod = value
        await Promise.allSettled([
          this.loadPeriodRisk(),
          typeof this.loadPeriodSupplement === 'function' ? this.loadPeriodSupplement() : Promise.resolve()
        ])
      },
      async loadPeriodRisk() {
        const params = this.periodRange
        const [summary, daily, departments, top] = await Promise.all([
          fetchMetricData(() => getMetricRiskSummary(config.path, params), this.riskSummary),
          fetchMetricData(() => getMetricDailyRisk(config.path, params), []),
          fetchMetricData(() => getMetricDepartmentRisk(config.path, params), []),
          fetchMetricData(() => getMetricPeriodUsers(config.path, { ...params, mode: 'abnormal', page: 1, size: 10 }), { list: [] })
        ])
        this.riskSummary = summary
        this.dailyRiskRows = daily
        this.deptRiskData = departments
        this.top5Data = top.list || []
        this.$nextTick(() => {
          this.renderRiskTrend?.(daily)
          this.renderRiskDepartments?.(departments)
          this.startTop5Scroll?.()
        })
      },
      openPeriodPortrait(item) {
        const code = item.userCode || item.empCode
        const { startDate, endDate } = this.periodRange
        this.$router.push({ path: '/health-monitor/health-portrait', query: { empCode: code, focus: `${config.focus}-abnormal`, period: this.activePeriod, startDate, endDate } })
      },
      openDepartmentRisk(row) {
        this.riskDrawer = { ...this.riskDrawer, visible: true, kind: 'users', key: 'department', zone: '', title: `${row.deptName} · ${this.metricPeriodLabel}${config.label}异常人员`, dept: row.deptName, page: 1, summary: null }
        this.loadRiskDrawerUsers(1)
      },
      openRiskMetric(key) {
        const titles = { abnormalUsers: `${this.metricPeriodLabel}异常人员`, coveredUsers: `${this.metricPeriodLabel}覆盖人员`, abnormalRecords: `${this.metricPeriodLabel}异常记录按日明细`, totalRecords: `${this.metricPeriodLabel}采样记录按日明细` }
        const daily = ['abnormalRecords', 'totalRecords'].includes(key)
        this.riskDrawer = { ...this.riskDrawer, visible: true, kind: daily ? 'daily' : 'users', key, zone: '', title: titles[key] || '周期风险证据', dept: '', page: 1, summary: null }
        if (!daily) this.loadRiskDrawerUsers(1)
      },
      openRiskZone(zone) {
        const summary = {
          count: zone.count,
          pct: zone.pct,
          label: zone.label,
          range: zone.range,
          color: zone.color,
          period: this.metricPeriodLabel,
          basis: '周期内按人员最高风险等级去重'
        }
        this.riskDrawer = {
          ...this.riskDrawer,
          visible: true,
          kind: 'users',
          key: 'zone',
          zone: zone.filter || zone.key,
          title: zone.key === 'normal'
            ? `${this.metricPeriodLabel}保持正常区间人员`
            : `${this.metricPeriodLabel}最高风险为${zone.label}的人员`,
          dept: '',
          page: 1,
          summary
        }
        this.loadRiskDrawerUsers(1)
      },
      async loadRiskDrawerUsers(page = this.riskDrawer.page) {
        this.riskDrawer.loading = true
        const params = { ...this.periodRange, page, size: this.riskDrawer.size }
        const data = this.riskDrawer.dept
          ? await fetchMetricData(() => getMetricDepartmentUsers(config.path, { ...params, dept: this.riskDrawer.dept }), { list: [], total: 0, page })
          : await fetchMetricData(() => getMetricPeriodUsers(config.path, {
            ...params,
            mode: this.riskDrawer.key === 'abnormalUsers' ? 'abnormal' : 'covered',
            zone: this.riskDrawer.zone || undefined
          }), { list: [], total: 0, page })
        const summary = this.riskDrawer.summary
          ? { ...this.riskDrawer.summary, count: data.total || 0 }
          : null
        this.riskDrawer = { ...this.riskDrawer, rows: data.list || [], total: data.total || 0, page: data.page || page, loading: false, summary }
      },
      openRiskDrawerPerson(item) {
        this.riskDrawer.visible = false
        if (this.riskDrawer.key === 'zone' && this.riskDrawer.zone === 'normal') this.goToPortrait(item)
        else this.openPeriodPortrait(item)
      }
    }
  }
}
