import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { getHtml2Canvas, getJsPDF, getXLSX } from '@/utils/lazy-vendors'
import { useTimeoutTask } from '@/composables/useTimeoutTask'
import {
  disposeReportCenterCharts,
  initReportCenterCharts,
  resizeReportCenterCharts
} from './report-center-chart'
import {
  fetchDailyCounts,
  fetchDeptSummary,
  fetchMonthlySummary,
  fetchTrendData,
  fetchWarnTypes
} from './report-center-runtime'
import {
  buildMonthlyKpis,
  buildReportExportState,
  buildReportInsightLines,
  buildReportOverviewCards,
  fmtReportMetric1
} from './report-center-view-model'
import { exportReportCenterExcel, exportReportCenterPdf } from './report-center-export'

type ReportTab = 'monthly' | 'dept' | 'trend'
type ExportFormat = 'excel' | 'pdf'
type ReportRow = Record<string, any>

export function useReportCenterPage() {
  const loading = ref(false)
  const exporting = ref(false)
  const exportingPdf = ref(false)
  const activeTab = ref<ReportTab>('monthly')
  const selectedMonth = ref(dayjs().format('YYYY-MM'))
  const trendDays = ref(30)
  const monthlySummary = ref<ReportRow[]>([])
  const deptSummary = ref<ReportRow[]>([])
  const dailyCounts = ref<ReportRow[]>([])
  const warnTypes = ref<ReportRow[]>([])
  const trendData = ref<ReportRow[]>([])
  const reportArea = ref<HTMLElement | null>(null)
  const charts: Record<string, any> = {}

  const monthlyKpis = computed(() => buildMonthlyKpis(monthlySummary.value, deptSummary.value))
  const reportOverviewCards = computed(() => buildReportOverviewCards({
    activeTab: activeTab.value,
    selectedMonth: selectedMonth.value,
    monthlySummary: monthlySummary.value,
    deptSummary: deptSummary.value,
    trendData: trendData.value,
    trendDays: trendDays.value
  }))
  const reportExportState = computed(() => buildReportExportState({
    loading: loading.value,
    exporting: exporting.value,
    exportingPdf: exportingPdf.value,
    monthlySummary: monthlySummary.value,
    deptSummary: deptSummary.value,
    trendData: trendData.value
  }))
  const insightLines = computed(() => buildReportInsightLines({
    activeTab: activeTab.value,
    monthlySummary: monthlySummary.value,
    deptSummary: deptSummary.value,
    trendData: trendData.value,
    trendDays: trendDays.value
  }))

  function queueChartInit() {
    void nextTick(() => {
      initReportCenterCharts({
        activeTab: activeTab.value,
        charts,
        dailyCounts: dailyCounts.value,
        warnTypes: warnTypes.value,
        deptSummary: deptSummary.value,
        trendData: trendData.value
      })
    })
  }

  async function loadAll() {
    loading.value = true
    try {
      await Promise.allSettled([
        loadMonthlySummary(),
        loadDailyCounts(),
        loadWarnTypes(),
        loadDeptSummary(),
        loadTrend()
      ])
      queueChartInit()
    } finally {
      loading.value = false
    }
  }

  async function loadMonthlySummary() {
    monthlySummary.value = await fetchMonthlySummary(selectedMonth.value)
  }

  async function loadDailyCounts() {
    dailyCounts.value = await fetchDailyCounts(selectedMonth.value)
  }

  async function loadWarnTypes() {
    warnTypes.value = await fetchWarnTypes(selectedMonth.value)
  }

  async function loadDeptSummary() {
    deptSummary.value = await fetchDeptSummary()
  }

  async function loadTrend() {
    trendData.value = await fetchTrendData(trendDays.value)
    if (activeTab.value === 'trend') queueChartInit()
  }

  const onMonthChange = () => void loadAll()
  const onTabChange = () => queueChartInit()

  function ensureExportReady(format: ExportFormat) {
    const state = reportExportState.value
    const canExport = format === 'pdf' ? state.canExportPdf : state.canExportExcel
    if (canExport) return true
    const reason = format === 'pdf' ? state.pdfDisabledReason : state.excelDisabledReason
    ElMessage.warning(reason || '当前报表暂不可导出')
    return false
  }

  async function exportExcel() {
    if (!ensureExportReady('excel')) return
    exporting.value = true
    try {
      await exportReportCenterExcel({
        getXLSX,
        selectedMonth: selectedMonth.value,
        monthlySummary: monthlySummary.value,
        deptSummary: deptSummary.value,
        trendData: trendData.value
      })
      ElMessage.success('Excel 导出成功')
    } catch (error) {
      ElMessage.error(`导出失败：${error instanceof Error ? error.message : '未知错误'}`)
    } finally {
      exporting.value = false
    }
  }

  async function exportPdf() {
    if (!ensureExportReady('pdf')) return
    exportingPdf.value = true
    try {
      await exportReportCenterPdf({
        getHtml2Canvas,
        getJsPDF,
        element: reportArea.value,
        selectedMonth: selectedMonth.value
      })
      ElMessage.success('PDF 导出成功')
    } catch (error) {
      ElMessage.error(`PDF 导出失败：${error instanceof Error ? error.message : '未知错误'}`)
    } finally {
      exportingPdf.value = false
    }
  }

  const fmt1 = (value: unknown) => fmtReportMetric1(value)
  const { start: scheduleResize } = useTimeoutTask(() => resizeReportCenterCharts(charts), 200)
  const handleResize = () => scheduleResize()

  onMounted(() => {
    window.addEventListener('resize', handleResize)
    void loadAll()
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    disposeReportCenterCharts(charts)
  })

  return {
    activeTab,
    dailyCounts,
    deptSummary,
    exportExcel,
    exportPdf,
    exporting,
    exportingPdf,
    fmt1,
    insightLines,
    loadTrend,
    loading,
    monthlyKpis,
    monthlySummary,
    onMonthChange,
    onTabChange,
    reportArea,
    reportExportState,
    reportOverviewCards,
    selectedMonth,
    trendData,
    trendDays,
    warnTypes
  }
}
