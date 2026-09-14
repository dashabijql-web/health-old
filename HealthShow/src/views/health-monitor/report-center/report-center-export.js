import { fmtReportMetric1 } from './report-center-view-model'

function firstValue(row, keys, fallback = null) {
  for (const key of keys) {
    const value = row?.[key]
    if (value !== undefined && value !== null && value !== '') {
      return value
    }
  }
  return fallback
}

export async function exportReportCenterExcel({
  getXLSX,
  selectedMonth,
  monthlySummary,
  deptSummary,
  trendData
}) {
  const XLSX = await getXLSX()
  const workbook = XLSX.utils.book_new()

  if (monthlySummary.length) {
    const monthlyData = monthlySummary.map((row) => ({
      姓名: firstValue(row, ['empName', 'emp_name'], '--'),
      部门: firstValue(row, ['deptName', 'dept_name'], '--'),
      记录条数: firstValue(row, ['recordCount', 'record_count'], 0),
      心率均值: fmtReportMetric1(firstValue(row, ['avgHeartRate', 'avg_heart_rate'])),
      血氧均值百分比: fmtReportMetric1(firstValue(row, ['avgBloodOxygen', 'avg_blood_oxygen'])),
      体温均值: fmtReportMetric1(firstValue(row, ['avgTemperature', 'avg_temperature'])),
      健康评分: firstValue(row, ['healthScore', 'health_score'], '--')
    }))
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(monthlyData), `${selectedMonth}员工月报`)
  }

  if (deptSummary.length) {
    const deptData = deptSummary.map((row) => ({
      部门: firstValue(row, ['deptName', 'dept_name'], '--'),
      在册人数: firstValue(row, ['employeeCount', 'employee_count'], 0),
      近30天预警次数: firstValue(row, ['warningCount', 'warning_count'], 0)
    }))
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(deptData), '部门对比')
  }

  if (trendData.length) {
    const trendSheet = trendData.map((row) => ({
      日期: row.date,
      心率异常率百分比: row.heartRateRate ?? row.heartRateAbnormalRate ?? '',
      血氧异常率百分比: row.bloodOxygenRate ?? row.bloodOxygenAbnormalRate ?? '',
      体温异常率百分比: row.temperatureRate ?? row.temperatureAbnormalRate ?? '',
      压力异常率百分比: row.pressureRate ?? row.pressureAbnormalRate ?? ''
    }))
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(trendSheet), '健康趋势')
  }

  XLSX.writeFile(workbook, `健康报表_${selectedMonth}.xlsx`)
}

export async function exportReportCenterPdf({
  getHtml2Canvas,
  getJsPDF,
  element,
  selectedMonth
}) {
  if (!element) {
    throw new Error('报表区域不存在')
  }

  const html2canvas = await getHtml2Canvas()
  const JsPDF = await getJsPDF()
  const canvas = await html2canvas(element, {
    scale: 1.5,
    useCORS: true,
    backgroundColor: '#0a1628',
    logging: false
  })

  const imageWidth = canvas.width / 1.5
  const imageHeight = canvas.height / 1.5
  const pdf = new JsPDF({
    orientation: imageHeight > imageWidth ? 'p' : 'l',
    unit: 'mm',
    format: [imageWidth * 0.264583, imageHeight * 0.264583]
  })
  pdf.addImage(
    canvas.toDataURL('image/png'),
    'PNG',
    0,
    0,
    imageWidth * 0.264583,
    imageHeight * 0.264583
  )
  pdf.save(`健康报表_${selectedMonth}.pdf`)
}
