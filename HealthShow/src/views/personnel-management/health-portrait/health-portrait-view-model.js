function formatMonthDay(date) {
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

export function buildGradeInfo(healthScores) {
  const s = healthScores || {}
  const vals = [s.heartRate, s.bloodOxygen, s.temperature, s.bloodPressure, s.pressure].filter((v) => v > 0)
  const avg = vals.length ? Math.round(vals.reduce((sum, item) => sum + item, 0) / vals.length) : 0
  if (avg >= 90) return { grade: 'A', score: avg, label: '健康状态优秀', cls: 'grade-a' }
  if (avg >= 75) return { grade: 'B', score: avg, label: '健康状态良好', cls: 'grade-b' }
  if (avg >= 60) return { grade: 'C', score: avg, label: '健康状态一般', cls: 'grade-c' }
  if (avg > 0) return { grade: 'D', score: avg, label: '健康状态较差，需关注', cls: 'grade-d' }
  return { grade: '--', score: 0, label: '暂无评分数据', cls: 'grade-none' }
}

export function buildNextCheckDate(warnings) {
  const list = warnings || []
  const hasHigh = list.some((w) => w.warningLevel === '高危' || w.warningLevel === '危急')
  const hasMid = list.some((w) => w.warningLevel === '中')
  const d = new Date()
  if (hasHigh) d.setDate(d.getDate() + 1)
  else if (hasMid) d.setDate(d.getDate() + 3)
  else d.setDate(d.getDate() + 7)
  return formatMonthDay(d)
}

export function buildWeekStats(trendData, vitals) {
  const trend = trendData || {}
  const hrs = trend.heartRates || []
  const bos = trend.bloodOxygens || []
  const temps = trend.temperatures || []
  const avgHr = hrs.length ? +(hrs.reduce((sum, item) => sum + item, 0) / hrs.length).toFixed(1) : null
  const minOxygen = bos.length ? Math.min(...bos) : null
  const avgTemp = temps.length ? +(temps.reduce((sum, item) => sum + item, 0) / temps.length).toFixed(1) : (vitals?.temperature || null)
  return { avgHr, minOxygen, avgTemp }
}

export function buildRiskLevels(warnings) {
  const list = warnings || []
  const total = list.length || 1
  const byIndicator = {}
  list.forEach((w) => {
    const key = w.indicatorName || w.warningType || '其他'
    if (!byIndicator[key]) byIndicator[key] = { high: 0, mid: 0, low: 0 }
    if (w.warningLevel === '高危' || w.warningLevel === '危急' || w.warningLevel === '高') byIndicator[key].high++
    else if (w.warningLevel === '中') byIndicator[key].mid++
    else byIndicator[key].low++
  })
  const keys = Object.keys(byIndicator).slice(0, 4)
  const colorMap = {
    high: 'linear-gradient(90deg,#ff5252,#ff8a65)',
    mid: 'linear-gradient(90deg,#ffd200,#ffe082)',
    low: 'linear-gradient(90deg,#38ef7d,#69f0ae)'
  }
  const labelMap = { high: '高', mid: '中', low: '低' }
  const textColorMap = { high: '#ff5252', mid: '#ffd200', low: '#38ef7d' }
  return keys.map((key) => {
    const item = byIndicator[key]
    const dominant = item.high > 0 ? 'high' : item.mid > 0 ? 'mid' : 'low'
    const pct = Math.min(100, Math.round(((item.high * 3 + item.mid * 2 + item.low) / (total * 0.5)) * 100))
    return {
      name: key.slice(0, 3),
      pct: Math.max(8, pct),
      color: colorMap[dominant],
      label: labelMap[dominant],
      textColor: textColorMap[dominant]
    }
  })
}

export function buildHeatmapCells(hourlyHrData) {
  return (hourlyHrData || []).map((hr, h) => {
    const label = `${String(h).padStart(2, '0')}:00`
    if (!hr) return { color: '#1a2a4d', label }
    if (hr > 100 || hr < 55) return { color: '#ff5252', label: `${label} ${hr}bpm 需关注` }
    if (hr > 90) return { color: '#f97316', label: `${label} ${hr}bpm` }
    return { color: '#1565c0', label: `${label} ${hr}bpm` }
  })
}

export function buildFatigueInfo(vitals, warnings) {
  const pressure = vitals?.pressure || 0
  const hr = vitals?.heartRate || 0
  const warnCount = (warnings || []).length
  const hrFatigue = hr > 100 ? 70 : hr < 60 ? 30 : Math.max(0, ((hr - 60) / 40) * 50)
  const warnFatigue = Math.min(100, warnCount * 5)
  const index = Math.min(100, Math.round(pressure * 0.4 + hrFatigue * 0.3 + warnFatigue * 0.3))
  const color = index >= 70 ? '#ff5252' : index >= 45 ? '#ffd200' : '#38ef7d'
  const label = index >= 70 ? '严重疲劳，建议休息' : index >= 45 ? '中度疲劳，适当减负' : '精力状态良好'
  const cls = index >= 70 ? 'fatigue-high' : index >= 45 ? 'fatigue-mid' : 'fatigue-low'
  const tip = index >= 70 ? '建议暂停作业，立即休息' : index >= 45 ? '建议控制连续工时，补充休息' : '当前状态良好，可正常作业'
  return {
    index,
    color,
    label,
    cls,
    tip,
    factors: [
      { name: '压力指数', pct: pressure, color: pressure > 75 ? '#ff5252' : pressure > 50 ? '#ffd200' : '#38ef7d', label: pressure > 75 ? '偏高' : pressure > 50 ? '中等' : '正常' },
      { name: '心率状态', pct: Math.min(100, hr ? Math.round(((hr - 40) / 80) * 100) : 0), color: (hr > 100 || hr < 60) ? '#ff5252' : '#38ef7d', label: hr > 100 ? '偏快' : hr < 60 ? '偏慢' : '正常' },
      { name: '近期预警', pct: Math.min(100, warnCount * 10), color: warnCount > 5 ? '#ff5252' : warnCount > 2 ? '#ffd200' : '#38ef7d', label: warnCount > 5 ? '频繁' : warnCount > 0 ? '有预警' : '良好' }
    ]
  }
}

export function buildOccRisks({ deptName, jobTypeName }, vitals, warnings) {
  const dept = deptName || ''
  const job = jobTypeName || ''
  const isDustJob = /掘进|综采|炮采|采煤|矿工/.test(job) || /综采|掘进/.test(dept)
  const isNoiseJob = /机电|泵房|压风|通风|运输/.test(job)
  const isChemJob = /化验|检测|火药|爆破/.test(job)
  const cvRisk = Math.min(100, ((vitals?.systolic > 130 ? 30 : 0) + (vitals?.diastolic > 85 ? 20 : 0) + (vitals?.heartRate > 95 ? 20 : 0) + ((warnings || []).filter((w) => /心率|血压/.test(w.warningType || '')).length * 5)))
  const dustRisk = Math.min(100, (isDustJob ? 40 : 10) + (vitals?.bloodOxygen < 96 ? 20 : 0) + (vitals?.bloodOxygen < 94 ? 20 : 0))
  const noiseRisk = Math.min(100, isNoiseJob ? 35 : isChemJob ? 15 : 8)

  const mkItem = (name, score, tip) => {
    const color = score >= 60 ? '#ff5252' : score >= 35 ? '#ffd200' : '#38ef7d'
    const level = score >= 60 ? '高风险' : score >= 35 ? '中风险' : '低风险'
    return { name, score, color, level, tip }
  }

  return [
    mkItem('心血管疾病', cvRisk, cvRisk >= 60 ? '建议尽快复查血压、心率，避免高强度作业' : '保持健康生活习惯'),
    mkItem('尘肺病风险', dustRisk, isDustJob ? '处于高粉尘作业环境，注意佩戴防尘装备' : '当前工种粉尘暴露较低'),
    mkItem('噪声性耳聋', noiseRisk, isNoiseJob ? '长期噪声暴露，建议定期听力检查' : '当前噪声暴露风险较低')
  ]
}

export function buildMentalHealthInfo(vitals, warnings) {
  const pressure = vitals?.pressure || 0
  const hr = vitals?.heartRate || 0
  const warnCount = (warnings || []).length
  const stressScore = Math.max(0, 100 - pressure)
  const hrScore = hr > 0 ? (hr >= 60 && hr <= 85 ? 90 : hr <= 100 ? 70 : 40) : 50
  const warnScore = Math.max(0, 100 - warnCount * 8)
  const score = Math.round((stressScore * 0.45 + hrScore * 0.3 + warnScore * 0.25))
  const level = score >= 80 ? '心理状态良好' : score >= 60 ? '轻度压力状态' : score >= 40 ? '中度压力状态' : '压力较大，需关注'
  const color = score >= 80 ? '#38ef7d' : score >= 60 ? '#ffd200' : '#ff5252'
  const badgeCls = score >= 80 ? 'badge-ok' : score >= 60 ? 'badge-warn' : 'badge-danger'
  const advice = score >= 80 ? '当前心理状态健康，保持规律作息和适度运动。'
    : score >= 60 ? '建议关注工作压力，适当进行放松调节，保证充足睡眠。'
      : score >= 40 ? '心理压力较大，建议与心理辅导人员沟通，减少高强度作业。'
        : '请立即关注该员工心理健康状态，建议暂停作业并安排心理疏导。'
  return {
    score,
    level,
    color,
    badgeCls,
    advice,
    dims: [
      { name: '压力状态', score: stressScore, color: stressScore >= 70 ? '#38ef7d' : stressScore >= 40 ? '#ffd200' : '#ff5252', label: stressScore >= 70 ? '轻松' : stressScore >= 40 ? '适中' : '偏高' },
      { name: '心率稳定', score: hrScore, color: hrScore >= 70 ? '#38ef7d' : hrScore >= 50 ? '#ffd200' : '#ff5252', label: hrScore >= 70 ? '平稳' : hrScore >= 50 ? '轻波' : '波动' },
      { name: '近期健康', score: warnScore, color: warnScore >= 70 ? '#38ef7d' : warnScore >= 40 ? '#ffd200' : '#ff5252', label: warnScore >= 70 ? '稳定' : warnScore >= 40 ? '偶发' : '频发' }
    ]
  }
}

export function buildMineAvgCompare(vitals, mineAvgData) {
  const avg = mineAvgData || {}
  const v = vitals || {}
  const items = []

  const pct = (val, min, max) => (val != null ? Math.min(100, Math.max(0, ((val - min) / (max - min)) * 100)) : 0)

  if (v.heartRate && avg.avgHeartRate) {
    const myPct = pct(v.heartRate, 40, 120)
    const avgPct = pct(avg.avgHeartRate, 40, 120)
    const diff = v.heartRate - avg.avgHeartRate
    const normal = v.heartRate >= 60 && v.heartRate <= 100
    items.push({
      name: '心率 (bpm)',
      myVal: v.heartRate,
      avgVal: Math.round(avg.avgHeartRate),
      myPct,
      avgPct,
      myColor: normal ? '#38ef7d' : '#ff5252',
      rankCls: Math.abs(diff) <= 5 ? 'mac-same' : normal ? 'mac-better' : 'mac-worse',
      rankLabel: Math.abs(diff) <= 5 ? '≈均值' : diff > 0 ? `+${Math.round(diff)}` : `${Math.round(diff)}`
    })
  }
  if (v.bloodOxygen && avg.avgBloodOxygen) {
    const myPct = pct(v.bloodOxygen, 85, 100)
    const avgPct = pct(avg.avgBloodOxygen, 85, 100)
    const diff = v.bloodOxygen - avg.avgBloodOxygen
    items.push({
      name: '血氧 (%)',
      myVal: v.bloodOxygen,
      avgVal: avg.avgBloodOxygen?.toFixed(1),
      myPct,
      avgPct,
      myColor: v.bloodOxygen >= 95 ? '#38ef7d' : '#ff5252',
      rankCls: diff >= 0 ? 'mac-better' : 'mac-worse',
      rankLabel: diff >= 0 ? `优+${diff.toFixed(1)}%` : `低${diff.toFixed(1)}%`
    })
  }
  if (v.pressure && avg.avgPressure) {
    const myPct = pct(v.pressure, 0, 100)
    const avgPct = pct(avg.avgPressure, 0, 100)
    const diff = v.pressure - avg.avgPressure
    items.push({
      name: '压力指数',
      myVal: v.pressure,
      avgVal: Math.round(avg.avgPressure),
      myPct,
      avgPct,
      myColor: v.pressure <= 50 ? '#38ef7d' : v.pressure <= 70 ? '#ffd200' : '#ff5252',
      rankCls: diff <= 0 ? 'mac-better' : 'mac-worse',
      rankLabel: diff <= 0 ? `低${Math.abs(Math.round(diff))}` : `高+${Math.round(diff)}`
    })
  }

  return items
}

export function buildRenderedReport(content) {
  if (!content) return ''
  return content
    .replace(/^## (.+)$/gm, '<h4>$1</h4>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}

export function buildScorePills(healthScores) {
  const s = healthScores || {}
  return {
    heartRate: { label: '心率', value: s.heartRate || 0, color: '#ff5252' },
    bloodOxygen: { label: '血氧', value: s.bloodOxygen || 0, color: '#00d4ff' },
    temperature: { label: '体温', value: s.temperature || 0, color: '#ffd200' },
    bloodPressure: { label: '血压', value: s.bloodPressure || 0, color: '#38ef7d' },
    pressure: { label: '压力', value: s.pressure || 0, color: '#fb923c' },
    activity: { label: '活动', value: s.activity || 0, color: '#764ba2' }
  }
}

export function formatPortraitWarningTime(value) {
  if (!value) return '--'
  const text = String(value).replace('T', ' ').replace(/\.\d+.*$/, '')
  return text.length >= 16 ? text.slice(5, 16) : text
}

export function resolvePortraitLevelTagType(level) {
  if (!level) return 'info'
  if (level.includes('高') || level.includes('危急')) return 'danger'
  if (level.includes('中')) return 'warning'
  return 'info'
}

export function resolvePortraitLevelLabel(level) {
  if (!level) return '--'
  const map = { 低: '低危', 中: '中危', 高: '高危' }
  return map[level] || level
}

export function resolveVitalStatus(value, min, max) {
  if (value == null) return 'unknown'
  return value >= min && value <= max ? 'normal' : 'abnormal'
}

export function resolvePressureStatus(value) {
  if (value == null) return 'unknown'
  return value < 70 ? 'normal' : 'abnormal'
}

export function resolveVitalStatusText(value, min, max) {
  if (value == null) return '--'
  return value >= min && value <= max ? '正常' : '异常'
}

export function buildPortraitHealthScores(vitals, exercise) {
  const v = vitals || {}
  const steps = exercise?.todaySteps ?? 0
  const hr = v.heartRate
  const spo = v.bloodOxygen
  const tmp = v.temperature
  const sbp = v.systolic
  const dbp = v.diastolic
  const prs = v.pressure

  return {
    heartRate: hr ? (hr >= 60 && hr <= 100 ? 90 : hr >= 50 && hr <= 110 ? 70 : 50) : 0,
    bloodOxygen: spo ? (spo >= 95 ? 95 : spo >= 90 ? 65 : 40) : 0,
    temperature: tmp ? (tmp >= 36.0 && tmp <= 37.5 ? 90 : tmp >= 35.5 && tmp <= 38.0 ? 65 : 50) : 0,
    bloodPressure: (sbp && dbp) ? (sbp <= 135 && dbp <= 85 ? 85 : sbp <= 145 && dbp <= 95 ? 65 : 50) : 0,
    pressure: prs != null ? (prs < 70 ? 90 : prs < 85 ? 65 : 40) : 0,
    activity: steps >= 10000 ? 90 : steps >= 5000 ? 65 : steps >= 2000 ? 45 : steps > 0 ? 30 : 0
  }
}

export function buildPortraitSnapshot(data, fallbackEmpCode = '') {
  const d = data || {}
  const portrait = {
    empName: d.empName || d.employee?.empName || '',
    empCode: d.empCode || d.employee?.empCode || fallbackEmpCode,
    deptName: d.deptName || d.employee?.deptName || '',
    jobTypeName: d.jobTypeName || d.employee?.jobTypeName || '',
    gender: d.gender ?? d.employee?.gender ?? null,
    bloodType: d.bloodType || d.employee?.bloodType || '',
    height: d.height ?? d.employee?.height ?? null,
    weight: d.weight ?? d.employee?.weight ?? null
  }

  const vitals = {
    heartRate: null,
    bloodOxygen: null,
    temperature: null,
    systolic: null,
    diastolic: null,
    pressure: null
  }

  const rawVitals = d.vitals || d.realtime
  if (rawVitals) {
    vitals.heartRate = rawVitals.heartRate ?? rawVitals.heart_rate ?? null
    vitals.bloodOxygen = rawVitals.bloodOxygen ?? rawVitals.blood_oxygen ?? null
    vitals.temperature = rawVitals.temperature ?? null
    vitals.systolic = rawVitals.systolic ?? rawVitals.sbp ?? null
    vitals.diastolic = rawVitals.diastolic ?? rawVitals.dbp ?? null
    vitals.pressure = rawVitals.pressure ?? null
  }

  return {
    portrait,
    vitals,
    trendData: d.trend || { dates: [], heartRates: [], bloodOxygens: [] },
    hourlyHrData: Array.isArray(d.hourlyHr) ? d.hourlyHr : new Array(24).fill(0),
    healthScores: d.healthScores || buildPortraitHealthScores(vitals, d.exercise),
    warnings: sortPortraitWarnings(d.warnings || d.recentWarnings || [])
  }
}

export function sortPortraitWarnings(warnings) {
  return [...(warnings || [])].sort(
    (a, b) => new Date(b.createTime || b.time || 0) - new Date(a.createTime || a.time || 0)
  )
}
