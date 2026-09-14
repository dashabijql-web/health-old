import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, toRefs } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { useIntervalTask } from '@/composables/useIntervalTask'
import { createIntervalTask } from '@/utils/task-timer'
import riskWarningPageState from './risk-warning-page-state'
import { riskWarningPageViewModel } from './risk-warning-view-model'
import { riskWarningPageRuntime } from './risk-warning-runtime'

type RiskState = Record<string, any>

export function useRiskWarningPage(): any {
  const model = reactive((riskWarningPageState.data as () => RiskState)())
  const stateRefs = toRefs(model)
  const trendRef = ref<HTMLElement | null>(null)
  const deptRef = ref<HTMLElement | null>(null)
  const donutRef = ref<HTMLElement | null>(null)
  const listRef = ref<HTMLElement | null>(null)
  const vitalChartRef = ref<HTMLElement | null>(null)
  const refs = {
    trendRef: null as HTMLElement | null,
    deptRef: null as HTMLElement | null,
    donutRef: null as HTMLElement | null,
    listRef: null as HTMLElement | null,
    vitalChartRef: null as HTMLElement | null
  }
  const syncRefs = () => {
    refs.trendRef = trendRef.value
    refs.deptRef = deptRef.value
    refs.donutRef = donutRef.value
    refs.listRef = listRef.value
    refs.vitalChartRef = vitalChartRef.value
  }

  const ctx: any = model
  ctx.$refs = refs
  ctx.$nextTick = nextTick
  ctx.$message = ElMessage
  const periodRange = computed(() => {
    const today = dayjs().format('YYYY-MM-DD')
    if (model.activePeriod === 'day') return { startDate: today, endDate: today }
    if (model.activePeriod === 'week') return { startDate: dayjs().subtract(6, 'day').format('YYYY-MM-DD'), endDate: today }
    return { startDate: dayjs().subtract(29, 'day').format('YYYY-MM-DD'), endDate: today }
  })
  Object.defineProperties(ctx, {
    periodRange: { configurable: true, get: () => periodRange.value },
    metricPeriodLabel: { configurable: true, get: () => ({ day: '当日', week: '近7日', month: '近30日' }[model.activePeriod] || '当前周期') },
    filteredRealtimeList: { configurable: true, get: () => model.warningList },
    pagedList: { configurable: true, get: () => model.warningList },
    totalPages: { configurable: true, get: () => Math.max(1, Math.ceil(model.totalWarnings / model.pageSize)) }
  })

  const { start: startClock, stop: stopClock } = useIntervalTask(() => {
    model.currentTime = dayjs().format('YYYY年MM月DD日 HH:mm:ss')
  }, 1000)
  const refreshTask = createIntervalTask(() => ctx.fetchData(), 30000)
  ctx.initClock = () => {
    model.currentTime = dayjs().format('YYYY年MM月DD日 HH:mm:ss')
    startClock()
  }
  ctx.initPage = () => {
    ctx.initClock()
    void ctx._doFetchWithLoading()
    void nextTick(() => ctx.startAutoScroll())
    refreshTask.start()
  }
  ctx._doFetchWithLoading = async () => {
    model.pageLoading = true
    try { await ctx.fetchData() } finally { model.pageLoading = false }
  }

  const runtimeMethods = riskWarningPageRuntime.methods as Record<string, (...args: any[]) => any>
  Object.entries(runtimeMethods).forEach(([name, method]) => {
    ctx[name] = (...args: any[]) => method.call(ctx, ...args)
  })
  const computedDefs = riskWarningPageViewModel.computed as Record<string, { call?: (value: any) => any }>
  const computedValues: Record<string, any> = {}
  Object.keys(computedDefs).forEach((name) => {
    computedValues[name] = computed(() => computedDefs[name].call!(ctx))
  })
  // The extracted Options API getters still reference sibling computed values
  // through `this` (for example allPendingSelected -> pendingInFiltered).
  // Expose the refs on the shared context so those dependencies stay reactive
  // after the Composition API migration.
  Object.entries(computedValues).forEach(([name, value]) => {
    Object.defineProperty(ctx, name, {
      configurable: true,
      get: () => value.value
    })
  })

  onMounted(() => {
    syncRefs()
    ctx.$refs = refs
    riskWarningPageRuntime.mounted?.call(ctx)
  })
  onBeforeUnmount(() => {
    riskWarningPageRuntime.beforeUnmount?.call(ctx)
    refreshTask.stop()
    stopClock()
    ctx.scrollLoop?.stop?.()
    Object.values(model.charts || {}).forEach((chart: any) => chart?.dispose?.())
  })

  return {
    ...stateRefs,
    ...computedValues,
    ...Object.fromEntries(Object.keys(runtimeMethods).map((name) => [name, ctx[name]])),
    trendRef,
    deptRef,
    donutRef,
    listRef,
    vitalChartRef,
    currentPage: computed(() => Number(model.currentPage) || 1),
    pageSize: computed(() => Number(model.pageSize) || 1),
    listRowIndex: (index: number | string) => (Number(model.currentPage) - 1) * Number(model.pageSize) + Number(index) + 1,
    switchPeriod: (period: string) => {
      if (model.activePeriod === period) return
      model.activePeriod = period
      model.currentPage = 1
      void ctx.fetchData()
    }
  }
}
