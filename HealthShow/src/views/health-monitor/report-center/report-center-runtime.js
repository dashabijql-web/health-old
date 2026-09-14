import dayjs from 'dayjs'
import request from '@/utils/request'

export function createReportCenterState() {
  return {
    loading: false,
    exporting: false,
    exportingPdf: false,
    activeTab: 'monthly',
    selectedMonth: dayjs().format('YYYY-MM'),
    trendDays: 30,
    monthlySummary: [],
    deptSummary: [],
    dailyCounts: [],
    warnTypes: [],
    trendData: [],
    charts: {}
  }
}

async function requestList(config) {
  try {
    const response = await request(config)
    return response.code === 200 && Array.isArray(response.data) ? response.data : []
  } catch {
    return []
  }
}

export function fetchMonthlySummary(selectedMonth) {
  return requestList({
    url: '/statistics/monthly-summary',
    method: 'get',
    params: { month: selectedMonth }
  })
}

export function fetchDailyCounts(selectedMonth) {
  return requestList({
    url: '/statistics/daily-counts',
    method: 'get',
    params: { month: selectedMonth }
  })
}

export function fetchWarnTypes(selectedMonth) {
  return requestList({
    url: '/statistics/warning-types',
    method: 'get',
    params: { month: selectedMonth }
  })
}

export function fetchDeptSummary() {
  return requestList({
    url: '/statistics/dept-summary',
    method: 'get'
  })
}

export function fetchTrendData(trendDays) {
  return requestList({
    url: '/dashboard/daily-trend',
    method: 'get',
    params: { days: trendDays }
  })
}
