import request from '@/utils/request'

/**
 * 获取血氧统计概览
 */
export function getBloodOxygenOverview(startDate, endDate) {
  return request({
    url: '/blood-oxygen/overview',
    method: 'get',
    params: { startDate, endDate }
  })
}

/**
 * 获取血氧趋势数据
 * @param {Number} days 天数，默认7天
 */
export function getBloodOxygenTrend(days = 7) {
  return request({
    url: '/blood-oxygen/trend',
    method: 'get',
    params: { days }
  })
}

/**
 * 获取血氧分布数据
 */
export function getBloodOxygenDistribution(startDate, endDate) {
  return request({
    url: '/blood-oxygen/distribution',
    method: 'get',
    params: { startDate, endDate }
  })
}

/**
 * 获取TOP用户血氧数据
 * @param {Number} limit 返回数量，默认5
 */
export function getBloodOxygenTopUsers(limit = 5, startDate, endDate) {
  return request({
    url: '/blood-oxygen/top-users',
    method: 'get',
    params: { limit, startDate, endDate }
  })
}

/**
 * 获取部门血氧异常统计
 */
export function getBloodOxygenDeptStats(startDate, endDate) {
  return request({
    url: '/blood-oxygen/department-stats',
    method: 'get',
    params: { startDate, endDate }
  })
}

/**
 * 获取各年龄段平均血氧
 */
export function getAgeBloodOxygen(startDate, endDate) {
  return request({
    url: '/blood-oxygen/age-stats',
    method: 'get',
    params: { startDate, endDate }
  })
}

/**
 * 获取实时血氧数据
 * @param {Number} limit 返回数量，默认20
 */
export function getRealtimeBloodOxygen(limit = 20) {
  return request({
    url: '/blood-oxygen/realtime',
    method: 'get',
    params: { limit }
  })
}

/**
 * 获取逐小时平均血氧（支持单日或日期范围）
 * @param {String} startDate 开始日期 YYYY-MM-DD
 * @param {String} endDate 结束日期 YYYY-MM-DD
 */
export function getHourlyBloodOxygen(startDate, endDate) {
  return request({
    url: '/blood-oxygen/hourly',
    method: 'get',
    params: { startDate, endDate }
  })
}