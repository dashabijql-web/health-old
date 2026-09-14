import * as echarts from '@/utils/echarts-setup'

const TAB_CHART_KEYS = {
  monthly: ['daily', 'warnType'],
  dept: ['deptBar', 'deptWarn'],
  trend: ['trend', 'personTrend']
}

function setEmptyChart(chart) {
  chart.setOption({
    backgroundColor: 'transparent',
    graphic: [{
      type: 'text',
      left: 'center',
      top: 'middle',
      style: { text: '暂无数据', fill: '#4a6080', fontSize: 13 }
    }]
  })
}

function replaceChart(charts, key, domId) {
  const dom = document.getElementById(domId)
  if (!dom) {
    return null
  }
  charts[key]?.dispose?.()
  const chart = echarts.init(dom)
  charts[key] = chart
  return chart
}

function disposeChartKeys(charts, keys) {
  keys.forEach((key) => {
    charts[key]?.dispose?.()
    delete charts[key]
  })
}

function initDailyCountChart(charts, dailyCounts) {
  const chart = replaceChart(charts, 'daily', 'rcDailyCountChart')
  if (!chart) return

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(10,20,50,0.9)',
      borderColor: '#00d4ff',
      textStyle: { color: '#fff', fontSize: 11 }
    },
    grid: { left: 40, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: dailyCounts.map((item) => String(item.day || item.date || '')),
      axisLabel: { color: '#6a7a9a', fontSize: 10 },
      axisLine: { lineStyle: { color: '#1e3a5f' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#6a7a9a', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(100,160,255,0.08)' } }
    },
    series: [{
      type: 'bar',
      data: dailyCounts.map((item) => item.count || item.recordCount || 0),
      barMaxWidth: 16,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#00d4ff' },
          { offset: 1, color: '#00d4ff33' }
        ]),
        borderRadius: [3, 3, 0, 0]
      }
    }]
  })
}

function initWarnTypeChart(charts, warnTypes) {
  const chart = replaceChart(charts, 'warnType', 'rcWarnTypeChart')
  if (!chart) return

  const palette = ['#ff5252', '#ffd200', '#00d4ff', '#67C23A', '#a78bfa', '#ff9800']
  const data = warnTypes.map((item, index) => ({
    name: item.typeName || item.name || item.indicatorName || `类型${index}`,
    value: item.count || item.value || 0,
    itemStyle: { color: palette[index % palette.length] }
  }))

  if (!data.length) {
    setEmptyChart(chart)
    return
  }

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(10,20,50,0.9)',
      borderColor: '#00d4ff',
      textStyle: { color: '#fff', fontSize: 12 },
      formatter: '{b}: {c}次 ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: { color: '#8ba6c8', fontSize: 11 },
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 10
    },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['38%', '50%'],
      data,
      label: { show: false }
    }]
  })
}

function initDeptBarChart(charts, deptSummary) {
  const chart = replaceChart(charts, 'deptBar', 'rcDeptBarChart')
  if (!chart) return

  if (!deptSummary.length) {
    setEmptyChart(chart)
    return
  }

  const getName = (item) => item.deptName || item.dept_name || '--'
  const getEmployeeCount = (item) => item.employeeCount || item.employee_count || 0
  const getWarningCount = (item) => item.warningCount || item.warning_count || 0

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(10,20,50,0.9)',
      borderColor: '#00d4ff',
      textStyle: { color: '#fff', fontSize: 11 }
    },
    legend: { top: 0, textStyle: { color: '#8ba6c8', fontSize: 11 } },
    grid: { left: 90, right: 20, top: 30, bottom: 20 },
    xAxis: {
      type: 'value',
      axisLabel: { color: '#6a7a9a', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(100,160,255,0.08)' } }
    },
    yAxis: {
      type: 'category',
      data: deptSummary.map(getName),
      axisLabel: { color: '#8ba6c8', fontSize: 11, width: 80, overflow: 'truncate' },
      axisLine: { show: false }
    },
    series: [
      {
        name: '在册人数',
        type: 'bar',
        data: deptSummary.map(getEmployeeCount),
        barMaxWidth: 12,
        itemStyle: { color: '#00d4ff', borderRadius: [0, 3, 3, 0] }
      },
      {
        name: '近30天预警次数',
        type: 'bar',
        data: deptSummary.map(getWarningCount),
        barMaxWidth: 12,
        itemStyle: { color: '#ff9800', borderRadius: [0, 3, 3, 0] }
      }
    ]
  })
}

