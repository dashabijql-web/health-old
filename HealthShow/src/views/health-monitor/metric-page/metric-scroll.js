import { createScrollLoop } from '@/composables/useScrollLoop'

export function startMetricTop5Scroll(page, options = {}) {
  if (!page._top5ScrollLoop) {
    page._top5ScrollLoop = createScrollLoop({
      getElement: () => page.$refs.top5ScrollRef,
      intervalMs: options.intervalMs ?? 40,
      step: options.step ?? 1,
      endPauseMs: options.endPauseMs ?? 1500,
      shouldScroll: options.shouldScroll
    })
  }
  page._top5ScrollLoop.start()
}

export function stopMetricTop5Scroll(page) {
  if (page._top5ScrollLoop) {
    page._top5ScrollLoop.stop()
  }
}
