import { onBeforeUnmount } from 'vue'

export function useIntervalTask(task: () => void | Promise<void>, intervalMs: number) {
  let timer: ReturnType<typeof setInterval> | null = null

  const stop = () => {
    if (!timer) return
    clearInterval(timer)
    timer = null
  }

  const start = (overrideMs = intervalMs) => {
    stop()
    if (!overrideMs || overrideMs <= 0) return
    timer = setInterval(() => {
      task()
    }, overrideMs)
  }

  onBeforeUnmount(stop)

  return { start, stop }
}
