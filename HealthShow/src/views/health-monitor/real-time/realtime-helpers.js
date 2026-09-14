import dayjs from 'dayjs'

export function classifyHeartRate(value) {
  if (!value) return 'c-dim'
  if (value < 50 || value > 120) return 'c-danger'
  if (value < 60 || value > 100) return 'c-warn'
  return 'c-ok'
}

export function classifyBloodOxygen(value) {
  if (!value) return 'c-dim'
  if (value < 90) return 'c-danger'
  if (value < 95) return 'c-warn'
  return 'c-ok'
}

export function classifyTemperature(value) {
  if (!value) return 'c-dim'
  if (value < 35 || value > 38) return 'c-danger'
  if (value < 36 || value > 37.5) return 'c-warn'
  return 'c-ok'
}

export function classifySystolic(value) {
  if (!value) return 'c-dim'
  if (value >= 180) return 'c-danger'
  if (value >= 140 || value < 90) return 'c-warn'
  return 'c-bp'
}

export function classifyDiastolic(value) {
  if (!value) return 'c-dim'
  if (value >= 100) return 'c-danger'
  if (value >= 90 || value < 60) return 'c-warn'
  return 'c-bp'
}

export function classifyPressure(value) {
  if (value === null || value === undefined) return 'c-dim'
  if (value >= 90) return 'c-danger'
  if (value >= 85) return 'c-warn'
  return 'c-pressure'
}

const fallbackClassifiers = {
  heartRate: classifyHeartRate,
  bloodOxygen: classifyBloodOxygen,
  temperature: classifyTemperature,
  bloodPressureHigh: classifySystolic,
  bloodPressureLow: classifyDiastolic,
  pressure: classifyPressure
}

export function getMetricClass(user, key) {
  const state = user?.indicatorStates?.[key]
  if (state === 'danger') return 'c-danger'
  if (state === 'warning') return 'c-warn'
  const classifier = fallbackClassifiers[key]
  return classifier ? classifier(user?.[key]) : 'c-dim'
}

export function getRealtimeIndicator(user) {
  if (user?.warningReasons?.length) return user.warningReasons[0]
  if (user?.status === 'stale') return `数据已 ${formatRealtimeAge(user.dataAgeSeconds)} 未更新`
  if (user?.status === 'no_data') return '暂无有效体征'
  return '体征正常'
}

export function getRealtimeStatusLabel(status) {
  if (status === 'warning') return '异常'
  if (status === 'stale') return '陈旧'
  if (status === 'no_data') return '待补'
  return '正常'
}

export function getRealtimeRowClass({ row } = {}) {
  if (row?.status === 'stale' || row?.status === 'no_data') return 'row-stale'
  if (row?.status !== 'warning') return ''
  return row.severity === 'danger' ? 'row-danger' : 'row-warning'
}

export function formatRealtimeTime(value) {
  if (!value) return '--'
  const time = dayjs(value)
  if (!time.isValid()) return '--'
  return time.isSame(dayjs(), 'day') ? time.format('HH:mm:ss') : time.format('MM-DD HH:mm')
}

export function formatRealtimeFullTime(value) {
  if (!value) return '--'
  const time = dayjs(value)
  return time.isValid() ? time.format('YYYY-MM-DD HH:mm:ss') : '--'
}

export function formatRealtimeAge(seconds) {
  const safe = Math.max(0, Number(seconds) || 0)
  if (safe < 60) return `${Math.round(safe)} 秒`
  if (safe < 3600) return `${Math.floor(safe / 60)} 分钟`
  if (safe < 86400) return `${Math.floor(safe / 3600)} 小时`
  return `${Math.floor(safe / 86400)} 天`
}

export function normalizeRealtimeUsersResponse(data) {
  const list = Array.isArray(data?.list) ? data.list : (Array.isArray(data) ? data : [])
  return {
    list,
    total: data?.total ?? list.length,
    page: data?.page ?? 1,
    size: data?.size ?? list.length,
    stale: Boolean(data?.stale),
    refreshedAt: data?.refreshedAt || '',
    summary: data?.summary || {
      onlineCount: data?.total ?? list.length,
      normalCount: list.filter((user) => user.status === 'normal').length,
      warningCount: list.filter((user) => user.status === 'warning').length,
      staleCount: list.filter((user) => user.status === 'stale').length,
      noDataCount: list.filter((user) => user.status === 'no_data').length,
      onlineWindowMinutes: 15,
      freshnessMinutes: 5
    },
    departments: Array.isArray(data?.departments) ? data.departments : [],
    warningPreview: Array.isArray(data?.warningPreview) ? data.warningPreview : []
  }
}
