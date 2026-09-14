import * as echarts from '@/utils/echarts-setup-radar'
import {
  dashboardWarningLevelLabel,
  dashboardWarningLevelMarkerColor
} from './dashboard-warning-level'

function emptyChartOption(message = '暂无数据') {
  return {
    backgroundColor: 'transparent',
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: 'middle',
        style: { text: message, fill: '#4a6080', fontSize: 13 }
      }
    ]
  }
}

function buildDeptChartRows(deptStatsList, deptDataList, riskDeptList) {
  if ((deptStatsList || []).length) {
    return [...deptStatsList]
      .sort((a, b) => (b.personCount || 0) - (a.personCount || 0))
      .slice(0, 10)
      .map((d) => ({
        name: d.deptName || d.name,
        dataCount: d.personCount || 0,
        warningCount: d.abnormalPersonCount || 0
      }))
  }

  const deptMap = {}
  deptDataList.forEach((d) => {
    deptMap[d.name] = { name: d.name, dataCount: d.count, warningCount: 0 }
  })
  riskDeptList.forEach((d) => {
    if (deptMap[d.name]) deptMap[d.name].warningCount = d.count
    else deptMap[d.name] = { name: d.name, dataCount: 0, warningCount: d.count }
  })
  return Object.values(deptMap).sort((a, b) => b.dataCount - a.dataCount).slice(0, 10)
}

export function buildDeptChartOption({ deptStatsList, deptDataList, riskDeptList }) {
  const sorted = buildDeptChartRows(deptStatsList, deptDataList, riskDeptList)
  if (sorted.length === 0) return emptyChartOption()

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(5,9,22,0.96)',
      borderColor: '#00c8ff',
      textStyle: { color: '#fff', fontSize: 11 },
      formatter: (p) =>
        `${p[0].name}<br/>` +
        `<span style="color:#00c8ff">●</span> 检测人数: <b style="color:#00c8ff">${p[0].value.toLocaleString()}</b><br/>` +
        `<span style="color:#ff8c00">●</span> 异常人数: <b style="color:#ff8c00">${p[1].value.toLocaleString()}</b>`
    },
    legend: {
      data: ['检测人数', '异常人数'],
      top: 0,
      right: 10,
      textStyle: { color: '#8ba6c8', fontSize: 10 },
      itemWidth: 12,
      itemHeight: 8
    },
    grid: { left: 90, right: 20, top: 26, bottom: 4, containLabel: false },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: 'rgba(0,200,255,0.07)', type: 'dashed' } },
      axisLabel: {
        color: '#8ba6c8',
        fontSize: 9,
        formatter: (v) => (v >= 1000 ? `${(v / 1000).toFixed(1)}k` : v)
      }
    },
    yAxis: {
      type: 'category',
      data: sorted.map((d) => d.name),
      inverse: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#8ba6c8', fontSize: 9, width: 82, overflow: 'truncate', interval: 0 }
    },
    series: [
      {
        name: '检测人数',
        type: 'bar',
        barMaxWidth: 10,
        barGap: '20%',
        data: sorted.map((d) => d.dataCount),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
            { offset: 0, color: '#00c8ff' },
            { offset: 1, color: '#00c8ff33' }
          ]),
          borderRadius: [0, 3, 3, 0]
        },
        label: {
          show: true,
          position: 'right',
          color: '#00c8ff',
          fontSize: 8,
          formatter: (p) => (p.value >= 1000 ? `${(p.value / 1000).toFixed(1)}k` : p.value)
        }
      },
      {
        name: '异常人数',
        type: 'bar',
        barMaxWidth: 10,
        data: sorted.map((d) => d.warningCount),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
            { offset: 0, color: '#ff8c00' },
            { offset: 1, color: '#ffcc0244' }
          ]),
          borderRadius: [0, 3, 3, 0]
        },
        label: {
          show: true,
          position: 'right',
          color: '#ff8c00',
          fontSize: 8,
          formatter: (p) => p.value || ''
        }
      }
    ]
  }
}

