import request from '@/utils/request'

/**
 * 获取睡眠趋势数据
 * @param {Number} days 天数，默认7天
 */
export function getSleepTrend(days = 7) {
  return request({
    url: '/sleep/trend',
    method: 'get',
    params: { days }
  })
}

/**
 * 获取睡眠质量分布
 */
export function getSleepQualityDistribution() {
  return request({
    url: '/sleep/quality-distribution',
    method: 'get'
  })
}

/**
 * 获取睡眠页面完整数据
 */
export function getSleepPageData() {
  return request({
    url: '/sleep/page-data',
    method: 'get'
  })
}
