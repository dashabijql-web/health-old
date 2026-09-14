export function buildEmpInfoFromRoute(route) {
  return {
    empCode: route.query.empCode || '',
    empName: route.query.empName || '',
    gender: Number(route.query.gender) || 1,
    birthDate: route.query.birthDate || '',
    deptName: route.query.deptName || '',
    jobTypeName: route.query.jobTypeName || '',
    phone: route.query.phone || '',
    imei: route.query.imei || ''
  }
}

export const fmtTime = (t) => !t ? '' : String(t).length > 16 ? String(t).substring(5, 16) : String(t)
export const calcAge = (birthDate) => {
  if (!birthDate) return '--'
  const age = new Date().getFullYear() - new Date(birthDate).getFullYear()
  return age > 0 && age < 100 ? `${age}岁` : '--'
}

export function buildProfileInsightLines({ pendCount, warnCount, warn7Count }) {
  const lines = []

  if (pendCount > 0) {
    lines.push(`近期仍有 ${pendCount} 条未处理预警，建议先核查最新异常记录与现场处置状态。`)
  } else if (warnCount > 0) {
    lines.push(`近30日共记录 ${warnCount} 次预警，但当前均已处理，可重点回看高频异常类型。`)
  } else {
    lines.push('当前未发现近30日预警记录，人员近期健康状态整体稳定。')
  }

  if (warn7Count >= 3) {
    lines.push(`近7日预警达到 ${warn7Count} 次，建议结合趋势和具体预警记录复盘连续异常时段。`)
  } else {
    lines.push('当前规则提示只依据权威预警数量，不对原始读数生成医学风险评分。')
  }

  return lines
}

export function buildProfileSummaryCards({ pendCount, warnCount }) {
  return [
    {
      key: 'warning-closure',
      label: '预警闭环',
      value: `${pendCount} / ${warnCount}`,
      sub: warnCount ? '待处理 / 近30日总预警 · 点击查看明细' : '近30日暂无预警记录 · 点击查看明细',
      tone: pendCount > 0 ? 'danger' : warnCount > 0 ? 'info' : 'safe',
      clickable: true
    }
  ]
}
