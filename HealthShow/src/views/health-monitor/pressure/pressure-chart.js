import { emptyOption, chartTooltip, categoryAxis, valueAxis, deptGrid, hourlyGrid, barLabel } from '@/utils/echarts-config'
import { initChart, gradH, gradV } from '@/utils/chart-helpers'

export const pressureChartMethods = {
  initDept(data) {
    const c = initChart(this.charts, 'dept', this.$refs.deptRef)
    if (!c) return
    if (!data.length) {
      c.setOption(emptyOption())
      return
    }
    const d = data.slice(0, 10)
    c.setOption({
      backgroundColor: 'transparent',
      grid: { ...deptGrid(), top: '8%' },
      xAxis: { ...valueAxis(), min: 0, max: 100 },
      yAxis: { ...categoryAxis(d.map(x => x.deptName), { show: false }), inverse: true },
      series: [{
        name: '平均压力',
        type: 'bar',
        barWidth: '46%',
        data: d.map(x => ({
          value: x.avgPressure,
          itemStyle: {
            color: gradH(
              x.avgPressure >= 85 ? '#ff5252' : x.avgPressure >= 70 ? '#FFB84D' : '#fb923c',
              x.avgPressure >= 85 ? '#b91c1c' : x.avgPressure >= 70 ? '#d97706' : '#c2410c'
            ),
            borderRadius: [0, 4, 4, 0]
          }
        })),
        label: barLabel(),
        markLine: {
          silent: true,
          lineStyle: { color: '#FFB84D55', type: 'dashed' },
          data: [{ xAxis: 70, name: '偏高线' }]
        }
      }]
    })
    c.off('click')
    c.on('click', params => {
      const name = d[params.dataIndex]?.deptName
      if (!name) return
      this.filterDept = this.filterDept === name ? '' : name
    })
  },

  renderHourly(vals) {
    const c = initChart(this.charts, 'hourly', this.$refs.hourlyRef)
    if (!c) return
    const hours = Array.from({ length: 24 }, (_, i) => i + ':00')
    c.setOption({
      backgroundColor: 'transparent',
      tooltip: chartTooltip(p => p[0].value != null
        ? `${p[0].name}<br/>压力指数：<b style="color:#fb923c">${p[0].value}</b>`
        : `${p[0].name}<br/>暂无数据`),
      grid: hourlyGrid(),
      xAxis: { ...categoryAxis(hours, { fontSize: 9, interval: 3, lineColor: 'rgba(251,146,60,0.15)' }), boundaryGap: false },
      yAxis: { ...valueAxis({ fontSize: 9, splitColor: 'rgba(251,146,60,0.06)' }), min: 0, max: 100 },
      series: [{
        type: 'line',
        data: vals,
        smooth: true,
        symbol: 'none',
        connectNulls: false,
        lineStyle: { color: '#fb923c', width: 2 },
        areaStyle: { color: gradV('rgba(251,146,60,0.28)', 'rgba(251,146,60,0.02)') },
        markLine: {
          silent: true,
          symbol: 'none',
          data: [
            { yAxis: 70, lineStyle: { color: '#FFB84D', type: 'dashed', width: 1 }, label: { color: '#FFB84D', fontSize: 10, formatter: '偏高 70' } },
            { yAxis: 85, lineStyle: { color: '#ff5252', type: 'dashed', width: 1 }, label: { color: '#ff5252', fontSize: 10, formatter: '高压 85' } }
          ]
        }
      }]
    })
  },

  renderHourlyDaily(dates, vals) {
    const c = initChart(this.charts, 'hourly', this.$refs.hourlyRef)
    if (!c) return
    if (!dates.length) {
      c.setOption(emptyOption('暂无数据', 13))
      return
    }
    c.setOption({
      backgroundColor: 'transparent',
      tooltip: chartTooltip(p => `${p[0].name}<br/>压力指数：<b style="color:#fb923c">${p[0].value}</b>`),
      grid: hourlyGrid(),
      xAxis: { ...categoryAxis(dates, { fontSize: 9, interval: Math.floor(dates.length / 5), lineColor: 'rgba(251,146,60,0.15)' }), boundaryGap: true },
      yAxis: { ...valueAxis({ fontSize: 9, splitColor: 'rgba(251,146,60,0.06)' }), min: 0, max: 100 },
      series: [{
        type: 'bar',
        data: vals,
        barMaxWidth: 14,
        itemStyle: {
          color: gradV('#fb923c', 'rgba(251,146,60,0.2)'),
          borderRadius: [3, 3, 0, 0]
        },
        markLine: {
          silent: true,
          symbol: 'none',
          data: [
            { yAxis: 70, lineStyle: { color: '#FFB84D', type: 'dashed', width: 1 }, label: { color: '#FFB84D', fontSize: 10, formatter: '偏高 70' } },
            { yAxis: 85, lineStyle: { color: '#ff5252', type: 'dashed', width: 1 }, label: { color: '#ff5252', fontSize: 10, formatter: '高压 85' } }
          ]
        }
      }]
    })
  }
}