export function buildDeptDetailChartOption({
  days,
  personCounts,
  abnormalCounts,
  personColor = '#00e5ff',
  abnormalColor = '#ff8c00'
}) {
  return {
    backgroundColor: '#0a1628',
    grid: { left: 60, right: 30, top: 50, bottom: 50, containLabel: false },
    legend: {
      top: 12,
      left: 'center',
      orient: 'horizontal',
      textStyle: { color: 'rgba(200,224,248,0.8)', fontSize: 13 },
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 30
    },
    xAxis: {
      type: 'category',
      data: days,
      boundaryGap: false,
      axisLabel: { color: 'rgba(180,210,240,0.55)', fontSize: 12, margin: 12 },
      axisLine: { lineStyle: { color: 'rgba(0,200,255,0.12)' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      name: '人次',
      minInterval: 1,
      nameTextStyle: { color: 'rgba(180,210,240,0.45)', fontSize: 12, padding: [0, 0, 0, -30] },
      axisLabel: { color: 'rgba(180,210,240,0.55)', fontSize: 12 },
      splitLine: { lineStyle: { color: 'rgba(0,180,255,0.08)' } },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(8,14,40,0.92)',
      borderColor: 'rgba(0,200,255,0.3)',
      textStyle: { color: '#e8f4ff', fontSize: 12 },
      formatter: (params) => {
        let s = `<div style="font-size:11px;color:#8ba6c8;margin-bottom:4px">${params[0].axisValue}</div>`
        params.forEach((p) => {
          s += `<div>${p.marker}${p.seriesName}: <b>${p.value}</b> 人</div>`
        })
        return s
      }
    },
    series: [
      {
        name: '检测人数',
        type: 'line',
        smooth: true,
        data: personCounts,
        lineStyle: { width: 2, color: personColor, shadowColor: personColor + '66', shadowBlur: 4 },
        itemStyle: { color: personColor },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: personColor + '33' },
              { offset: 1, color: personColor + '05' }
            ]
          }
        },
        symbol: 'circle',
        symbolSize: 5
      },
      {
        name: '异常人数',
        type: 'line',
        smooth: true,
        data: abnormalCounts,
        lineStyle: { width: 2, color: abnormalColor, shadowColor: abnormalColor + '66', shadowBlur: 4 },
        itemStyle: { color: abnormalColor },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: abnormalColor + '33' },
              { offset: 1, color: abnormalColor + '05' }
            ]
          }
        },
        symbol: 'circle',
        symbolSize: 5
      }
    ]
  }
}

