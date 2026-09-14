import request from '@/utils/request'

export function getJobTypeList(params) {
  return request({ url: '/job-type/list', method: 'get', params })
}

export function createJobType(data) {
  return request({ url: '/job-type/create', method: 'post', data })
}

export function updateJobType(data) {
  return request({ url: '/job-type/update', method: 'put', data })
}

export function deleteJobType(id) {
  return request({ url: `/job-type/delete/${id}`, method: 'delete' })
}
