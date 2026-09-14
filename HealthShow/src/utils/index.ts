/**
 * 格式化日期为中文本地化字符串
 * @param {string|number|Date} date - 日期值
 * @returns {string} 格式化后的日期字符串，如 "2026/3/13 14:30:00"
 */
export function formatDate(date: string | number | Date | null | undefined): string {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}
