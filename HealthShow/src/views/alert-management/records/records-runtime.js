import {
  getRiskWarningList,
  getRiskWarningOverview,
  handleBatchRiskWarning,
  handleRiskWarning
} from '@/api/risk-warning'
import { buildWarningLifecycleItem } from '../common/warning-lifecycle'

export async function fetchRecordsOverview() {
  const response = await getRiskWarningOverview()
  if (response.code !== 200) return null
  const data = response.data || {}
  return {
    todayTotal: data.totalWarnings ?? data.todayTotal ?? 0,
    pending: data.pendingWarnings ?? data.pending ?? 0,
    critical: data.dangerCount ?? data.critical ?? 0,
    handled: data.handledWarnings ?? data.handled ?? 0
  }
}

export async function fetchRecordsPage(query) {
  const response = await getRiskWarningList(query)
  if (response.code !== 200) return { rows: [], total: 0 }
  const data = response.data || {}
  return {
    rows: (data.list || data.records || []).map((row) => buildWarningLifecycleItem(row)),
    total: data.total || 0
  }
}

export async function fetchRecordsExportRows(query) {
  const response = await getRiskWarningList(query)
  return response.code === 200 ? (response.data?.list || response.data?.records || []) : []
}

export function submitRecordHandle(id, data) {
  return handleRiskWarning(id, data)
}

export function submitBatchRecordHandle(locators) {
  return handleBatchRiskWarning(locators)
}
