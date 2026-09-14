import {
  assignPermissions,
  createRole,
  deleteRole,
  getPermissionTree,
  getRoleDetail,
  getRoleList,
  getRolePermissions,
  getRoleStats,
  getRoleUsers,
  updateRole
} from '@/api/role'

export function fetchRoleStats() {
  return getRoleStats()
}

export function fetchRoleList(params) {
  return getRoleList(params)
}

export function fetchRoleDetail(id) {
  return getRoleDetail(id)
}

export function createRoleRecord(payload) {
  return createRole(payload)
}

export function updateRoleRecord(payload) {
  return updateRole(payload)
}

export function deleteRoleRecord(id) {
  return deleteRole(id)
}

export function fetchPermissionTree() {
  return getPermissionTree()
}

export function fetchRolePermissionIds(roleId) {
  return getRolePermissions(roleId)
}

export function saveRolePermissions(roleId, permissionIds) {
  return assignPermissions(roleId, permissionIds)
}

export function fetchRoleUsers(roleId) {
  return getRoleUsers(roleId)
}
