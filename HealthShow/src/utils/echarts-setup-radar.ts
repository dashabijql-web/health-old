/**
 * ECharts 扩展入口：在基础图表运行时上追加 Radar 支持。
 * 仅在 dashboard / workbench / health-portrait 这类真正用到雷达图的页面引入。
 */
import './echarts-setup'

import { use, init, graphic } from 'echarts/core'
import { RadarChart } from 'echarts/charts'
import { RadarComponent } from 'echarts/components'

use([
  RadarChart,
  RadarComponent
])

export { init, graphic }
