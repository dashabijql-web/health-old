import dayjs from 'dayjs'
import { resolveCommandCenterIncident } from '@/api/command-center'
import { bindDashboardChart } from './dashboard-chart-methods'
import {
  buildEmpTrendChartOption,
  buildEmpRadarChartOption,
  buildWarnCurveChartOption
} from './dashboard-chart-options'
import {
  buildDashboardWarnCurveSeries,
  fetchDashboardEmployeeDetail,
  fetchDashboardWarnCurveState
} from './dashboard-detail-data'

export const dashboardDetailMethods = {
  async openEmployeeDrawer(item) {
    const userName = item.userName || item.name || ''
    const empCode = item.empCode || item.userCode || userName

    this.empDrawer.visible = true
    this.empDrawer.loading = true
    this.empDrawer.userName = userName
    this.empDrawer.abnormalCount = item.count || 0
    this.empDrawer.data = {}
    this.empDrawer.vitals = []
    this.empDrawer.warnings = []

    try {
      const detail = await fetchDashboardEmployeeDetail(empCode, userName)
      if (!detail) return
      this.empDrawer.data = detail.profile
      this.empDrawer.vitals = detail.vitals
      this.empDrawer.warnings = detail.warnings
      this.$nextTick(() => {
        this.initEmpTrendChart(detail.trend)
        this.initEmpRadarChart(detail.healthScores)
      })
    } catch {
      // API 失败时 drawer 仍然打开，显示"暂无数据"
    } finally {
      this.empDrawer.loading = false
    }
  },

  initEmpTrendChart(trend) {
    const dom = document.getElementById('empTrendChart')
    if (!dom) return
    const chart = bindDashboardChart(this.empDrawer, 'trendChart', dom)
    chart.setOption(buildEmpTrendChartOption(trend))
  },

  initEmpRadarChart(scores) {
    const dom = document.getElementById('empRadarChart')
    if (!dom) return
    const chart = bindDashboardChart(this.empDrawer, 'radarChart', dom)
    chart.setOption(buildEmpRadarChartOption(scores))
  },

  alertTypeLabel(type) {
    const map = { 1: '心率异常', 2: '血氧异常', 3: '体温异常', 4: '血压异常', 5: '综合异常' }
    return map[type] || '其他'
  },

  formatWarnTime(time) {
    if (!time) return '--'
    return dayjs(time).format('MM-DD HH:mm')
  },

  openHandleDialog(ev) {
    this.handleDialog.event = ev
    this.handleDialog.remark = ''
    this.handleDialog.visible = true
    this.handleDialog.submitting = false
  },

  async submitHandle() {
    const event = this.handleDialog.event
    if (!event) return
    this.handleDialog.submitting = true
    try {
      await resolveCommandCenterIncident(event.id, {
        occurredAt: event.occurredAt,
        remark: this.handleDialog.remark
      })
      this.handleDialog.visible = false
      await this.fetchWarningEvents()
      await this.fetchKpiData()
      this.$message?.success('预警已处理')
    } catch {
      this.$message?.error('处理失败，请重试')
    } finally {
      this.handleDialog.submitting = false
    }
  },

  formatTimeAgo(timestamp) {
    if (!timestamp) return ''
    const diff = Date.now() - new Date(timestamp).getTime()
    const mins = Math.floor(diff / 60000)
    if (mins < 1) return '刚刚'
    if (mins < 60) return `${mins}分钟前`
    const hours = Math.floor(mins / 60)
    if (hours < 24) return `${hours}小时前`
    return dayjs(timestamp).format('MM-DD HH:mm')
  },

  async openWarnCurve(ev) {
    if (!ev.userCode || !ev.time) return
    this.warnCurveModal.event = ev
    this.warnCurveModal.title = `${ev.userName} · ${ev.type} 附近时段健康曲线`
    this.warnCurveModal.loading = true
    this.warnCurveModal.hasData = false
    this.warnCurveModal.records = []
    this.warnCurveModal.visible = true

    try {
      const curveState = await fetchDashboardWarnCurveState(ev)
      this.warnCurveModal.title = curveState.title
      this.warnCurveModal.records = curveState.records
      this.warnCurveModal.hasData = curveState.hasData
    } catch {
      this.warnCurveModal.hasData = false
    } finally {
      this.warnCurveModal.loading = false
    }
  },

  initWarnCurveChart(elArg) {
    const dom = elArg || this.$refs.warnCurveChartRef
    if (!dom || !this.warnCurveModal.hasData) return
    const records = this.warnCurveModal.records
    const event = this.warnCurveModal.event
    const seriesDef = buildDashboardWarnCurveSeries(records, event?.type)
    const times = records.map(r => r.time ? String(r.time).slice(11, 16) : '')
    const warnTime = event?.time ? String(event.time).slice(11, 16) : null

    const chart = bindDashboardChart(this.warnCurveModal, 'chart', dom)
    chart.setOption(buildWarnCurveChartOption({
      times,
      series: seriesDef.map(item => ({
        label: item.label,
        color: item.color,
        values: records.map(record => record[item.key] != null
          ? (item.divisor ? +(record[item.key] / item.divisor).toFixed(1) : record[item.key])
          : null)
      })),
      warnTime,
      warnLevel: event?.level
    }))
  }
}
