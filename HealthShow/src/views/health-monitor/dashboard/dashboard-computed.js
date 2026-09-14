import dayjs from 'dayjs'
import {
  buildDashboardAiSummary,
  buildDispatchActionItems,
  buildDispatchPriority,
  buildRiskDeptList,
  buildWarningTypeData,
  getFocusWarningEvents,
  getLatestDangerEvent
} from './dashboard-summary'
import {
  buildDashboardDeviceCards,
  buildDashboardHealthExceptionCards,
  buildDashboardHeaderKpis,
  buildDashboardMetricCards,
  buildDashboardTop5DisplayData,
  buildDashboardVitalCards
} from './dashboard-view-model'

export const dashboardComputed = {
  mineAiRendered() {
    if (!this.mineAiReport) return ''
    return this.mineAiReport
      .replace(/^## (.+)$/gm, '<h4>$1</h4>')
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/\n/g, '<br>')
  },

  dashboardAiSummary() {
    return buildDashboardAiSummary({
      mineAiReport: this.mineAiReport,
      kpiUnhandledHigh: this.kpiUnhandledHigh,
      preShiftData: this.preShiftData,
      riskDeptList: this.riskDeptList,
      latestDangerEvent: this.latestDangerEvent
    })
  },

  dispatchPriority() {
    return buildDispatchPriority({
      kpiUnhandledHigh: this.kpiUnhandledHigh,
      preShiftData: this.preShiftData,
      focusWarningEvents: this.focusWarningEvents
    })
  },

  dispatchActionItems() {
    return buildDispatchActionItems({
      warningEvents: this.warningEvents,
      focusWarningEvents: this.focusWarningEvents,
      kpiUnhandledHigh: this.kpiUnhandledHigh,
      periodLabel: this.periodLabel,
      preShiftData: this.preShiftData
    })
  },

  focusWarningEvents() {
    return getFocusWarningEvents(this.warningEvents)
  },

  periodRange() {
    const today = dayjs()
    if (this.activePeriod === 'day') {
      return { startTime: today.format('YYYY-MM-DD'), endTime: today.format('YYYY-MM-DD') }
    }
    if (this.activePeriod === 'week') {
      return {
        startTime: today.subtract(6, 'day').format('YYYY-MM-DD'),
        endTime: today.format('YYYY-MM-DD')
      }
    }
    return {
      startTime: today.subtract(29, 'day').format('YYYY-MM-DD'),
      endTime: today.format('YYYY-MM-DD')
    }
  },

  periodLabel() {
    return { day: '当日', week: '近7日', month: '近30日' }[this.activePeriod]
  },

  trendDayCount() {
    return { day: 24, week: 7, month: 30 }[this.activePeriod]
  },

  trendXLabels() {
    if (this.activePeriod === 'day') {
      return Array.from({ length: 24 }, (_, i) => `${i}时`)
    }
    if (this.activePeriod === 'week') {
      return ['7天前', '6天前', '5天前', '4天前', '3天前', '昨天', '今天']
    }
    return Array.from({ length: 30 }, (_, i) => {
      const d = dayjs().subtract(29 - i, 'day')
      return (i % 7 === 0 || i === 29) ? d.format('MM/DD') : ''
    })
  },

  trendBlockTitle() {
    return { day: '今日体征趋势', week: '近7日体征趋势', month: '近30日体征趋势' }[this.activePeriod]
  },

  hourDistTitle() {
    return { day: '今日预警时段', week: '近7日预警时段', month: '近30日预警时段' }[this.activePeriod]
  },

  totalRecords() {
    const d = this.checkData
    if (!d || Object.keys(d).length === 0) return null
    return (d.heartRate || 0) + (d.bloodOxygen || 0) + (d.steps || 0) + (d.temperature || 0) + (d.pressure || 0)
  },

  kpiOnline() {
    return Math.round(this.deviceStats.total * (this.deviceStats.usageRate || 0) / 100)
  },

  deviceOnline() {
    if (this.commandSummary?.device?.online !== undefined) return this.commandSummary.device.online
    return Math.round((this.deviceStats.total || 0) * (this.deviceStats.activeRate || 0) / 100)
  },

  deviceOffline() {
    if (this.commandSummary?.device?.offline !== undefined) return this.commandSummary.device.offline
    return Math.max(0, (this.deviceStats.total || 0) - this.deviceOnline)
  },

  lowBatteryCount() {
    const capability = this.commandSummary?.device?.lowBattery
    if (capability) return capability.status === 'AVAILABLE' ? capability.value : null
    if (this.deviceStats.lowBattery !== undefined && this.deviceStats.lowBattery !== null) {
      return this.deviceStats.lowBattery
    }
    if (this.deviceStats.lowBatteryRate && this.deviceStats.total) {
      return Math.round((this.deviceStats.total || 0) * (this.deviceStats.lowBatteryRate || 0) / 100)
    }
    return null
  },

  warningRateList() {
    return this.warningRates || []
  },

  kpiWarningDelta() {
    if (!this.kpiYesterdayWarnings) return null
    return Math.round((this.kpiTodayWarnings - this.kpiYesterdayWarnings) / this.kpiYesterdayWarnings * 100)
  },

  unhandledHighCount() {
    return (this.warningEvents || []).filter((e) => !e.handled && e.level === 'danger').length
  },

  headerKpis() {
    return buildDashboardHeaderKpis({
      kpiRealtimeOnline: this.kpiRealtimeOnline,
      kpiRealtimeTotal: this.kpiRealtimeTotal,
      kpiTodayWarnings: this.kpiTodayWarnings,
      kpiYesterdayWarnings: this.kpiYesterdayWarnings,
      kpiUnhandledHigh: this.kpiUnhandledHigh,
      kpiUnhandledMid: this.kpiUnhandledMid,
      warningEvents: this.warningEvents,
      personCounts: this.personCounts,
      deviceActivationRate: this.deviceStats.total > 0 ? Math.round(this.deviceStats.activeRate || 0) : null,
      preShiftData: this.preShiftData,
      periodLabel: this.periodLabel
    })
  },

  totalPersons() {
    const v = this.personCounts.totalPersons
    return v != null ? Number(v) : null
  },

  metricCards() {
    return buildDashboardMetricCards({
      metricList: this.metricList,
      personCounts: this.personCounts,
      checkData: this.checkData,
      kpiRealtimeTotal: this.kpiRealtimeTotal
    })
  },

  healthExceptionCards() {
    return buildDashboardHealthExceptionCards({
      vitalCards: this.vitalCards,
      warningEvents: this.warningEvents,
      healthSnapshot: this.healthSnapshot
    })
  },

  healthSnapshotStatusText() {
    return {
      NORMAL: '正常',
      PARTIAL: '部分覆盖',
      STALE: '数据过期',
      NO_DATA: '暂无数据'
    }[this.healthSnapshot?.status] || (this.lastRefreshTime ? '暂无数据' : '加载中')
  },

  vitalCards() {
    return buildDashboardVitalCards({
      bodyIndicators: this.bodyIndicators,
      warningRates: this.warningRates
    })
  },

  healthAssess() {
    const b = this.bodyIndicators
    return [
      {
        label: '心率',
        pct: b.avgHeartRate ? Math.min(100, Math.round((b.avgHeartRate - 40) / (120 - 40) * 100)) : 0,
        color: (b.avgHeartRate > 100 || b.avgHeartRate < 55) ? '#ff5252' : '#38ef7d',
        tag: !b.avgHeartRate ? '--' : (b.avgHeartRate > 100) ? '偏快' : (b.avgHeartRate < 55) ? '偏慢' : '正常'
      },
      {
        label: '血氧',
        pct: b.avgBloodOxygen || 0,
        color: (b.avgBloodOxygen < 90) ? '#ff5252' : (b.avgBloodOxygen < 95) ? '#ffd200' : '#38ef7d',
        tag: !b.avgBloodOxygen ? '--' : (b.avgBloodOxygen < 90) ? '过低' : (b.avgBloodOxygen < 95) ? '偏低' : '良好'
      },
      {
        label: '压力',
        pct: Math.min(100, b.avgPressure || 0),
        color: (b.avgPressure > 80) ? '#ff5252' : (b.avgPressure > 60) ? '#ffd200' : '#38ef7d',
        tag: !b.avgPressure ? '--' : (b.avgPressure > 80) ? '过高' : (b.avgPressure > 60) ? '偏高' : '适中'
      }
    ]
  },

  latestDangerEvent() {
    return getLatestDangerEvent(this.warningEvents)
  },

  top5DisplayData() {
    return buildDashboardTop5DisplayData(this.top5Data)
  },

  top5Max() {
    const data = this.top5DisplayData
    return Math.max(...data.map((d) => d.count || 0), 1)
  },

  trendItems() {
    const base = [
      { key: 'heartRate', label: '心率', color: '#00d4ff' },
      { key: 'bloodOxygen', label: '血氧', color: '#67C23A' },
      { key: 'pressure', label: '压力', color: '#3eb7ff' }
    ]
    return base.map((item) => {
      const found = this.warningRates.find((rateItem) => rateItem.name && rateItem.name.includes(item.label))
      return { ...item, latest: found ? found.rate : 0 }
    })
  },

  warnTypeData() {
    return buildWarningTypeData({
      warningTypesData: this.warningTypesData,
      warningEvents: this.warningEvents
    })
  },

  riskDeptList() {
    return buildRiskDeptList(this.deptDataList)
  },

  deviceCards() {
    return buildDashboardDeviceCards({
      deviceStats: this.deviceStats,
      deviceOnline: this.deviceOnline,
      deviceOffline: this.deviceOffline,
      lowBatteryCount: this.lowBatteryCount,
      dataInterrupted: this.commandSummary?.device?.dataInterrupted,
      faulted: this.commandSummary?.device?.faulted
    })
  }
}
