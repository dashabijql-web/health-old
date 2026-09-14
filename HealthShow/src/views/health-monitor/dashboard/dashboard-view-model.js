export function buildDashboardHeaderKpis({
  kpiRealtimeOnline,
  kpiRealtimeTotal,
  kpiTodayWarnings,
  kpiYesterdayWarnings,
  kpiUnhandledHigh,
  kpiUnhandledMid,
  warningEvents,
  personCounts,
  deviceActivationRate,
  preShiftData,
  periodLabel
}) {
  const delta = kpiYesterdayWarnings
    ? Math.round((kpiTodayWarnings - kpiYesterdayWarnings) / kpiYesterdayWarnings * 100)
    : null

  const deltaText = periodLabel === '当日'
    ? delta !== null
      ? `${delta > 0 ? '↑' : '↓'}${Math.abs(delta)}% 较昨${kpiYesterdayWarnings}件`
      : `昨日 ${kpiYesterdayWarnings}件`
    : `${periodLabel}累计`

  const abnormalUsers = new Set(
    (warningEvents || [])
      .map((item) => item.userCode || item.empCode || item.id)
      .filter(Boolean)
  ).size

  return [
    {
      label: '当前在线',
      val: kpiRealtimeTotal > 0 ? kpiRealtimeOnline : '--',
      cls: kpiRealtimeTotal > 0 && kpiRealtimeOnline < kpiRealtimeTotal ? 'kpi-orange' : 'kpi-teal',
      clickable: true,
      route: '/health-monitor/real-time',
      sub: kpiRealtimeTotal > 0 ? `短时有上报 · 监测人员 ${kpiRealtimeTotal}` : '数据加载中...'
    },
    {
      label: `${periodLabel}预警`,
      val: kpiTodayWarnings,
      cls: 'kpi-red',
      clickable: true,
      route: '/health-monitor/risk-warning',
      sub: deltaText,
      subCls: delta !== null && delta > 0 ? 'sub-up' : 'sub-down'
    },
    {
      label: '高危待处理',
      val: kpiUnhandledHigh,
      cls: kpiUnhandledHigh > 0 ? 'kpi-red' : 'kpi-teal',
      clickable: true,
      route: '/health-monitor/risk-warning',
      sub: `中危 ${kpiUnhandledMid}`
    },
    {
      label: '异常人员',
      val: abnormalUsers,
      cls: 'kpi-orange',
      clickable: true,
      route: '/health-monitor/risk-warning',
      sub: `${periodLabel}累计`
    },
    {
      label: '设备激活',
      val: deviceActivationRate !== null ? `${deviceActivationRate}%` : '--',
      cls: deviceActivationRate !== null && deviceActivationRate < 80 ? 'kpi-orange' : 'kpi-teal',
      clickable: true,
      sub: '当前绑定设备激活率',
      route: '/admin/device-list'
    },
    {
      label: '班前达标',
      val: preShiftData.preShiftRate !== null ? `${preShiftData.preShiftRate}%` : '--',
      cls: preShiftData.preShiftRate !== null && preShiftData.preShiftRate < 80 ? 'kpi-orange' : 'kpi-teal',
      clickable: true,
      sub: preShiftData.totalToday > 0
        ? `达标 ${preShiftData.qualifiedCount} / ${preShiftData.totalToday} 人`
        : '今日暂无数据',
      route: '/health-monitor/mine-entry'
    }
  ]
}

