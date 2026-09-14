export function createEmptyPortrait() {
  return {
    empName: '',
    empCode: '',
    deptName: '',
    jobTypeName: '',
    gender: null,
    bloodType: '',
    height: null,
    weight: null
  }
}

export function createEmptyVitals() {
  return {
    heartRate: null,
    bloodOxygen: null,
    temperature: null,
    systolic: null,
    diastolic: null,
    pressure: null
  }
}

export function createEmptyTrendData() {
  return { dates: [], heartRates: [], bloodOxygens: [] }
}

export function createEmptyHourlyHrData() {
  return new Array(24).fill(0)
}

export function createEmptyHealthScores() {
  return {
    heartRate: 0,
    bloodOxygen: 0,
    temperature: 0,
    bloodPressure: 0,
    pressure: 0,
    activity: 0
  }
}

export function createEmptyMineAvgData() {
  return {
    avgHeartRate: null,
    avgBloodOxygen: null,
    avgCalories: null,
    avgPressure: null,
    avgSteps: null
  }
}

export function buildHealthPortraitRealtimeRoute(portrait) {
  return {
    path: '/health-monitor/real-time',
    query: {
      empCode: portrait.empCode,
      empName: portrait.empName,
      gender: portrait.gender ?? 1,
      deptName: portrait.deptName,
      jobTypeName: portrait.jobTypeName
    }
  }
}

export function applyHealthPortraitAiReport(aiReport, reportData) {
  if (!reportData) return
  aiReport.content = reportData.reportContent || ''
  aiReport.generateTime = reportData.generateTime || ''
  aiReport.expiresAt = reportData.expiresAt || ''
}

export function buildHealthPortraitHistoryKey(empCode) {
  return `ai_history_${empCode || ''}`
}

export function loadHealthPortraitHistory(empCode) {
  try {
    const raw = localStorage.getItem(buildHealthPortraitHistoryKey(empCode)) || '[]'
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function saveHealthPortraitHistory(reportHistory, empCode, report) {
  const item = { content: report.content, generateTime: report.generateTime }
  const nextHistory = reportHistory
    .filter((entry) => entry.generateTime !== item.generateTime)
    .slice(0, 4)
  nextHistory.unshift(item)
  try {
    localStorage.setItem(buildHealthPortraitHistoryKey(empCode), JSON.stringify(nextHistory))
  } catch {}
  return nextHistory
}

export function restoreHealthPortraitHistory(aiReport, historyItem) {
  aiReport.content = historyItem?.content || ''
  aiReport.generateTime = historyItem?.generateTime || ''
}

export function resetHealthPortraitState({
  portrait,
  vitals,
  trendData,
  hourlyHrData,
  healthScores,
  warnings,
  aiReport,
  reportHistory,
  histExpanded,
  mineAvgData
}) {
  Object.assign(portrait, createEmptyPortrait())
  Object.assign(vitals, createEmptyVitals())
  trendData.value = createEmptyTrendData()
  hourlyHrData.value = createEmptyHourlyHrData()
  healthScores.value = createEmptyHealthScores()
  warnings.value = []
  aiReport.content = ''
  aiReport.generateTime = ''
  aiReport.expiresAt = ''
  reportHistory.value = []
  histExpanded.value = false
  if (mineAvgData) mineAvgData.value = createEmptyMineAvgData()
}

