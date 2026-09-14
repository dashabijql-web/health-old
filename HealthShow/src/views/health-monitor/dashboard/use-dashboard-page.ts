import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, reactive, ref, toRefs, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { buildDashboardAdmissionQueueItems, buildDashboardClosureLaneItems, dashboardCommandWorkflowMethods } from './dashboard-command-workflow'
import { dashboardComputed } from './dashboard-computed'
import { createDashboardPageState } from './dashboard-page-state'
import { dashboardViewActions } from './dashboard-view-actions'
import { dashboardChartMethods } from './dashboard-chart-methods'
import { dashboardDetailMethods } from './dashboard-detail-methods'
import { activateDashboardPage, mountDashboardPage, unmountDashboardPage } from './dashboard-lifecycle'
import { dashboardRuntimeMethods } from './dashboard-runtime'

export function useDashboardPage(): any {
  const model = reactive(createDashboardPageState())
  const stateRefs = toRefs(model)
  const route = useRoute()
  const router = useRouter()
  const ctx: any = model
  ctx.$router = router
  ctx.$route = route
  ctx.$nextTick = nextTick
  ctx.$message = ElMessage
  const dmScale = ref<HTMLElement | null>(null)
  const dmBody = ref<HTMLElement | null>(null)
  const unifiedTrendChart = ref<HTMLElement | null>(null)
  const envChartRef = ref<HTMLElement | null>(null)
  const refs = { dmScale: null as HTMLElement | null, dmBody: null as HTMLElement | null, unifiedTrendChart: null as HTMLElement | null, envChartRef: null as HTMLElement | null }
  const syncRefs = () => {
    refs.dmScale = dmScale.value
    refs.dmBody = dmBody.value
    refs.unifiedTrendChart = unifiedTrendChart.value
    refs.envChartRef = envChartRef.value
  }
  ctx.$refs = refs

  const methodGroups = [
    dashboardRuntimeMethods,
    dashboardCommandWorkflowMethods,
    dashboardViewActions,
    dashboardChartMethods,
    dashboardDetailMethods
  ]
  methodGroups.forEach((methods) => {
    Object.entries(methods).forEach(([name, fn]) => {
      if (typeof fn === 'function') ctx[name] = fn.bind(ctx)
    })
  })
  const computedValues: Record<string, any> = {}
  Object.entries(dashboardComputed).forEach(([name, getter]) => {
    computedValues[name] = computed(() => (getter as (this: any) => any).call(ctx))
  })
  // The extracted getters still compose through `this` as they did in the
  // Options API page. Expose their unwrapped values on the shared context so
  // one computed getter can safely depend on another.
  Object.entries(computedValues).forEach(([name, value]) => {
    Object.defineProperty(ctx, name, {
      configurable: true,
      get: () => value.value
    })
  })
  computedValues.headerMetricStripItems = computed(() => (ctx.headerKpis || []).map((item: any, index: number) => ({
    key: `${item.label}-${index}`,
    label: item.label,
    value: item.valHtml ? String(item.valHtml).replace(/<[^>]+>/g, ' ') : String(item.val ?? '--'),
    note: ctx.normalizeHeaderMetricNote(item.sub),
    tone: ctx.resolveHeaderMetricTone(item.cls),
    clickable: Boolean(item.clickable),
    route: item.route
  })))
  computedValues.closureLaneItems = computed(() => buildDashboardClosureLaneItems({
    warningSummary: ctx.commandSummary?.warning,
    preShiftData: ctx.preShiftData
  }))
  computedValues.admissionQueueItems = computed(() => buildDashboardAdmissionQueueItems({
    preShiftData: ctx.preShiftData,
    admissionSummary: ctx.commandSummary?.admission
  }))
  computedValues.primaryVitalCards = computed(() => (ctx.vitalCards || []).slice(0, 6))
  computedValues.supplementalVitalCards = computed(() => (ctx.vitalCards || []).slice(6))
  computedValues.dashboardHeroDescription = computed(() => {
    const pending = (ctx.warningEvents || []).filter((item: any) => !item.handled).length
    return `${ctx.periodLabel}重点关注 ${pending} 条待处理预警、班前准入和趋势变化。`
  })

  ctx.resolveHeaderMetricTone = (cls: string) => {
    if (cls === 'kpi-red') return 'danger'
    if (cls === 'kpi-orange') return 'warning'
    if (cls === 'kpi-green' || cls === 'kpi-teal') return 'success'
    return 'primary'
  }
  ctx.normalizeHeaderMetricNote = (note: unknown) => {
    if (!note) return ''
    const normalized = String(note).replace(/\s+/g, ' ').trim()
    if (normalized === '数据加载中...') return '等待刷新'
    return normalized.length > 18 ? `${normalized.slice(0, 18)}…` : normalized
  }
  ctx.onHeaderMetricSelect = (item: any) => {
    if (item?.route) void router.push(item.route)
  }

  onMounted(() => {
    syncRefs()
    mountDashboardPage(ctx)
  })
  onActivated(() => {
    syncRefs()
    activateDashboardPage(ctx)
  })
  onBeforeUnmount(() => {
    unmountDashboardPage(ctx)
  })
  watch(() => route.query, (query) => {
    ctx.openIncidentFromRoute(query)
  }, { immediate: true, deep: true })

  return {
    ...stateRefs,
    ...computedValues,
    ...toRefs(model),
    ...Object.fromEntries(methodGroups.flatMap((methods) => Object.keys(methods)).map((name) => [name, ctx[name]])),
    $router: router,
    dmScale,
    dmBody,
    unifiedTrendChart
    ,envChartRef
  }
}
