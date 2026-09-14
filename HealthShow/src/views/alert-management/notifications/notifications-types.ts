export type WarningLevel = '' | '低危' | '中危' | '高危'
export type EventSource = '' | 'HEALTH_THRESHOLD' | 'DEVICE_ALARM' | 'TREND_WARNING'

export interface WarningItem {
  id: number
  createTime?: string
  userCode?: string
  empCode?: string
  handled?: boolean
  _loading?: boolean
  [key: string]: unknown
}

export interface IntervalTask {
  start: (delay?: number) => void
  stop: () => void
}

export interface RiskWarningQuery {
  page: number
  size: number
  level?: string
  handled?: boolean
  eventSource?: string
  eventCode?: string
  userCode?: string
}

export interface RiskWarningPage {
  list?: Record<string, unknown>[]
  records?: Record<string, unknown>[]
  total?: number
}

export interface ApiResult<T> {
  code: number
  data?: T
  message?: string
}

export interface WarningLocator {
  warningId: number
  occurredAt?: string
}
