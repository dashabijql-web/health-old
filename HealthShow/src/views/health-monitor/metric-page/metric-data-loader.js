import dayjs from 'dayjs'

export function getMetricToday() {
  return dayjs().format('YYYY-MM-DD')
}

export function metricDaysForPeriod(period) {
  return period === 'day' ? 1 : period === 'week' ? 7 : 30
}

export async function fetchMetricData(fetcher, fallback) {
  try {
    const response = await fetcher()
    if (response?.code === 200) return response.data ?? fallback
  } catch {}
  return fallback
}

export async function loadMetricOverview(page, fetcher, fallback = {}) {
  const { startDate, endDate } = page.periodRange
  page.overview = await fetchMetricData(() => fetcher(startDate, endDate), fallback)
}

export async function loadMetricTopUsers(page, fetcher, limit = 10) {
  const { startDate, endDate } = page.periodRange
  page.top5Data = await fetchMetricData(() => fetcher(limit, startDate, endDate), [])
  page.$nextTick(() => page.startTop5Scroll())
}

export async function loadMetricRangeChart(page, fetcher, renderMethod, options = {}) {
  const { startDate, endDate } = page.periodRange
  const data = await fetchMetricData(() => fetcher(startDate, endDate), [])
  if (options.assignTo) page[options.assignTo] = data
  page.$nextTick(() => page[renderMethod](data))
}

export async function loadMetricDistribution(page, fetcher, renderMethod) {
  const { startDate, endDate } = page.periodRange
  const data = await fetchMetricData(() => fetcher(startDate, endDate), [])
  const filtered = (data || []).filter(item => item.name && item.value > 0)
  page.distLegend = filtered
  if (renderMethod) page.$nextTick(() => page[renderMethod](filtered))
}

export async function loadMetricRealtime(page, fetcher, limit = 1000) {
  const rows = await fetchMetricData(() => fetcher(limit), [])
  const seen = new Set()
  page.realtimeList = rows.filter((row) => {
    const key = row.userCode || row.empCode || row.userName
    if (!key || seen.has(key)) return false
    seen.add(key)
    return true
  })
}

export function createHourlySeries(rows, valueKey) {
  const values = new Array(24).fill(null)
  ;(rows || []).forEach((row) => {
    if (row.hour >= 0 && row.hour < 24) values[row.hour] = row[valueKey]
  })
  return values
}
