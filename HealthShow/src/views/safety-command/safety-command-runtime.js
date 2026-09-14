import { getRiskWarningOverview, getRiskWarningTrend, getDeptWarningStats } from '@/api/risk-warning'
import { getCommandCenterDashboardSummary, getCommandCenterIncidents } from '@/api/command-center'
import { getRealtimeOverview, getRealtimeStatistics } from '@/api/realtime'
import { getDeviceActivation, getBodyIndicators } from '@/api/health'
import { getEmployeeStats } from '@/api/employee'
import { getHourlyHeartRate } from '@/api/heart-rate'
import { getHourlyBloodOxygen } from '@/api/blood-oxygen'
import { getDeptAiReport, generateDeptAiReport } from '@/api/ai'
import {
  buildAreasFromDepartments,
  buildDepartmentListFromWarningStats,
  buildWatchStatus,
  normalizeWarningTrendData,
  resolveWarningTotals
} from './safety-command-view-model'

export async function loadSafetyCommandDeptAi(deptName) {
  if (!deptName) return null
  try {
    const res = await getDeptAiReport(deptName)
    if (res.code === 200 && res.data) return res.data
  } catch {}
  return null
}

export async function generateSafetyCommandDeptAi(deptName, force = false) {
  if (!deptName) return { ok: false, message: '缺少部门名称' }
  try {
    const res = await generateDeptAiReport(deptName, force)
    if (res.code === 200 && res.data) return { ok: true, data: res.data }
    return { ok: false, message: res.message || '生成失败' }
  } catch {
    return { ok: false, message: 'AI 服务暂时不可用' }
  }
}

export async function fetchSafetyCommandCritical({
  processWarningData,
  handledCountRef,
  pendingWarningsRef,
  totalWarningsRef,
  criticalWarningsRef,
  commandSummaryRef,
  dataAsOfRef
}) {
  try {
    const today = getLocalToday()
    const [overviewResult, listResult, summaryResult] = await Promise.allSettled([
      getRiskWarningOverview(today, today),
      getCommandCenterIncidents({ scope: 'today', status: 'OPEN', page: 1, size: 50 }),
      getCommandCenterDashboardSummary()
    ])

    if (listResult.status === 'fulfilled' && listResult.value?.data) {
      processWarningData({ list: listResult.value.data.items || [] })
    }

    if (overviewResult.status === 'fulfilled' && overviewResult.value?.data) {
      const totals = resolveWarningTotals(overviewResult.value.data, pendingWarningsRef.value)
      handledCountRef.value = totals.handled
      pendingWarningsRef.value = totals.pending
      totalWarningsRef.value = totals.total
      criticalWarningsRef.value = totals.critical
    }

    applyCommandSummary(summaryResult, {
      commandSummaryRef,
      dataAsOfRef,
      pendingWarningsRef,
      criticalWarningsRef
    })
  } catch {}
}

export async function fetchSafetyCommandHourlyVitals(vitalsHistory) {
  const today = getLocalToday()
  const [hrResult, oxygenResult] = await Promise.allSettled([
    getHourlyHeartRate(today, today),
    getHourlyBloodOxygen(today, today)
  ])

  if (hrResult.status === 'fulfilled' && Array.isArray(hrResult.value?.data) && hrResult.value.data.length >= 2) {
    vitalsHistory.hr = hrResult.value.data
  }
  if (oxygenResult.status === 'fulfilled' && Array.isArray(oxygenResult.value?.data) && oxygenResult.value.data.length >= 2) {
    vitalsHistory.bo = oxygenResult.value.data
  }
}

