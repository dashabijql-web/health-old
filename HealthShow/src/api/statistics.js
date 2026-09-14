import request from '@/utils/request'

export function getWarningTypes(params, requestOptions = {}) {
  return request({ ...requestOptions, url: '/statistics/warning-types', method: 'get', params })
}
