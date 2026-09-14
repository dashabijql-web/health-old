export async function exportHealthPortraitAiPdf({
  getHtml2Canvas,
  getJsPDF,
  selector = '.ai-content',
  portrait,
  empCode,
  generateTime
}) {
  const html2canvas = await getHtml2Canvas()
  const JsPDF = await getJsPDF()
  const el = document.querySelector(selector)
  if (!el) throw new Error('未找到 AI 报告内容区域')

  const canvas = await html2canvas(el, { backgroundColor: '#141830', scale: 2 })
  const imgData = canvas.toDataURL('image/png')
  const pdf = new JsPDF({ orientation: 'p', unit: 'mm', format: 'a4' })
  const pageWidth = pdf.internal.pageSize.getWidth()
  const imgWidth = pageWidth - 20
  const imgHeight = (canvas.height * imgWidth) / canvas.width
  const name = portrait?.empName || empCode

  pdf.setFontSize(14)
  pdf.setTextColor(40, 40, 40)
  pdf.text(`AI 健康分析报告 — ${name}`, 10, 14)
  pdf.setFontSize(9)
  pdf.setTextColor(120, 120, 120)
  pdf.text(`生成时间：${generateTime}    员工编号：${empCode}`, 10, 20)
  pdf.addImage(imgData, 'PNG', 10, 25, imgWidth, imgHeight)
  pdf.save(`AI健康报告_${name}_${String(generateTime || Date.now()).replace(/[: ]/g, '')}.pdf`)
}

export async function exportHealthPortraitCompliancePdf({
  getJsPDF,
  portrait,
  vitals,
  gradeInfo,
  fatigueInfo,
  mentalHealthInfo,
  occRisks,
  warnings,
  aiReportContent
}) {
  const JsPDF = await getJsPDF()
  const pdf = new JsPDF({ orientation: 'p', unit: 'mm', format: 'a4' })
  const pageWidth = pdf.internal.pageSize.getWidth()
  const today = new Date().toLocaleDateString('zh-CN')

  pdf.setFontSize(18)
  pdf.setTextColor(30, 30, 80)
  pdf.text('职业健康档案报告', pageWidth / 2, 20, { align: 'center' })
  pdf.setFontSize(10)
  pdf.setTextColor(100, 100, 100)
  pdf.text(`生成日期：${today}    员工编号：${portrait?.empCode || '--'}`, pageWidth / 2, 28, { align: 'center' })
  pdf.setDrawColor(180, 180, 200)
  pdf.line(15, 32, pageWidth - 15, 32)

  pdf.setFontSize(13)
  pdf.setTextColor(40, 40, 120)
  pdf.text('一、基本信息', 15, 42)
  pdf.setFontSize(10)
  pdf.setTextColor(60, 60, 60)
  const infoRows = [
    [`姓名：${portrait?.empName || '--'}`, `员工编号：${portrait?.empCode || '--'}`],
    [`部门：${portrait?.deptName || '--'}`, `工种：${portrait?.jobTypeName || '--'}`],
    [`性别：${portrait?.gender === 1 ? '男' : portrait?.gender === 2 ? '女' : '--'}`, `血型：${portrait?.bloodType || '--'}`],
    [`身高：${portrait?.height ? `${portrait.height} cm` : '--'}`, `体重：${portrait?.weight ? `${portrait.weight} kg` : '--'}`]
  ]
  infoRows.forEach((row, index) => {
    pdf.text(row[0], 20, 52 + index * 7)
    pdf.text(row[1], pageWidth / 2, 52 + index * 7)
  })

  pdf.setFontSize(13)
  pdf.setTextColor(40, 40, 120)
  pdf.text('二、当前体征', 15, 86)
  pdf.setFontSize(10)
  pdf.setTextColor(60, 60, 60)
  const vitalRows = [
    [`心率：${vitals?.heartRate ?? '--'} bpm`, `血氧：${vitals?.bloodOxygen ?? '--'} %`],
    [`体温：${vitals?.temperature ?? '--'} °C`, `压力指数：${vitals?.pressure ?? '--'}`],
    [`血压（高）：${vitals?.systolic ?? '--'} mmHg`, `血压（低）：${vitals?.diastolic ?? '--'} mmHg`]
  ]
  vitalRows.forEach((row, index) => {
    pdf.text(row[0], 20, 96 + index * 7)
    pdf.text(row[1], pageWidth / 2, 96 + index * 7)
  })

  pdf.setFontSize(13)
  pdf.setTextColor(40, 40, 120)
  pdf.text('三、健康综合评分', 15, 124)
  pdf.setFontSize(10)
  pdf.setTextColor(60, 60, 60)
  pdf.text(`综合等级：${gradeInfo?.grade || '--'}级  评分：${gradeInfo?.score || 0} / 100  评价：${gradeInfo?.label || '--'}`, 20, 134)
  pdf.text(`疲劳指数：${fatigueInfo?.index || 0} / 100  状态：${fatigueInfo?.label || '--'}`, 20, 143)
  pdf.text(`心理健康：${mentalHealthInfo?.score || 0} / 100  状态：${mentalHealthInfo?.level || '--'}`, 20, 152)

  pdf.setFontSize(13)
  pdf.setTextColor(40, 40, 120)
  pdf.text('四、职业病风险评估', 15, 165)
  pdf.setFontSize(10)
  pdf.setTextColor(60, 60, 60)
  ;(occRisks || []).forEach((risk, index) => {
    pdf.text(`${risk.name}：风险分 ${risk.score}  评级：${risk.level}  建议：${risk.tip}`, 20, 175 + index * 8)
  })

  pdf.setFontSize(13)
  pdf.setTextColor(40, 40, 120)
  pdf.text('五、近期预警记录（近30天）', 15, 202)
  pdf.setFontSize(10)
  pdf.setTextColor(60, 60, 60)
  if (!(warnings || []).length) {
    pdf.text('近30天内无预警记录', 20, 212)
  } else {
    warnings.slice(0, 10).forEach((warning, index) => {
      const time = warning.createTime ? new Date(warning.createTime).toLocaleString('zh-CN') : '--'
      pdf.text(`${index + 1}. [${warning.warningLevel || '--'}] ${warning.warningType || '--'} ${warning.warningValue || ''} — ${time}`, 20, 212 + index * 7, { maxWidth: pageWidth - 30 })
    })
  }

  if (aiReportContent) {
    pdf.addPage()
    pdf.setFontSize(13)
    pdf.setTextColor(40, 40, 120)
    pdf.text('六、AI 健康分析报告摘要', 15, 20)
    pdf.setFontSize(9)
    pdf.setTextColor(60, 60, 60)
    const lines = pdf.splitTextToSize(aiReportContent.replace(/#+\s*/g, '').replace(/\*\*/g, ''), pageWidth - 30)
    pdf.text(lines, 15, 30)
  }

  const name = portrait?.empName || portrait?.empCode || 'unknown'
  pdf.save(`职业健康档案_${name}_${today.replace(/\//g, '')}.pdf`)
}
