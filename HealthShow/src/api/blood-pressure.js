import request from '@/utils/request'

export function getBPOverview(startDate, endDate) {
  return request({ url: '/blood-pressure/overview', method: 'get', params: { startDate, endDate } })
}

export function getBPTrend(days = 30) {
  return request({ url: '/blood-pressure/trend', method: 'get', params: { days } })
}

export function getBPDistribution(startDate, endDate) {
  return request({ url: '/blood-pressure/distribution', method: 'get', params: { startDate, endDate } })
}

export function getBPTopUsers(limit = 5, startDate, endDate) {
  return request({ url: '/blood-pressure/top-users', method: 'get', params: { limit, startDate, endDate } })
}

export function getBPDeptStats(startDate, endDate) {
  return request({ url: '/blood-pressure/department-stats', method: 'get', params: { startDate, endDate } })
}

export function getBPRealtime(limit = 1000) {
  return request({ url: '/blood-pressure/realtime', method: 'get', params: { limit } })
}

export function getBPHourly(date) {
  return request({ url: '/blood-pressure/hourly', method: 'get', params: { date } })
}
