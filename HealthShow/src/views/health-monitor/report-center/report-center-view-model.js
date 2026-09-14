function firstValue(row, keys, fallback = null) {
  for (const key of keys) {
    const value = row?.[key]
    if (value !== undefined && value !== null && value !== '') {
      return value
    }
  }
  return fallback
}

function numberValue(row, keys, fallback = 0) {
  const raw = firstValue(row, keys, null)
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : fallback
}

function stringValue(row, keys, fallback = '--') {
  const value = firstValue(row, keys, null)
  return value === null ? fallback : String(value)
}

function sortedByWarningCount(deptSummary) {
  return [...deptSummary].sort((a, b) => {
    return numberValue(b, ['warningCount', 'warning_count']) - numberValue(a, ['warningCount', 'warning_count'])
  })
}

function formatDelta(trendData, key) {
  if (!trendData.length) {
    return '暂无数据'
  }

  const first = trendData[0]
  const last = trendData[trendData.length - 1]
  const start = numberValue(first, [key, key.replace('Rate', 'AbnormalRate')])
  const end = numberValue(last, [key, key.replace('Rate', 'AbnormalRate')])
  const diff = +(end - start).toFixed(1)
  return `${diff > 0 ? '上升' : diff < 0 ? '下降' : '持平'} ${Math.abs(diff)}%`
}

export function fmtReportMetric1(value) {
  if (value === null || value === undefined || value === '') {
    return '--'
  }
  const parsed = Number.parseFloat(value)
  return Number.isFinite(parsed) ? parsed.toFixed(1) : '--'
}

function hasRows(value) {
  return Array.isArray(value) && value.length > 0
}

export function hasReportExportData({ monthlySummary = [], deptSummary = [], trendData = [] } = {}) {
  return hasRows(monthlySummary) || hasRows(deptSummary) || hasRows(trendData)
}

export function buildReportExportState({
  loading = false,
  exporting = false,
  exportingPdf = false,
  monthlySummary = [],
  deptSummary = [],
  trendData = []
} = {}) {
  const hasExportData = hasReportExportData({ monthlySummary, deptSummary, trendData })
  const exportDisabledReason = loading
    ? '报表数据加载中，请稍后导出'
    : hasExportData
      ? ''
      : '当前筛选范围暂无可导出的报表数据'
  const excelDisabledReason = exporting ? 'Excel 正在导出，请稍后' : exportDisabledReason
  const pdfDisabledReason = exportingPdf ? 'PDF 正在导出，请稍后' : exportDisabledReason

  return {
    canExportExcel: !loading && !exporting && hasExportData,
    canExportPdf: !loading && !exportingPdf && hasExportData,
    exportDisabledReason,
    excelDisabledReason,
    pdfDisabledReason,
    hasExportData
  }
}

export function buildMonthlyKpis(monthlySummary, deptSummary) {
  if (!monthlySummary.length) {
    return []
  }

  const persons = monthlySummary.length
  const records = monthlySummary.reduce((sum, row) => {
    return sum + numberValue(row, ['recordCount', 'record_count'])
  }, 0)
  const totalScore = monthlySummary.reduce((sum, row) => {
    return sum + numberValue(row, ['healthScore', 'health_score'])
  }, 0)
  const avgScore = persons > 0 ? (totalScore / persons).toFixed(1) : '--'
  const warnCount = deptSummary.reduce((sum, row) => {
    return sum + numberValue(row, ['warningCount', 'warning_count'])
  }, 0)

  return [
    { label: '检测人员数', val: persons.toLocaleString(), color: '#00d4ff' },
    { label: '检测记录条数', val: records.toLocaleString(), color: '#67C23A' },
    { label: '部门预警合计', val: warnCount.toLocaleString(), color: '#ffd200' },
    { label: '人均健康评分', val: avgScore, color: '#a78bfa' }
  ]
}

