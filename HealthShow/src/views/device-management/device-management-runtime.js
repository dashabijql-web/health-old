import request from '@/utils/request'
import {
  bindDeviceToUser,
  deleteBufferData,
  getOnlineDevices,
  markDeviceFault,
  resolveDeviceFault,
  sendWatchMessage,
  transferBufferData,
  unbindDevice
} from '@/api/device'

export async function fetchOnlineDeviceList() {
  const response = await getOnlineDevices()
  return response.code === 200 ? (response.data?.devices || []) : []
}

export async function searchDeviceUsers(query) {
  const response = await request({
    url: '/user/search',
    method: 'get',
    params: { query }
  })

  if (response.code !== 200) {
    return []
  }

  return (response.data || []).map((user) => ({
    value: user.realName,
    id: user.id,
    realName: user.realName,
    deptName: user.deptName,
    phone: user.phone
  }))
}

export function bindDevice(deviceId, userId) {
  return bindDeviceToUser(deviceId, userId)
}

export function unbindDeviceById(deviceId) {
  return unbindDevice(deviceId)
}

export function transferDeviceBuffer(deviceId, userId) {
  return transferBufferData(deviceId, userId)
}

export function clearDeviceBuffer(deviceId) {
  return deleteBufferData(deviceId)
}

export function pushDeviceMessage(imei, text) {
  return sendWatchMessage(imei, text)
}

export function registerDeviceFault(deviceId, data) {
  return markDeviceFault(deviceId, data)
}

export function closeDeviceFault(deviceId, remark) {
  return resolveDeviceFault(deviceId, { remark })
}