export function buildDashboardMetricCards({ metricList, personCounts, checkData, kpiRealtimeTotal }) {
  const metrics = metricList || []
  const personValues = metrics.map((item) => Number(personCounts[item.key] || 0))
  const maxPersonValue = Math.max(...personValues, 0)
  const total = Number(personCounts.totalPersons || kpiRealtimeTotal || maxPersonValue || 1)
  const maxRec = Math.max(...metrics.map((item) => Number(checkData[item.key] || 0)), 1)

  const cards = metrics.map((item) => {
    const personValue = Number(personCounts[item.key] || 0)
    const recordValue = Number(checkData[item.key] || 0)
    return {
      key: item.key,
      label: item.label,
      color: item.color,
      val: personValue,
      rate: Math.round(personValue / total * 100),
      records: recordValue,
      recPct: Math.round(recordValue / maxRec * 100),
      pct: Math.round(personValue / total * 100),
      metricDetail: true
    }
  })

  const coveredPersons = personValues.length ? Math.min(...personValues) : 0
  const coverageRate = Math.max(0, Math.min(100, Math.round(coveredPersons / total * 100)))
  const coverageColor = coverageRate >= 80 ? '#38ef7d' : coverageRate >= 60 ? '#ff8c00' : '#ff5252'

  cards.push({
    key: 'allCoverage',
    label: '全项覆盖',
    color: coverageColor,
    val: coverageRate,
    unit: '%',
    rate: coverageRate,
    records: coveredPersons,
    recPct: coverageRate,
    pct: coverageRate,
    metricDetail: false,
    summary: true
  })

  return cards
}

function warningEventMatchesMetric(event, metricKey) {
  const text = `${event?.type || ''} ${event?.indicator || ''}`.toLowerCase()
  const aliases = {
    heartRate: ['心率', 'heartrate', 'heart rate'],
    bloodOxygen: ['血氧', 'bloodoxygen', 'spo2'],
    pressure: ['压力', 'pressure'],
    temperature: ['体温', 'temperature'],
    bloodPressureHigh: ['血压', '收缩压', '高压', 'systolic', 'bloodpressurehigh'],
    bloodPressureLow: ['舒张压', '低压', 'diastolic', 'bloodpressurelow']
  }
  return (aliases[metricKey] || []).some(alias => text.includes(alias.toLowerCase()))
}

export function buildDashboardHealthExceptionCards({ vitalCards, warningEvents, healthSnapshot }) {
  const metricKeys = ['heartRate', 'bloodOxygen', 'pressure', 'temperature', 'bloodPressureHigh', 'bloodPressureLow']
  const snapshotMetrics = new Map((healthSnapshot?.metrics || []).map(metric => [metric.key, metric]))
  return metricKeys.map((metricKey) => {
    const card = (vitalCards || []).find(item => item.metricKey === metricKey)
    const metric = snapshotMetrics.get(metricKey)
    const users = new Set(
      (warningEvents || [])
        .filter(event => !event.handled && warningEventMatchesMetric(event, metricKey))
        .map(event => event.userCode || event.userName || event.id)
        .filter(Boolean)
    )
    const exceptionCount = users.size
    if (metric) {
      const hasData = metric.coveredUsers > 0
      return {
        ...card,
        metricKey,
        label: `${metric.label}异常`,
        val: `${metric.abnormalUsers}/${metric.coveredUsers}`,
        unit: '异常/覆盖',
        exceptionCount: metric.abnormalUsers,
        tag: hasData ? `群体均值 ${metric.average}${metric.unit}` : '暂无有效数据',
        tagCls: metric.abnormalUsers > 0 ? 'vtag-warn' : hasData ? 'vtag-ok' : '',
        exceptionText: hasData
          ? `范围 ${metric.minimum}-${metric.maximum}${metric.unit} · 覆盖 ${metric.coveredUsers} 人`
          : '当前快照没有新鲜体征',
        tone: metric.abnormalUsers > 0 ? 'danger' : hasData ? 'success' : 'warning'
      }
    }
    return {
      ...card,
      metricKey,
      exceptionCount,
      warningType: card?.label || metricKey,
      exceptionText: exceptionCount > 0 ? `异常 ${exceptionCount} 人` : '暂无待处置异常',
      tone: exceptionCount > 0 ? 'danger' : card?.tagCls === 'vtag-warn' ? 'warning' : 'success'
    }
  }).filter(card => card.label)
}

