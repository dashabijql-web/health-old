import { buildDeptChartOption, buildDeptPersonChartOption } from './dashboard-chart-options'
import { fetchDashboardDeptPersonChartData } from './dashboard-chart-data'
import { bindDashboardChart } from './dashboard-chart-methods'

const CHART_RETRY_LIMIT = 12
const CHART_RETRY_MS = 160

function formatDayRange(daysBack = 6) {
  const today = new Date()
  const start = new Date(today)
  start.setDate(start.getDate() - daysBack)
  const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  return [fmt(start), fmt(today)]
}

export const dashboardViewActions = {
  goToEmployeeProfile(item) {
    const code = item?.userCode || item?.empCode
    if (!code) return
    this.$router.push({
      path: '/health-monitor/employee-profile',
      query: {
        empCode: code,
        empName: item.userName || item.empName || '',
        deptName: item.deptName || '',
        jobTypeName: item.jobTypeName || '',
        phone: item.phone || '',
        imei: item.imei || '',
        from: this.$route.fullPath
      }
    })
  },

  goToCommandIncident(event) {
    if (!event?.id || !event?.occurredAt) return
    this.$router.push({
      path: '/safety-command/index',
      query: {
        warningId: String(event.id),
        occurredAt: event.occurredAt,
        incidentId: event.incidentId || '',
        person: event.userName || '',
        area: event.location || event.deptName || '',
        from: 'dashboard'
      }
    })
  },

  openDeptPersonModal() {
    this.deptPersonModal.dateRange = formatDayRange()
    this.deptPersonModal.visible = true
  },

  async loadDeptPersonChart(elArg) {
    const modal = this.deptPersonModal
    modal.loading = true
    const [startTime, endTime] = modal.dateRange
    try {
      const chartData = await fetchDashboardDeptPersonChartData({ startTime, endTime })
      if (!chartData) return
      await this.$nextTick()
      const el = elArg || this.$refs.deptPersonChartRef
      if (!el) return
      bindDashboardChart(modal, 'chart', el)
      modal.chart.setOption(buildDeptPersonChartOption(chartData))
    } finally {
      modal.loading = false
    }
  },

  switchPeriod(val) {
    if (this.activePeriod === val) return
    this.activePeriod = val
    this.fetchData()
  },

  getReadyChartDom(target, retryFn) {
    const dom = typeof target === 'string' ? document.getElementById(target) : target
    if (!dom) return null
    const retryKey = typeof target === 'string' ? target : (dom.id || retryFn?.name || 'chart')
    if (dom.clientWidth > 0 && dom.clientHeight > 0) {
      if (this._chartRetryCounts) this._chartRetryCounts[retryKey] = 0
      return dom
    }
    if (!retryFn) return null
    const count = this._chartRetryCounts?.[retryKey] || 0
    if (count >= CHART_RETRY_LIMIT) return null
    if (this._chartRetryCounts) this._chartRetryCounts[retryKey] = count + 1
    const token = this._chartRetryToken
    setTimeout(() => {
      if (this._chartRetryToken !== token) return
      retryFn.call(this)
    }, CHART_RETRY_MS)
    return null
  },

  initDeptChart() {
    const dom = this.getReadyChartDom('deptDataChart', this.initDeptChart)
    if (!dom) return
    const chart = bindDashboardChart(this.charts, 'dept', dom)
    chart.setOption(buildDeptChartOption({
      deptStatsList: this.deptPersonStatsList,
      deptDataList: this.deptDataList,
      riskDeptList: this.riskDeptList
    }))
    chart.off('click')
    chart.on('click', params => {
      if (params.name) this.openDeptDetailModal(params.name)
    })
    chart.getZr().setCursorStyle('pointer')
    this.$nextTick(() => { chart.resize() })
  }
}
