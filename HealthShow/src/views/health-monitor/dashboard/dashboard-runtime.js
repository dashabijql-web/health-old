import dayjs from 'dayjs'
import { getMineAiReport, generateMineAiReport } from '@/api/ai'
import {
  buildDashboardAbnormalUserCount,
  buildDashboardUnhandledStats,
  collectDashboardNewDangerEvents,
  fetchDashboardBodyIndicatorData,
  fetchDashboardHealthSnapshot,
  fetchCommandCenterDashboardSummary,
  fetchDashboardDeptState,
  fetchDashboardDeviceState,
  fetchDashboardKpiSnapshot,
  fetchDashboardOverviewData,
  fetchDashboardPersonCountData,
  fetchDashboardPreShiftData,
  fetchDashboardTop5Data,
  fetchDashboardWarningEventState,
  fetchUnifiedControlSnapshot,
  formatDashboardRefreshText
} from './dashboard-runtime-data'

export const dashboardRuntimeMethods = {
  async handleMineAi(force = false) {
    if (this.mineAiLoading) return
    this.mineAiLoading = true
    try {
      const res = await generateMineAiReport(force)
      if (res.code === 200 && res.data) {
        this.mineAiReport = res.data.reportContent
        this.mineAiTime = res.data.generateTime
        this.$message.success('全矿 AI 分析完成')
      } else {
        this.$message.error(res.message || '生成失败')
      }
    } catch {
      this.$message.error('AI 服务暂时不可用，请稍后重试')
    } finally {
      this.mineAiLoading = false
    }
  },

  async loadMineAiCache() {
    try {
      const res = await getMineAiReport()
      if (res.code === 200 && res.data) {
        this.mineAiReport = res.data.reportContent
        this.mineAiTime = res.data.generateTime
      }
    } catch {}
  },

  toggleMineAiPanel() {
    if (this.mineAiReport) {
      this.mineAiDialogVisible = true
      return
    }
    return this.handleMineAi(false)
  },

  initTime() {
    this.updateTime()
    this._timeTask?.start()
  },

  updateTime() {
    this.currentTime = dayjs().format('YYYY年MM月DD日 HH:mm:ss')
  },

  toggleFullscreen() {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(() => {})
    } else {
      document.exitFullscreen().catch(() => {})
    }
  },

  triggerDangerNotification(event) {
    if (!('Notification' in window) || Notification.permission !== 'granted') return
    const typeNames = { heartRate: '心率异常', bloodOxygen: '血氧偏低', temperature: '体温异常', pressure: '压力异常', SOS: 'SOS求助', fall: '跌倒' }
    const typeName = typeNames[event.type] || event.type || '健康预警'
    const n = new Notification(`高危预警：${event.userName || '未知人员'}`, {
      body: `${typeName}  ${event.value || ''}  —  请立即处理`,
      icon: '/favicon.ico',
      tag: `alert-${event.id}`
    })
    n.onclick = () => { window.focus(); n.close() }
  },

  async fetchData(force = false) {
    if (this.isRefreshing) return
    this.isRefreshing = true
    try {
      const snapshot = await fetchUnifiedControlSnapshot(this.periodRange, this.activePeriod)
      if (snapshot) this.applyUnifiedControlSnapshot(snapshot)
      await this.fetchWarningEvents()
      this.lastRefreshTime = Date.now()
      this.updateRefreshText()
      const total = this.deviceStats.total || 0
      const onDutyCount = Math.round(total * (this.deviceStats.usageRate || 0) / 100)
      this.onDutyStats.onDuty = onDutyCount
      this.onDutyStats.offDuty = total - onDutyCount
      this.$nextTick(() => {
        this.initHourDistChart()
        this.initWarnTypeChart()
        this.initUnifiedTrendChart()
        this.initDeptChart()
        this.initEnvHealthChart()
        this.initDeviceCharts()
      })
    } finally {
      this.isRefreshing = false
    }
  },

  async fetchKpiData() {
    if (this.isRefreshing) return
    const [snapshot] = await Promise.all([
      fetchDashboardKpiSnapshot(this.warningEvents, this.personCounts?.totalPersons || this.deviceStats.total),
      this.fetchCommandSummary(this.activePeriod)
    ])
    this.kpiRealtimeOnline = snapshot.kpiRealtimeOnline
    this.kpiRealtimeTotal = snapshot.kpiRealtimeTotal
    this.kpiTodayWarnings = this.commandSummary?.warning?.periodNew ?? snapshot.kpiTodayWarnings
    this.kpiYesterdayWarnings = snapshot.kpiYesterdayWarnings
    this.kpiUnhandledHigh = this.commandSummary?.warning?.criticalPending ?? snapshot.kpiUnhandledHigh
    this.kpiUnhandledMid = snapshot.kpiUnhandledMid
  },

  async fetchCommandSummary(period = this.activePeriod) {
    const summary = await fetchCommandCenterDashboardSummary(period)
    if (!summary) return
    this.applyCommandSummary(summary)
  },

  applyCommandSummary(summary) {
    this.commandSummary = summary
    this.kpiTodayWarnings = summary.warning?.periodNew ?? this.kpiTodayWarnings
    this.kpiUnhandledHigh = summary.warning?.criticalPending ?? this.kpiUnhandledHigh
    this.preShiftData = {
      ...this.preShiftData,
      qualifiedCount: summary.admission?.passed ?? this.preShiftData.qualifiedCount,
      failedCount: summary.admission?.prohibited ?? this.preShiftData.failedCount
    }
    if (summary.device) {
      this.deviceStats = {
        ...this.deviceStats,
        total: summary.device.total ?? this.deviceStats.total,
        boundDevices: summary.device.total ?? this.deviceStats.boundDevices,
        activeRate: summary.device.onlineRate ?? this.deviceStats.activeRate
      }
    }
  },

  applyUnifiedControlSnapshot(snapshot) {
    if (snapshot.overview) this.checkData = snapshot.overview
    if (snapshot.bodyIndicators) this.bodyIndicators = snapshot.bodyIndicators
    if (snapshot.deviceActivation) {
      this.deviceStats = snapshot.deviceActivation.stats || this.deviceStats
      this.warningRates = snapshot.deviceActivation.warningRates || []
    }
    if (snapshot.top5) this.top5Data = snapshot.top5
    if (snapshot.personCounts) this.personCounts = snapshot.personCounts
    if (snapshot.preShift) this.preShiftData = snapshot.preShift
    if (snapshot.deptHealthCounts) this.deptDataList = snapshot.deptHealthCounts
    if (snapshot.deptPersonStats) this.deptPersonStatsList = snapshot.deptPersonStats
    if (snapshot.dailyTrend) this.trendDailyData = snapshot.dailyTrend
    if (snapshot.warningDistribution) this.warningDistData = snapshot.warningDistribution
    if (snapshot.warningTypes) this.warningTypesData = snapshot.warningTypes
    if (snapshot.healthSnapshot) {
      this.healthSnapshot = snapshot.healthSnapshot
      this.kpiRealtimeOnline = snapshot.healthSnapshot.onlineUsers ?? this.kpiRealtimeOnline
    }
    this.kpiRealtimeTotal = snapshot.personCounts?.totalPersons ?? snapshot.deviceActivation?.stats?.total ?? this.kpiRealtimeTotal
    if (snapshot.commandSummary) this.applyCommandSummary(snapshot.commandSummary)
  },

  updateRefreshText() {
    this.lastRefreshText = formatDashboardRefreshText(this.lastRefreshTime)
  },

  async fetchTop5Data() {
    this.top5Data = await fetchDashboardTop5Data(this.periodRange)
  },

  async fetchDashboardData() {
    this.checkData = await fetchDashboardOverviewData(this.periodRange)
  },

  async fetchPreShiftRate(force = false) {
    const nextPreShiftData = await fetchDashboardPreShiftData(force)
    if (nextPreShiftData) this.preShiftData = nextPreShiftData
  },

  async fetchPersonCounts() {
    this.personCounts = await fetchDashboardPersonCountData(this.periodRange)
  },

  async fetchBodyIndicators() {
    this.bodyIndicators = await fetchDashboardBodyIndicatorData(this.periodRange)
  },

  async fetchHealthSnapshot() {
    const snapshot = await fetchDashboardHealthSnapshot()
    if (snapshot) this.healthSnapshot = snapshot
  },

  async fetchDeviceData() {
    const nextState = await fetchDashboardDeviceState(this.periodRange)
    this.deviceStats = nextState.deviceStats
    this.warningRates = nextState.warningRates
  },

  async fetchWarningEvents() {
    this.warningEvents = await fetchDashboardWarningEventState()
    this.onDutyStats.abnormal = buildDashboardAbnormalUserCount(this.warningEvents)
    const unhandled = buildDashboardUnhandledStats(this.warningEvents)
    this.kpiUnhandledMid = unhandled.kpiUnhandledMid
    if (!this.commandSummary) this.kpiUnhandledHigh = unhandled.kpiUnhandledHigh
    collectDashboardNewDangerEvents(this.warningEvents, this.seenAlertIds).forEach((event) => {
      this.triggerDangerNotification(event)
    })
  },

  async loadDeptData(force = false) {
    const nextState = await fetchDashboardDeptState(this.periodRange, force)
    this.deptDataList = nextState.deptDataList
    this.deptPersonStatsList = nextState.deptPersonStatsList
  },

  startAutoRefresh() {
    this._refreshTask?.start()
  },

  onVisibilityChange() {
    if (document.hidden) {
      this._timeTask?.stop()
      this._refreshTask?.stop()
      this._kpiRefreshTask?.stop()
      this._refreshTextTask?.stop()
    } else {
      this.fetchData(true)
      this._timeTask?.start()
      this.startAutoRefresh()
      this._kpiRefreshTask?.start()
      this._refreshTextTask?.start()
    }
  },

  handleResize() {
    this._resizeTask?.start()
  }
}
