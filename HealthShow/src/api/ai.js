import request from '@/utils/request'

/**
 * 查询已有缓存的 AI 健康报告（不触发生成）
 */
export function getCachedAiReport(empCode) {
  return request({ url: '/ai/health-report', method: 'get', params: { empCode } })
}

/**
 * 生成 AI 健康报告
 * @param {string} empCode
 * @param {boolean} force - true: 强制刷新忽略缓存
 */
export function generateAiReport(empCode, force = false) {
  return request({ url: '/ai/health-report/generate', method: 'post', params: { empCode, force }, timeout: 150000 })
}

export function getMineAiReport() {
  return request({ url: '/ai/health-report/mine', method: 'get', silentError: true })
}
export function generateMineAiReport(force = false) {
  return request({ url: '/ai/health-report/mine/generate', method: 'post', params: { force }, timeout: 150000 })
}

export function getDeptAiReport(deptName) {
  return request({ url: '/ai/health-report/dept', method: 'get', params: { deptName } })
}
export function generateDeptAiReport(deptName, force = false) {
  return request({ url: '/ai/health-report/dept/generate', method: 'post', params: { deptName, force }, timeout: 150000 })
}

/**
 * 生成员工 AI 健康诊断报告（调用新版 /ai/report/employee 接口）
 * @param {string} empCode 员工工号
 */
export function generateEmployeeReport(empCode) {
  return request({ url: '/ai/report/employee', method: 'post', data: { empCode }, timeout: 120000 })
}

/**
 * 生成部门 AI 健康诊断报告
 * @param {string} deptName 部门名称
 */
export function generateDepartmentReport(deptName) {
  return request({ url: '/ai/report/department', method: 'post', data: { deptName }, timeout: 120000 })
}

/**
 * AI 健康助手对话（Text2SQL RAG，支持多轮对话）
 * @param {string} question   用户的自然语言问题
 * @param {string} sessionId  会话ID，同一会话保持不变
 */
export function aiChat(question, sessionId) {
  return request({ url: '/ai/chat', method: 'post', data: { question, sessionId }, timeout: 60000 })
}

/**
 * 清除 AI 会话历史（开始新对话）
 * @param {string} sessionId
 */
export function clearAiSession(sessionId) {
  return request({ url: `/ai/chat/${sessionId}`, method: 'delete' })
}