export function buildDashboardVitalCards({ bodyIndicators, warningRates = [] }) {
  const b = bodyIndicators || {}
  return [
    {
      metricKey: 'heartRate', label: '心率均值', val: b.avgHeartRate || '--', unit: 'bpm', color: '#00d4ff', icon: 'Monitor',
      route: {
        path: '/health-monitor/heart-rate',
        query: { period: 'day', focus: 'current-anomaly' }
      },
      tag: !b.avgHeartRate ? '-' : b.avgHeartRate > 100 ? '偏快' : b.avgHeartRate < 55 ? '偏慢' : '正常',
      tagCls: !b.avgHeartRate ? '' : (b.avgHeartRate > 100 || b.avgHeartRate < 55) ? 'vtag-warn' : 'vtag-ok'
    },
    {
      metricKey: 'bloodOxygen', label: '血氧均值', val: b.avgBloodOxygen || '--', unit: '%', color: '#67C23A', icon: 'FirstAidKit',
      route: '/health-monitor/blood-oxygen',
      tag: !b.avgBloodOxygen ? '-' : b.avgBloodOxygen < 90 ? '过低' : b.avgBloodOxygen < 95 ? '偏低' : '良好',
      tagCls: !b.avgBloodOxygen ? '' : b.avgBloodOxygen < 90 ? 'vtag-danger' : b.avgBloodOxygen < 95 ? 'vtag-warn' : 'vtag-ok'
    },
    {
      metricKey: 'pressure', label: '压力均值', val: b.avgPressure || '--', unit: '', color: '#3eb7ff', icon: 'MagicStick',
      route: '/health-monitor/pressure',
      tag: !b.avgPressure ? '-' : b.avgPressure > 80 ? '过高' : b.avgPressure > 60 ? '偏高' : '适中',
      tagCls: !b.avgPressure ? '' : b.avgPressure > 80 ? 'vtag-danger' : b.avgPressure > 60 ? 'vtag-warn' : 'vtag-ok'
    },
    {
      metricKey: 'temperature', label: '体温均值', val: b.avgTemperature || '--', unit: '°C', color: '#00c8c8', icon: 'Sunny',
      tag: !b.avgTemperature ? '-' : b.avgTemperature > 37.5 ? '偏高' : b.avgTemperature < 36 ? '偏低' : '正常',
      tagCls: !b.avgTemperature ? '' : (b.avgTemperature > 37.5 || b.avgTemperature < 36) ? 'vtag-warn' : 'vtag-ok'
    },
    {
      label: '日均步数', val: b.avgSteps ? Math.round(b.avgSteps / 1000 * 10) / 10 + 'k' : '--', unit: '', color: '#F56C6C', icon: 'Promotion',
      tag: !b.avgSteps ? '-' : b.avgSteps < 5000 ? '偏少' : b.avgSteps > 12000 ? '充足' : '达标',
      tagCls: !b.avgSteps ? '' : b.avgSteps < 5000 ? 'vtag-warn' : 'vtag-ok'
    },
    {
      metricKey: 'bloodPressureHigh',
      label: '高压均值',
      val: b.avgBloodPressureHigh ? Math.round(b.avgBloodPressureHigh) : '--',
      unit: 'mmHg',
      color: '#f87171',
      icon: 'Top',
      route: '/health-monitor/blood-pressure',
      tag: !b.avgBloodPressureHigh ? '-'
        : b.avgBloodPressureHigh <= 120 ? '正常'
        : b.avgBloodPressureHigh <= 140 ? '偏高'
        : '高血压',
      tagCls: !b.avgBloodPressureHigh ? ''
        : b.avgBloodPressureHigh <= 120 ? 'vtag-ok'
        : 'vtag-warn'
    },
    {
      metricKey: 'bloodPressureLow',
      label: '低压均值',
      val: b.avgBloodPressureLow ? Math.round(b.avgBloodPressureLow) : '--',
      unit: 'mmHg',
      color: '#36d399',
      icon: 'Bottom',
      route: '/health-monitor/blood-pressure',
      tag: !b.avgBloodPressureLow ? '-'
        : b.avgBloodPressureLow <= 80 ? '正常'
        : b.avgBloodPressureLow <= 90 ? '偏高'
        : '高血压',
      tagCls: !b.avgBloodPressureLow ? ''
        : b.avgBloodPressureLow <= 80 ? 'vtag-ok'
        : 'vtag-warn'
    },
    {
      label: '热量均值',
      val: b.avgCalories ? Math.round(b.avgCalories) : '--',
      unit: 'kcal',
      color: '#FFB84D',
      icon: 'Odometer',
      tag: !b.avgCalories ? '-'
        : b.avgCalories < 300 ? '偏低'
        : b.avgCalories > 800 ? '充足'
        : '适中',
      tagCls: !b.avgCalories ? ''
        : b.avgCalories < 300 ? 'vtag-warn'
        : 'vtag-ok'
    },
    (() => {
      const pressure = b.avgPressure || 0
      const hr = b.avgHeartRate || 0
      const hrDev = hr > 0 ? Math.min(100, Math.abs(hr - 75) / 25 * 100) : 0
      const warnRate = warningRates.length
        ? warningRates.reduce((sum, item) => sum + (item.rate || 0), 0) / warningRates.length
        : 0
      const fatigue = Math.min(100, Math.round(pressure * 0.4 + hrDev * 0.3 + warnRate * 0.3))
      return {
        label: '疲劳指数', val: pressure > 0 ? fatigue : '--', unit: '',
        color: fatigue >= 70 ? '#ff5252' : fatigue >= 45 ? '#ffd200' : '#38ef7d',
        icon: 'Cpu',
        tag: fatigue >= 70 ? '高疲劳' : fatigue >= 45 ? '中疲劳' : pressure > 0 ? '良好' : '-',
        tagCls: fatigue >= 70 ? 'vtag-danger' : fatigue >= 45 ? 'vtag-warn' : pressure > 0 ? 'vtag-ok' : ''
      }
    })()
  ]
}

