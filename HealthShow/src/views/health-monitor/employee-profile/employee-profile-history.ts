export interface HistoryMetric {
  key: string
  label: string
  unit: string
  color: string
  yAxisIndex: number
}

export interface HistoryPoint extends Record<string, unknown> {
  time?: string
}

export interface HealthHistory {
  points?: HistoryPoint[]
  granularity?: string
}

interface TooltipEntry {
  value?: unknown
  dataIndex: number
  name?: string
  marker?: string
  seriesName?: string
}

export const HISTORY_METRICS: HistoryMetric[] = [
  { key: 'heartRate', label: '心率', unit: 'bpm', color: '#ff6268', yAxisIndex: 0 },
  { key: 'bloodOxygen', label: '血氧', unit: '%', color: '#35a7ff', yAxisIndex: 1 },
  { key: 'temperature', label: '体温', unit: '°C', color: '#f0ad36', yAxisIndex: 2 },
  { key: 'systolic', label: '收缩压', unit: 'mmHg', color: '#c879ff', yAxisIndex: 0 },
  { key: 'diastolic', label: '舒张压', unit: 'mmHg', color: '#9a6cff', yAxisIndex: 0 },
  { key: 'pressure', label: '压力', unit: '', color: '#36c98f', yAxisIndex: 0 },
  { key: 'steps', label: '步数', unit: '步', color: '#4dd0a8', yAxisIndex: 3 },
  { key: 'calories', label: '热量', unit: 'kcal', color: '#ff8f5c', yAxisIndex: 4 }
]

export function defaultHistoryRange(days = 7, now = new Date()): [string, string] {
  const end = new Date(now)
  const start = new Date(now)
  start.setDate(start.getDate() - Math.max(1, days) + 1)
  return [formatDate(start), formatDate(end)]
}

export function formatDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function normalizeHistoryRecords(payload: unknown) {
  const data = (payload && typeof payload === 'object' ? payload : {}) as Record<string, unknown>
  return {
    records: Array.isArray(data.records) ? data.records : [],
    total: Number(data.total) || 0,
    current: Number(data.current) || 1,
    size: Number(data.size) || 20
  }
}

export function formatHistoryTemperature(value: unknown): string {
  if (value === null || value === undefined || value === '') return '--'
  const numeric = Number(value)
  return `${(numeric > 100 ? numeric / 10 : numeric).toFixed(1)} °C`
}

export function formatHistoryBloodPressure(record: Record<string, unknown> | null | undefined): string {
  const high = record?.bloodPressureHigh ?? record?.blood_pressure_high
  const low = record?.bloodPressureLow ?? record?.blood_pressure_low
  return high && low ? `${high}/${low}` : '--'
}

export function buildHistoryChartOption(history: HealthHistory | null | undefined, activeMetrics: string[]) {
  const points = history?.points || []
  const metrics = HISTORY_METRICS.filter((metric) => activeMetrics.includes(metric.key))
  const rawRecords = history?.granularity === 'record'
  const hourly = history?.granularity === 'hour'
  const chartPoints = rawRecords
    ? points.filter((point) => metrics.some((metric) => point[metric.key] !== null && point[metric.key] !== undefined))
    : points
  return {
    animation: false,
    color: metrics.map((metric) => metric.color),
    grid: { left: 52, right: 54, top: 46, bottom: 56 },
    tooltip: {
      trigger: 'axis',
      triggerOn: 'mousemove|click',
      confine: true,
      axisPointer: {
        type: 'line',
        snap: true,
        lineStyle: { color: '#7fa3bf', width: 1, type: 'dashed' }
      },
      backgroundColor: '#0b172a',
      borderColor: '#26496c',
      textStyle: { color: '#eaf6ff' },
      formatter: (params: TooltipEntry | TooltipEntry[]) => {
        const entries = (Array.isArray(params) ? params : [params])
          .filter((item) => item.value !== null && item.value !== undefined && item.value !== '')
        if (!entries.length) return ''

        const time = chartPoints[entries[0].dataIndex]?.time || entries[0].name || ''
        const values = entries.map((item) => (
          `${item.marker}${item.seriesName}<span style="float:right;margin-left:20px;font-weight:600">${item.value}</span>`
        ))
        return [`<div style="margin-bottom:4px">${time}</div>`, ...values].join('<br>')
      }
    },
    legend: { top: 8, textStyle: { color: '#91b0ca' } },
    dataZoom: chartPoints.length > 14 ? [{ type: 'inside' }, { type: 'slider', height: 18, bottom: 8 }] : [],
    xAxis: {
      type: 'category',
      data: chartPoints.map((point) => rawRecords
        ? point.time?.slice(5, 19)
        : hourly ? point.time?.slice(5, 16) : point.time?.slice(5, 10)),
      boundaryGap: false,
      axisPointer: { show: true, snap: true },
      axisLabel: { color: '#6f91ad', hideOverlap: true },
      axisLine: { lineStyle: { color: '#25415e' } }
    },
    yAxis: [
      { type: 'value', scale: true, axisLabel: { color: '#6f91ad' }, splitLine: { lineStyle: { color: '#162b44' } } },
      { type: 'value', min: 80, max: 100, axisLabel: { color: '#35a7ff' }, splitLine: { show: false } },
      { type: 'value', min: 34, max: 42, show: false },
      { type: 'value', scale: true, show: false },
      { type: 'value', scale: true, show: false }
    ],
    series: metrics.map((metric) => ({
      name: `${metric.label} ${metric.unit}`.trim(),
      type: 'line',
      yAxisIndex: metric.yAxisIndex,
      data: chartPoints.map((point) => point[metric.key]),
      connectNulls: rawRecords,
      showSymbol: rawRecords || chartPoints.length <= 31,
      symbolSize: 5,
      lineStyle: { width: 2 },
      emphasis: { focus: 'series' }
    }))
  }
}
