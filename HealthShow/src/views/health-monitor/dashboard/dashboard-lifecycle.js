import { createEventBinding, createIntervalTask, createTimeoutTask } from '@/utils/task-timer'

function resizeDashboardCharts(vm) {
  vm.$nextTick(() => {
    Object.values(vm.charts).forEach((chart) => {
      if (chart && chart.resize) chart.resize()
    })
  })
}

function attachDashboardListeners(vm) {
  vm._resizeHandler = () => vm.handleResize()
  vm._resizeBinding = createEventBinding(() => window, 'resize', vm._resizeHandler)
  vm._resizeBinding.start()

  vm._onVisibilityChange = () => vm.onVisibilityChange()
  vm._visibilityBinding = createEventBinding(() => document, 'visibilitychange', vm._onVisibilityChange)
  vm._visibilityBinding.start()

  vm._fullscreenHandler = () => {
    vm.isFullscreen = !!document.fullscreenElement
  }
  vm._fullscreenBinding = createEventBinding(() => document, 'fullscreenchange', vm._fullscreenHandler)
  vm._fullscreenBinding.start()
}

function detachDashboardListeners(vm) {
  vm._resizeBinding?.stop?.()
  vm._visibilityBinding?.stop?.()
  vm._fullscreenBinding?.stop?.()
}

function disposeChart(chart) {
  if (chart) chart.dispose()
}

function resetDashboardScale(vm) {
  const el = vm.$refs.dmScale
  if (!el) return
  el.style.transform = ''
  el.style.width = ''
  el.style.height = ''
  el.style.transformOrigin = ''
}

function forceDashboardScrollTop(vm) {
  window.scrollTo?.(0, 0)
  document.documentElement.scrollTop = 0
  document.body.scrollTop = 0

  vm.$refs.dmBody?.scrollTo?.({ top: 0, left: 0, behavior: 'auto' })
  if (vm.$refs.dmBody) vm.$refs.dmBody.scrollTop = 0
  if (vm.$refs.dmScale) vm.$refs.dmScale.scrollTop = 0
}

function resetDashboardScroll(vm) {
  vm.$nextTick(() => {
    forceDashboardScrollTop(vm)
  })
}

function requestDashboardNotificationPermission() {
  if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission()
  }
}

export function mountDashboardPage(vm) {
  resetDashboardScroll(vm)

  vm._timeTask = createIntervalTask(() => vm.updateTime(), 1000)
  vm._refreshTask = createIntervalTask(() => vm.fetchData(), 120000)
  vm._kpiRefreshTask = createIntervalTask(() => vm.fetchKpiData(), 30000)
  vm._refreshTextTask = createIntervalTask(() => vm.updateRefreshText(), 5000)
  vm._resizeTask = createTimeoutTask(() => resizeDashboardCharts(vm), 200)

  vm.initTime()
  vm.$nextTick(() => {
    vm.initHourDistChart()
    vm.initUnifiedTrendChart()
    vm.initWarnTypeChart()
    vm.initEnvHealthChart()
  })
  vm.fetchData()
  vm.loadMineAiCache()
  vm.startAutoRefresh()

  attachDashboardListeners(vm)
  requestDashboardNotificationPermission()
}

export function activateDashboardPage(vm) {
  resetDashboardScroll(vm)
  vm.fetchData()
  resizeDashboardCharts(vm)
}

export function unmountDashboardPage(vm) {
  vm._chartRetryToken = (vm._chartRetryToken || 0) + 1
  vm._timeTask?.stop()
  vm._refreshTask?.stop()
  vm._kpiRefreshTask?.stop()
  vm._refreshTextTask?.stop()
  vm._resizeTask?.stop()

  detachDashboardListeners(vm)

  Object.values(vm.charts || {}).forEach(disposeChart)
  disposeChart(vm._envChart)
  disposeChart(vm.empDrawer.trendChart)
  disposeChart(vm.empDrawer.radarChart)
  disposeChart(vm.deptPersonModal?.chart)
  disposeChart(vm.deptDetailModal?.chart)
  disposeChart(vm.metricDetailModal?.chart)
  disposeChart(vm.warnCurveModal?.chart)
  vm._envChart = null
  vm.empDrawer.trendChart = null
  vm.empDrawer.radarChart = null
  if (vm.charts) {
    Object.keys(vm.charts).forEach((key) => { vm.charts[key] = null })
  }

  resetDashboardScale(vm)
}
