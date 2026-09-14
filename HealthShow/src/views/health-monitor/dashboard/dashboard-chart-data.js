import { getDailyTrend, getWarningCounts, getDeptDailyDetail, getMetricDailyDetail, getDeptDailyPersons } from '@/api/health'
import { getWarningTypes } from '@/api/statistics'
import { dashboardCache as _cache } from './dashboard-cache'
import { DASHBOARD_REQUEST_OPTIONS } from './dashboard-runtime-data'

const deptChartPalette = ['#00e5ff', '#00e676', '#ffd740', '#ff6e40', '#ea80fc', '#40c4ff', '#f48fb1', '#69f0ae', '#ffab40', '#b388ff', '#80d8ff', '#ccff90']

function postPerfLog(message) {
  fetch('/perf-log', { method: 'POST', body: message }).catch(() => {})
}

export function buildDashboardDateRange(daysBack = 6) {
  const today = new Date()
  const start = new Date(today)
  start.setDate(start.getDate() - daysBack)
  const formatDate = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  return [formatDate(start), formatDate(today)]
}

export function buildDashboardEnvSeries() {
  const now = new Date()
  const hours = []
  const coData = []
  const dustData = []
  const boData = []

  for (let i = 41; i >= 0; i--) {
    const d = new Date(now - i * 4 * 3600000)
    hours.push(`${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}h`)
    const isShift = d.getHours() >= 8 && d.getHours() <= 20
    const co = +((isShift ? 8 : 2) + Math.random() * (isShift ? 6 : 2)).toFixed(1)
    const dust = +((isShift ? 1.5 : 0.3) + Math.random() * (isShift ? 1.8 : 0.5)).toFixed(2)
    const baseBO = 97.5 - co * 0.08 - dust * 0.3
    const bo = +(baseBO + (Math.random() - 0.5) * 0.4).toFixed(1)
    coData.push(co)
    dustData.push(dust)
    boData.push(Math.min(99, Math.max(93, bo)))
  }

  return { hours, coData, dustData, boData }
}

export async function fetchDashboardDeptDetailData({ deptName, startTime, endTime }) {
  const res = await getDeptDailyDetail({ deptName, startTime, endTime })
  if (res.code !== 200) return null
  const rows = res.data || []
  return {
    days: rows.map((row) => row.day),
    personCounts: rows.map((row) => row.personCount || 0),
    abnormalCounts: rows.map((row) => row.abnormalPersonCount || 0)
  }
}

export async function fetchDashboardMetricDetailData({ metricType, startTime, endTime }) {
  const res = await getMetricDailyDetail({ metricType, startTime, endTime })
  if (res.code !== 200) return null
  const rows = res.data || []
  return {
    days: rows.map((row) => row.day),
    personCounts: rows.map((row) => row.personCount || 0),
    abnormalCounts: rows.map((row) => row.abnormalPersonCount || 0)
  }
}

export async function fetchDashboardDeptPersonChartData({ startTime, endTime }) {
  const res = await getDeptDailyPersons({ startTime, endTime })
  if (res.code !== 200) return null

  const rows = res.data || []
  const deptMap = {}
  const daySet = new Set()

  rows.forEach((row) => {
    if (!deptMap[row.deptName]) deptMap[row.deptName] = {}
    deptMap[row.deptName][row.day] = row.personCount
    daySet.add(row.day)
  })

  const days = Array.from(daySet).sort()
  const depts = Object.keys(deptMap)
  depts.sort((a, b) => {
    const lastDay = days[days.length - 1]
    return (deptMap[b][lastDay] || 0) - (deptMap[a][lastDay] || 0)
  })

  return {
    days,
    series: depts.map((dept, index) => {
      const color = deptChartPalette[index % deptChartPalette.length]
      return {
        name: dept,
        type: 'line',
        smooth: true,
        data: days.map((day) => deptMap[dept][day] || 0),
        lineStyle: { width: 2, color, shadowColor: color + '66', shadowBlur: 4 },
        itemStyle: { color },
        symbol: 'circle',
        symbolSize: 4,
        emphasis: {
          focus: 'series',
          lineStyle: { width: 3, shadowBlur: 8 },
          itemStyle: { symbolSize: 7 }
        }
      }
    })
  }
}

