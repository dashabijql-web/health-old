import { ref, onBeforeUnmount } from 'vue'
import dayjs from 'dayjs'
import { useIntervalTask } from '@/composables/useIntervalTask'

export function useClock(fmt = 'YYYY年MM月DD日 HH:mm:ss') {
  const currentTime = ref(dayjs().format(fmt))
  const { start, stop } = useIntervalTask(() => {
    currentTime.value = dayjs().format(fmt)
  }, 1000)

  start()
  onBeforeUnmount(stop)
  return { currentTime, startClock: start, stopClock: stop }
}
