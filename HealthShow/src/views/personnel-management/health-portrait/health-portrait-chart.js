function buildFallbackTrendDates() {
  const dates = []
  for (let i = 6; i >= 0; i--) {
    const date = new Date()
    date.setDate(date.getDate() - i)
    dates.push(`${date.getMonth() + 1}/${date.getDate()}`)
  }
  return dates
}

export function buildPortraitTrendChartOption(echarts, trendData) {
  const rawDates = trendData?.dates || []
  const hasData = rawDates.length > 0
  const dates = hasData ? rawDates : buildFallbackTrendDates()
  const heartRates = hasData ? (trendData?.heartRates || []) : []
  const bloodOxygens = hasData ? (trendData?.bloodOxygens || []) : []

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(20,24,48,.95)',
      borderColor: '#2d3561',
      textStyle: { color: '#c8d8e8', fontSize: 12 }
    },
    legend: { data: ['心率', '血氧'], bottom: 2, textStyle: { color: '#7eb8d4', fontSize: 11 } },
    grid: { top: 16, right: 52, bottom: 38, left: 52 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#232b4d' } },
      axisLabel: { color: '#7eb8d4', fontSize: 10 }
    },
    yAxis: [
      {
        type: 'value',
        name: '心率',
        min: 40,
        max: 140,
        nameTextStyle: { color: '#7eb8d4', fontSize: 10 },
        axisLine: { lineStyle: { color: '#232b4d' } },
        splitLine: { lineStyle: { color: '#232b4d' } },
        axisLabel: { color: '#7eb8d4', fontSize: 10 }
      },
      {
        type: 'value',
        name: '血氧%',
        min: 85,
        max: 100,
        nameTextStyle: { color: '#7eb8d4', fontSize: 10 },
        axisLine: { lineStyle: { color: '#232b4d' } },
        splitLine: { show: false },
        axisLabel: { color: '#7eb8d4', fontSize: 10 }
      }
    ],
    series: [
      {
        name: '心率',
        type: 'line',
        data: heartRates,
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2 },
        itemStyle: { color: '#ff5252' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(255,82,82,.3)' },
            { offset: 1, color: 'rgba(255,82,82,0)' }
          ])
        }
      },
      {
        name: '血氧',
        type: 'line',
        yAxisIndex: 1,
        data: bloodOxygens,
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2 },
        itemStyle: { color: '#00d4ff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(0,212,255,.3)' },
            { offset: 1, color: 'rgba(0,212,255,0)' }
          ])
        }
      }
    ]
  }
}

export function buildMetricAbnormalTrendOption(echarts, rows, startDate, endDate, config = {}) {
  const ordered = [...(rows || [])].reverse()
  const dates = ordered.map(row => String(row.recordTime || '').slice(5, 16))
  const primaryValues = ordered.map(row => row.primaryValue)
  const secondaryValues = ordered.map(row => row.secondaryValue)
  const allValues = [...primaryValues, ...secondaryValues].filter(value => value != null)
  const unit = config.unit ? ` ${config.unit}` : ''
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(20,24,48,.95)',
      borderColor: '#2d3561',
      textStyle: { color: '#c8d8e8', fontSize: 12 },
      formatter: params => {
        const point = params?.[0]
        const row = ordered[point?.dataIndex]
        if (!row) return '暂无数据'
        const secondary = row.secondaryValue == null ? '' : `<br/>${config.secondaryLabel || '次值'}：<b>${row.secondaryValue}${unit}</b>`
        return `${row.recordTime}<br/>${config.primaryLabel || '指标'}：<b>${row.primaryValue}${unit}</b>${secondary}` +
          `<br/>${row.direction === 'low' ? '偏低' : row.level === 'danger' ? '高危' : '偏高'}`
      }
    },
    grid: { top: 20, right: 28, bottom: 42, left: 52 },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#232b4d' } },
      axisLabel: { color: '#7eb8d4', fontSize: 10, interval: Math.max(0, Math.floor(dates.length / 6) - 1) }
    },
    yAxis: {
      type: 'value',
      name: `${config.label || '指标'}${unit}`,
      min: allValues.length ? Math.max(0, Math.min(...allValues) - (config.padding || 5)) : 0,
      max: allValues.length ? Math.max(...allValues) + (config.padding || 5) : 100,
      nameTextStyle: { color: '#7eb8d4', fontSize: 10 },
      splitLine: { lineStyle: { color: '#232b4d' } },
      axisLabel: { color: '#7eb8d4', fontSize: 10 }
    },
    series: [{
      name: config.primaryLabel || `${startDate} 至 ${endDate}`,
      type: 'line',
      data: primaryValues,
      smooth: false,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: '#ff7070', width: 2 },
      itemStyle: { color: '#ff7070' },
      markLine: config.markLines?.length ? {
        silent: true,
        symbol: 'none',
        data: config.markLines.map(line => ({
          yAxis: line.value,
          lineStyle: { color: line.color, type: 'dashed' },
          label: { formatter: `${line.label} ${line.value}`, color: line.color }
        }))
      } : undefined
    }, ...(config.dual ? [{
      name: config.secondaryLabel || '次值', type: 'line', data: secondaryValues,
      smooth: false, symbol: 'circle', symbolSize: 5,
      lineStyle: { color: '#38bdf8', width: 2 }, itemStyle: { color: '#38bdf8' }
    }] : [])]
  }
}

