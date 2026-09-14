export const WEEK_DAYS = ['日', '一', '二', '三', '四', '五', '六']

export const DEPT_COLORS = ['#60a5fa', '#34d399', '#f59e0b', '#f87171', '#a78bfa', '#fb923c', '#38bdf8', '#4ade80']

export function createCalendarCells({ year, month, dayData, today = new Date() }) {
  const cells = []
  const firstDay = new Date(year, month - 1, 1).getDay()
  const daysInMonth = new Date(year, month, 0).getDate()
  const todayKey = toDateKey(today.getFullYear(), today.getMonth() + 1, today.getDate())

  for (let i = 0; i < firstDay; i++) cells.push({ day: 0 })
  for (let day = 1; day <= daysInMonth; day++) {
    const dateStr = toDateKey(year, month, day)
    cells.push({
      day,
      dateStr,
      data: dayData[dateStr] || null,
      isToday: dateStr === todayKey
    })
  }
  while (cells.length % 7 !== 0) cells.push({ day: 0 })
  return cells
}

export function summarizeCalendarRows(dayData) {
  const rows = Object.values(dayData)
  const totalDays = rows.length
  const warnDays = rows.filter(row => row.warningCount > 0).length
  const goodDays = rows.filter(row => row.warningCount === 0 && row.avgHeartRate).length
  const totalWarnings = rows.reduce((sum, row) => sum + (row.warningCount || 0), 0)
  const hrRows = rows.filter(row => row.avgHeartRate)
  const boRows = rows.filter(row => row.avgBloodOxygen)
  const avgHR = hrRows.length
    ? Math.round(hrRows.reduce((sum, row) => sum + row.avgHeartRate, 0) / hrRows.length)
    : 0
  const avgBO = boRows.length
    ? (boRows.reduce((sum, row) => sum + row.avgBloodOxygen, 0) / boRows.length).toFixed(1)
    : 0
  return { totalDays, goodDays, warnDays, totalWarnings, avgHR: avgHR || 0, avgBO: avgBO || 0 }
}

export function calendarCellClass(cell, selectedDateStr) {
  if (!cell.day) return 'cal-cell-empty'
  const classes = []
  if (cell.isToday) classes.push('cal-cell-today')
  if (cell.data) {
    classes.push(cell.data.warningCount > 0 ? 'cal-cell-warn' : 'cal-cell-good')
  } else {
    classes.push('cal-cell-nodata')
  }
  if (selectedDateStr === cell.dateStr) classes.push('cal-cell-selected')
  return classes
}

export function rankTitleFor(type) {
  return {
    heartRate: '心率排行（偏差最大）',
    bloodOxygen: '血氧排行（最低）',
    steps: '步数排行（最少）',
    warnings: '当日预警列表（严重优先）'
  }[type] || ''
}

export function rankValueClass(type, row) {
  if (type === 'heartRate') {
    const value = row.avgHeartRate
    return (value < 60 || value > 100) ? 'text-red' : 'text-green'
  }
  if (type === 'bloodOxygen') return row.avgBloodOxygen < 95 ? 'text-red' : 'text-green'
  if (type === 'steps') return row.avgSteps < 6000 ? 'text-red' : ''
  return ''
}

export function warningLevelLabel(level) {
  return level || '—'
}

export function warningLevelClass(level) {
  if (!level) return ''
  if (['危急', '高危', '高', '危险'].includes(level)) return 'wl-high'
  if (['中', '警告'].includes(level)) return 'wl-mid'
  if (level === '低') return 'wl-low'
  return ''
}

export function healthMetricClass(type, value) {
  if (!value) return ''
  if (type === 'heartRate') return (value < 60 || value > 100) ? 'wt-red' : 'wt-green'
  if (type === 'bloodOxygen') return value < 95 ? 'wt-red' : 'wt-green'
  if (type === 'systolic') return value >= 140 ? 'wt-red' : value >= 130 ? 'wt-orange' : 'wt-green'
  if (type === 'pressure') return value > 70 ? 'wt-red' : value > 50 ? 'wt-orange' : ''
  return ''
}

function toDateKey(year, month, day) {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}
