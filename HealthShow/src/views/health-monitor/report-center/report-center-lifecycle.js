import { createEventBinding, createTimeoutTask } from '@/utils/task-timer'
import {
  disposeReportCenterCharts,
  initReportCenterCharts,
  resizeReportCenterCharts
} from './report-center-chart'

export function queueReportCenterChartInit(vm) {
  vm.$nextTick(() => {
    initReportCenterCharts(vm)
  })
}

export function mountReportCenterPage(vm) {
  vm._resizeTask = createTimeoutTask(() => {
    resizeReportCenterCharts(vm.charts)
  }, 200)
  vm._resizeHandler = () => vm._resizeTask.start()
  vm._resizeBinding = createEventBinding(() => window, 'resize', vm._resizeHandler)
  vm._resizeBinding.start()
}

export function unmountReportCenterPage(vm) {
  vm._resizeBinding?.stop?.()
  vm._resizeTask?.stop?.()
  disposeReportCenterCharts(vm.charts)
}
