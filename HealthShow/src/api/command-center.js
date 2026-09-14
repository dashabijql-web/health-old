import request from '@/utils/request'

export function getCommandCenterIncidents(params, requestOptions = {}) {
  return request({
    ...requestOptions,
    url: '/command-center/incidents',
    method: 'get',
    params
  })
}

export function getCommandCenterDashboardSummary(params) {
  const requestOptions = arguments[1] || {}
  return request({
    ...requestOptions,
    url: '/command-center/dashboard-summary',
    method: 'get',
    params
  })
}

export function getPreShiftReviews(params) {
  return request({
    url: '/command-center/pre-shift-reviews',
    method: 'get',
    params
  })
}

export function applyPreShiftReviewAction(empCode, data) {
  return request({
    url: `/command-center/pre-shift-reviews/${encodeURIComponent(empCode)}/action`,
    method: 'post',
    data
  })
}

export function getCommandCenterIncident(warningId, occurredAt) {
  return request({
    url: `/command-center/incidents/${warningId}`,
    method: 'get',
    params: { occurredAt }
  })
}

export function resolveCommandCenterIncident(warningId, data) {
  return request({
    url: `/command-center/incidents/${warningId}/resolve`,
    method: 'post',
    data
  })
}

export function acknowledgeCommandCenterIncident(warningId, data) {
  return request({ url: '/command-center/incidents/' + warningId + '/ack', method: 'post', data })
}

export function assignCommandCenterIncident(warningId, data) {
  return request({ url: '/command-center/incidents/' + warningId + '/assign', method: 'post', data })
}

export function falseAlarmCommandCenterIncident(warningId, data) {
  return request({ url: '/command-center/incidents/' + warningId + '/false-alarm', method: 'post', data })
}

export function executeCommandCenterExternalAction(warningId, action, data) {
  return request({ url: '/command-center/incidents/' + warningId + '/' + action, method: 'post', data })
}

export function getCommandCenterIncidentTimeline(warningId, occurredAt) {
  return request({
    url: '/command-center/incidents/' + warningId + '/timeline',
    method: 'get',
    params: { occurredAt }
  })
}
