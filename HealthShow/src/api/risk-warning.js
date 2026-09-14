/**
 * 风险预警 API 模块
 *
 * 对应后端 Controller：RiskWarningController（/health/risk-warning/...）
 *
 * 【预警类型说明】
 * eventSource 区分 HEALTH_THRESHOLD、DEVICE_ALARM、TREND_WARNING；
 * eventCode 使用稳定代码区分 SOS、FALL、HEART_RATE 等具体事件。
 *
 * 主要用途：
 *   - 安全指挥中心（index.vue）：实时事件列表、紧急预警横幅
 *   - 预警管理/记录页（records/index.vue）：历史记录查询
 *   - health-monitor/risk-warning：预警分析统计
 */

import request from '@/utils/request'

/**
 * 获取风险预警统计概览（包含各类型统计）
 *
 * 后端接口：GET /health/risk-warning/overview
 * 返回字段示例：
 *   { sosCount: 2, fallCount: 1, staticCount: 5, abnormalCount: 12,
 *     totalOnline: 234, normalCount: 214 }
 *
 * 使用场景：安全指挥中心 KPI 卡片、预警管理顶部统计卡
 */
export function getRiskWarningOverview(startDate, endDate) {
  return request({
    url: '/risk-warning/overview',
    method: 'get',
    params: { startDate, endDate }
  })
}

/**
 * 获取预警列表
 * @param {Object} params 查询参数
 * @param {String} params.level 预警级别
 * @param {Boolean} params.handled 是否已处理
 * @param {String} params.eventSource 事件来源
 * @param {String} params.eventCode 事件代码
 * @param {Number} params.page 页码
 * @param {Number} params.size 每页条数
 */
export function getRiskWarningList(params) {
  return request({
    url: '/risk-warning/list',
    method: 'get',
    params
  })
}

/**
 * 获取风险趋势（按类型分组）
 * @param {Number} days 天数，默认30天
 */
export function getRiskWarningTrend(days = 30) {
  return request({
    url: '/risk-warning/trend',
    method: 'get',
    params: { days }
  })
}

export function getRiskWarningTypeDistribution() {
  return request({
    url: '/risk-warning/type-distribution',
    method: 'get'
  })
}

/**
 * 获取各部门预警统计
 */
export function getDeptWarningStats(startDate, endDate) {
  return request({
    url: '/risk-warning/dept-stats',
    method: 'get',
    params: { startDate, endDate }
  })
}

/**
 * 处理预警
 * @param {Number} id 预警ID
 * @param {Object} data 处理数据
 */
export function handleRiskWarning(id, data) {
  return request({
    url: `/risk-warning/handle/${id}`,
    method: 'post',
    data
  })
}

/**
 * 批量处理预警
 * @param {Array} locators warningId + occurredAt 复合定位列表
 */
export function handleBatchRiskWarning(locators) {
  return request({
    url: '/risk-warning/handle-batch',
    method: 'post',
    data: locators
  })
}