export function buildReportOverviewCards({
  activeTab,
  selectedMonth,
  monthlySummary,
  deptSummary,
  trendData,
  trendDays
}) {
  if (activeTab === 'monthly') {
    const persons = monthlySummary.length
    const records = monthlySummary.reduce((sum, row) => {
      return sum + numberValue(row, ['recordCount', 'record_count'])
    }, 0)
    const topDept = sortedByWarningCount(deptSummary)[0]
    return [
      {
        label: '当前月份',
        value: selectedMonth,
        sub: '月度统计和导出都基于当前月份',
        tone: 'accent'
      },
      {
        label: '覆盖对象',
        value: `${persons} 人 / ${records} 条`,
        sub: persons ? '已形成月度健康汇总' : '当前月份暂无有效记录',
        tone: persons ? 'info' : 'muted'
      },
      {
        label: '重点部门',
        value: topDept ? stringValue(topDept, ['deptName', 'dept_name']) : '暂无',
        sub: topDept
          ? `近30天预警 ${numberValue(topDept, ['warningCount', 'warning_count'])} 次`
          : '建议先检查部门统计来源',
        tone: topDept ? 'warn' : 'muted'
      }
    ]
  }

  if (activeTab === 'dept') {
    const sorted = sortedByWarningCount(deptSummary)
    const topDept = sorted[0]
    const totalEmployees = deptSummary.reduce((sum, row) => {
      return sum + numberValue(row, ['employeeCount', 'employee_count'])
    }, 0)

    return [
      {
        label: '部门数量',
        value: `${deptSummary.length} 个`,
        sub: totalEmployees ? `覆盖在册人数 ${totalEmployees}` : '暂无有效部门样本',
        tone: deptSummary.length ? 'info' : 'muted'
      },
      {
        label: '最高风险部门',
        value: topDept ? stringValue(topDept, ['deptName', 'dept_name']) : '暂无',
        sub: topDept
          ? `预警 ${numberValue(topDept, ['warningCount', 'warning_count'])} 次`
          : '当前没有明显高风险部门',
        tone: topDept ? 'danger' : 'muted'
      },
      {
        label: '使用建议',
        value: '先看排行',
        sub: '先锁定高风险部门，再回人员画像与预警中心复核',
        tone: 'accent'
      }
    ]
  }

  const latest = trendData[trendData.length - 1]
  const dateLabel = latest?.date || '--'
  const activeSignals = latest
    ? [
        ['心率', numberValue(latest, ['heartRateRate', 'heartRateAbnormalRate'])],
        ['血氧', numberValue(latest, ['bloodOxygenRate', 'bloodOxygenAbnormalRate'])],
        ['体温', numberValue(latest, ['temperatureRate', 'temperatureAbnormalRate'])],
        ['压力', numberValue(latest, ['pressureRate', 'pressureAbnormalRate'])]
      ].sort((a, b) => b[1] - a[1])
    : []

  const primarySignal = activeSignals[0]
  return [
    {
      label: '时间范围',
      value: `近 ${trendDays} 天`,
      sub: '趋势页适合发现连续性偏移',
      tone: 'accent'
    },
    {
      label: '最新采样日',
      value: dateLabel,
      sub: latest ? `当日检测人次 ${numberValue(latest, ['personCount', 'checkCount'])}` : '暂无趋势样本',
      tone: latest ? 'info' : 'muted'
    },
    {
      label: '当前主信号',
      value: primarySignal ? primarySignal[0] : '暂无',
      sub: primarySignal ? `异常率 ${primarySignal[1]}%` : '建议先补齐趋势数据',
      tone: primarySignal && primarySignal[1] >= 10 ? 'danger' : 'warn'
    }
  ]
}

export function buildReportInsightLines({
  activeTab,
  monthlySummary,
  deptSummary,
  trendData,
  trendDays
}) {
  if (activeTab === 'monthly') {
    const persons = monthlySummary.length
    const records = monthlySummary.reduce((sum, row) => {
      return sum + numberValue(row, ['recordCount', 'record_count'])
    }, 0)
    const totalScore = monthlySummary.reduce((sum, row) => {
      return sum + numberValue(row, ['healthScore', 'health_score'])
    }, 0)
    const avgScore = persons > 0 ? +(totalScore / persons).toFixed(1) : null
    const topDept = sortedByWarningCount(deptSummary)[0]

    return [
      persons > 0
        ? `本月共覆盖 ${persons} 名员工，累计形成 ${records} 条健康记录，人均健康评分 ${avgScore ?? '--'}。`
        : '本月尚未形成有效健康记录，建议先核查统计数据来源和月份选择。',
      topDept
        ? `${stringValue(topDept, ['deptName', 'dept_name'])} 当前为重点关注部门，近30天预警次数最多。`
        : '当前没有明显集中的高风险部门，建议继续关注跨部门波动。',
      avgScore !== null && avgScore < 80
        ? '整体评分偏低，建议优先复盘高频预警类型和班前准入失败原因。'
        : '整体健康水平相对稳定，建议重点关注异常率突增和连续预警人群。'
    ]
  }

  if (activeTab === 'dept') {
    const sorted = sortedByWarningCount(deptSummary)
    const topDept = sorted[0]
    const quietDept = [...sorted].reverse()[0]
    return [
      topDept
        ? `${stringValue(topDept, ['deptName', 'dept_name'])} 的预警压力最高，应优先作为部门治理对象。`
        : '暂无部门对比数据，无法输出部门级风险结论。',
      quietDept
        ? `${stringValue(quietDept, ['deptName', 'dept_name'])} 当前预警次数最低，可作为稳定样本部门参考。`
        : '暂无稳定部门样本。',
      '建议将高预警部门与在册人数、岗位风险等级和设备在线率结合分析，避免只看单一次数。'
    ]
  }

  return [
    trendData.length
      ? `近 ${trendDays} 天心率异常率 ${formatDelta(trendData, 'heartRateRate')}，血氧异常率 ${formatDelta(trendData, 'bloodOxygenRate')}。`
      : '暂无趋势数据，无法输出趋势结论。',
    trendData.length
      ? `体温异常率 ${formatDelta(trendData, 'temperatureRate')}，压力异常率 ${formatDelta(trendData, 'pressureRate')}，建议重点关注拐点日期。`
      : '建议补齐每日趋势数据后再做趋势判断。',
    '趋势页适合发现“连续偏移”，发现问题后建议回到预警中心和人员画像做进一步处置。'
  ]
}
