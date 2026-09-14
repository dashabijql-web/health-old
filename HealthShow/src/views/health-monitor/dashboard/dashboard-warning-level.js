export function dashboardWarningLevelLabel(level) {
  return {
    danger: '危险',
    warn: '预警',
    info: '提示'
  }[level] || '提示'
}

export function dashboardWarningLevelBadgeClass(level) {
  return {
    danger: 'badge-danger',
    warn: 'badge-warn',
    info: 'badge-info'
  }[level] || 'badge-info'
}

export function dashboardWarningLevelEventClass(level) {
  return {
    danger: 'ev-danger',
    warn: 'ev-warn',
    info: 'ev-info'
  }[level] || 'ev-info'
}

export function dashboardWarningLevelMarkerColor(level) {
  return {
    danger: '#ff3b3b',
    warn: '#ff8c00',
    info: '#00c8ff'
  }[level] || '#00c8ff'
}

export function dashboardWarningLevelTagType(level) {
  return {
    danger: 'danger',
    warn: 'warning',
    info: 'info'
  }[level] || 'info'
}

export function dashboardWarningLevelDrawerClass(level) {
  return {
    danger: 'lv-danger',
    warn: 'lv-warn',
    info: 'lv-info'
  }[level] || 'lv-info'
}
