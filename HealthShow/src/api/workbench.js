import request from '@/utils/request'

/**
 * 获取工作台日历数据（指定年月，每天的健康均值 + 预警数）
 */
export function getCalendarData(year, month) {
  return request({ url: '/dashboard/calendar', method: 'get', params: { year, month } })
}

export function getDayHeartRateRank(date) {
  return request({ url: '/dashboard/calendar/day-heart-rate', method: 'get', params: { date } })
}

export function getDayBloodOxygenRank(date) {
  return request({ url: '/dashboard/calendar/day-blood-oxygen', method: 'get', params: { date } })
}

export function getDayStepsRank(date) {
  return request({ url: '/dashboard/calendar/day-steps', method: 'get', params: { date } })
}

export function getDayWarnings(date) {
  return request({ url: '/dashboard/calendar/day-warnings', method: 'get', params: { date } })
}