export function buildDeptPersonChartOption({ days, series }) {
  return {
    backgroundColor: '#0a1628',
    grid: { left: 60, right: 150, top: 20, bottom: 50, containLabel: false },
    legend: {
      right: 10, top: 'middle', orient: 'vertical', type: 'scroll',
      textStyle: { color: 'rgba(200,224,248,0.8)', fontSize: 12 },
      pageIconColor: '#00c8ff', pageTextStyle: { color: '#8ba6c8' },
      icon: 'circle', itemWidth: 10, itemHeight: 10, itemGap: 12
    },
    xAxis: {
      type: 'category',
      data: days,
      boundaryGap: false,
      axisLabel: { color: 'rgba(180,210,240,0.55)', fontSize: 12, margin: 12 },
      axisLine: { lineStyle: { color: 'rgba(0,200,255,0.12)' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      name: '人次',
      minInterval: 1,
      nameTextStyle: { color: 'rgba(180,210,240,0.45)', fontSize: 12, padding: [0, 0, 0, -30] },
      axisLabel: { color: 'rgba(180,210,240,0.55)', fontSize: 12 },
      splitLine: { lineStyle: { color: 'rgba(0,180,255,0.08)' } },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: 'rgba(8,16,38,0.96)',
      borderColor: 'rgba(255,255,255,0.06)',
      borderWidth: 1,
      padding: [14, 18],
      extraCssText: 'box-shadow:0 8px 32px rgba(0,0,0,0.7);border-radius:8px;z-index:99999!important',
      textStyle: { color: '#fff', fontSize: 13 },
      formatter(params) {
        const date = params[0] ? params[0].axisValue : ''
        let s = `<div style="font-size:14px;font-weight:600;color:#fff;margin-bottom:12px">${date}</div>`
        params.forEach(p => {
          if ((p.value ?? 0) === 0) return
          s += `<div style="display:flex;align-items:center;gap:10px;margin:6px 0;min-width:160px">` +
            `<span style="width:12px;height:12px;border-radius:50%;background:${p.color};display:inline-block;flex-shrink:0"></span>` +
            `<span style="color:rgba(220,235,255,0.85);flex:1;font-size:14px">${p.seriesName}</span>` +
            `<span style="font-weight:700;font-size:18px;color:#fff;letter-spacing:0.5px">${p.value}</span>` +
            `</div>`
        })
        return s
      },
      axisPointer: { type: 'line', lineStyle: { color: 'rgba(255,255,255,0.15)', type: 'dashed', width: 1 } }
    },
    series
  }
}

export function buildUnifiedTrendChartOption({ rawData, vitalRanges }) {
  if (!rawData.length) return emptyChartOption()

  const dates = rawData.map((d) => d.date)
  const sampleRecord = rawData[0] || {}
  const getFieldKey = (possibleKeys) => possibleKeys.find((key) => Object.prototype.hasOwnProperty.call(sampleRecord, key)) || possibleKeys[0]
  const seriesLeft = [
    { name: '心率', key: getFieldKey(['heartRateRate', 'hrRate', 'heartRateAbnormalRate']), color: '#00c8ff', threshold: vitalRanges.heartRate.max },
    { name: '血氧', key: getFieldKey(['bloodOxygenRate', 'boRate', 'bloodOxygenAbnormalRate']), color: '#67C23A', threshold: vitalRanges.bloodOxygen.max },
    { name: '体温', key: getFieldKey(['temperatureRate', 'tempRate', 'temperatureAbnormalRate']), color: '#ffd200', threshold: vitalRanges.temperature.max }
  ]
  const seriesRight = [
    { name: '压力', key: getFieldKey(['pressureRate', 'stressRate', 'pressureAbnormalRate']), color: '#00c8ff', threshold: vitalRanges.pressure.max, yAxisIndex: 1 }
  ]
  const allSeries = [...seriesLeft, ...seriesRight]

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(10,20,50,0.92)',
      borderColor: '#00c8ff33',
      borderWidth: 1,
      textStyle: { color: '#fff', fontSize: 11 },
      formatter(params) {
        const header = `<div style="color:#8ba0bb;margin-bottom:3px">${params[0].axisValue}</div>`
        const rows = params
          .map((p) => `<div><span style="color:${p.color}">● </span>${p.seriesName}异常率：<b style="color:${p.color}">${p.value ?? '--'}%</b></div>`)
          .join('')
        return header + rows
      }
    },
    legend: {
      top: 2,
      right: 4,
      textStyle: { color: '#8ba0bb', fontSize: 10 },
      itemWidth: 14,
      itemHeight: 3,
      data: allSeries.map((s) => ({ name: s.name, itemStyle: { color: s.color } }))
    },
    grid: { left: 32, right: 36, top: 22, bottom: 20 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { color: '#6a7a9a', fontSize: 10, formatter: (v) => v.slice(5) },
      axisLine: { lineStyle: { color: '#1e3a5f' } },
      splitLine: { show: false }
    },
    yAxis: [
      {
        type: 'value',
        min: 0,
        axisLabel: { color: '#6a7a9a', fontSize: 10, formatter: (v) => `${v}%` },
        splitLine: { lineStyle: { color: 'rgba(100,160,255,0.08)' } }
      },
      {
        type: 'value',
        min: 0,
        position: 'right',
        axisLabel: { color: '#00c8ff', fontSize: 10, formatter: (v) => `${v}%` },
        splitLine: { show: false }
      }
    ],
    series: allSeries.map((s) => ({
      name: s.name,
      type: 'line',
      smooth: true,
      cursor: 'pointer',
      yAxisIndex: s.yAxisIndex || 0,
      symbol: 'circle',
      symbolSize: 4,
      showSymbol: false,
      data: rawData.map((d) => d[s.key] ?? null),
      lineStyle: { color: s.color, width: 2 },
      itemStyle: { color: s.color },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: s.color + '33' },
            { offset: 1, color: s.color + '05' }
          ]
        }
      },
      markLine: {
        silent: true,
        symbol: 'none',
        lineStyle: { color: 'rgba(74,222,128,0.45)', type: 'dashed', width: 1 },
        data: [{ yAxis: s.threshold, name: '安全阈值' }]
      },
      markArea: {
        silent: true,
        itemStyle: { color: 'rgba(74,222,128,0.04)' },
        data: [[{ yAxis: 0 }, { yAxis: s.threshold }]]
      }
    }))
  }
}

