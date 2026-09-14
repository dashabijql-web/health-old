import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useIntervalTask } from '@/composables/useIntervalTask'
import { createEventBinding } from '@/utils/task-timer'
import { buildProcessedWarningData } from './safety-command-view-model'
import {
  fetchSafetyCommandCritical,
  fetchSafetyCommandData,
  fetchSafetyCommandHourlyVitals
} from './safety-command-runtime'

function updateClock(currentDate, currentTime) {
  const now = new Date()
  currentDate.value = now
    .toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
    .replace(/\//g, '-')
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour12: false })
}

export function useSafetyCommandPageData(options = {}) {
  const legacy = options.legacy === true
  const commandSummary = ref({ warning: {}, device: {} })
  const dataAsOf = ref('')
  const currentDate = ref('')
  const currentTime = ref('')
  const stats = ref({ underground: 0, total: 0, sos: 0, fall: 0, static: 0, abnormal: 0, normal: 0 })
  const watchStatus = ref({ total: 0, online: 0, offline: 0, lowBattery: 0 })
  const handledCount = ref(0)
  const pendingWarnings = ref(null)
  const totalWarnings = ref(null)
  const criticalWarnings = ref(0)
  const vitalAvg = ref({ heartRate: 0, bloodOxygen: 0, temperature: 0, pressure: 0 })
  const vitalsHistory = reactive({ hr: [], bo: [] })
  const events = ref([])
  const warningTrend = ref([])
  const riskPersons = ref([])
  const areas = ref([])
  const departments = ref([])

  const processWarningData = (raw) => {
    const nextState = buildProcessedWarningData(raw)
    events.value = nextState.events
    riskPersons.value = nextState.riskPersons
    stats.value.sos = nextState.sosCount
    stats.value.fall = nextState.fallCount
  }

  const fetchCritical = async () => fetchSafetyCommandCritical({
    processWarningData,
    handledCountRef: handledCount,
    pendingWarningsRef: pendingWarnings,
    totalWarningsRef: totalWarnings,
    criticalWarningsRef: criticalWarnings,
    commandSummaryRef: commandSummary,
    dataAsOfRef: dataAsOf
  })
  const fetchAllData = async () => fetchSafetyCommandData({
    handledCountRef: handledCount,
    pendingWarningsRef: pendingWarnings,
    totalWarningsRef: totalWarnings,
    criticalWarningsRef: criticalWarnings,
    commandSummaryRef: commandSummary,
    dataAsOfRef: dataAsOf,
    warningTrendRef: warningTrend,
    departmentsRef: departments,
    processWarningData,
    ...(legacy ? {
      statsRef: stats,
      watchStatusRef: watchStatus,
      vitalAvgRef: vitalAvg,
      areasRef: areas
    } : {})
  })
  const fetchHourlyVitals = async () => fetchSafetyCommandHourlyVitals(vitalsHistory)

  const { start: startClock, stop: stopClock } = useIntervalTask(() => updateClock(currentDate, currentTime), 1000)
  const { start: startCriticalPolling, stop: stopCriticalPolling } = useIntervalTask(fetchCritical, 5000)
  const { start: startAllPolling, stop: stopAllPolling } = useIntervalTask(fetchAllData, 30000)
  let visibilityBinding = null

  const startPolling = () => {
    startCriticalPolling()
    startAllPolling()
  }

  const stopPolling = () => {
    stopCriticalPolling()
    stopAllPolling()
  }

  const onVisibilityChange = () => {
    if (document.hidden) {
      stopPolling()
      return
    }
    fetchAllData()
    startPolling()
  }

  onMounted(() => {
    if (legacy) {
      updateClock(currentDate, currentTime)
      startClock()
      fetchHourlyVitals()
    }
    fetchAllData()
    startPolling()
    visibilityBinding = createEventBinding(() => document, 'visibilitychange', onVisibilityChange)
    visibilityBinding.start()
  })

  onUnmounted(() => {
    if (legacy) stopClock()
    stopPolling()
    visibilityBinding?.stop?.()
  })

  return {
    areas,
    commandSummary,
    currentDate,
    currentTime,
    dataAsOf,
    departments,
    events,
    fetchAllData,
    fetchCritical,
    fetchHourlyVitals,
    handledCount,
    pendingWarnings,
    totalWarnings,
    criticalWarnings,
    processWarningData,
    riskPersons,
    stats,
    startPolling,
    stopPolling,
    vitalAvg,
    vitalsHistory,
    watchStatus,
    warningTrend
  }
}
