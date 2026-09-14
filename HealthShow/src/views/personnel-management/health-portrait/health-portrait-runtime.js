import { ElMessage } from 'element-plus'
import { getHealthPortrait } from '@/api/health-portrait'
import { getCachedAiReport, generateAiReport } from '@/api/ai'
import { getBodyIndicators } from '@/api/health'
import { getHeartRateUserAbnormalRecords } from '@/api/heart-rate'
import { getMetricUserAbnormalRecords } from '@/api/period-risk'
import { buildPortraitSnapshot } from './health-portrait-view-model'

export async function fetchHealthPortrait(empCode) {
  if (!empCode) return null
  try {
    const res = await getHealthPortrait(empCode)
    if (res.code === 200 && res.data) return buildPortraitSnapshot(res.data, empCode)
  } catch {}
  return null
}

export async function loadHealthPortraitAiReport(empCode) {
  if (!empCode) return null
  try {
    const res = await getCachedAiReport(empCode)
    if (res.code === 200 && res.data) return res.data
  } catch {}
  return null
}

export async function generateHealthPortraitAiReport(empCode, force = false) {
  if (!empCode) return { ok: false, message: '缺少员工编号' }
  try {
    const res = await generateAiReport(empCode, force)
    if (res.code === 200 && res.data) return { ok: true, data: res.data }
    return { ok: false, message: res.message || 'AI 分析失败' }
  } catch (err) {
    return {
      ok: false,
      message: err?.code === 'ECONNABORTED' || err?.message?.includes('timeout')
        ? 'DeepSeek 响应较慢，报告后台仍在生成，30秒后自动加载…'
        : 'AI 分析失败，请稍后重试'
    }
  }
}

export async function fetchHealthPortraitMineAvg() {
  try {
    const res = await getBodyIndicators()
    if (res.code === 200 && res.data) return res.data
  } catch {}
  return null
}

export async function fetchMetricAbnormalRecords(path, params) {
  try {
    const res = path === 'heart-rate'
      ? await getHeartRateUserAbnormalRecords(params)
      : await getMetricUserAbnormalRecords(path, params)
    if (res.code === 200 && res.data) {
      const list = (res.data.list || []).map(row => path === 'heart-rate'
        ? { ...row, primaryValue: row.heartRate, secondaryValue: null }
        : row)
      return { ...res.data, list }
    }
  } catch {}
  return null
}

export function notifyHealthPortraitMessage(type, message) {
  if (!message) return
  if (type === 'success') ElMessage.success(message)
  else if (type === 'warning') ElMessage.warning(message)
  else if (type === 'info') ElMessage.info(message)
  else ElMessage.error(message)
}