export function buildEmpTrendChartOption(trend) {
  if (!trend || !trend.dates || trend.dates.length === 0) {
    return emptyChartOption('暂无趋势数据')
  }

  const dates = trend.dates
  const hrs = trend.heartRates || []
  const bos = trend.bloodOxygens || []

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(10,20,50,0.92)',
      borderColor: '#00c8ff',
      textStyle: { color: '#fff', fontSize: 12 }
    },
    legend: {
      data: ['心率', '血氧'],
      bottom: 0,
      textStyle: { color: '#8ba6c8', fontSize: 11 }
    },
    grid: { top: 20, right: 50, bottom: 36, left: 46 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { color: '#8ba6c8', fontSize: 10 },
      axisLine: { lineStyle: { color: '#1a4d8f44' } },
      axisTick: { show: false }
    },
    yAxis: [
      {
        type: 'value',
        name: 'bpm',
        min: 40,
        max: 140,
        nameTextStyle: { color: '#8ba6c8', fontSize: 10 },
        axisLabel: { color: '#8ba6c8', fontSize: 10 },
        axisLine: { show: false },
        splitLine: { lineStyle: { color: '#1a4d8f33' } }
      },
      {
        type: 'value',
        name: '%',
        min: 85,
        max: 100,
        nameTextStyle: { color: '#8ba6c8', fontSize: 10 },
        axisLabel: { color: '#8ba6c8', fontSize: 10 },
        axisLine: { show: false },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '心率',
        type: 'line',
        data: hrs,
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { color: '#ff3b3b', width: 2 },
        itemStyle: { color: '#ff3b3b' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(255,82,82,0.25)' },
              { offset: 1, color: 'rgba(255,82,82,0)' }
            ]
          }
        }
      },
      {
        name: '血氧',
        type: 'line',
        yAxisIndex: 1,
        data: bos,
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { color: '#00c8ff', width: 2 },
        itemStyle: { color: '#00c8ff' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(0,200,255,0.2)' },
              { offset: 1, color: 'rgba(0,200,255,0)' }
            ]
          }
        }
      }
    ]
  }
}

export function buildEmpRadarChartOption(scores) {
  if (!scores || (scores.heartRate === 0 && scores.bloodOxygen === 0 && scores.temperature === 0 && scores.bloodPressure === 0 && scores.activity === 0)) {
    return emptyChartOption('暂无评分数据')
  }

  const s = scores
  return {
    backgroundColor: 'transparent',
    radar: {
      center: ['50%', '50%'],
      radius: '60%',
      indicator: [
        { name: '心率', max: 100 },
        { name: '血氧', max: 100 },
        { name: '体温', max: 100 },
        { name: '血压', max: 100 },
        { name: '活动量', max: 100 }
      ],
      axisName: { color: '#8ba6c8', fontSize: 11 },
      axisLine: { lineStyle: { color: 'rgba(0,200,255,0.15)' } },
      splitLine: { lineStyle: { color: 'rgba(0,200,255,0.12)' } },
      splitArea: { areaStyle: { color: ['rgba(0,200,255,0.02)', 'rgba(0,200,255,0.05)'] } }
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: [s.heartRate || 0, s.bloodOxygen || 0, s.temperature || 0, s.bloodPressure || 0, s.activity || 0],
            areaStyle: { color: 'rgba(0,200,255,0.15)' },
            lineStyle: { color: '#00c8ff', width: 2 },
            itemStyle: { color: '#00c8ff' }
          }
        ]
      }
    ]
  }
}