export async function fetchDashboardTrendDailyData(activePeriod, force = false) {
  const days = { day: 7, week: 7, month: 30 }[activePeriod] || 30
  const now = Date.now()
  if (!force && _cache.trendDailyFetchedAt && _cache.trendDailyDays === days
    && (now - _cache.trendDailyFetchedAt) < 5 * 60 * 1000) {
    return _cache.trendDailyData || []
  }

  const start = performance.now()
  try {
    const res = await getDailyTrend(days, DASHBOARD_REQUEST_OPTIONS)
    postPerfLog(`[dashboard] getDailyTrend(${days}days): ${(performance.now() - start).toFixed(0)}ms`)
    const nextData = (res.code === 200 && Array.isArray(res.data)) ? res.data : []
    _cache.trendDailyFetchedAt = Date.now()
    _cache.trendDailyDays = days
    _cache.trendDailyData = nextData
    return nextData
  } catch {
    return []
  }
}

export async function fetchDashboardWarningDistData(periodRange, activePeriod, force = false) {
  const now = Date.now()
  const groupBy = activePeriod === 'day' ? 'hour' : 'day'
  const cacheKey = JSON.stringify(periodRange) + groupBy
  if (!force && _cache.warningDistFetchedAt && (now - _cache.warningDistFetchedAt) < 2 * 60 * 1000 && _cache.warningDistPeriodKey === cacheKey) {
    return _cache.warningDistData || { labels: [], counts: [] }
  }

  const start = performance.now()
  try {
    const res = await getWarningCounts({ ...periodRange, groupBy }, DASHBOARD_REQUEST_OPTIONS)
    postPerfLog(`[dashboard] getWarningCounts(${groupBy}): ${(performance.now() - start).toFixed(0)}ms`)
    const nextData = (res.code === 200 && res.data) ? res.data : { labels: [], counts: [] }
    _cache.warningDistFetchedAt = Date.now()
    _cache.warningDistPeriodKey = cacheKey
    _cache.warningDistData = nextData
    return nextData
  } catch {
    return { labels: [], counts: [] }
  }
}

export async function fetchDashboardWarningTypesData(periodRange) {
  try {
    const month = periodRange.startTime.slice(0, 7)
    const res = await getWarningTypes({ month }, DASHBOARD_REQUEST_OPTIONS)
    return res.code === 200 && Array.isArray(res.data) ? res.data : []
  } catch {
    return []
  }
}

export function resolveDashboardWarningDistSeries(dist, activePeriod) {
  if (!dist.labels || !dist.counts || !Array.isArray(dist.labels) || !Array.isArray(dist.counts) || dist.counts.length === 0) {
    return {
      labels: [],
      vals: [],
      maxVal: 1,
      yMax: undefined
    }
  }

  let labels = []
  let vals = []

  if (activePeriod === 'day') {
    const hourCounts = new Array(24).fill(0)
    dist.labels.forEach((hourLabel, index) => {
      const hourStr = String(hourLabel).split(':')[0].split(' ').pop()
      const hour = parseInt(hourStr, 10)
      if (!isNaN(hour) && hour >= 0 && hour < 24) {
        hourCounts[hour] = dist.counts[index] || 0
      }
    })
    labels = Array.from({ length: 24 }, (_, i) => i % 3 === 0 ? `${i}h` : '')
    vals = hourCounts
  } else {
    labels = (dist.labels || []).map((date) => String(date).slice(5))
    vals = dist.counts || []
  }

  const maxVal = Math.max(...vals, 1)
  const sorted = [...vals].filter((value) => value > 0).sort((a, b) => a - b)
  const median = sorted[Math.floor(sorted.length / 2)] || 1

  return {
    labels,
    vals,
    maxVal,
    yMax: activePeriod === 'day' ? undefined : Math.max(median * 3, 5)
  }
}

export function resolveDashboardWarningDistDate(dist, activePeriod, index) {
  if (activePeriod === 'day') {
    const now = new Date()
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  }

  const rawDate = (dist.labels || [])[index]
  return rawDate ? String(rawDate).slice(0, 10) : ''
}
