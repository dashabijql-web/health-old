import request from '@/utils/request'

/**
 * 获取角色列表
 * @param {Object} params 查询参数
 */
export function getRoleList(params) {
  return request({
    url: '/role/list',
    method: 'get',
    params
  })
}

/**
 * 获取角色详细信息
 * @param {Number} id 角色ID
 */
export function getRoleDetail(id) {
  return request({
    url: `/role/detail/${id}`,
    method: 'get'
  })
}

/**
 * 获取角色的用户列表
 * @param {Number} roleId 角色ID
 */
export function getRoleUsers(roleId) {
  return request({
    url: `/role/users/${roleId}`,
    method: 'get'
  })
}

/**
 * 获取角色统计数据
 */
export function getRoleStats() {
  return request({
    url: '/role/stats',
    method: 'get'
  })
}

/**
 * 获取所有可用角色
 */
export function getAvailableRoles() {
  return request({ url: '/role/available', method: 'get' })
}

// ==================== 角色 CRUD ====================

/** 新增角色 */
export function createRole(data) {
  return request({ url: '/role/create', method: 'post', data })
}

/** 编辑角色 */
export function updateRole(data) {
  return request({ url: '/role/update', method: 'put', data })
}

/** 删除角色 */
export function deleteRole(id) {
  return request({ url: `/role/delete/${id}`, method: 'delete' })
}

// ==================== 权限分配 ====================

/** 获取权限树（所有可分配权限） */
export function getPermissionTree() {
  return request({ url: '/permission/tree', method: 'get' })
}

/** 获取角色已分配的权限 ID 列表 */
export function getRolePermissions(roleId) {
  return request({ url: `/role/permissions/${roleId}`, method: 'get' })
}

/** 保存角色权限分配 */
export function assignPermissions(roleId, permissionIds) {
  return request({ url: '/role/assign-permissions', method: 'post', data: { roleId, permissionIds } })
}
