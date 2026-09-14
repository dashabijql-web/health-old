import { emptyOption, chartTooltip, categoryAxis, valueAxis, deptGrid, trendGrid, hourlyGrid, barLabel } from '@/utils/echarts-config'
import { initChart, gradH, gradV } from '@/utils/chart-helpers'

export const bloodPressureChartMethods = {
  initDeptChart(data) {
    const c = initChart(this.charts, 'dept', this.$refs.deptRef)
    if (!c) return
    if (!data.length) {
      c.setOption(emptyOption())
      return
    }
    const d = data.slice(0, 12)
    c.setOption({
      backgroundColor: 'transparent',
      legend: { data: ['收缩压', '舒张压'], right: 10, top: 6, textStyle: { color: '#8ba6c8', fontSize: 11 }, itemWidth: 10, itemHeight: 10, icon: 'rect' },
      grid: { ...deptGrid(), top: '14%' },
      xAxis: valueAxis(),
      yAxis: { ...categoryAxis(d.map(x => x.deptName), { show: false }), inverse: true },
      series: [
        {
          name: '收缩压',
          type: 'bar',
          stack: 'none',
          barWidth: '35%',
          data: d.map(x => x.avgSystolic || 0),
          itemStyle: { color: gradH('#a78bfa', '#7c3aed') },
          label: barLabel()
        },
        {
          name: '舒张压',
          type: 'bar',
          stack: 'none',
          barWidth: '35%',
          data: d.map(x => x.avgDiastolic || 0),
          itemStyle: { color: gradH('#38bdf8', '#0284c7'), borderRadius: [0, 4, 4, 0] },
          label: barLabel()
        }
      ]
    })
    c.off('click')
    c.on('click', params => {
      const name = d[params.dataIndex]?.deptName
      if (!name) return
      this.filterDept = this.filterDept === name ? '' : name
    })
  },

  initTrendChart(dates, sysVals, diaVals) {
    const c = initChart(this.charts, 'trend', this.$refs.trendRef)
    if (!c) return
    if (!dates.length) {
      c.setOption(emptyOption('暂无数据', 13))
      return
    }
    c.setOption({
      backgroundColor: 'transparent',
      tooltip: chartTooltip(p => {
        const sys = p.find(x => x.seriesName === '收缩压')
        const dia = p.find(x => x.seriesName === '舒张压')
        return `${p[0].name}<br/>` +
          (sys ? `收缩压：<b style="color:#a78bfa">${sys.value || '--'}</b> mmHg<br/>` : '') +
          (dia ? `舒张压：<b style="color:#38bdf8">${dia.value || '--'}</b> mmHg` : '')
      }),
      legend: {
        data: ['收缩压', '舒张压'],
        right: 10,
        top: 4,
        textStyle: { color: '#8ba6c8', fontSize: 11 },
        itemWidth: 14,
        itemHeight: 3
      },
      grid: { ...trendGrid(), right: '5%', top: '16%' },
      xAxis: { ...categoryAxis(dates, { fontSize: 10, interval: Math.floor(dates.length / 6) }), boundaryGap: false },
      yAxis: valueAxis({ name: 'mmHg', min: v => Math.max(0, Math.floor(v.min - 8)), max: v => Math.ceil(v.max + 8) }),
      series: [
        {
          name: '收缩压',
          type: 'line',
          data: sysVals,
          smooth: true,
          symbol: 'none',
          connectNulls: false,
          lineStyle: { color: '#a78bfa', width: 2 },
          areaStyle: { color: gradV('rgba(167,139,250,0.28)', 'rgba(167,139,250,0.02)') },
          markLine: {
            silent: true,
            symbol: 'none',
            lineStyle: { color: '#a78bfa55', type: 'dashed' },
            data: [{ yAxis: 139, label: { color: '#a78bfa', fontSize: 10, formatter: '偏高 139' } }]
          }
        },
        {
          name: '舒张压',
          type: 'line',
          data: diaVals,
          smooth: true,
          symbol: 'none',
          connectNulls: false,
          lineStyle: { color: '#38bdf8', width: 2 },
          areaStyle: { color: gradV('rgba(56,189,248,0.2)', 'rgba(56,189,248,0.02)') },
          markLine: {
            silent: true,
            symbol: 'none',
            lineStyle: { color: '#38bdf855', type: 'dashed' },
            data: [{ yAxis: 89, label: { color: '#38bdf8', fontSize: 10, formatter: '偏高 89' } }]
          }
        }
      ]
    })
  },

  initHourlyChart(sysVals, diaVals) {
    const c = initChart(this.charts, 'hourly', this.$refs.hourlyRef)
    if (!c) return
    const hours = Array.from({ length: 24 }, (_, i) => i + ':00')
    c.setOption({
      backgroundColor: 'transparent',
      tooltip: chartTooltip(p => {
        const sys = p.find(x => x.seriesName === '收缩压')
        const dia = p.find(x => x.seriesName === '舒张压')
        return `${p[0].name}<br/>` +
          `收缩压：<b style="color:#a78bfa">${sys?.value ?? '--'}</b> mmHg<br/>` +
          `舒张压：<b style="color:#38bdf8">${dia?.value ?? '--'}</b> mmHg`
      }),
      legend: {
        data: ['收缩压', '舒张压'],
        right: 4,
        top: 2,
        textStyle: { color: '#8ba6c8', fontSize: 10 },
        itemWidth: 12,
        itemHeight: 3
      },
      grid: { ...hourlyGrid(), top: '18%' },
      xAxis: { ...categoryAxis(hours, { fontSize: 9, interval: 3, lineColor: 'rgba(0,212,255,0.15)' }), boundaryGap: false },
      yAxis: valueAxis({ fontSize: 9, splitColor: 'rgba(0,212,255,0.06)', min: v => v.min > 0 ? v.min - 8 : 50, max: v => v.max > 0 ? v.max + 8 : 160 }),
      series: [
        {
          name: '收缩压',
          type: 'line',
          data: sysVals,
          smooth: true,
          symbol: 'none',
          connectNulls: false,
          lineStyle: { color: '#a78bfa', width: 1.5 },
          areaStyle: { color: gradV('rgba(167,139,250,0.22)', 'rgba(167,139,250,0.02)') }
        },
        {
          name: '舒张压',
          type: 'line',
          data: diaVals,
          smooth: true,
          symbol: 'none',
          connectNulls: false,
          lineStyle: { color: '#38bdf8', width: 1.5 },
          areaStyle: { color: gradV('rgba(56,189,248,0.18)', 'rgba(56,189,248,0.02)') }
        }
      ]
    })
  }
}
