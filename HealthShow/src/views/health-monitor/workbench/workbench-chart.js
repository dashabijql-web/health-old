import * as echarts from '@/utils/echarts-setup-radar'

export function renderDeptRadarChart({ chart, element, data, deptColors }) {
  if (!element) return chart

  const nextChart = chart || echarts.init(element, 'dark')
  if (!data.length) {
    nextChart.clear()
    return nextChart
  }

  nextChart.setOption(buildDeptRadarOption(data, deptColors), true)
  return nextChart
}

export function resizeDeptRadarChart(chart) {
  chart?.resize?.()
}

export function disposeDeptRadarChart(chart) {
  chart?.dispose?.()
}

export function buildDeptRadarOption(data, deptColors) {
  const indicators = [
    { name: '心率健康', max: 100 },
    { name: '血氧水平', max: 100 },
    { name: '血压安全', max: 100 },
    { name: '睡眠充足', max: 100 },
    { name: '活动量', max: 100 },
    { name: '压力控制', max: 100 }
  ]

  const seriesData = data.slice(0, 6).map((row, index) => {
    const color = deptColors[index % deptColors.length]
    return {
      name: row.deptName,
      value: normalizeDeptScore(row),
      lineStyle: { color, width: 2 },
      itemStyle: { color },
      areaStyle: { color, opacity: 0.08 }
    }
  })

  return {
    backgroundColor: 'transparent',
    legend: {
      data: seriesData.map(item => item.name),
      bottom: 0,
      textStyle: { color: '#9ca3af', fontSize: 11 },
      itemWidth: 12,
      itemHeight: 8
    },
    radar: {
      indicator: indicators,
      shape: 'circle',
      radius: '62%',
      center: ['50%', '46%'],
      splitNumber: 5,
      axisName: { color: '#9ca3af', fontSize: 11 },
      axisLabel: { show: false },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
      splitArea: { show: false },
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.12)' } }
    },
    series: [{ type: 'radar', data: seriesData, symbol: 'circle', symbolSize: 4 }],
    tooltip: {
      trigger: 'item',
      backgroundColor: '#1a1f3a',
      borderColor: 'rgba(255,255,255,0.15)',
      textStyle: { color: '#e2e8f0', fontSize: 12 }
    }
  }
}

function normalizeDeptScore(row) {
  const heartRate = row.avgHeartRate || 75
  const bloodOxygen = row.avgBloodOxygen || 97
  const systolic = row.avgSystolic || 120
  const sleepMinutes = row.avgSleepMinutes || 360
  const steps = row.avgSteps || 5000
  const pressure = row.avgPressure || 50

  const heartRateScore = heartRate >= 60 && heartRate <= 100
    ? 90 - Math.abs(heartRate - 75) * 0.6
    : Math.max(0, 60 - Math.abs(heartRate - 80))
  const bloodOxygenScore = Math.min(100, Math.max(0, (bloodOxygen - 85) / 15 * 100))
  const systolicScore = systolic < 120
    ? 95
    : systolic < 130
      ? 80
      : systolic < 140
        ? 60
        : Math.max(0, 40 - (systolic - 140) * 2)
  const sleepScore = Math.min(100, sleepMinutes / 480 * 100)
  const stepsScore = Math.min(100, steps / 10000 * 100)
  const pressureScore = Math.max(0, 100 - pressure)

  return [
    Math.round(heartRateScore),
    Math.round(bloodOxygenScore),
    Math.round(systolicScore),
    Math.round(sleepScore),
    Math.round(stepsScore),
    Math.round(pressureScore)
  ]
}
