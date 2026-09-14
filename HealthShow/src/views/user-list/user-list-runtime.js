import {
  createUser,
  deleteUser,
  getUserDetail,
  getUserHealthStats,
  getUserList,
  getUserStats,
  updateUser,
  updateUserStatus
} from '@/api/user'
import { getAvailableRoles } from '@/api/role'
import { getDepartmentList } from '@/api/department'

export function fetchUserList(params) {
  return getUserList(params)
}

export function fetchUserStats() {
  return getUserStats()
}

export function fetchAvailableRoles() {
  return getAvailableRoles()
}

export async function fetchUserDepartments() {
  const response = await getDepartmentList()
  if (response.code !== 200) {
    return []
  }
  return Array.isArray(response.data) ? response.data : (response.data?.list || [])
}

export function fetchUserDetail(id) {
  return getUserDetail(id)
}

export function fetchUserHealthStats(userCode) {
  return getUserHealthStats(userCode)
}

export function createUserRecord(payload) {
  return createUser(payload)
}

export function updateUserRecord(payload) {
  return updateUser(payload)
}

export function deleteUserRecord(id) {
  return deleteUser(id)
}

export function updateUserRecordStatus(id, status) {
  return updateUserStatus(id, status)
}
