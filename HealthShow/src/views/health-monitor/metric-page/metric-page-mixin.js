import { startMetricTop5Scroll, stopMetricTop5Scroll } from './metric-scroll'

export default {
  beforeUnmount() {
    stopMetricTop5Scroll(this)
  },
  methods: {
    startTop5Scroll() {
      const options = typeof this.getTop5ScrollOptions === 'function'
        ? this.getTop5ScrollOptions()
        : undefined
      startMetricTop5Scroll(this, options)
    }
  }
}