export function buildHeartRateAbnormalTrendOption(echarts, rows, startDate, endDate) {
  return buildMetricAbnormalTrendOption(echarts, rows.map(row => ({ ...row, primaryValue: row.primaryValue ?? row.heartRate })), startDate, endDate, {
    label: '心率', primaryLabel: '心率', unit: 'bpm', markLines: [
      { value: 55, label: '偏低', color: '#4fc3f7' }, { value: 120, label: '偏高', color: '#ffb84d' }
    ]
  })
}

export function buildPortraitRadarChartOption(healthScores) {
  const scores = healthScores || {}
  return {
    backgroundColor: 'transparent',
    radar: {
      center: ['50%', '50%'],
      radius: '68%',
      indicator: [
        { name: '心率', max: 100 },
        { name: '血氧', max: 100 },
        { name: '活动', max: 100 },
        { name: '血压', max: 100 },
        { name: '体温', max: 100 },
        { name: '压力', max: 100 }
      ],
      axisName: { color: '#7eb8d4', fontSize: 11 },
      axisLine: { lineStyle: { color: '#232b4d' } },
      splitLine: { lineStyle: { color: '#232b4d' } },
      splitArea: { areaStyle: { color: ['rgba(0,212,255,.02)', 'rgba(0,212,255,.05)'] } }
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: [
              scores.heartRate || null,
              scores.bloodOxygen || null,
              scores.activity || null,
              scores.bloodPressure || null,
              scores.temperature || null,
              scores.pressure || null
            ],
            name: '健康评分',
            areaStyle: { color: 'rgba(0,212,255,.18)' },
            lineStyle: { color: '#00d4ff', width: 2 },
            itemStyle: { color: '#00d4ff' }
          }
        ]
      }
    ]
  }
}

export function renderPortraitTrendChart({ echarts, element, chart, trendData }) {
  if (!element) return chart
  chart?.dispose()
  const nextChart = echarts.init(element)
  nextChart.setOption(buildPortraitTrendChartOption(echarts, trendData))
  return nextChart
}

export function renderHeartRateAbnormalTrend({ echarts, element, chart, rows, startDate, endDate }) {
  if (!element) return chart
  chart?.dispose()
  const nextChart = echarts.init(element)
  nextChart.setOption(buildHeartRateAbnormalTrendOption(echarts, rows, startDate, endDate))
  return nextChart
}

export function renderMetricAbnormalTrend({ echarts, element, chart, rows, startDate, endDate, config }) {
  if (!element) return chart
  chart?.dispose()
  const nextChart = echarts.init(element)
  nextChart.setOption(buildMetricAbnormalTrendOption(echarts, rows, startDate, endDate, config))
  return nextChart
}

export function renderPortraitRadarChart({ echarts, element, chart, healthScores }) {
  if (!element) return chart
  chart?.dispose()
  const nextChart = echarts.init(element)
  nextChart.setOption(buildPortraitRadarChartOption(healthScores))
  return nextChart
}

export function disposePortraitChart(chart) {
  chart?.dispose()
  return null
}
