/**
 * 设备管理 API
 */
import request from '@/utils/request'

/**
 * 获取在线设备列表
 */
export function getOnlineDevices() {
  return request({
    url: '/api/device/online',
    method: 'get'
  })
}

/**
 * 转移缓冲数据到用户
 */
export function transferBufferData(deviceId, userId) {
  return request({
    url: `/api/device/${deviceId}/transfer-buffer`,
    method: 'post',
    params: { userId }
  })
}

/**
 * 删除设备缓冲数据
 */
export function deleteBufferData(deviceId) {
  return request({
    url: `/api/device/${deviceId}/buffer`,
    method: 'delete'
  })
}

/**
 * 发送文字消息到手表 (BP40)
 */
export function sendWatchMessage(imei, text) {
  return request({
    url: '/api/device/message',
    method: 'post',
    data: { imei, text }
  })
}

/**
 * 绑定设备到用户
 */
export function bindDeviceToUser(deviceId, userId) {
  return request({
    url: `/api/device/${deviceId}/bind`,
    method: 'post',
    params: { userId }
  })
}

/**
 * 获取语音广播模板列表
 */
export function getVoiceTemplates() {
  return request({
    url: '/api/device/voice/templates',
    method: 'get'
  })
}

/**
 * 向手表发送语音广播（BP28）
 */
export function sendVoiceMessage(imei, templateId) {
  return request({
    url: '/api/device/voice/send',
    method: 'post',
    data: { imei, templateId }
  })
}

/**
 * 解绑设备
 */
export function unbindDevice(deviceId) {
  return request({
    url: `/api/device/${deviceId}/unbind`,
    method: 'post'
  })
}

export function markDeviceFault(deviceId, data) {
  return request({
    url: `/api/device/${deviceId}/fault`,
    method: 'post',
    data
  })
}

export function resolveDeviceFault(deviceId, data) {
  return request({
    url: `/api/device/${deviceId}/fault/resolve`,
    method: 'post',
    data
  })
}
