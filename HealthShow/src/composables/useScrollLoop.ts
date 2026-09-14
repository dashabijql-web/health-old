import { onBeforeUnmount } from 'vue'
import { createIntervalTask, createTimeoutTask } from '@/utils/task-timer'

export interface ScrollLoopOptions {
  getElement: () => HTMLElement | null | undefined
  intervalMs?: number
  step?: number
  endPauseMs?: number
  shouldScroll?: () => boolean
  onReachEnd?: () => void
  onTick?: ((element: HTMLElement) => void) | null
}

export interface ScrollLoopController {
  start: () => void
  stop: () => void
  pause: () => void
  resume: () => void
  isActive: () => boolean
  isPaused: () => boolean
}

/**
 * 纯滚动循环控制器，不绑定 Vue 生命周期。
 */
export function createScrollLoop({
  getElement,
  intervalMs = 40,
  step = 1,
  endPauseMs = 1500,
  shouldScroll = () => true,
  onReachEnd = () => {},
  onTick = null
}: ScrollLoopOptions): ScrollLoopController {
  let active = false
  let paused = false

  const resumeTask = createTimeoutTask(() => {
    paused = false
    onReachEnd()
    if (active) {
      loopTask.start()
    }
  }, endPauseMs)

  const loopTask = createIntervalTask(() => {
    if (!active || paused || !shouldScroll()) return
    const el = getElement?.()
    if (!el) return

    const max = el.scrollHeight - el.clientHeight
    if (max <= 0) return

    if (typeof onTick === 'function') onTick(el)

    el.scrollTop += step
    if (el.scrollTop >= max - 1) {
      paused = true
      loopTask.stop()
      resumeTask.start()
    }
  }, intervalMs)

  const start = () => {
    active = true
    paused = false
    loopTask.start()
  }

  const stop = () => {
    active = false
    paused = false
    loopTask.stop()
    resumeTask.stop()
  }

  const pause = () => {
    paused = true
  }

  const resume = () => {
    paused = false
  }

  return { start, stop, pause, resume, isActive: () => active, isPaused: () => paused }
}

/**
 * Vue 组合式封装：自动在卸载时停止滚动循环。
 */
export function useScrollLoop(options: ScrollLoopOptions): ScrollLoopController {
  const loop = createScrollLoop(options)
  onBeforeUnmount(loop.stop)
  return loop
}
