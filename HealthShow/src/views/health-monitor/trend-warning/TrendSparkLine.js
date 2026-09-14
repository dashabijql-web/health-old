import { defineComponent, h, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from '@/utils/echarts-setup'

export default defineComponent({
  name: 'TrendSparkLine',
  props: {
    data: { type: Array, default: () => [] },
    threshold: { type: Number, default: null },
    risky: { type: String, default: 'high' },
    metric: { type: String, default: '' }
  },
  setup(props) {
    const el = ref(null)
    let chart = null
    let disposed = false

    function buildChart() {
      if (disposed || !el.value || !props.data?.length) return
      if (!chart) chart = echarts.init(el.value)
      chart.setOption(buildSparkLineOption(props))
    }

    onMounted(buildChart)
    onBeforeUnmount(() => {
      disposed = true
      if (chart) {
        chart.dispose()
        chart = null
      }
    })
    watch(() => props.data, buildChart, { deep: true })

    return () => h('div', { ref: el, style: 'width:200px;height:54px' })
  }
})

function buildSparkLineOption(props) {
  const validData = props.data.map((value, index) => [index, value])
  const yVals = props.data.filter(value => value !== null && value !== undefined)
  const minY = yVals.length ? Math.min(...yVals) : 0
  const maxY = yVals.length ? Math.max(...yVals) : 100
  const lineColor = props.risky === 'high'
    ? (props.metric === 'avg_blood_oxygen' ? '#00d4ff' : '#ff6b6b')
    : '#00d4ff'

  return {
    backgroundColor: 'transparent',
    grid: { top: 4, right: 4, bottom: 4, left: 4, containLabel: false },
    xAxis: { type: 'category', show: false },
    yAxis: {
      type: 'value',
      show: false,
      min: Math.floor(Math.min(minY, props.threshold ?? minY) * 0.97),
      max: Math.ceil(Math.max(maxY, props.threshold ?? maxY) * 1.03)
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: validData,
        lineStyle: { color: lineColor, width: 2 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: lineColor.replace(')', ',0.35)').replace('rgb', 'rgba') },
              { offset: 1, color: lineColor.replace(')', ',0.02)').replace('rgb', 'rgba') }
            ]
          }
        }
      },
      props.threshold !== null ? {
        type: 'line',
        symbol: 'none',
        silent: true,
        markLine: {
          silent: true,
          symbol: 'none',
          data: [{ yAxis: props.threshold }],
          lineStyle: { color: '#ff4444', type: 'dashed', width: 1 }
        }
      } : null
    ].filter(Boolean),
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(13,40,71,0.9)',
      textStyle: { color: '#e8f4fd', fontSize: 11 },
      formatter: point => `第${point[0].dataIndex + 1}天: ${point[0].value[1] ?? '--'}`
    }
  }
}