export function buildDashboardDeviceCards({ deviceStats, deviceOnline, deviceOffline, lowBatteryCount, dataInterrupted, faulted }) {
  const hasDeviceData = (deviceStats.total > 0) || (deviceStats.boundDevices > 0)
  const showVal = (val) => hasDeviceData ? val : '--'

  const total = deviceStats.boundDevices ?? deviceStats.total
  const capabilityValue = (capability) => capability?.status === 'AVAILABLE' ? capability.value : '--'

  return [
    { label: '设备总数', val: showVal(total), cls: 'dc-blue', route: { path: '/admin/device-list' } },
    { label: '在线设备', val: showVal(deviceOnline), cls: 'dc-green', route: { path: '/admin/device-list', query: { online: 1 } } },
    { label: '离线设备', val: showVal(deviceOffline), cls: 'dc-gray', route: { path: '/admin/device-list', query: { online: 0 } } },
    { label: '低电设备', val: lowBatteryCount === null ? '--' : showVal(lowBatteryCount), cls: 'dc-orange', route: lowBatteryCount === null ? null : { path: '/admin/device-list', query: { filter: 'lowBattery' } } },
    {
      label: '数据中断',
      val: capabilityValue(dataInterrupted),
      cls: 'dc-orange',
      route: dataInterrupted?.status === 'AVAILABLE'
        ? { path: '/admin/device-list', query: { filter: 'dataInterrupted' } }
        : null
    },
    {
      label: '故障设备',
      val: capabilityValue(faulted),
      cls: 'dc-red',
      route: faulted?.status === 'AVAILABLE'
        ? { path: '/admin/device-list', query: { filter: 'faulted' } }
        : null
    }
  ]
}

export function buildDashboardTop5DisplayData(top5Data) {
  return (top5Data || [])
    .filter((d) => d.userName && d.userName !== '--')
    .slice(0, 15)
}