export function buildWarnCurveChartOption({ times, series, warnTime, warnLevel }) {
  const markerColor = dashboardWarningLevelMarkerColor(warnLevel)
  const markerLabel = `${dashboardWarningLevelLabel(warnLevel)}时刻`
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(10,20,50,0.92)',
      borderColor: '#00c8ff44',
      textStyle: { color: '#fff', fontSize: 11 },
      formatter: params => {
        const header = `<div style="color:#8ba0bb;margin-bottom:3px">${params[0]?.axisValue}</div>`
        return header + params.map(p => `<div><span style="color:${p.color}">● </span>${p.seriesName}：<b style="color:${p.color}">${p.value ?? '--'}</b></div>`).join('')
      }
    },
    legend: { top: 4, right: 4, textStyle: { color: '#8ba0bb', fontSize: 10 }, itemWidth: 14, itemHeight: 3 },
    grid: { left: 36, right: 16, top: 28, bottom: 24 },
    xAxis: {
      type: 'category',
      data: times,
      axisLabel: { color: '#6a7a9a', fontSize: 9 },
      axisLine: { lineStyle: { color: '#1e3a5f' } }
    },
    yAxis: { type: 'value', axisLabel: { color: '#6a7a9a', fontSize: 10 }, splitLine: { lineStyle: { color: 'rgba(100,160,255,0.08)' } } },
    series: [
      ...series.map((s) => ({
        name: s.label,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 4,
        data: s.values,
        lineStyle: { color: s.color, width: 2 },
        itemStyle: { color: s.color },
        connectNulls: false
      })),
      ...(warnTime ? [{
        name: markerLabel,
        type: 'line',
        data: [],
        markLine: {
          symbol: 'none',
          lineStyle: { color: markerColor, type: 'solid', width: 1.5 },
          label: { formatter: markerLabel, color: markerColor, fontSize: 10 },
          data: [{ xAxis: warnTime }]
        }
      }] : [])
    ]
  }
}

export function buildWarnTypeChartOption(data) {
  if (!data.length) {
    return {
      backgroundColor: 'transparent',
      graphic: [{ type: 'text', left: 'center', top: 'middle',
        style: { text: '暂无数据', fill: '#4a6080', fontSize: 13 } }]
    }
  }

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(5,9,22,0.96)',
      borderColor: '#00c8ff',
      textStyle: { color: '#fff', fontSize: 12 },
      formatter: '{b}: {c}次 ({d}%)'
    },
    series: [{
      type: 'pie',
      radius: ['52%', '78%'],
      center: ['50%', '50%'],
      data: data.map(d => ({ name: d.name, value: d.value, itemStyle: { color: d.color } })),
      label: { show: false },
      emphasis: {
        itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,200,255,0.3)' }
      }
    }]
  }
}

