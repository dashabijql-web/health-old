export function buildDashboardClosureLaneItems({
  warningSummary,
  preShiftData
}) {
  const critical = warningSummary?.criticalPending ?? 0
  const pending = warningSummary?.pendingTotal ?? 0
  const unassigned = warningSummary?.unassignedTotal ?? 0
  const overdue = warningSummary?.overdueTotal ?? 0
  return [
    {
      key: 'high',
      label: '高危闭环',
      value: critical,
      note: '通知页优先处置',
      tone: critical > 0 ? 'danger' : 'success',
      route: '/alert-management/notifications'
    },
    {
      key: 'pending',
      label: '今日待办',
      value: pending,
      note: '服务端全量统计',
      tone: pending > 0 ? 'warning' : 'success',
      route: '/alert-management/records'
    },
    {
      key: 'unassigned',
      label: '未分派事件',
      value: unassigned,
      note: '尚未明确处置责任人',
      tone: unassigned > 0 ? 'warning' : 'success',
      route: '/alert-management/notifications'
    },
    {
      key: 'overdue',
      label: '已超时事件',
      value: overdue,
      note: '已配置 SLA 且超过时限',
      tone: overdue > 0 ? 'danger' : 'success',
      route: '/alert-management/notifications'
    }
  ]
}

function capabilityValue(capability) {
  return capability?.status === 'AVAILABLE' ? (capability.value ?? 0) : '--'
}

export function buildDashboardAdmissionQueueItems({ preShiftData, admissionSummary }) {
  const awaitingReview = admissionSummary?.awaitingReview
  const retestOverdue = admissionSummary?.retestOverdue
  return [
    {
      key: 'passed',
      label: '准入通过',
      value: preShiftData?.qualifiedCount || 0,
      note: '查看今日合格名单',
      tone: 'success',
      route: { path: '/health-monitor/mine-entry', query: { status: 'pass', from: 'dashboard' } }
    },
    {
      key: 'prohibited',
      label: '禁止入井',
      value: preShiftData?.failedCount || 0,
      note: '查看异常指标与人员',
      tone: (preShiftData?.failedCount || 0) > 0 ? 'danger' : 'success',
      route: { path: '/health-monitor/mine-entry', query: { status: 'fail', from: 'dashboard' } }
    },
    {
      key: 'review',
      label: '待复检',
      value: capabilityValue(awaitingReview),
      note: awaitingReview?.status === 'AVAILABLE' ? '进入复检任务名单' : '复检状态未接入',
      tone: (awaitingReview?.value || 0) > 0 ? 'warning' : 'success',
      unavailable: awaitingReview?.status !== 'AVAILABLE',
      route: { path: '/health-monitor/mine-entry', query: { status: 'review', from: 'dashboard' } }
    },
    {
      key: 'retest-overdue',
      label: '复检超时',
      value: capabilityValue(retestOverdue),
      note: retestOverdue?.status === 'AVAILABLE' ? '优先处理已超时任务' : '复检时限未接入',
      tone: (retestOverdue?.value || 0) > 0 ? 'danger' : 'success',
      unavailable: retestOverdue?.status !== 'AVAILABLE',
      route: { path: '/health-monitor/mine-entry', query: { status: 'overdue', from: 'dashboard' } }
    }
  ]
}

export const dashboardCommandWorkflowMethods = {
  openAdmissionQueue(item) {
    if (item?.route) this.$router.push(item.route)
  },
  openCommandIncident(event) {
    if (!event?.id || !event?.occurredAt) return
    this.currentIncidentEvent = event
    this.incidentDrawerVisible = true
  },
  openIncidentFromRoute(query) {
    if (!query?.warningId || !query?.occurredAt) return
    const key = `${query.warningId}:${query.occurredAt}`
    if (this.routeIncidentKey === key) return
    this.routeIncidentKey = key
    this.openCommandIncident({
      id: query.warningId,
      incidentId: query.incidentId || '',
      occurredAt: query.occurredAt,
      userName: query.person || '',
      location: query.area || ''
    })
  },
  async handleIncidentUpdated() {
    await Promise.all([this.fetchWarningEvents(), this.fetchKpiData()])
  }
}
