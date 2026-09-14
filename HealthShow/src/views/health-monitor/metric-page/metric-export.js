import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { exportToExcel } from '@/utils/export-excel'

export async function exportMetricRows({ rows, columns, filenamePrefix, mapRow }) {
  if (!rows.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  const data = rows.map(mapRow)
  await exportToExcel(data, columns, `${filenamePrefix}_${dayjs().format('YYYYMMDD')}`)
  ElMessage.success(`已导出 ${rows.length} 条记录`)
}
