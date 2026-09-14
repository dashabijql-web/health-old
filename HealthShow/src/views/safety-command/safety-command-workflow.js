export function buildSafetyCommandQueue(events) {
  const queue = (events || []).map((event) => ({
    id: event.incidentId || `${event.id || 'warning'}-${event.occurredAt || event.time || event.user}`,
    title: `${event.user || '未知人员'} · ${event.type || '预警'}`,
    meta: `${event.location || event.dept || '未接入定位'} / ${event.time || '刚刚'}`,
    context: `责任 ${event.owner || '未分派'} / SLA ${event.sla || '未配置'}`,
    action: '处理',
    tone: event.eventType === 'sos' || event.eventType === 'fall' ? 'danger' : 'warning',
    event
  }))

  return queue.length ? queue : [{
    id: 'safe-duty',
    title: '当前无紧急事件',
    meta: '保持在线巡查，关注设备离线和低电量变化',
    context: '无事件上下文，不能发起外部指令',
    action: '广播',
    tone: 'safe',
    event: null
  }]
}

export function buildSafetyPriorityEvent(events) {
  const event = events?.[0]
  if (!event) return null
  return {
    ...event,
    tone: event.eventType === 'sos' ? 'danger' : event.eventType === 'fall' ? 'warning' : 'primary',
    statusLabel: {
      NEW: '待确认',
      ACKED: '已确认',
      RESOLVED: '已处理',
      FALSE_ALARM: '误报关闭'
    }[event.status] || '待处置'
  }
}

export function buildDashboardReturnQuery(event) {
  if (!event?.id || !event?.occurredAt) return {}
  return {
    warningId: String(event.id),
    occurredAt: event.occurredAt,
    incidentId: event.incidentId || '',
    person: event.user || '',
    area: event.location || event.dept || ''
  }
}
