const typeMap = {
  SOS: { l: 'SOS求助', t: 'danger' },
  fall: { l: '跌倒', t: 'warning' },
  heartRate: { l: '心率异常', t: 'primary' },
  bloodOxygen: { l: '血氧异常', t: 'info' },
  temperature: { l: '体温异常', t: 'warning' },
  bloodPressure: { l: '血压偏高', t: 'danger' },
  pressure: { l: '压力偏高', t: 'warning' },
  staticAlert: { l: '静态预警', t: 'info' }
}

const levelMap = {
  高危: { t: 'danger' },
  中危: { t: 'warning' },
  低危: { t: 'info' },
  危险: { t: 'danger' },
  预警: { t: 'warning' },
  提示: { t: 'info' }
}

export function createRecordsOverview() {
  return { todayTotal: 0, pending: 0, critical: 0, handled: 0 }
}

export function createRecordsSearchForm() {
  return { dateRange: null, warningType: '', warningLevel: '', handleStatus: 'handled', keyword: '' }
}

export function createRecordsPagination() {
  return { page: 1, size: 20, total: 0 }
}

export function createRecordsHandleForm() {
  return { handleType: 1, handleNote: '', notify: false }
}

export function typeLabel(type) {
  return typeMap[type]?.l || type || '-'
}

export function typeTag(type) {
  return typeMap[type]?.t || 'primary'
}

export function levelTag(level) {
  return levelMap[level]?.t || 'primary'
}

export function buildRecordsQuery(searchForm, pagination, { includePage = true } = {}) {
  const query = includePage ? { page: pagination.page, size: pagination.size } : { page: 1, size: 5000 }
  if (searchForm.dateRange?.length === 2) {
    query.startDate = searchForm.dateRange[0]
    query.endDate = searchForm.dateRange[1]
  }
  if (searchForm.warningType) query.warningType = searchForm.warningType
  if (searchForm.warningLevel) query.level = searchForm.warningLevel
  if (searchForm.handleStatus === 'handled') query.handled = true
  if (searchForm.handleStatus === 'unhandled') query.handled = false
  if (searchForm.keyword) query.userCode = searchForm.keyword
  return query
}

export function buildExportRows(rows, { formatDate, levelLabel, warningHandledStatusLabel }) {
  return rows.map((row) => ({
    预警时间: formatDate(row.createTime),
    姓名: row.userName || '-',
    性别: row.gender === 1 ? '男' : row.gender === 2 ? '女' : '-',
    年龄: row.age ?? '-',
    事件来源: sourceLabel(row.eventSource),
    预警类型: typeLabel(row.warningType),
    预警值: row.warningValue || '-',
    预警级别: levelLabel(row.warningLevel),
    处理状态: warningHandledStatusLabel(row),
    处理人: row.handleBy || '-',
    处理备注: row.handleNote || '-'
  }))
}

export function sourceLabel(source) {
  return { HEALTH_THRESHOLD: '体征预警', DEVICE_ALARM: '设备报警', TREND_WARNING: '趋势风险' }[source] || '历史事件'
}

export const exportColumns = [
  { label: '预警时间', key: '预警时间' },
  { label: '姓名', key: '姓名' },
  { label: '性别', key: '性别' },
  { label: '年龄', key: '年龄' },
  { label: '事件来源', key: '事件来源' },
  { label: '预警类型', key: '预警类型' },
  { label: '预警值', key: '预警值' },
  { label: '预警级别', key: '预警级别' },
  { label: '处理状态', key: '处理状态' },
  { label: '处理人', key: '处理人' },
  { label: '处理备注', key: '处理备注' }
]
