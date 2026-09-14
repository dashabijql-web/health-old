export const DEFAULT_QUICK_QUESTIONS = [
  '本周有多少人心率异常？',
  '各部门平均血氧对比',
  '睡眠不足的员工有哪些？',
  '今天有哪些预警未处理？'
]

const DEPARTMENT_QUESTION_TEMPLATES = [
  (department) => `${department.deptName}平均心率是多少？`,
  (department) => `${department.deptName}血氧正常率如何？`,
  (department) => `${department.deptName}有多少人压力偏高？`,
  (department) => `${department.deptName}本周有哪些预警？`
]

export function generateSessionId() {
  return 'sess-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
}

export function buildDynamicQuickQuestions(departments) {
  const depts = Array.isArray(departments) ? departments.filter(item => item?.deptName) : []
  if (!depts.length) return null

  const shuffled = [...depts].sort(() => Math.random() - 0.5)
  const picked = shuffled.slice(0, 2)
  const deptQuestions = picked.map((department, index) => {
    const offset = Math.floor(Math.random() * DEPARTMENT_QUESTION_TEMPLATES.length)
    return DEPARTMENT_QUESTION_TEMPLATES[(index + offset) % DEPARTMENT_QUESTION_TEMPLATES.length](department)
  })

  return [
    ...deptQuestions,
    '睡眠不足的员工有哪些？',
    '各部门平均血氧排名'
  ]
}
