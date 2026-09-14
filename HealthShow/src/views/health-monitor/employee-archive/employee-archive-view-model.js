export function calcEmployeeArchiveAge(birthDate) {
  if (!birthDate) return '--'
  const age = new Date().getFullYear() - new Date(birthDate).getFullYear()
  return age > 0 && age < 100 ? `${age}岁` : '--'
}

export function resolveEmployeeHealthBarColor(score) {
  if (score >= 80) return '#00e676'
  if (score >= 60) return '#ffaa00'
  return '#ff3b3b'
}

export function buildEmployeeArchiveDeptList(employees) {
  const deptSet = new Set()
  employees.forEach((employee) => {
    if (employee.deptName) {
      deptSet.add(employee.deptName)
    }
  })
  return [...deptSet].sort()
}

export function buildFilteredEmployees(employees, keyword, selectedDept) {
  let list = employees
  if (keyword.trim()) {
    const query = keyword.trim().toLowerCase()
    list = list.filter((employee) => {
      return (employee.empName || '').toLowerCase().includes(query)
        || (employee.empCode || '').toLowerCase().includes(query)
    })
  }

  if (selectedDept) {
    list = list.filter((employee) => employee.deptName === selectedDept)
  }

  return list
}

export function paginateEmployeeArchiveEmployees(employees, currentPage, pageSize) {
  const start = (currentPage - 1) * pageSize
  return employees.slice(start, start + pageSize)
}

export function createEmptyEmployeeArchiveForm() {
  return {
    id: null,
    empName: '',
    empCode: '',
    gender: 1,
    phone: '',
    deptId: null,
    jobTypeId: null,
    birthDate: '',
    hireDate: '',
    height: null,
    weight: null,
    bloodType: '',
    emergencyContact: '',
    emergencyPhone: '',
    medicalHistory: [],
    statusBool: true
  }
}

export function applyEmployeeToArchiveForm(form, employee) {
  Object.assign(form, {
    id: employee.id,
    empName: employee.empName || '',
    empCode: employee.empCode || '',
    gender: employee.gender ?? 1,
    phone: employee.phone || '',
    deptId: employee.deptId || null,
    jobTypeId: employee.jobTypeId || null,
    birthDate: employee.birthDate || '',
    hireDate: employee.hireDate || '',
    height: employee.height || null,
    weight: employee.weight || null,
    bloodType: employee.bloodType || '',
    emergencyContact: employee.emergencyContact || '',
    emergencyPhone: employee.emergencyPhone || '',
    medicalHistory: employee.medicalHistory
      ? (Array.isArray(employee.medicalHistory)
          ? employee.medicalHistory
          : employee.medicalHistory.split(',').filter(Boolean))
      : [],
    statusBool: employee.status === 0
  })
}

export function buildEmployeeArchiveSubmitPayload(form) {
  const payload = {
    ...form,
    status: form.statusBool ? 0 : 1
  }
  delete payload.statusBool
  if (Array.isArray(payload.medicalHistory)) {
    payload.medicalHistory = payload.medicalHistory.join(',')
  }
  return payload
}

export function buildEmployeeArchivePortraitRoute(employee) {
  return {
    path: '/health-monitor/employee-profile',
    query: {
      empCode: employee.empCode || '',
      empName: employee.empName || '',
      gender: employee.gender ?? 1,
      birthDate: employee.birthDate || '',
      deptName: employee.deptName || '',
      jobTypeName: employee.jobTypeName || '',
      phone: employee.phone || ''
    }
  }
}

export function buildEmployeeArchivePrintHtml({ title, bodyHtml, generatedAt }) {
  return `<!DOCTYPE html>
<html lang="zh-CN"><head>
<meta charset="UTF-8">
<title>${title}</title>
<style>
  body { font-family: 'Microsoft YaHei', sans-serif; max-width: 800px; margin: 0 auto; padding: 24px; color: #1a1a2e; }
  h1,h2,h3 { color: #0066cc; }
  h2 { border-bottom: 1px solid #dde; padding-bottom: 6px; margin-top: 24px; }
  table { border-collapse: collapse; width: 100%; margin: 12px 0; }
  th { background: #e8f0ff; color: #003399; padding: 8px 12px; text-align: left; }
  td { padding: 7px 12px; border-bottom: 1px solid #eee; }
  blockquote { border-left: 4px solid #0066cc; margin: 12px 0; padding: 8px 16px; background: #f5f8ff; color: #444; }
  .meta { font-size: 12px; color: #888; margin-bottom: 20px; }
  @media print { body { padding: 0; } }
</style>
</head><body>
<div class="meta">生成时间：${generatedAt}</div>
${bodyHtml}
</body></html>`
}