export async function fetchSafetyCommandData({
  handledCountRef,
  pendingWarningsRef,
  totalWarningsRef,
  criticalWarningsRef,
  commandSummaryRef,
  dataAsOfRef,
  warningTrendRef,
  departmentsRef,
  processWarningData,
  statsRef,
  watchStatusRef,
  vitalAvgRef,
  areasRef
}) {
  try {
    const today = getLocalToday()
    const results = await Promise.allSettled([
      getRiskWarningOverview(today, today),
      getCommandCenterIncidents({ scope: 'today', status: 'OPEN', page: 1, size: 50 }),
      getDeptWarningStats(today, today),
      getRiskWarningTrend(7),
      getCommandCenterDashboardSummary()
    ])

    if (results[1].status === 'fulfilled' && results[1].value?.data) {
      processWarningData({ list: results[1].value.data.items || [] })
    }

    if (results[0].status === 'fulfilled' && results[0].value?.data) {
      const data = results[0].value.data
      const totals = resolveWarningTotals(data, pendingWarningsRef.value)
      handledCountRef.value = totals.handled
      pendingWarningsRef.value = totals.pending
      totalWarningsRef.value = totals.total
      criticalWarningsRef.value = totals.critical
      if (statsRef) {
        statsRef.value = {
          ...statsRef.value,
          underground: data.totalOnline || data.underground || statsRef.value.underground,
          total: data.totalUsers || data.total || statsRef.value.total
        }
      }
    }

    if (results[2].status === 'fulfilled' && results[2].value?.data) {
      departmentsRef.value = buildDepartmentListFromWarningStats(results[2].value.data)
    }

    if (results[3].status === 'fulfilled' && results[3].value?.data) {
      warningTrendRef.value = normalizeWarningTrendData(results[3].value.data)
    }

    applyCommandSummary(results[4], {
      commandSummaryRef,
      dataAsOfRef,
      pendingWarningsRef,
      criticalWarningsRef
    })

    if (statsRef && watchStatusRef && vitalAvgRef && areasRef) {
      await fetchLegacySafetyCommandData({
        statsRef,
        watchStatusRef,
        vitalAvgRef,
        departmentsRef,
        areasRef
      })
    }
  } catch {}
}

async function fetchLegacySafetyCommandData({ statsRef, watchStatusRef, vitalAvgRef, departmentsRef, areasRef }) {
  const [realtimeResult, activationResult, overviewResult, employeeResult, indicatorResult] = await Promise.allSettled([
    getRealtimeStatistics(),
    getDeviceActivation(),
    getRealtimeOverview(),
    getEmployeeStats(),
    getBodyIndicators()
  ])

  if (realtimeResult.status === 'fulfilled' && realtimeResult.value?.data) {
    watchStatusRef.value = buildWatchStatus(realtimeResult.value.data)
  }
  if (activationResult.status === 'fulfilled' && activationResult.value?.data?.stats && !watchStatusRef.value.total) {
    const stats = activationResult.value.data.stats
    watchStatusRef.value.total = stats.totalDevices || 0
    watchStatusRef.value.online = stats.activeDevices || 0
    watchStatusRef.value.offline = Math.max(0, watchStatusRef.value.total - watchStatusRef.value.online)
  }
  if (overviewResult.status === 'fulfilled' && overviewResult.value?.data) {
    const undergroundCount = overviewResult.value.data.onlineCount || overviewResult.value.data.underground || 0
    if (undergroundCount > 0) statsRef.value.underground = undergroundCount
  }
  if (employeeResult.status === 'fulfilled' && employeeResult.value?.data) {
    const totalCount = employeeResult.value.data.total || employeeResult.value.data.totalCount || 0
    if (totalCount > 0) statsRef.value.total = totalCount
  }
  if (indicatorResult.status === 'fulfilled' && indicatorResult.value?.data) {
    const avg = indicatorResult.value.data
    vitalAvgRef.value = {
      heartRate: avg.avgHeartRate || 0,
      bloodOxygen: avg.avgBloodOxygen || 0,
      temperature: avg.avgTemperature || 0,
      pressure: avg.avgPressure || 0
    }
  }
  areasRef.value = buildAreasFromDepartments(departmentsRef.value)
}

function applyCommandSummary(result, refs) {
  if (result?.status !== 'fulfilled' || result.value?.code !== 200 || !result.value?.data) return
  const summary = result.value.data
  refs.commandSummaryRef.value = summary
  refs.dataAsOfRef.value = summary.dataAsOf || ''
  if (summary.warning) {
    refs.pendingWarningsRef.value = Number(summary.warning.pendingTotal || 0)
    refs.criticalWarningsRef.value = Number(summary.warning.criticalPending || 0)
  }
}

function getLocalToday() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}