export function buildHourDistChartOption({ labels, vals, activePeriod, maxVal, yMax }) {
  if (!labels.length || !vals.length || vals.every((v) => v === 0)) {
    return {
      backgroundColor: 'transparent',
      graphic: [{ type: 'text', left: 'center', top: 'middle',
        style: { text: '暂无数据', fill: '#4a6080', fontSize: 13 } }]
    }
  }

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(5,9,22,0.96)',
      borderColor: '#00c8ff',
      textStyle: { color: '#fff', fontSize: 10 },
      formatter: p => `${p[0].axisValue}：${p[0].value}次`
    },
    grid: { left: 4, right: 4, top: 4, bottom: 20 },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: { color: '#8ba6c8', fontSize: 9, margin: 4 },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    yAxis: { type: 'value', show: false, max: yMax },
    series: [{
      type: 'bar',
      data: vals,
      barMaxWidth: activePeriod === 'day' ? 10 : 8,
      cursor: 'pointer',
      itemStyle: {
        color: params => {
          const v = params.value
          if (v >= maxVal * 0.7) return '#ff3b3b'
          if (v >= maxVal * 0.4) return '#ffd200'
          return '#00c8ff'
        },
        borderRadius: [2, 2, 0, 0]
      },
      label: {
        show: activePeriod !== 'day',
        position: 'top',
        fontSize: 8,
        color: '#8ba6c8',
        formatter: p => yMax && p.value > yMax ? p.value + '↑' : ''
      }
    }]
  }
}

export function buildEnvHealthChartOption({ hours, coData, dustData, boData }) {
  return {
    backgroundColor: 'transparent',
    grid: { top: 28, right: 60, bottom: 26, left: 48, containLabel: false },
    legend: {
      data: ['CO浓度(ppm)','粉尘(mg/m³)','平均血氧(%)'],
      top: 4, right: 4, textStyle: { color: '#9ca3af', fontSize: 10 },
      itemWidth: 12, itemHeight: 6,
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#1a1f3a',
      borderColor: 'rgba(255,255,255,0.15)',
      textStyle: { color: '#e2e8f0', fontSize: 11 },
    },
    xAxis: {
      type: 'category', data: hours,
      axisLabel: { color: '#6b7280', fontSize: 9, interval: 5 },
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } },
      splitLine: { show: false },
    },
    yAxis: [
      {
        type: 'value', name: 'ppm/mg', nameTextStyle: { color: '#6b7280', fontSize: 9 },
        axisLabel: { color: '#6b7280', fontSize: 9 },
        splitLine: { lineStyle: { color: 'rgba(255,255,255,0.06)' } },
        min: 0, max: 20,
      },
      {
        type: 'value', name: '%', nameTextStyle: { color: '#6b7280', fontSize: 9 },
        axisLabel: { color: '#6b7280', fontSize: 9 },
        splitLine: { show: false },
        min: 92, max: 100,
      },
    ],
    series: [
      {
        name: 'CO浓度(ppm)', type: 'line', yAxisIndex: 0,
        data: coData, smooth: true, symbol: 'none',
        lineStyle: { color: '#fbbf24', width: 1.5 },
        areaStyle: { color: 'rgba(251,191,36,0.08)' },
      },
      {
        name: '粉尘(mg/m³)', type: 'line', yAxisIndex: 0,
        data: dustData, smooth: true, symbol: 'none',
        lineStyle: { color: '#f87171', width: 1.5 },
        areaStyle: { color: 'rgba(248,113,113,0.06)' },
      },
      {
        name: '平均血氧(%)', type: 'line', yAxisIndex: 1,
        data: boData, smooth: true, symbol: 'none',
        lineStyle: { color: '#34d399', width: 2 },
        areaStyle: { color: 'rgba(52,211,153,0.1)' },
      },
    ],
  }
}

export function buildGaugeChartOption({ value, highColor, lowColor }) {
  const color = value >= 50 ? highColor : value >= 20 ? '#ff8c00' : lowColor
  return {
    backgroundColor: 'transparent',
    series: [{
      type: 'gauge',
      startAngle: 200, endAngle: -20,
      radius: '90%', center: ['50%', '65%'],
      min: 0, max: 100,
      axisLine: {
        lineStyle: { width: 8, color: [[value / 100, color], [1, 'rgba(0, 200, 255, 0.1)']] }
      },
      pointer: { show: false },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false },
      detail: {
        valueAnimation: true,
        formatter: '{value}%',
        color, fontSize: 13, fontWeight: 'bold', fontFamily: 'Consolas',
        offsetCenter: [0, '15%']
      },
      data: [{ value }]
    }]
  }
}
