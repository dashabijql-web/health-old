import { emptyOption, chartTooltip, categoryAxis, valueAxis, deptGrid, trendGrid, barLabel } from '@/utils/echarts-config'
import { initChart, gradH, gradV } from '@/utils/chart-helpers'

export const heartRateChartMethods = {
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
      highCount: x.highCount || 0,
      abnormalCount: x.abnormalCount || (x.lowCount || 0) + (x.highCount || 0)
    }))
    c.setOption({
      backgroundColor: 'transparent',
      tooltip: chartTooltip(params => {
        const low = params.find(x => x.seriesName === '偏低')?.value || 0
        const high = params.find(x => x.seriesName === '偏高')?.value || 0
        return `${params[0].name}<br/>偏低记录：<b style="color:#4FC3F7">${low}</b> 条<br/>偏高记录：<b style="color:#FFB84D">${high}</b> 条<br/>异常合计：${low + high} 条`
      }),
      legend: {
        data: ['偏低', '偏高'],
        right: 8,
        top: 4,
        textStyle: { color: '#8ba6c8', fontSize: 10 },
        itemWidth: 10,
        itemHeight: 8,
        icon: 'rect'
      },
      grid: { ...deptGrid(), top: '12%' },
      xAxis: {
        ...valueAxis(),
        min: 0,
        minInterval: 1,
        axisLabel: { color: '#8ba6c8', fontSize: 10, formatter: value => `${value}条` }
      },
      yAxis: { ...categoryAxis(d.map(x => x.deptName)), inverse: true },
      series: [
        {
          name: '偏低',
          type: 'bar',
          stack: 'total',
          barWidth: '46%',
          data: d.map(x => x.lowCount),
          itemStyle: { color: gradH('#4FC3F7', '#0284c7') },
          label: barLabel()
        },
        {
          name: '偏高',
          type: 'bar',
          stack: 'total',
          barWidth: '46%',
          data: d.map(x => x.highCount),
          itemStyle: { color: gradH('#FFB84D', '#FF6B35'), borderRadius: [0, 4, 4, 0] },
          label: barLabel()
        }
      ]
    })
    c.off('click')
    c.on('click', params => {
      const row = d[params.dataIndex]
      if (!row) return
      c.dispatchAction({ type: 'downplay' })
      c.dispatchAction({ type: 'highlight', seriesIndex: 0, dataIndex: params.dataIndex })
      c.dispatchAction({ type: 'highlight', seriesIndex: 1, dataIndex: params.dataIndex })
      this.openDepartmentDrilldown(row)
    })
  },

  renderDailyRiskTrend(rows) {
    const c = initChart(this.charts, 'trend', this.$refs.trendRef)
    if (!c) return
    if (!rows.length) {
      c.setOption(emptyOption('暂无有效覆盖数据'))
      return
    }
    const data = rows.map(row => {
      const anomalyCount = Number(row.anomalyCount || 0)
      const coveredUsers = Number(row.coveredUsers || 0)
      const anomalyRate = row.anomalyRate == null
        ? (coveredUsers ? Number((anomalyCount * 100 / coveredUsers).toFixed(1)) : 0)
        : Number(row.anomalyRate)
      return {
        ...row,
        anomalyCount,
        coveredUsers,
        anomalyRate,
        lowCount: Number(row.lowCount || 0),
        highCount: Number(row.highCount || 0)
      }
    })
    const maxRate = Math.max(...data.map(row => row.anomalyRate), 0)
    const rateMax = Math.min(100, Math.max(10, Math.ceil(maxRate * 1.2)))
    c.setOption({
      backgroundColor: 'transparent',
      tooltip: chartTooltip(params => {
        const row = data[params[0].dataIndex]
        return `${row.date}<br/>异常人数：<b style="color:#FFB84D">${row.anomalyCount}</b> 人` +
          `<br/>异常率：<b style="color:#00d4ff">${row.anomalyRate}%</b>` +
          `<br/>有效覆盖：${row.coveredUsers} 人` +
          `<br/>偏低 / 偏高：${row.lowCount} / ${row.highCount} 人`
      }),
      grid: { ...trendGrid(), right: '6%' },
      xAxis: {
        ...categoryAxis(data.map(row => row.date?.slice(5) || row.date), {
          fontSize: 10,
          interval: Math.max(0, Math.floor(data.length / 7) - 1)
        }),
        boundaryGap: true
      },
      yAxis: [
        {
          ...valueAxis({ name: '异常人数' }),
          min: 0,
          minInterval: 1,
          axisLabel: { color: '#8ba6c8', fontSize: 10, formatter: value => `${value}人` }
        },
        {
          ...valueAxis({ name: '异常率', max: rateMax, splitColor: 'transparent' }),
          min: 0,
          axisLabel: { color: '#8ba6c8', fontSize: 10, formatter: value => `${value}%` }
        }
      ],
      series: [
        {
          name: '异常人数',
          type: 'bar',
          yAxisIndex: 0,
          data: data.map(row => row.anomalyCount),
          barMaxWidth: 18,
          itemStyle: {
            color: gradV('#FFB84D', 'rgba(255,184,77,0.2)'),
            borderRadius: [3, 3, 0, 0]
          }
        },
        {
          name: '异常率',
          type: 'line',
          yAxisIndex: 1,
          data: data.map(row => row.anomalyRate),
          smooth: true,
          symbol: 'circle',
          symbolSize: 5,
          lineStyle: { color: '#00d4ff', width: 2 },
          itemStyle: { color: '#00d4ff' },
          areaStyle: { color: gradV('rgba(0,212,255,0.16)', 'rgba(0,212,255,0.01)') }
        }
      ]
    })
  }
}
