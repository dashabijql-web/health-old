import request from '@/utils/request'

export function getPressureOverview(startDate, endDate) {
  return request({ url: '/pressure/overview', method: 'get', params: { startDate, endDate } })
}
export function getPressureTrend(days = 30) {
  return request({ url: '/pressure/trend', method: 'get', params: { days } })
}
export function getPressureDistribution(startDate, endDate) {
  return request({ url: '/pressure/distribution', method: 'get', params: { startDate, endDate } })
}
export function getPressureTopUsers(limit = 5, startDate, endDate) {
  return request({ url: '/pressure/top-users', method: 'get', params: { limit, startDate, endDate } })
}
export function getPressureDeptStats(startDate, endDate) {
  return request({ url: '/pressure/department-stats', method: 'get', params: { startDate, endDate } })
}
export function getPressureRealtime(limit = 1000) {
  return request({ url: '/pressure/realtime', method: 'get', params: { limit } })
}

export function getPressureHourly(date) {
  return request({ url: '/pressure/hourly', method: 'get', params: { date } })
}
