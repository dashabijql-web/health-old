import request from '@/utils/request'

/**
 * 获取心率统计概览
 */
export function getHeartRateOverview(startDate, endDate) {
  return request({
    url: '/heart-rate/overview',
    method: 'get',
    params: { startDate, endDate }
  })
}

/**
 * 获取心率趋势数据
 * @param {Number} days 天数，默认7天
 */
export function getHeartRateTrend(days = 7) {
  return request({
    url: '/heart-rate/trend',
    method: 'get',
    params: { days }
  })
}

/**
 * 获取心率分布数据
 */
export function getHeartRateDistribution(startDate, endDate) {
  return request({
    url: '/heart-rate/distribution',
    method: 'get',
    params: { startDate, endDate }
  })
}

/**
 * 获取TOP用户心率数据
 * @param {Number} limit 返回数量，默认5
 */
export function getHeartRateTopUsers(limit = 5, startDate, endDate) {
  return request({
    url: '/heart-rate/top-users',
    method: 'get',
    params: { limit, startDate, endDate }
  })
}

/**
 * 获取部门心率异常统计
 */
export function getHeartRateDeptStats(startDate, endDate) {
  return request({
    url: '/heart-rate/department-stats',
    method: 'get',
    params: { startDate, endDate }
  })
}

/**
 * 获取指定部门在当前统计周期内的异常人员明细
 */
export function getHeartRateDepartmentUsers(params) {
  return request({
    url: '/heart-rate/department-users',
    method: 'get',
    params
  })
}

/**
 * 获取当前统计周期内的覆盖/异常人员证据
 */
export function getHeartRatePeriodUsers(params) {
  return request({
    url: '/heart-rate/period-users',
    method: 'get',
    params
  })
}

/**
 * 获取指定人员在日期范围内的原始异常心率记录
 */
export function getHeartRateUserAbnormalRecords(params) {
  return request({
    url: '/heart-rate/user-abnormal-records',
    method: 'get',
    params
  })
}

/**
 * 获取各年龄段平均心率
 */
export function getAgeHeartRate(startDate, endDate) {
  return request({
    url: '/heart-rate/age-stats',
    method: 'get',
    params: { startDate, endDate }
  })
}

/**
 * 获取实时心率数据
 * @param {Number} limit 返回数量，默认20
 */
export function getRealtimeHeartRate(limit = 20) {
  return request({
    url: '/heart-rate/realtime',
    method: 'get',
    params: { limit }
  })
}

/**
 * 获取逐小时平均心率（支持单日或日期范围）
 * @param {String} startDate 开始日期 YYYY-MM-DD
 * @param {String} endDate 结束日期 YYYY-MM-DD
 */
export function getHourlyHeartRate(startDate, endDate) {
  return request({
    url: '/heart-rate/hourly',
    method: 'get',
    params: { startDate, endDate }
  })
}

/**
 * 按日统计心率异常人次
 */
export function getDailyAnomalyHeartRate(startDate, endDate) {
  return request({
    url: '/heart-rate/daily-anomaly',
    method: 'get',
    params: { startDate, endDate }
  })
}
