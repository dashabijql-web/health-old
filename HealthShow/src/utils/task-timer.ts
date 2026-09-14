export type AsyncTask = () => void | Promise<void>

export interface StartStopTask {
  start: (delay?: number) => void
  stop: () => void
}

export function createIntervalTask(task: AsyncTask, defaultDelay: number): StartStopTask {
  let timer: ReturnType<typeof setInterval> | null = null

  const stop = () => {
    if (timer === null) return
    clearInterval(timer)
    timer = null
  }

  const start = (delay = defaultDelay) => {
    stop()
    if (!delay || delay <= 0) return
    timer = setInterval(() => {
      task()
    }, delay)
  }

  return { start, stop }
}

export function createTimeoutTask(task: AsyncTask, defaultDelay: number): StartStopTask {
  let timer: ReturnType<typeof setTimeout> | null = null

  const stop = () => {
    if (timer === null) return
    clearTimeout(timer)
    timer = null
  }

  const start = (delay = defaultDelay) => {
    stop()
    if (!delay || delay <= 0) return
    timer = setTimeout(() => {
      timer = null
      task()
    }, delay)
  }

  return { start, stop }
}

export function createEventBinding<T extends EventTarget>(
  getTarget: T | null | (() => T | null),
  eventName: string,
  listener: EventListenerOrEventListenerObject,
  options?: boolean | AddEventListenerOptions
): StartStopTask {
  let target: T | null = null

  const stop = () => {
    if (!target) return
    target.removeEventListener(eventName, listener, options)
    target = null
  }

  const start = () => {
    const nextTarget = typeof getTarget === 'function' ? getTarget() : getTarget
    if (!nextTarget || typeof nextTarget.addEventListener !== 'function') return
    if (target === nextTarget) return
    stop()
    nextTarget.addEventListener(eventName, listener, options)
    target = nextTarget
  }

  return { start, stop }
}
