import request from '@/utils/request'

export function getWatchRawPackets(params = {}) {
  return request({
    url: '/api/watch/raw-packets',
    method: 'get',
    params,
    timeout: 10000
  })
}

export function sendWatchCommand(data) {
  return request({
    url: '/api/watch/command',
    method: 'post',
    data,
    timeout: 10000
  })
}
