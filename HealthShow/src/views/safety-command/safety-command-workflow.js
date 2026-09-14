import { getEventLevelTone } from './safety-command-view-model.js'

export function buildSafetyCommandQueue(events) {
  const queue = (events || []).map((event) => ({
    id: event.incidentId || `${event.id || 'warning'}-${event.occurredAt || event.time || event.user}`,
    title: `${event.user || '未知人员'} · ${event.type || '预警'}`,
    meta: `${event.location || event.dept || '未接入定位'} / ${event.time || '刚刚'}`,
    context: `责任 ${event.owner || '未分派'} / SLA ${event.slaText || event.sla || '未配置'}`,
    action: '处置',
    tone: event.levelTone || getEventLevelTone(event.level),
    event
  }))

  return queue.length ? queue : [{
    id: 'safe-duty',
    title: '当前无未闭环预警',
    meta: '保持在线巡查，关注设备离线和低电量变化',
    context: '当前所有事件均已闭环或无待处置事件',
    action: '',
    tone: 'safe',
    event: null
  }]
}

export function buildSafetyPriorityEvent(events) {
  const event = events?.[0]
  if (!event) return null
  return {
    ...event,
    tone: event.level === 'critical' ? 'danger' : event.level === 'high' ? 'warning' : 'primary',
    statusLabel: event.statusLabel || {
      NEW: '待确认',
      ACKED: '已确认',
      DISPATCHED: '已派遣',
      PROCESSING: '处理中',
      RESOLVED: '已处理',
      FALSE_ALARM: '误报关闭'
    }[event.status] || '待确认'
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
