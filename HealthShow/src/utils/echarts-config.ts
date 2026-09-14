/**
 * Shared ECharts configuration factories for health-monitor pages.
 * Keeps visual consistency and eliminates ~200 lines of duplication.
 */

/* ── colour tokens ── */
const C = {
  label:     '#8ba6c8',
  labelAlt:  '#a8c5e6',
  line:      'rgba(0,212,255,0.18)',
  lineLight: 'rgba(0,212,255,0.15)',
  split:     'rgba(0,212,255,0.07)',
  splitAlt:  'rgba(0,212,255,0.06)',
  tipBg:     'rgba(8,13,35,0.92)',
  tipBorder: 'rgba(0,212,255,0.25)',
  tipText:   '#e0eaf4',
  emptyText: '#8ba6c8'
}

type TooltipFormatter = (...args: any[]) => string

interface CategoryAxisOptions {
  fontSize?: number
  interval?: number | 'auto'
  labelColor?: string
  lineColor?: string
  show?: boolean
  extra?: Record<string, unknown>
}

interface ValueAxisOptions {
  fontSize?: number
  name?: string
  splitColor?: string
  min?: number
  max?: number
}

/** Empty chart placeholder (暂无数据) */
export function emptyOption(text = '暂无数据', fontSize = 14): Record<string, unknown> {
  return {
    backgroundColor: 'transparent',
    graphic: [{
      type: 'text', left: 'center', top: 'middle',
      style: { text, fill: C.emptyText, fontSize }
    }]
  }
}

/** Standard dark tooltip */
export function chartTooltip(formatter?: TooltipFormatter, trigger = 'axis'): Record<string, unknown> {
  return {
    trigger,
    backgroundColor: C.tipBg,
    borderColor: C.tipBorder,
    textStyle: { color: C.tipText, fontSize: 12 },
    ...(formatter ? { formatter } : {})
  }
}

/** Category axis (x or y) with line */
export function categoryAxis(data: unknown[], opts: CategoryAxisOptions = {}): Record<string, unknown> {
  const { fontSize = 11, interval, labelColor = C.labelAlt, lineColor = C.line, show = true } = opts
  return {
    type: 'category',
    data,
    axisLine: show ? { lineStyle: { color: lineColor } } : { show: false },
    axisTick: { show: false },
    axisLabel: { color: labelColor, fontSize, ...(interval != null ? { interval } : {}) },
    ...opts.extra
  }
}

/** Value axis with hidden line + dashed split */
export function valueAxis(opts: ValueAxisOptions = {}): Record<string, unknown> {
  const { fontSize = 10, name, splitColor = C.split, min, max } = opts
  return {
    type: 'value',
    ...(name ? { name, nameTextStyle: { color: C.label, fontSize } } : {}),
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: splitColor, type: 'dashed' } },
    axisLabel: { color: C.label, fontSize },
    ...(min != null ? { min } : {}),
    ...(max != null ? { max } : {})
  }
}

/** Horizontal bar grid (department stats) */
export function deptGrid(): Record<string, unknown> {
  return { left: '26%', right: '8%', top: '10%', bottom: '6%' }
}

/** Standard trend grid */
export function trendGrid(): Record<string, unknown> {
  return { left: '5%', right: '3%', top: '12%', bottom: '12%', containLabel: true }
}

/** Hourly / secondary chart grid */
export function hourlyGrid(): Record<string, unknown> {
  return { left: '8%', right: '2%', top: '14%', bottom: '16%', containLabel: true }
}

/** Age distribution grid */
export function ageGrid(): Record<string, unknown> {
  return { left: '10%', right: '4%', top: '16%', bottom: '16%' }
}

/** Stacked bar label (show only non-zero) */
export function barLabel(position = 'inside'): Record<string, unknown> {
  return {
    show: true, position, color: '#fff', fontSize: 10,
    formatter: (params: { value: number }) => params.value > 0 ? params.value : ''
  }
}
