import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, toRefs } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { useIntervalTask } from '@/composables/useIntervalTask'
import { useScrollLoop } from '@/composables/useScrollLoop'
import sleepPageState from './sleep-page-state'
import { sleepPageComputed } from './sleep-view-model'
import { sleepPageRuntime } from './sleep-runtime'

type SleepState = Record<string, any>

export function useSleepPage() {
  const model = reactive((sleepPageState.data as () => SleepState)())
  const stateRefs = toRefs(model)
  const stageRef = ref<HTMLElement | null>(null)
  const scoreRef = ref<HTMLElement | null>(null)
  const trendRef = ref<HTMLElement | null>(null)
  const durationRef = ref<HTMLElement | null>(null)
  const bedtimeRef = ref<HTMLElement | null>(null)
  const deptRef = ref<HTMLElement | null>(null)
  const listRef = ref<HTMLElement | null>(null)

  const refs = {
    stageRef: null as HTMLElement | null,
    scoreRef: null as HTMLElement | null,
    trendRef: null as HTMLElement | null,
    durationRef: null as HTMLElement | null,
    bedtimeRef: null as HTMLElement | null,
    deptRef: null as HTMLElement | null,
    listRef: null as HTMLElement | null
  }
  const syncRefs = () => {
    refs.stageRef = stageRef.value
    refs.scoreRef = scoreRef.value
    refs.trendRef = trendRef.value
    refs.durationRef = durationRef.value
    refs.bedtimeRef = bedtimeRef.value
    refs.deptRef = deptRef.value
    refs.listRef = listRef.value
  }

  const scrollLoop = useScrollLoop({
    getElement: () => listRef.value,
    intervalMs: 80,
    step: 1,
    endPauseMs: 1500
  })
  const { start: startClock, stop: stopClock } = useIntervalTask(() => {
    model.currentTime = dayjs().format('YYYY年MM月DD日 HH:mm:ss')
  }, 1000)

  const ctx: any = model
  ctx.$refs = refs
  ctx.$nextTick = nextTick
  ctx.$message = ElMessage
  ctx.initClock = () => {
    model.currentTime = dayjs().format('YYYY年MM月DD日 HH:mm:ss')
    startClock()
  }
  ctx.startAutoScroll = () => {
    syncRefs()
    scrollLoop.start()
  }
  ctx.fmtTime = (value: unknown) => value ? dayjs(value as string).format('MM-DD HH:mm') : ''

  const computedDefs = sleepPageComputed.computed as Record<string, { call?: (value: any) => any }>
  const headerKpis = computed(() => computedDefs.headerKpis.call!(ctx))
  const statCards = computed(() => computedDefs.statCards.call!(ctx))
  const pagedList = computed(() => computedDefs.pagedList.call!(ctx))
  const totalPages = computed(() => computedDefs.totalPages.call!(ctx))

  const runtimeMethods = sleepPageRuntime.methods as Record<string, (...args: any[]) => any>
  const callRuntime = (name: string, ...args: any[]) => runtimeMethods[name]?.call(ctx, ...args)
  const fetchData = () => callRuntime('fetchData')
  const exportExcel = () => callRuntime('exportExcel')
  const openRecordDialog = (item: SleepState) => callRuntime('openRecordDialog', item)
  const scoreClass = (score: number) => callRuntime('scoreClass', score)

  onMounted(() => {
    syncRefs()
    ctx.$refs = refs
    sleepPageRuntime.mounted?.call(ctx)
  })

  onBeforeUnmount(() => {
    sleepPageRuntime.beforeUnmount?.call(ctx)
    scrollLoop.stop()
    stopClock()
    Object.values(model.charts || {}).forEach((chart: any) => chart?.dispose?.())
  })

  return {
    alertList: stateRefs.alertList,
    bedtimeRef,
    bedtimeTips: stateRefs.bedtimeTips,
    currentPage: stateRefs.currentPage,
    currentTime: stateRefs.currentTime,
    deptRef,
    deptUploadList: stateRefs.deptUploadList,
    detailList: stateRefs.detailList,
    durationRef,
    durationLegend: stateRefs.durationLegend,
    durationTips: stateRefs.durationTips,
    exportExcel,
    fmtTime: ctx.fmtTime,
    headerKpis,
    listRef,
    openRecordDialog,
    overview: stateRefs.overview,
    pagedList,
    recordDialog: stateRefs.recordDialog,
    scoreClass,
    scoreRef,
    stageLegend: stateRefs.stageLegend,
    stageRef,
    statCards,
    totalPages,
    trendRef,
    yesterdayDate: stateRefs.yesterdayDate,
    fetchData
  }
}
