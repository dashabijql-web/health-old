<template>
  <section :class="['uc-panel', 'uc-map-panel', `uc-map-panel--${variant}`]">
    <div class="uc-panel-head">
      <span><i>{{ variant === 'park' ? '02' : '05' }}</i>{{ variant === 'park' ? '园区地图' : '实时态势' }}</span>
      <div class="uc-map-head-actions">
        <span v-if="variant === 'realtime'" class="uc-map-live"><b></b> LIVE</span>
        <button type="button" title="切换图层">▱ 图层</button>
        <button type="button" title="全屏查看">⛶ 全屏</button>
      </div>
    </div>

    <div v-if="variant === 'park'" class="uc-map-stat-row">
      <div v-for="item in parkStats" :key="item.label" class="uc-map-stat">
        <span class="uc-map-stat-icon">{{ item.icon }}</span>
        <span>{{ item.label }}</span>
        <strong :class="`tone-${item.tone}`">{{ item.value }}</strong>
      </div>
    </div>

    <div class="uc-map-stage">
      <div class="uc-map-gridlines"></div>
      <div class="uc-map-road uc-map-road--a"></div>
      <div class="uc-map-road uc-map-road--b"></div>
      <div class="uc-map-road uc-map-road--c"></div>

      <template v-if="variant === 'park'">
        <button
          v-for="(area, index) in parkAreas"
          :key="area.name"
          type="button"
          :class="['uc-map-building', { 'is-selected': selectedArea === area.name, 'is-alert': area.alert }]"
          :style="{ left: area.x + '%', top: area.y + '%', width: area.w + '%', height: area.h + '%' }"
          @click="selectedArea = area.name"
        >
          <span>{{ area.name }}</span>
          <small v-if="area.count !== null">{{ area.count }} 人</small>
          <em v-if="area.alert">!</em>
        </button>
        <div class="uc-map-tool-rail" aria-label="地图工具">
          <button type="button" title="选择">⌁</button>
          <button type="button" title="测距">⌁</button>
          <button type="button" title="定位">⌖</button>
          <button type="button" title="放大">＋</button>
          <button type="button" title="缩小">−</button>
        </div>
      </template>

      <template v-else>
        <button
          v-for="marker in realtimeMarkers"
          :key="marker.key"
          type="button"
          :class="['uc-map-marker', `tone-${marker.tone}`, { 'is-selected': selectedEvent === marker.event }]"
          :style="{ left: marker.x + '%', top: marker.y + '%' }"
          :title="marker.label"
          @click="selectEvent(marker.event)"
        >
          <span>{{ marker.icon }}</span>
        </button>
        <div class="uc-map-route route-a"></div>
        <div class="uc-map-route route-b"></div>
        <div v-if="selectedEvent" class="uc-map-incident-callout">
          <span class="uc-map-callout-dot"></span>
          <div>
            <strong>{{ selectedEvent.userName || selectedEvent.user || '重点人员' }}</strong>
            <small>{{ selectedEvent.location || selectedEvent.dept || '现场区域' }} · {{ selectedEvent.type || '风险预警' }}</small>
          </div>
          <b>{{ selectedEvent.value || selectedEvent.val || '--' }}</b>
        </div>
      </template>

      <div class="uc-map-compass">N</div>
      <div class="uc-map-scale">100m</div>
    </div>

    <div v-if="variant === 'park'" class="uc-map-foot">
      <div class="uc-map-foot-title"><strong>{{ selectedArea || parkAreas[0]?.name || '园区' }}</strong><span>· 监控覆盖</span></div>
      <div class="uc-map-foot-status"><b></b>{{ coverageLabel }}</div>
      <div class="uc-map-camera-strip">
        <div v-for="(camera, index) in cameraCards" :key="index" class="uc-map-camera">
          <span class="uc-map-camera-image"></span>
          <i :class="camera.online ? 'is-online' : 'is-offline'"></i>
        </div>
        <button type="button" class="uc-map-camera-next" title="查看更多监控">›</button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, type PropType } from 'vue'

