/**
 * Excel 导出工具
 * 基于按需加载的 xlsx 库，将数组数据导出为 .xlsx 文件
 */
import { getXLSX } from '@/utils/lazy-vendors'

export interface ExcelColumn {
  label: string
  key: string
}

/**
 * 导出数据为 Excel 文件
 * @param {Array} data     行数据数组，每项为对象
 * @param {Array} columns  列定义 [{ label: '显示名', key: '字段名' }, ...]
 * @param {String} filename 文件名（不含扩展名）
 */
export async function exportToExcel(
  data: Record<string, unknown>[],
  columns: ExcelColumn[],
  filename = '导出数据'
): Promise<void> {
  if (!data || !data.length) {
    alert('暂无数据可导出')
    return
  }

  const XLSX = await getXLSX()

  // 构建表头行
  const header = columns.map(c => c.label)

  // 构建数据行
  const rows = data.map(row =>
    columns.map(c => {
      const val = row[c.key]
      return val !== undefined && val !== null ? val : ''
    })
  )

  // 合并成 sheet 数据（第一行为表头）
  const sheetData = [header, ...rows]

  const ws = XLSX.utils.aoa_to_sheet(sheetData)

  // 自动列宽
  const colWidths = columns.map((c, i) => {
    const maxLen = Math.max(
      c.label.length * 2,
      ...rows.map(r => String(r[i] || '').length)
    )
    return { wch: Math.min(Math.max(maxLen, 8), 30) }
  })
  ws['!cols'] = colWidths

  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, 'Sheet1')
  XLSX.writeFile(wb, `${filename}_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.xlsx`)
}
