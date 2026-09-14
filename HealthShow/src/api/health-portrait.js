import request from '@/utils/request'

export function getHealthPortrait(empCode) {
  return request({ url: `/health-portrait/${empCode}`, method: 'get' })
}
