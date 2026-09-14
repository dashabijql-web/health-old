import request from '@/utils/request'

export function getDepartmentList(params) {
  return request({ url: '/department/list', method: 'get', params })
}

export function createDepartment(data) {
  return request({ url: '/department/create', method: 'post', data })
}

export function updateDepartment(data) {
  return request({ url: '/department/update', method: 'put', data })
}

export function deleteDepartment(id) {
  return request({ url: `/department/delete/${id}`, method: 'delete' })
}
