import request from '@/utils/request'

export function getTrendWarningPrediction() {
  return request({ url: '/trend-warning/predict', method: 'get' })
}