interface DashboardEvent {
  id?: number | string
  userName?: string
  user?: string
  location?: string
  dept?: string
  type?: string
  value?: string | number
  val?: string | number
  level?: string
  handled?: boolean
  [key: string]: unknown
}

interface RiskDepartment {
  name?: string
  count?: number
  pct?: number
  [key: string]: unknown
}

const props = defineProps({
  variant: { type: String as PropType<'park' | 'realtime'>, default: 'realtime' },
  warningEvents: { type: Array as PropType<DashboardEvent[]>, default: () => [] },
  riskDeptList: { type: Array as PropType<RiskDepartment[]>, default: () => [] },
  healthSnapshot: { type: Object as PropType<Record<string, unknown> | null>, default: null }
})

const selectedArea = ref('')
const selectedEvent = ref<DashboardEvent | null>(null)

const firstValue = (...values: unknown[]) => {
  const found = values.find((value) => value !== null && value !== undefined && value !== '')
  return found === undefined ? '--' : found
}

const parkStats = computed(() => [
  { icon: '◉', label: '监控点', value: firstValue(props.healthSnapshot?.monitorOnline, props.healthSnapshot?.cameraOnline), tone: 'cyan' },
  { icon: '●', label: '人员定位', value: firstValue(props.healthSnapshot?.onlineCount, props.healthSnapshot?.online), tone: 'green' },
  { icon: '◌', label: '环境设备', value: firstValue(props.healthSnapshot?.environmentOnline), tone: 'cyan' }
])

const parkAreas = computed(() => {
  const source = props.riskDeptList.slice(0, 6)
  const names = ['北区宿舍', '研发中心', '生产车间', '成品仓库', '综合办公楼', '停车场']
  const layout = [
    { x: 10, y: 17, w: 30, h: 19 },
    { x: 44, y: 19, w: 35, h: 20 },
    { x: 31, y: 45, w: 42, h: 24 },
    { x: 72, y: 52, w: 24, h: 23 },
    { x: 16, y: 72, w: 31, h: 17 },
    { x: 50, y: 78, w: 23, h: 13 }
  ]
  return layout.map((box, index) => {
    const sourceItem = source[index]
    const name = sourceItem?.name || names[index]
    const count = typeof sourceItem?.count === 'number' ? sourceItem.count : null
    return { ...box, name, count, alert: index === 2 && props.warningEvents.length > 0 }
  })
})

const coverageLabel = computed(() => {
  const online = props.healthSnapshot?.monitorOnline
  const total = props.healthSnapshot?.monitorTotal
  if (online !== undefined && total !== undefined) return `${online}/${total} 在线`
  return props.warningEvents.length ? `${props.warningEvents.length} 条实时事件` : '等待数据'
})

const cameraCards = computed(() => [0, 1, 2].map((index) => ({ online: index !== 2 || props.warningEvents.length === 0 })))

const realtimeMarkers = computed(() => {
  const events = props.warningEvents.filter((event) => !event.handled).slice(0, 6)
  const positions = [
    [52, 48], [31, 33], [71, 26], [22, 62], [80, 60], [61, 75]
  ]
  return events.map((event, index) => ({
    key: String(event.id || `${event.userName || event.user || 'event'}-${index}`),
    event,
    x: positions[index][0],
    y: positions[index][1],
    tone: ['danger', 'warning', 'cyan', 'cyan', 'green', 'cyan'][index],
    icon: index === 0 ? '!' : index % 2 ? '●' : '▣',
    label: `${event.userName || event.user || ''} ${event.type || '预警'}`
  }))
})

function selectEvent(event: DashboardEvent) {
  selectedEvent.value = event
  emit('select-event', event)
}

const emit = defineEmits<{ 'select-event': [event: DashboardEvent] }>()
</script>
