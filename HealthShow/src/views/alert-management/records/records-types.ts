export interface RecordsOverview {
  todayTotal: number
  pending: number
  critical: number
  handled: number
}

export interface RecordsSearchForm {
  dateRange: [string, string] | null
  warningType: string
  warningLevel: string
  handleStatus: string
  keyword: string
}

export interface RecordsPagination {
  page: number
  size: number
  total: number
}

export interface WarningRecord {
  id: number
  createTime?: string
  userName?: string
  gender?: number
  age?: number
  eventSource?: string
  warningType?: string
  warningValue?: string | number
  warningLevel?: string
  handled?: boolean
  handleBy?: string
  handleTime?: string
  handleNote?: string
  slaStatus?: string
  slaClockLabel?: string
  slaClockText?: string
  [key: string]: unknown
}

export interface RecordsHandleForm {
  handleType: number
  handleNote: string
  notify: boolean
}

export type RecordsCardFilter = '' | 'all' | 'unhandled' | 'critical' | 'handled'
