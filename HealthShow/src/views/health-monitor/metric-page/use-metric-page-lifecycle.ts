import { computed, nextTick, onBeforeUnmount, onMounted, ref, type Ref } from 'vue'
import dayjs from 'dayjs'
import { useIntervalTask } from '@/composables/useIntervalTask'
import { useScrollLoop } from '@/composables/useScrollLoop'
import { useTimeoutTask } from '@/composables/useTimeoutTask'

export type MetricPeriod = 'day' | 'week' | 'month'

interface MetricPageLifecycleOptions {
  activePeriod: Ref<MetricPeriod>
  charts: Record<string, any>
  top5ScrollRef: Ref<HTMLElement | null>
  fetchData: () => void | Promise<void>
  refreshData?: () => void | Promise<void>
}

export function useMetricPageLifecycle(options: MetricPageLifecycleOptions) {
  const currentTime = ref('')
  const pageLoading = ref(false)
  const metricPeriodLabel = computed(() => ({
    day: '当日',
    week: '近7日',
    month: '近30日'
  })[options.activePeriod.value])
  const periodRange = computed(() => {
    const today = dayjs().format('YYYY-MM-DD')
    if (options.activePeriod.value === 'day') return { startDate: today, endDate: today }
    if (options.activePeriod.value === 'week') {
      return { startDate: dayjs().subtract(6, 'day').format('YYYY-MM-DD'), endDate: today }
    }
    return { startDate: dayjs().subtract(29, 'day').format('YYYY-MM-DD'), endDate: today }
  })

  const top5Scroll = useScrollLoop({
    getElement: () => options.top5ScrollRef.value,
    intervalMs: 80,
    step: 1,
    endPauseMs: 1500
  })
  const tickClock = () => {
    currentTime.value = dayjs().format('YYYY年MM月DD日 HH:mm:ss')
  }
  const { start: startClock } = useIntervalTask(tickClock, 1000)
  const refreshTask = options.refreshData || options.fetchData
  const { start: startRefresh, stop: stopRefresh } = useIntervalTask(refreshTask, 30000)
  const { start: scheduleResize } = useTimeoutTask(() => {
    void nextTick(() => Object.values(options.charts).forEach((chart) => chart?.resize?.()))
  }, 200)
  const handleResize = () => scheduleResize()
  const handleVisibility = () => {
    if (document.hidden) {
      stopRefresh()
      top5Scroll.stop()
      return
    }
    void refreshTask()
    startRefresh()
    top5Scroll.start()
  }

  onMounted(async () => {
    tickClock()
    startClock()
    pageLoading.value = true
    try {
      await options.fetchData()
    } finally {
      pageLoading.value = false
    }
    void nextTick(top5Scroll.start)
    startRefresh()
    window.addEventListener('resize', handleResize)
    document.addEventListener('visibilitychange', handleVisibility)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize)
    document.removeEventListener('visibilitychange', handleVisibility)
    Object.values(options.charts).forEach((chart) => chart?.dispose?.())
  })

  return {
    currentTime,
    metricPeriodLabel,
    pageLoading,
    periodRange,
    startTop5Scroll: top5Scroll.start
  }
}