function initDeptWarnChart(charts, deptSummary) {
  const chart = replaceChart(charts, 'deptWarn', 'rcDeptWarnChart')
  if (!chart) return

  const getWarningCount = (item) => item.warningCount || item.warning_count || 0
  const getName = (item) => item.deptName || item.dept_name || '--'
  const sorted = [...deptSummary].sort((a, b) => getWarningCount(b) - getWarningCount(a))

  if (!sorted.length) {
    setEmptyChart(chart)
    return
  }

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      formatter: (params) => `${params[0].name}：${params[0].value} 次`,
      backgroundColor: 'rgba(10,20,50,0.9)',
      borderColor: '#00d4ff',
      textStyle: { color: '#fff', fontSize: 11 }
    },
    grid: { left: 90, right: 40, top: 10, bottom: 20 },
    xAxis: {
      type: 'value',
      axisLabel: { color: '#6a7a9a', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(100,160,255,0.08)' } }
    },
    yAxis: {
      type: 'category',
      data: sorted.map(getName),
      axisLabel: { color: '#8ba6c8', fontSize: 11, width: 80, overflow: 'truncate' },
      axisLine: { show: false }
    },
    series: [{
      type: 'bar',
      data: sorted.map((item) => {
        const value = getWarningCount(item)
        return {
          value,
          itemStyle: {
            color: value > 10 ? '#ff5252' : value > 3 ? '#ffd200' : '#4CAF50',
            borderRadius: [0, 4, 4, 0]
          }
        }
      }),
      barMaxWidth: 14,
      label: { show: true, position: 'right', color: '#8ba6c8', fontSize: 10 }
    }]
  })
}

function initTrendChart(charts, trendData) {
  const chart = replaceChart(charts, 'trend', 'rcTrendChart')
  if (!chart) return

  if (!trendData.length) {
    setEmptyChart(chart)
    return
  }

  const dates = trendData.map((item) => item.date)
  const series = [
    { name: '心率异常率', key: 'heartRateRate', color: '#00d4ff' },
    { name: '血氧异常率', key: 'bloodOxygenRate', color: '#67C23A' },
    { name: '体温异常率', key: 'temperatureRate', color: '#ffd200' },
    { name: '压力异常率', key: 'pressureRate', color: '#a78bfa' }
  ].map((seriesItem) => ({
    name: seriesItem.name,
    type: 'line',
    smooth: true,
    data: trendData.map((item) => item[seriesItem.key] ?? item[seriesItem.key.replace('Rate', 'AbnormalRate')] ?? null),
    lineStyle: { color: seriesItem.color, width: 2 },
    itemStyle: { color: seriesItem.color },
    symbol: 'circle',
    symbolSize: 4
  }))

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(10,20,50,0.92)',
      borderColor: '#00d4ff33',
      textStyle: { color: '#fff', fontSize: 11 },
      formatter: (params) => {
        return `<div style="color:#8ba0bb;margin-bottom:3px">${params[0].axisValue}</div>` + params.map((item) => {
          return `<div><span style="color:${item.color}">●</span> ${item.seriesName}：<b>${item.value ?? '--'}%</b></div>`
        }).join('')
      }
    },
    legend: { top: 2, textStyle: { color: '#8ba0bb', fontSize: 11 }, itemWidth: 14, itemHeight: 3 },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { color: '#6a7a9a', fontSize: 10, formatter: (value) => value.slice(5) },
      axisLine: { lineStyle: { color: '#1e3a5f' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#6a7a9a', fontSize: 10, formatter: (value) => `${value}%` },
      splitLine: { lineStyle: { color: 'rgba(100,160,255,0.08)' } }
    },
    series
  })
}

function initPersonTrendChart(charts, trendData) {
  const chart = replaceChart(charts, 'personTrend', 'rcPersonTrendChart')
  if (!chart) return

  if (!trendData.length) {
    setEmptyChart(chart)
    return
  }

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(10,20,50,0.9)',
      borderColor: '#00d4ff',
      textStyle: { color: '#fff', fontSize: 11 }
    },
    grid: { left: 40, right: 20, top: 10, bottom: 30 },
    xAxis: {
      type: 'category',
      data: trendData.map((item) => item.date ? item.date.slice(5) : ''),
      axisLabel: { color: '#6a7a9a', fontSize: 10 },
      axisLine: { lineStyle: { color: '#1e3a5f' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#6a7a9a', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(100,160,255,0.08)' } }
    },
    series: [{
      type: 'line',
      smooth: true,
      data: trendData.map((item) => item.personCount || item.checkCount || 0),
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0,212,255,0.25)' },
          { offset: 1, color: 'rgba(0,212,255,0.02)' }
        ])
      },
      lineStyle: { color: '#00d4ff', width: 2 },
      itemStyle: { color: '#00d4ff' }
    }]
  })
}

export function initReportCenterCharts(vm) {
  const inactiveTabs = Object.keys(TAB_CHART_KEYS).filter((tab) => tab !== vm.activeTab)
  inactiveTabs.forEach((tab) => disposeChartKeys(vm.charts, TAB_CHART_KEYS[tab]))

  if (vm.activeTab === 'monthly') {
    initDailyCountChart(vm.charts, vm.dailyCounts)
    initWarnTypeChart(vm.charts, vm.warnTypes)
    return
  }

  if (vm.activeTab === 'dept') {
    initDeptBarChart(vm.charts, vm.deptSummary)
    initDeptWarnChart(vm.charts, vm.deptSummary)
    return
  }

  initTrendChart(vm.charts, vm.trendData)
  initPersonTrendChart(vm.charts, vm.trendData)
}

export function resizeReportCenterCharts(charts) {
  Object.values(charts).forEach((chart) => chart?.resize?.())
}

export function disposeReportCenterCharts(charts) {
  Object.keys(charts).forEach((key) => {
    charts[key]?.dispose?.()
    delete charts[key]
  })
}
