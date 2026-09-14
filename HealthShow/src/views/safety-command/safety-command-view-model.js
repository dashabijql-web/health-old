const DONUT_CIRCUMFERENCE = 339.3
const WARNING_TYPE_KEYS = ['心率', '血氧', '体温', '压力']
const WARNING_TYPE_COLORS = ['#ff3b3b', '#ff8c00', '#00e676', '#00c8ff']
export const SEVERITY_LEVEL_MAP = {
  CRITICAL: 'critical',
  HIGH: 'high',
  MEDIUM: 'medium',
  LOW: 'low'
}

export const EVENT_LEVEL_TEXT_MAP = {
  critical: '特急',
  high: '紧急',
  medium: '一般',
  low: '轻微'
}

export const EVENT_LEVEL_TONE_MAP = {
  critical: 'danger',
  high: 'warning',
  medium: 'primary',
  low: 'info'
}

export const EVENT_LEVEL_COLOR_MAP = {
  critical: '#ff3b3b',
  high: '#ff8c00',
  medium: '#00c8ff',
  low: '#6e94b0'
}

export const EVENT_STATUS_TEXT_MAP = {
  NEW: '待确认',
  ACKED: '已确认',
  DISPATCHED: '已派遣',
  PROCESSING: '处理中',
  RESOLVED: '已处理',
  FALSE_ALARM: '误报关闭'
}

export const EVENT_SOURCE_TEXT_MAP = {
  HEALTH_THRESHOLD: '体征预警',
  DEVICE_ALARM: '设备报警',
  TREND_WARNING: '趋势风险',
  HEALTH: '体征预警',
  LEGACY: '历史事件'
}

export const SLA_STATUS_TEXT_MAP = {
  NOT_CONFIGURED: '未配置',
  ON_TIME: '按期处置',
  OVERDUE: '已超时'
}

const DEPT_LEVEL_TEXT_MAP = { H: '高危', M: '中危', L: '低', N: '正常' }

export function getEventLevelText(level) {
  return EVENT_LEVEL_TEXT_MAP[level] || '一般'
}

export function getEventLevelTone(level) {
  return EVENT_LEVEL_TONE_MAP[level] || 'primary'
}

export function getEventLevelColor(level) {
  return EVENT_LEVEL_COLOR_MAP[level] || '#00c8ff'
}

export function getEventStatusText(status) {
  return EVENT_STATUS_TEXT_MAP[status] || (status ? String(status) : '待确认')
}

export function getEventSourceText(source) {
  return EVENT_SOURCE_TEXT_MAP[source] || '历史事件'
}

export function getSlaStatusText(slaStatus) {
  return SLA_STATUS_TEXT_MAP[slaStatus] || '未配置'
}

export function buildKpiDetailText(key, stats, watchStatus) {
  const statData = stats || {}
  const watchData = watchStatus || {}
  return {
    underground: `井下人员 ${statData.underground || 0} 人，合计 ${statData.total || 0} 人。`,
    sos: `当前 SOS 求救 ${statData.sos || 0} 条。`,
    fall: `当前跌倒事件 ${statData.fall || 0} 条。`,
    alerts: `静止 ${statData.static || 0} 条，异常 ${statData.abnormal || 0} 条。`,
    watch: `在线 ${watchData.online || 0}/${watchData.total || 0}，离线 ${watchData.offline || 0}，低电量 ${watchData.lowBattery || 0}。`
  }[key] || '暂无详情'
}

