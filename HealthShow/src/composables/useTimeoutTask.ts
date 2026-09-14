import { onBeforeUnmount } from 'vue'

export function useTimeoutTask(task: () => void | Promise<void>, timeoutMs: number) {
  let timer: ReturnType<typeof setTimeout> | null = null

  const stop = () => {
    if (!timer) return
    clearTimeout(timer)
    timer = null
  }

  const start = (overrideMs = timeoutMs) => {
    stop()
    if (!overrideMs || overrideMs <= 0) return
    timer = setTimeout(() => {
      timer = null
      task()
    }, overrideMs)
  }

  onBeforeUnmount(stop)

  return { start, stop }
}
