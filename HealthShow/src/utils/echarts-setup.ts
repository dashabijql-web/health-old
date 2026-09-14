/**
 * ECharts 按需引入 — 只注册项目实际使用的图表类型和组件
 * 替代 `import * as echarts from 'echarts'`，减少打包体积
 *
 * 已注册：line / bar / pie / gauge + 常用组件
 */
import { use, init, graphic } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart, GaugeChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  MarkLineComponent,
  MarkPointComponent,
  TitleComponent,
  GraphicComponent
} from 'echarts/components'

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  PieChart,
  GaugeChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  MarkLineComponent,
  MarkPointComponent,
  TitleComponent,
  GraphicComponent
])

// 导出使用到的核心 API（命名导出，让文件通过 echarts.init / echarts.graphic 调用）
export { init, graphic }
