import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from '@/utils/echarts-setup-radar'
import { getHtml2Canvas, getJsPDF } from '@/utils/lazy-vendors'
import { useClock } from '@/composables/useClock'
import { useIntervalTask } from '@/composables/useIntervalTask'
import { useScrollLoop } from '@/composables/useScrollLoop'
import { useTimeoutTask } from '@/composables/useTimeoutTask'
import { createEventBinding } from '@/utils/task-timer'
import {
  buildFatigueInfo,
  buildGradeInfo,
  buildHeatmapCells,
  buildMentalHealthInfo,
  buildMineAvgCompare,
  buildNextCheckDate,
  buildOccRisks,
  buildRenderedReport,
  buildRiskLevels,
  buildScorePills,
  buildWeekStats,
  formatPortraitWarningTime,
  resolvePortraitLevelLabel,
  resolvePortraitLevelTagType,
  resolvePressureStatus,
  resolveVitalStatus,
  resolveVitalStatusText
} from './health-portrait-view-model'
import {
  disposePortraitChart,
  renderMetricAbnormalTrend,
  renderPortraitRadarChart,
  renderPortraitTrendChart
} from './health-portrait-chart'
import {
  exportHealthPortraitAiPdf,
  exportHealthPortraitCompliancePdf
} from './health-portrait-export'
import {
  fetchHealthPortrait,
  fetchMetricAbnormalRecords,
  fetchHealthPortraitMineAvg,
  generateHealthPortraitAiReport,
  loadHealthPortraitAiReport
} from './health-portrait-runtime'
import {
  applyHealthPortraitAiReport,
  buildHealthPortraitRealtimeRoute,
  createEmptyHealthScores,
  createEmptyHourlyHrData,
  createEmptyMineAvgData,
  createEmptyPortrait,
  createEmptyTrendData,
  createEmptyVitals,
  loadHealthPortraitHistory,
  resetHealthPortraitState,
  restoreHealthPortraitHistory,
  saveHealthPortraitHistory
} from './health-portrait-page-state'

const METRIC_DRILLDOWN_CONFIG = {
  'heartRate-abnormal': {
    path: 'heart-rate', label: '心率', primaryLabel: '心率', unit: 'bpm', padding: 5,
    markLines: [{ value: 55, label: '偏低', color: '#4fc3f7' }, { value: 120, label: '偏高', color: '#ffb84d' }]
  },
  'pressure-abnormal': {
    path: 'pressure', label: '压力', primaryLabel: '压力指数', unit: '', padding: 5,
    markLines: [{ value: 70, label: '偏高', color: '#ffb84d' }, { value: 85, label: '高压', color: '#ff7070' }]
  },
  'bloodPressure-abnormal': {
    path: 'blood-pressure', label: '血压', primaryLabel: '收缩压', secondaryLabel: '舒张压', unit: 'mmHg', dual: true, padding: 8,
    markLines: [{ value: 120, label: '收缩压偏高', color: '#ffb84d' }, { value: 160, label: '收缩压高危', color: '#ff7070' }]
  },
  'bloodOxygen-abnormal': {
    path: 'blood-oxygen', label: '血氧', primaryLabel: '血氧', unit: '%', padding: 2,
    markLines: [{ value: 90, label: '危险', color: '#ff7070' }, { value: 95, label: '正常下限', color: '#4fc3f7' }]
  }
}

