import { emptyOption, chartTooltip, categoryAxis, valueAxis, deptGrid, trendGrid } from '@/utils/echarts-config'
import { initChart, gradH, gradV } from '@/utils/chart-helpers'

export function renderPeriodRiskTrend(page, refName, rows, colors = {}) {
  const chart = initChart(page.charts, 'periodRisk', page.$refs[refName])
  if (!chart) return
  if (!rows?.length) {
    chart.setOption(emptyOption('暂无周期风险数据', 13))
    return
  }
  const barColor = colors.bar || '#FFB84D'
  const lineColor = colors.line || '#00d4ff'
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: chartTooltip((params) => {
      const row = rows[params[0]?.dataIndex]
      if (!row) return '暂无数据'
      return `${row.date}<br/>异常人数：<b style="color:${barColor}">${row.anomalyCount}</b> 人` +
        `<br/>异常率：<b style="color:${lineColor}">${row.anomalyRate}%</b>` +
        `<br/>有效覆盖：${row.coveredUsers} 人`
    }),
    grid: { ...trendGrid(), right: '8%', top: '14%' },
    xAxis: { ...categoryAxis(rows.map(row => row.date?.slice(5) || row.date), { fontSize: 10, interval: Math.max(0, Math.floor(rows.length / 6) - 1) }) },
    yAxis: [
      valueAxis({ name: '人数', min: 0, fontSize: 10 }),
      valueAxis({ name: '异常率', min: 0, max: 100, fontSize: 10, position: 'right' })
    ],
    series: [
      {
        name: '异常人数', type: 'bar', barMaxWidth: 16,
        data: rows.map(row => row.anomalyCount || 0),
        itemStyle: { color: gradV(barColor, `${barColor}33`), borderRadius: [3, 3, 0, 0] }
      },
      {
        name: '异常率', type: 'line', yAxisIndex: 1, smooth: true, symbolSize: 5,
        data: rows.map(row => Number(row.anomalyRate || 0)),
        lineStyle: { color: lineColor, width: 2 }, itemStyle: { color: lineColor },
        areaStyle: { color: gradV(`${lineColor}33`, `${lineColor}05`) }
      }
    ]
  })
}

export function renderDepartmentRisk(page, refName, rows, colors = {}) {
  const chart = initChart(page.charts, 'dept', page.$refs[refName])
  if (!chart) return
  if (!rows?.length) {
    chart.setOption(emptyOption('暂无部门风险数据', 13))
    return
  }
  const data = rows.slice(0, 12)
  const barColor = colors.bar || '#FFB84D'
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: chartTooltip((params) => {
      const row = data[params[0]?.dataIndex]
      return row ? `${row.deptName}<br/>异常人数：${row.abnormalUsers} 人<br/>异常记录：${row.abnormalRecords} 条<br/>覆盖：${row.coveredUsers} 人` : '暂无数据'
    }),
    grid: { ...deptGrid(), top: '8%' },
    xAxis: valueAxis({ min: 0 }),
    yAxis: { ...categoryAxis(data.map(row => row.deptName), { show: false }), inverse: true },
    series: [{
      name: '异常人数', type: 'bar', barWidth: '46%',
      data: data.map(row => row.abnormalUsers || 0),
      itemStyle: { color: gradH(barColor, `${barColor}88`), borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', color: '#c8d8e8', fontSize: 10 }
    }]
  })
  chart.off('click')
  chart.on('click', params => {
    const row = data[params.dataIndex]
    if (row) page.openDepartmentRisk(row)
  })
}
