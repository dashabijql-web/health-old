import request from '@/utils/request'

export function getAlertConfigList() {
  return request({ url: '/alert-config/list', method: 'get' })
}

export function updateAlertConfig(data) {
  return request({ url: '/alert-config/update', method: 'put', data })
}

export function toggleAlertConfig(id) {
  return request({ url: `/alert-config/toggle/${id}`, method: 'put' })
}
