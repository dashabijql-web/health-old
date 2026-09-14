export function decodeBase64Utf8(base64Text) {
  const binary = atob(base64Text)
  const bytes = Uint8Array.from(binary, ch => ch.charCodeAt(0))
  return new TextDecoder('utf-8').decode(bytes)
}

export function normalizeQueryPayload(payload) {
  if (Array.isArray(payload)) {
    return {
      columns: inferColumns(payload),
      rows: payload,
      rowCount: payload.length
    }
  }

  const rows = Array.isArray(payload?.rows) ? payload.rows : []
  return {
    columns: Array.isArray(payload?.columns) && payload.columns.length > 0
      ? payload.columns
      : inferColumns(rows),
    rows,
    rowCount: Number.isFinite(payload?.rowCount) ? payload.rowCount : rows.length
  }
}

export function inferColumns(rows) {
  const seen = new Set()
  rows.forEach(row => {
    Object.keys(row || {}).forEach(key => seen.add(key))
  })
  return Array.from(seen)
}

export function getQueryColumns(msg) {
  return msg.queryColumns && msg.queryColumns.length > 0
    ? msg.queryColumns
    : inferColumns(msg.queryData || [])
}

export function getVizType(data) {
  if (!data || data.length === 0) return 'none'
  const keys = Object.keys(data[0])
  if (keys.length === 0) return 'none'

  const timeKeys = ['record_time', 'create_time', 'update_time', 'warning_time', 'date', 'month', 'week', 'day']
  const hasTime = keys.some(k => timeKeys.some(t => k.toLowerCase().includes(t)))
  const numCols = keys.filter(k => typeof data[0][k] === 'number' && isFinite(data[0][k]))
  const textCols = keys.filter(k => typeof data[0][k] === 'string')

  if (data.length < 2) return 'none'
  if (hasTime && numCols.length >= 1) return 'line'
  if (textCols.length >= 1 && numCols.length >= 2) return 'table'
  if (textCols.length >= 1 && numCols.length === 1) return 'bar'
  if (data.length >= 2 && keys.length >= 2) return 'table'
  return 'none'
}

export function formatCell(val) {
  if (typeof val === 'number' && !isFinite(val)) return '-'
  if (typeof val === 'number') return Number.isInteger(val) ? val : val.toFixed(2)
  if (val === null || val === undefined) return '-'
  return val
}