export function useHealthPortraitPage() {
  const route = useRoute()
  const router = useRouter()
  const { currentTime } = useClock('YYYY/MM/DD HH:mm:ss')

  const empCode = ref('')
  const firstLoading = ref(false)

  const portrait = reactive(createEmptyPortrait())
  const vitals = reactive(createEmptyVitals())
  const trendData = ref(createEmptyTrendData())
  const hourlyHrData = ref(createEmptyHourlyHrData())
  const healthScores = ref(createEmptyHealthScores())
  const warnings = ref([])
  const metricAbnormalRows = ref([])
  const abnormalRecordTotal = ref(0)
  const abnormalRecordPage = ref(1)
  const abnormalRecordSize = 20
  const mineAvgData = ref(createEmptyMineAvgData())

  const trendChartRef = ref(null)
  const radarChartRef = ref(null)
  const warnPanelRef = ref(null)
  let trendChart = null
  let radarChart = null

  const aiReport = reactive({ content: '', generateTime: '', expiresAt: '' })
  const aiLoading = ref(false)
  const reportHistory = ref([])
  const histExpanded = ref(false)
  const pdfExporting = ref(false)
  const complianceExporting = ref(false)

  const warnScrollLoop = useScrollLoop({
    getElement: () => warnPanelRef.value?.querySelector('.el-scrollbar__wrap'),
    shouldScroll: () => !METRIC_DRILLDOWN_CONFIG[String(route.query.focus || '')] && warnings.value.length > 0,
    intervalMs: 40,
    step: 1,
    endPauseMs: 1500
  })

  const { start: startWheelResume, stop: stopWheelResume } = useTimeoutTask(() => {
    warnScrollLoop.resume()
  }, 2000)

  const { start: startReportRetry, stop: stopReportRetry } = useTimeoutTask(async () => {
    const reportData = await loadHealthPortraitAiReport(empCode.value)
    if (!reportData) return
    applyHealthPortraitAiReport(aiReport, reportData)
    saveToHistory(aiReport)
    ElMessage.success('AI 报告已就绪')
  }, 30000)

  const gradeInfo = computed(() => buildGradeInfo(healthScores.value))
  const nextCheckDate = computed(() => buildNextCheckDate(warnings.value))
  const weekStats = computed(() => buildWeekStats(trendData.value, vitals))
  const riskLevels = computed(() => buildRiskLevels(warnings.value))
  const heatmapCells = computed(() => buildHeatmapCells(hourlyHrData.value))
  const fatigueInfo = computed(() => buildFatigueInfo(vitals, warnings.value))
  const occRisks = computed(() => buildOccRisks({
    deptName: portrait.deptName,
    jobTypeName: portrait.jobTypeName
  }, vitals, warnings.value))
  const mentalHealthInfo = computed(() => buildMentalHealthInfo(vitals, warnings.value))
  const mineAvgCompare = computed(() => buildMineAvgCompare(vitals, mineAvgData.value))
  const renderedReport = computed(() => buildRenderedReport(aiReport.content))
  const scorePills = computed(() => buildScorePills(healthScores.value))
  const metricDrilldownConfig = computed(() => METRIC_DRILLDOWN_CONFIG[String(route.query.focus || '')] || null)
  const metricDrilldown = computed(() => Boolean(metricDrilldownConfig.value))
  const drilldownStartDate = computed(() => String(route.query.startDate || ''))
  const drilldownEndDate = computed(() => String(route.query.endDate || ''))
  const trendPanelTitle = computed(() => metricDrilldown.value ? `周期异常${metricDrilldownConfig.value.label}趋势（当前页）` : '7天趋势')
  const warningPanelTitle = computed(() => metricDrilldown.value ? `周期${metricDrilldownConfig.value.label}异常记录` : '近30天预警记录')
  const abnormalValueTitle = computed(() => metricDrilldownConfig.value?.primaryLabel || '指标')
  const abnormalValueUnit = computed(() => metricDrilldownConfig.value?.unit || '')
  const abnormalDualValue = computed(() => Boolean(metricDrilldownConfig.value?.dual))

  function syncEmpCodeFromRoute() {
    empCode.value = route.query.empCode ? String(route.query.empCode) : ''
  }

  function stopPortraitCharts() {
    trendChart = disposePortraitChart(trendChart)
    radarChart = disposePortraitChart(radarChart)
  }

  function resetPortraitPageState() {
    resetHealthPortraitState({
      portrait,
      vitals,
      trendData,
      hourlyHrData,
      healthScores,
      warnings,
      aiReport,
      reportHistory,
      histExpanded,
      mineAvgData
    })
    metricAbnormalRows.value = []
    abnormalRecordTotal.value = 0
    abnormalRecordPage.value = 1
    warnScrollLoop.stop()
    stopWheelResume()
    stopReportRetry()
    stopPortraitCharts()
  }

  function goRealtime() {
    router.push(buildHealthPortraitRealtimeRoute(portrait))
  }

  function goEmployeeList() {
    router.push('/personnel-management/employee')
  }

  function goBack() {
    router.back()
  }

  function pauseWarnAutoScroll() {
    warnScrollLoop.pause()
  }

  function resumeWarnAutoScroll() {
    warnScrollLoop.resume()
  }

  function onWarnWheel() {
    warnScrollLoop.pause()
    startWheelResume()
  }

  function loadHistory() {
    reportHistory.value = loadHealthPortraitHistory(empCode.value)
  }

  function saveToHistory(report) {
    reportHistory.value = saveHealthPortraitHistory(reportHistory.value, empCode.value, report)
  }

  function restoreHistory(historyItem) {
    restoreHealthPortraitHistory(aiReport, historyItem)
  }

  async function loadMineAvg() {
    const data = await fetchHealthPortraitMineAvg()
    if (data) mineAvgData.value = data
  }

  async function exportPdf() {
    if (pdfExporting.value) return
    pdfExporting.value = true
    ElMessage.info('正在生成 PDF，请稍候…')
    try {
      await exportHealthPortraitAiPdf({
        getHtml2Canvas,
        getJsPDF,
        portrait,
        empCode: empCode.value,
        generateTime: aiReport.generateTime
      })
      ElMessage.success('PDF 导出成功')
    } catch (error) {
      ElMessage.error(`PDF 生成失败：${error.message}`)
    } finally {
      pdfExporting.value = false
    }
  }

  async function exportComplianceReport() {
    if (complianceExporting.value) return
    complianceExporting.value = true
    try {
      await exportHealthPortraitCompliancePdf({
        getJsPDF,
        portrait,
        vitals,
        gradeInfo: gradeInfo.value,
        fatigueInfo: fatigueInfo.value,
        mentalHealthInfo: mentalHealthInfo.value,
        occRisks: occRisks.value,
        warnings: warnings.value,
        aiReportContent: aiReport.content
      })
      ElMessage.success('职业健康档案导出成功')
    } catch (error) {
      ElMessage.error(`导出失败：${error.message}`)
    } finally {
      complianceExporting.value = false
    }
  }

  async function loadCachedReport() {
    const data = await loadHealthPortraitAiReport(empCode.value)
    if (!data) return
    applyHealthPortraitAiReport(aiReport, data)
  }

  async function handleGenerateReport(force = false) {
    if (aiLoading.value) return
    aiLoading.value = true
    try {
      const result = await generateHealthPortraitAiReport(empCode.value, force)
      if (result.ok && result.data) {
        applyHealthPortraitAiReport(aiReport, result.data)
        saveToHistory(aiReport)
        ElMessage.success('AI 报告生成成功')
        return
      }

      if (result.message?.includes('后台仍在生成')) {
        ElMessage.warning(result.message)
        startReportRetry()
        return
      }

      ElMessage.error(result.message || 'AI 分析失败')
    } finally {
      aiLoading.value = false
    }
  }

  const fmtTime = (row, col, val) => formatPortraitWarningTime(val)
  const levelTagType = (level) => resolvePortraitLevelTagType(level)
  const levelLabel = (level) => resolvePortraitLevelLabel(level)
  const vitalStatus = (val, min, max) => resolveVitalStatus(val, min, max)
  const pressureStatus = (val) => resolvePressureStatus(val)
  const vitalStatusText = (val, min, max) => resolveVitalStatusText(val, min, max)

  async function fetchPortrait(isFirstLoad = false) {
    if (!empCode.value) return
    if (isFirstLoad) firstLoading.value = true

    try {
      const snapshot = await fetchHealthPortrait(empCode.value)
      if (!snapshot) return

      Object.assign(portrait, snapshot.portrait)
      Object.assign(vitals, snapshot.vitals)
      trendData.value = snapshot.trendData
      hourlyHrData.value = snapshot.hourlyHrData
      healthScores.value = snapshot.healthScores
      warnings.value = snapshot.warnings

      if (metricDrilldown.value) await loadMetricAbnormalRecords(abnormalRecordPage.value)

      await nextTick()
      trendChart = metricDrilldown.value
        ? renderMetricAbnormalTrend({
            echarts,
            element: trendChartRef.value,
            chart: trendChart,
            rows: metricAbnormalRows.value,
            startDate: drilldownStartDate.value,
            endDate: drilldownEndDate.value,
            config: metricDrilldownConfig.value
          })
        : renderPortraitTrendChart({
            echarts,
            element: trendChartRef.value,
            chart: trendChart,
            trendData: trendData.value
          })
      radarChart = renderPortraitRadarChart({
        echarts,
        element: radarChartRef.value,
        chart: radarChart,
        healthScores: healthScores.value
      })

      if (isFirstLoad) warnScrollLoop.start()
    } catch {
      if (isFirstLoad) ElMessage.error('加载健康画像失败')
    } finally {
      firstLoading.value = false
    }
  }

  async function loadMetricAbnormalRecords(page = abnormalRecordPage.value) {
    if (!metricDrilldown.value || !empCode.value || !drilldownStartDate.value || !drilldownEndDate.value) return
    const data = await fetchMetricAbnormalRecords(metricDrilldownConfig.value.path, {
      userCode: empCode.value,
      startDate: drilldownStartDate.value,
      endDate: drilldownEndDate.value,
      page,
      size: abnormalRecordSize
    })
    metricAbnormalRows.value = data?.list || []
    abnormalRecordTotal.value = data?.total || 0
    abnormalRecordPage.value = data?.page || page
  }

  async function changeAbnormalRecordPage(page) {
    await loadMetricAbnormalRecords(page)
    await nextTick()
    trendChart = renderMetricAbnormalTrend({
      echarts,
      element: trendChartRef.value,
      chart: trendChart,
      rows: metricAbnormalRows.value,
      startDate: drilldownStartDate.value,
      endDate: drilldownEndDate.value,
      config: metricDrilldownConfig.value
    })
  }

  function handleResize() {
    trendChart?.resize()
    radarChart?.resize()
  }

  const { start: startPortraitPolling, stop: stopPortraitPolling } = useIntervalTask(() => {
    fetchPortrait(false)
  }, 10000)
  let resizeBinding = null

  async function loadPortraitPage(isFirstLoad = false) {
    syncEmpCodeFromRoute()
    resetPortraitPageState()
    if (!empCode.value) return

    void loadCachedReport()
    loadHistory()
    void loadMineAvg()
    await fetchPortrait(isFirstLoad)
  }

  onMounted(async () => {
    await loadPortraitPage(true)
    resizeBinding = createEventBinding(() => window, 'resize', handleResize)
    resizeBinding.start()
    startPortraitPolling()
  })

  watch(() => route.fullPath, () => {
    if (route.name !== 'HealthPortrait') return
    void loadPortraitPage(true)
  })

  onBeforeUnmount(() => {
    stopPortraitPolling()
    warnScrollLoop.stop()
    stopWheelResume()
    stopReportRetry()
    resizeBinding?.stop?.()
    stopPortraitCharts()
  })

  return {
    aiLoading,
    aiReport,
    complianceExporting,
    currentTime,
    empCode,
    exportComplianceReport,
    exportPdf,
    fatigueInfo,
    firstLoading,
    fmtTime,
    goBack,
    goEmployeeList,
    goRealtime,
    gradeInfo,
    handleGenerateReport,
    heatmapCells,
    abnormalRecordPage,
    abnormalRecordSize,
    abnormalRecordTotal,
    changeAbnormalRecordPage,
    metricAbnormalRows,
    metricDrilldown,
    metricDrilldownConfig,
    abnormalValueTitle,
    abnormalValueUnit,
    abnormalDualValue,
    histExpanded,
    levelLabel,
    levelTagType,
    mentalHealthInfo,
    mineAvgCompare,
    occRisks,
    onWarnWheel,
    pauseWarnAutoScroll,
    pdfExporting,
    portrait,
    pressureStatus,
    radarChartRef,
    renderedReport,
    reportHistory,
    restoreHistory,
    resumeWarnAutoScroll,
    riskLevels,
    scorePills,
    trendChartRef,
    trendPanelTitle,
    vitals,
    vitalStatus,
    vitalStatusText,
    warnings,
    warningPanelTitle,
    warnPanelRef,
    weekStats,
    nextCheckDate
  }
}