export function buildRenderedDeptAiReport(content) {
  if (!content) return ''
  return content
    .replace(/^## (.+)$/gm, '<h4>$1</h4>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}

export function inferFallbackSeverity(record) {
  const eventCode = String(record?.eventCode || record?.event_code || '').toUpperCase()
  const incidentType = String(record?.type || '').toUpperCase()
  const warningType = String(record?.typeLabel || record?.warningType || record?.warning_type || record?.type || '')

  if (eventCode === 'SOS' || incidentType === 'SOS' || warningType.includes('SOS') || warningType.includes('sos')) {
    return 'critical'
  }
  if (eventCode === 'FALL' || incidentType === 'FALL' || warningType.includes('跌倒') || warningType.includes('fall')) {
    return 'high'
  }
  if (warningType.includes('心率') || warningType.includes('血氧')) {
    return 'high'
  }
  if (warningType.includes('体温')) {
    return 'medium'
  }
  if (warningType.includes('疲劳') || warningType.includes('睡眠')) {
    return 'low'
  }
  return 'medium'
}

export function inferFallbackEventSource(record) {
  const eventCode = String(record?.eventCode || record?.event_code || '').toUpperCase()
  const incidentType = String(record?.type || '').toUpperCase()
  const warningType = String(record?.typeLabel || record?.warningType || record?.warning_type || record?.type || '')

  if (
    ['SOS', 'FALL', 'STILL', 'STATIC'].includes(eventCode) ||
    ['SOS', 'FALL', 'STILL', 'STATIC'].includes(incidentType) ||
    /SOS|sos|跌倒|静止|static|fall/.test(warningType)
  ) {
    return 'DEVICE_ALARM'
  }
  if (/趋势|预测|trend/i.test(warningType)) return 'TREND_WARNING'
  return 'HEALTH_THRESHOLD'
}

export function mapWarningToEvent(record) {
  if (!record) return null
  const warningType = record.typeLabel || record.warningType || record.warning_type || record.type || ''
  const eventCode = String(record.eventCode || record.event_code || '').toUpperCase()
  const incidentType = String(record.type || '').toUpperCase()
  const structuredEventSource = record.eventSource || record.source || record.event_source
  const eventSource = structuredEventSource
    ? String(structuredEventSource).toUpperCase()
    : inferFallbackEventSource(record)

  // 1. Structured severity takes strict precedence over warningType keywords
  const rawSeverity = String(record.severity || record.warningSeverity || '').toUpperCase()
  const level = SEVERITY_LEVEL_MAP[rawSeverity] || inferFallbackSeverity(record)

  // 2. Classify device alarms only inside DEVICE_ALARM. Legacy records without
  // structured source/code are normalized above before this check.
  let eventType = 'abnormal'
  let icon = 'WARN'

  const isDeviceAlarm = eventSource === 'DEVICE_ALARM'
  const isSos = isDeviceAlarm && (
    eventCode === 'SOS' ||
    (!eventCode && (incidentType === 'SOS' || /SOS/i.test(warningType)))
  )
  const isFall = isDeviceAlarm && (
    eventCode === 'FALL' ||
    (!eventCode && (incidentType === 'FALL' || /跌倒|fall/i.test(warningType)))
  )
  const isStatic = isDeviceAlarm && (
    ['STILL', 'STATIC'].includes(eventCode) ||
    (!eventCode && (['STILL', 'STATIC'].includes(incidentType) || /静止|static/i.test(warningType)))
  )

  if (isSos) {
    eventType = 'sos'
    icon = 'SOS'
  } else if (isFall) {
    eventType = 'fall'
    icon = 'FALL'
  } else if (isStatic) {
    eventType = 'static'
    icon = 'IDLE'
  } else if (warningType.includes('心率')) {
    eventType = 'abnormal'
    icon = 'HR'
  } else if (warningType.includes('血氧')) {
    eventType = 'abnormal'
    icon = 'SpO2'
  } else if (warningType.includes('体温')) {
    eventType = 'abnormal'
    icon = 'TEMP'
  } else if (warningType.includes('疲劳') || warningType.includes('睡眠')) {
    eventType = 'abnormal'
    icon = 'REST'
  }

  const createdAt = record.occurredAt || record.createTime || record.create_time || record.time || ''
  let durationMinutes = 0
  if (createdAt) {
    const createdDate = new Date(createdAt)
    if (!isNaN(createdDate)) {
      durationMinutes = Math.max(0, Math.round((Date.now() - createdDate.getTime()) / 60000))
    }
  }

  const backendStatus = String(record.status || (record.handled ? 'RESOLVED' : 'NEW')).toUpperCase()
  const statusLabel = getEventStatusText(backendStatus)

  const slaConfigured = record.sla?.configured === true
  const slaStatus = String(record.sla?.status || (slaConfigured ? 'ON_TIME' : 'NOT_CONFIGURED')).toUpperCase()

  const ownerStatus = String(record.owner?.status || (record.owner?.name ? 'ASSIGNED' : 'UNASSIGNED')).toUpperCase()
  const ownerName = record.owner?.name || ''
  const ownerDisplay = ownerName || (record.owner?.status === 'UNASSIGNED' ? '未分派' : '未分派')

  const locationDisplay = record.location?.status === 'UNAVAILABLE'
    ? '未接入定位'
    : (record.location?.label || record.location || record.deptName || record.dept_name || '未接入定位')

  const vitalSnapshot = record.vitalSnapshot
    ? `${record.vitalSnapshot.indicator || ''}: ${record.vitalSnapshot.value || ''}`
    : ''

  return {
    id: record.warningId ?? record.id,
    incidentId: record.incidentId || '',
    icon,
    type: warningType,
    eventSource,
    eventCode,
    user: record.person?.name || record.userName || record.user_name || '',
    userCode: record.person?.userCode || record.userCode || record.user_code || '',
    dept: record.person?.department || record.deptName || record.dept_name || '',
    location: locationDisplay,
    time: formatEventAge(createdAt),
    durationText: formatEventDuration(durationMinutes),
    occurredAt: createdAt,
    owner: ownerDisplay,
    ownerStatus,
    sla: record.sla?.configured ? record.sla.deadlineAt : '未配置',
    slaStatus,
    slaText: getSlaStatusText(slaStatus),
    level,
    severity: rawSeverity || (level === 'critical' ? 'CRITICAL' : level === 'high' ? 'HIGH' : level === 'medium' ? 'MEDIUM' : 'LOW'),
    eventType,
    durationMinutes,
    status: backendStatus,
    statusLabel,
    vitalSnapshot
  }
}

export function buildProcessedWarningData(raw) {
  const records = Array.isArray(raw) ? raw : (raw?.records || raw?.list || [])
  const events = records.map(mapWarningToEvent)
  return {
    events,
    ...buildEventRiskSummary(events)
  }
}

export function resolveWarningTotals(overview, fallbackPending = 0) {
  const data = overview || {}
  const handled = Number(data.handledWarnings ?? data.handledCount ?? data.handled ?? 0)
  const pendingValue = data.pendingWarnings ?? data.pendingCount
  const totalValue = data.totalWarnings ?? data.totalCount ?? data.total
  const pending = Number(pendingValue ?? fallbackPending)

  return {
    pending,
    handled,
    total: Number(totalValue ?? (pending + handled)),
    critical: Number(data.dangerCount ?? data.criticalCount ?? 0)
  }
}

export function buildEventRiskSummary(events) {
  const list = Array.isArray(events) ? events : []
  const personMap = new Map()

  list.forEach((event) => {
    if (!event.user) return
    if (!personMap.has(event.user)) {
      personMap.set(event.user, {
        id: event.id,
        name: event.user,
        userCode: event.userCode,
        dept: event.dept,
        count: 0,
        types: [],
        latestEvent: event
      })
    }
    const person = personMap.get(event.user)
    person.count++
    if (event.type && !person.types.includes(event.type)) person.types.push(event.type)
  })

  return {
    riskPersons: Array.from(personMap.values()).sort((a, b) => b.count - a.count),
    sosCount: list.filter((event) => event.eventType === 'sos').length,
    fallCount: list.filter((event) => event.eventType === 'fall').length
  }
}

export function buildDeviceCoverage(deviceSummary) {
  const source = deviceSummary || {}
  const numberOrNull = (value) => value === null || value === undefined || value === ''
    ? null
    : Number(value)
  const total = numberOrNull(source.total)
  const online = numberOrNull(source.online)
  const offline = numberOrNull(source.offline)
  const onlineRate = numberOrNull(source.onlineRate)
  const hasCoverage = [total, online, offline, onlineRate].some(Number.isFinite)

  const capability = (key, label, tone) => {
    const item = source[key]
    const available = item?.status === 'AVAILABLE' && item.value !== null && item.value !== undefined
    return {
      key,
      label,
      value: available ? Number(item.value) : null,
      display: available ? String(Number(item.value)) : '未接入',
      message: item?.message || '当前没有权威数据源',
      tone: available && Number(item.value) > 0 ? tone : available ? 'safe' : 'muted',
      available
    }
  }

  return {
    available: hasCoverage,
    total,
    online,
    offline,
    onlineRate,
    cards: [
      { key: 'total', label: '设备总数', display: total ?? '--', tone: 'primary' },
      { key: 'online', label: '在线设备', display: online ?? '--', tone: 'safe' },
      { key: 'offline', label: '离线设备', display: offline ?? '--', tone: Number(offline) > 0 ? 'warning' : 'safe' }
    ],
    capabilities: [
      capability('lowBattery', '低电设备', 'warning'),
      capability('dataInterrupted', '数据中断', 'danger'),
      capability('faulted', '设备故障', 'danger')
    ]
  }
}

export function buildLoadedEventTypeItems(events) {
  const list = Array.isArray(events) ? events : []
  const definitions = [
    { key: 'sos', label: 'SOS', tone: 'danger', matches: (event) => event.eventType === 'sos' },
    { key: 'fall', label: '跌倒', tone: 'danger', matches: (event) => event.eventType === 'fall' },
    { key: 'vital', label: '体征异常', tone: 'warning', matches: (event) => ['心率', '血氧', '体温', '压力', '睡眠', '疲劳'].some((key) => String(event.type || '').includes(key)) },
    { key: 'other', label: '其他', tone: 'primary', matches: () => true }
  ]
  const claimed = new Set()

  return definitions.map((definition) => {
    const count = list.reduce((sum, event, index) => {
      if (claimed.has(index) || !definition.matches(event)) return sum
      claimed.add(index)
      return sum + 1
    }, 0)
    return {
      key: definition.key,
      label: definition.label,
      count,
      tone: definition.tone,
      percent: list.length ? Math.round(count / list.length * 100) : 0
    }
  })
}

export function buildLoadedWorkflowSignals(events) {
  const list = Array.isArray(events) ? events : []
  return [
    { key: 'new', label: '待确认', value: list.filter((event) => String(event.status || '').toUpperCase() === 'NEW').length, tone: 'warning' },
    { key: 'acked', label: '已确认', value: list.filter((event) => String(event.status || '').toUpperCase() === 'ACKED').length, tone: 'primary' },
    { key: 'assigned', label: '已分派', value: list.filter((event) => event.owner && event.owner !== '未分派').length, tone: 'safe' },
    { key: 'overdue', label: 'SLA 超时', value: list.filter((event) => String(event.slaStatus || '').toUpperCase() === 'OVERDUE').length, tone: 'danger' }
  ]
}

export function buildDeptRankData(departments) {
  return [...(departments || [])]
    .map((dept) => {
      const warnings = (dept.sos || 0) + (dept.fall || 0) + (dept.abnormal || 0)
      const level = warnings > 0 ? 'M' : 'N'
      return {
        ...dept,
        warnings,
        level,
        statusText: warnings > 0 ? '条预警' : '正常'
      }
    })
    .sort((a, b) => b.warnings - a.warnings)
}

export function buildTop5RiskPersons(riskPersons) {
  return (riskPersons || [])
    .slice(0, 5)
    .map((person) => ({
      ...person,
      tags: person.tags || [person.dept || '未分组', `预警×${person.count}`]
    }))
}

export function buildDonutSegments(events) {
  const list = events || []
  const counts = WARNING_TYPE_KEYS.map((key) => list.filter((event) => (event.type || '').includes(key)).length)
  const total = counts.reduce((sum, count) => sum + count, 0) || 1
  let offset = 0

  return WARNING_TYPE_KEYS.map((name, index) => {
    const pct = counts[index] / total
    const dash = +(pct * DONUT_CIRCUMFERENCE).toFixed(1)
    const rem = +(DONUT_CIRCUMFERENCE - dash).toFixed(1)
    const segment = {
      name,
      cnt: counts[index],
      pct: Math.round(pct * 100),
      color: WARNING_TYPE_COLORS[index],
      dash,
      rem,
      offset: +(-offset).toFixed(1)
    }
    offset += dash
    return segment
  })
}

export function buildTypeHandleProgress(events, handledCount) {
  const list = events || []
  const handled = handledCount || 0
  return WARNING_TYPE_KEYS.map((key, index) => {
    const pending = list.filter((event) => (event.type || '').includes(key)).length
    const handledShare = Math.floor(handled / 4) + (index < handled % 4 ? 1 : 0)
    const total = pending + handledShare
    const rate = total > 0 ? Math.round(handledShare / total * 100) : 0
    return {
      key,
      rate,
      color: rate >= 90 ? '#00e676' : rate >= 60 ? '#ffd600' : '#ff8c00'
    }
  })
}

export function buildVitalsRows(vitalAvg, vitalsHistory) {
  const avg = vitalAvg || {}
  const history = vitalsHistory || {}
  const hr = avg.heartRate || 0
  const bloodOxygen = avg.bloodOxygen || 0
  const temperature = avg.temperature || 0
  const pressure = avg.pressure || 0

  const hrVals = (history.hr || []).length >= 2
    ? history.hr.map((item) => item.avgHeartRate || 0).filter((value) => value > 0)
    : []

  const oxygenVals = (history.bo || []).length >= 2
    ? history.bo.map((item) => item.avgBloodOxygen || 0).filter((value) => value > 0)
    : []

  const temperatureVals = []
  const pressureVals = []

  const hrPath = buildSparkPath(hrVals, 50, 120)
  const oxygenPath = buildSparkPath(oxygenVals, 90, 100)
  const temperaturePath = buildSparkPath(temperatureVals, 36, 39)
  const pressurePath = buildSparkPath(pressureVals, 0, 100)

  return [
    { key: 'hr', label: '心率群体基线', val: hr > 0 ? Math.round(hr) : '--', unit: 'bpm', color: '#ff3b3b', gradId: 'vg-hr', hasTrend: hrVals.length >= 2, ...hrPath },
    { key: 'bo', label: '血氧群体基线', val: bloodOxygen > 0 ? Math.round(bloodOxygen) : '--', unit: '%', color: '#ff8c00', gradId: 'vg-bo', hasTrend: oxygenVals.length >= 2, ...oxygenPath },
    { key: 'temp', label: '体温群体基线', val: temperature > 0 ? temperature.toFixed(1) : '--', unit: '°C', color: '#00e676', gradId: 'vg-temp', hasTrend: false, ...temperaturePath },
    { key: 'pres', label: '压力群体基线', val: pressure > 0 ? Math.round(pressure) : '--', unit: 'idx', color: '#a855f7', gradId: 'vg-pres', hasTrend: false, ...pressurePath }
  ]
}

export function buildTrendPath(warningTrend) {
  const list = warningTrend || []
  if (!list.length) return null
  const counts = list.map((item) => item.count || item.cnt || 0)
  const maxVal = Math.max(...counts, 1)
  const width = 380
  const height = 175
  const padTop = 15
  const padBottom = 20
  const pts = counts.map((count, index) => ({
    x: Math.round(index / Math.max(counts.length - 1, 1) * (width - 4) + 2),
    y: Math.round(height - padBottom - (count / maxVal) * (height - padTop - padBottom))
  }))
  const line = `M${pts.map((point) => `${point.x},${point.y}`).join(' L ')}`
  return { line, area: `${line} L${width},${height} L0,${height} Z`, pts }
}

export function buildTrend7dayTotal(warningTrend) {
  return (warningTrend || []).reduce((sum, item) => sum + (item.count || item.cnt || 0), 0)
}

export function buildTrendChange(warningTrend) {
  const list = warningTrend || []
  if (list.length < 2) return 0
  const today = list[list.length - 1]?.count || list[list.length - 1]?.cnt || 0
  const yesterday = list[list.length - 2]?.count || list[list.length - 2]?.cnt || 0
  return yesterday ? Math.round((today - yesterday) / yesterday * 100) : 0
}

export function buildAreasFromDepartments(departments) {
  return (departments || []).map((dept, index) => ({
    id: dept.id || index + 1,
    name: dept.name,
    count: dept.abnormal || 0,
    sos: dept.sos || 0,
    fall: dept.fall || 0,
    warning: dept.abnormal || 0,
    level: dept.status || 'safe'
  }))
}

export function buildDepartmentListFromWarningStats(deptData) {
  if (!Array.isArray(deptData) || deptData.length === 0) return []
  return deptData.map((dept, index) => {
    const totalWarn = dept.total || (dept.heartRate || 0) + (dept.bloodOxygen || 0) + (dept.sleep || 0) + (dept.temperature || 0) + (dept.pressure || 0)
    return {
      id: dept.id || index + 1,
      name: dept.deptName || dept.dept_name || dept.name || `部门${index + 1}`,
      online: 0,
      sos: 0,
      fall: 0,
      static: 0,
      abnormal: totalWarn,
      status: totalWarn > 0 ? 'warning' : 'safe'
    }
  })
}

export function buildWatchStatus(realtimeStats) {
  const data = realtimeStats || {}
  const online = data.onlineUsers || data.onlineDevices || data.online || 0
  const total = data.totalUsers || data.totalDevices || data.total || 0
  return {
    total,
    online,
    offline: Math.max(0, total - online),
    lowBattery: data.lowBattery || 0
  }
}

export function buildStageIntelItems({ pendingCount, criticalCount, unassignedCount, overdueCount, dataAsOf }) {
  return [
    {
      key: 'critical',
      label: '高危待办',
      value: criticalCount || 0,
      note: '优先介入',
      tone: criticalCount > 0 ? 'danger' : 'safe'
    },
    {
      key: 'overdue',
      label: 'SLA超时',
      value: overdueCount || 0,
      note: '超过时限',
      tone: overdueCount > 0 ? 'danger' : 'safe'
    },
    {
      key: 'unassigned',
      label: '未分派',
      value: unassignedCount || 0,
      note: '暂无责任人',
      tone: unassignedCount > 0 ? 'warning' : 'safe'
    },
    {
      key: 'active-risk',
      label: '待处置',
      value: pendingCount || 0,
      note: `高危 ${criticalCount || 0} / 其他 ${Math.max(0, (pendingCount || 0) - (criticalCount || 0))}`,
      tone: criticalCount > 0 ? 'danger' : pendingCount > 0 ? 'warning' : 'safe'
    },
    {
      key: 'freshness',
      label: '数据时间',
      value: dataAsOf ? String(dataAsOf).slice(11, 16) : '--:--',
      note: dataAsOf ? String(dataAsOf).slice(0, 10) : '等待权威汇总',
      tone: 'primary'
    }
  ]
}

export function buildStageActionItems({ todayNew, pendingCount, handledCount, criticalCount, warningHandledRate }) {
  return [
    {
      key: 'today-new',
      label: '今日新增',
      value: todayNew || 0,
      note: '今日产生的全部预警',
      tone: todayNew > 0 ? 'warning' : 'safe'
    },
    {
      key: 'critical',
      label: '高危待办',
      value: criticalCount || 0,
      note: '需要优先介入',
      tone: criticalCount > 0 ? 'danger' : 'safe'
    },
    {
      key: 'handled',
      label: '今日已处置',
      value: handledCount || 0,
      note: `待处置 ${pendingCount || 0}`,
      tone: handledCount > 0 ? 'safe' : 'warning'
    },
    {
      key: 'review',
      label: '复盘闭环',
      value: handledCount || 0,
      note: `闭环率 ${warningHandledRate || 0}%`,
      tone: warningHandledRate >= 80 ? 'safe' : 'warning'
    }
  ]
}

export function buildStageNodes({ deptsSorted, areas, departments }) {
  const positions = [
    [17, 38], [80, 25], [25, 64], [84, 58], [50, 20], [52, 74]
  ]
  const deptNodes = (deptsSorted || []).slice(0, 6).map((dept, index) => ({
    key: `dept-${dept.id || dept.name || index}`,
    label: dept.name || `部门${index + 1}`,
    value: dept.warnings || 0,
    meta: '今日部门预警总量',
    status: dept.statusText || '正常',
    tone: dept.level === 'H' ? 'danger' : dept.level === 'M' ? 'warning' : dept.level === 'L' ? 'primary' : 'safe',
    dept,
    x: positions[index][0],
    y: positions[index][1]
  }))

  if (deptNodes.length) return deptNodes

  const fallbackAreas = (areas?.length ? areas : buildAreasFromDepartments(departments)).slice(0, 6)
  return fallbackAreas.map((area, index) => ({
    key: `area-${area.id || area.name || index}`,
    label: area.name || `区域${index + 1}`,
    value: area.warning || area.count || 0,
    meta: `SOS ${area.sos || 0} / 跌倒 ${area.fall || 0} / 异常 ${area.warning || area.count || 0}`,
    status: area.level === 'danger' ? '高危' : area.level === 'warning' ? '中危' : '正常',
    tone: area.level === 'danger' ? 'danger' : area.level === 'warning' ? 'warning' : 'safe',
    area,
    x: positions[index][0],
    y: positions[index][1]
  }))
}

export function normalizeWarningTrendData(rawTrend) {
  if (Array.isArray(rawTrend)) return rawTrend
  if (rawTrend?.dates && Array.isArray(rawTrend.dates)) {
    const seriesArrays = Object.values(rawTrend.series || {}).filter(Array.isArray)
    return rawTrend.dates.map((date, index) => ({
      date,
      count: seriesArrays.reduce((sum, series) => sum + (series[index] || 0), 0)
    }))
  }
  return []
}

function buildSparkPath(values, minV, maxV) {
  if (!values || values.length < 2) return null
  const width = 260
  const height = 44
  const padTop = 6
  const padBottom = 8
  const range = maxV - minV || 1
  const points = values.map((value, index) => ({
    x: Math.round(index / Math.max(values.length - 1, 1) * width),
    y: Math.round(padTop + (1 - Math.max(0, Math.min(1, (value - minV) / range))) * (height - padTop - padBottom))
  }))
  const linePath = points.reduce((acc, point, index) => {
    if (index === 0) return `M${point.x},${point.y}`
    const prev = points[index - 1]
    const cx = (prev.x + point.x) / 2
    return `${acc} C${cx},${prev.y} ${cx},${point.y} ${point.x},${point.y}`
  }, '')
  return {
    linePath,
    areaPath: `${linePath} L${width},${height} L0,${height} Z`,
    endX: points[points.length - 1].x,
    endY: points[points.length - 1].y
  }
}

function formatEventAge(value) {
  if (!value) return ''
  const date = new Date(value)
  if (isNaN(date)) return value
  const diffMinutes = Math.round((Date.now() - date.getTime()) / 60000)
  if (diffMinutes < 1) return '刚刚'
  if (diffMinutes < 60) return `${diffMinutes}分钟前`
  return `${Math.floor(diffMinutes / 60)}小时前`
}

function formatEventDuration(minutes) {
  const value = Math.max(0, Number(minutes) || 0)
  if (value < 1) return '刚触发'
  if (value < 60) return `${value} MIN`
  return `${Math.floor(value / 60)}H ${value % 60}M`
}
