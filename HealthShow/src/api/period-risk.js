import request from '@/utils/request'

function get(path, endpoint, params) {
  return request({ url: `/${path}/${endpoint}`, method: 'get', params })
}

export const getMetricRiskSummary = (path, params) => get(path, 'risk-summary', params)
export const getMetricDailyRisk = (path, params) => get(path, 'daily-risk', params)
export const getMetricDepartmentRisk = (path, params) => get(path, 'department-risk', params)
export const getMetricPeriodUsers = (path, params) => get(path, 'period-users', params)
export const getMetricDepartmentUsers = (path, params) => get(path, 'department-users', params)
export const getMetricUserAbnormalRecords = (path, params) => get(path, 'user-abnormal-records', params)
