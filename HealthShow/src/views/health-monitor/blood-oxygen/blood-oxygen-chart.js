import { SPO2 } from '@/constants/health-thresholds'
import { emptyOption, chartTooltip, categoryAxis, valueAxis, deptGrid, trendGrid, barLabel } from '@/utils/echarts-config'
import { initChart, gradH, gradV } from '@/utils/chart-helpers'

export const bloodOxygenChartMethods = {
  initDept(data) {
    const c = initChart(this.charts, 'dept', this.$refs.deptRef)
    if (!c) return
    if (!data.length) {
      c.setOption(emptyOption())
      return
    }
    const d = data.map(x => ({
      deptName: x.deptName || x.name,
      lowCount: x.lowCount || 0,
      highCount: x.highCount || 0
    }))
    c.setOption({
      backgroundColor: 'transparent',
      grid: deptGrid(),
      xAxis: valueAxis(),
      yAxis: { ...categoryAxis(d.map(x => x.deptName), { show: false }), inverse: true },
      series: [
        {
          name: '偏低',
          type: 'bar',
          stack: 'total',
          barWidth: '46%',
          data: d.map(x => x.lowCount),
          itemStyle: { color: gradH('#FFB84D', '#FFA726') },
          label: barLabel()
        },
        {
          name: '优秀',
          type: 'bar',
          stack: 'total',
          barWidth: '46%',
          data: d.map(x => x.highCount),
          itemStyle: { color: gradH('#4FC3F7', '#29B6F6'), borderRadius: [0, 4, 4, 0] },
          label: barLabel()
        }
      ]
    })
    c.off('click')
    c.on('click', (params) => {
      this.filterDept = this.filterDept === params.name ? '' : params.name
      this.currentPage = 1
    })
  },

  initTrend(data) {
    const c = initChart(this.charts, 'trend', this.$refs.trendRef)
    if (!c) return
    const dates = data.dates || []
    const vals = data.values || []
    if (!dates.length) {
      c.setOption(emptyOption('暂无数据', 13))
      return
    }
    c.setOption({
      backgroundColor: 'transparent',
      tooltip: chartTooltip(p => `${p[0].name}<br/>平均血氧：<b style="color:#00d4ff">${p[0].value}%</b>`),
      grid: trendGrid(),
      xAxis: { ...categoryAxis(dates, { fontSize: 10, interval: 4 }), boundaryGap: false },
      yAxis: { ...valueAxis({ min: v => Math.max(80, v.min - 2), max: v => Math.min(100, v.max + 2) }), axisLabel: { color: '#8ba6c8', fontSize: 10, formatter: v => v + '%' } },
      series: [{
        type: 'line',
        data: vals,
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#00d4ff', width: 2 },
        areaStyle: { color: gradV('rgba(0,212,255,0.28)', 'rgba(0,212,255,0.02)') },
        markPoint: {
          symbol: 'circle',
          symbolSize: 6,
          label: { fontSize: 10, fontWeight: 'bold', fontFamily: 'Consolas', offset: [0, -14] },
          data: [
            { type: 'min', name: '最低', itemStyle: { color: '#FFB84D' }, label: { color: '#FFB84D', formatter: p => '▼' + p.value + '%' } },
            { type: 'max', name: '最高', itemStyle: { color: '#4FC3F7' }, label: { color: '#4FC3F7', formatter: p => '▲' + p.value + '%' } }
          ]
        },
        markLine: {
          silent: true,
          symbol: 'none',
          data: [
            { yAxis: SPO2.DANGER, lineStyle: { color: '#ff5252', type: 'dashed', width: 1 }, label: { color: '#ff5252', fontSize: 10, formatter: '危险 ' + SPO2.DANGER + '%' } },
            { yAxis: SPO2.LOW, lineStyle: { color: '#FFB84D', type: 'dashed', width: 1 }, label: { color: '#FFB84D', fontSize: 10, formatter: '正常 ' + SPO2.LOW + '%' } }
          ]
        }
      }]
    })
  },

  initTrendDay(vals) {
    const c = initChart(this.charts, 'trend', this.$refs.trendRef)
    if (!c) return
    const hours = Array.from({ length: 24 }, (_, i) => i + ':00')
    c.setOption({
      backgroundColor: 'transparent',
      tooltip: chartTooltip(p => p[0].value != null
        ? `${p[0].name}<br/>血氧：<b style="color:#00d4ff">${p[0].value}%</b>`
        : `${p[0].name}<br/>暂无数据`),
      grid: trendGrid(),
      xAxis: { ...categoryAxis(hours, { fontSize: 10, interval: 3 }), boundaryGap: false },
      yAxis: { ...valueAxis({ min: 90, max: 100 }), axisLabel: { color: '#8ba6c8', fontSize: 10, formatter: v => v + '%' } },
      series: [{
        type: 'line',
        data: vals,
        smooth: true,
        symbol: 'none',
        connectNulls: false,
        lineStyle: { color: '#00d4ff', width: 2 },
        areaStyle: { color: gradV('rgba(0,212,255,0.28)', 'rgba(0,212,255,0.02)') },
        markLine: {
          silent: true,
          symbol: 'none',
          data: [
            { yAxis: SPO2.LOW, lineStyle: { color: '#FFB84D', type: 'dashed', width: 1 }, label: { color: '#FFB84D', fontSize: 10, formatter: '正常下限 ' + SPO2.LOW + '%' } },
            { yAxis: SPO2.DANGER, lineStyle: { color: '#ff5252', type: 'dashed', width: 1 }, label: { color: '#ff5252', fontSize: 10, formatter: '危险 ' + SPO2.DANGER + '%' } }
          ]
        }
      }]
    })
  }
}
