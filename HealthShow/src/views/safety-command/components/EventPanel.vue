<template>
  <div class="panel ev-panel">
    <div class="ph">
      <span class="pt">
        <span class="pt-bar red-bar"></span>
        {{ title }}
        <span class="pt-cnt">{{ events.length }}</span>
      </span>
      <div class="tabs">
        <span v-for="f in filterTabs" :key="f.type" :class="['tb', currentFilter===f.type&&'on']" @click="currentFilter=f.type">
          {{ f.label }}({{ f.count }})
        </span>
      </div>
    </div>
    <div ref="trendRef" class="ev-trend" v-if="trendData.length > 0"></div>
    <div class="ev-list">
      <div v-if="filteredEvents.length === 0" class="ev-empty">暂无事件</div>
      <div
        v-for="ev in filteredEvents" :key="ev.id"
        :class="['ev', `ev-${eventVisualTone(ev)}`]"
        @click="$emit('showDetail', ev)"
      >
        <div :class="['ev-side', `side-${eventVisualTone(ev)}`]"></div>
        <div class="ev-main">
          <div class="ev-primary" :title="ev.type || '未知事件'">
            <div class="ev-title-row">
              <span :class="['ev-level', `ev-level-${ev.level || 'medium'}`]">{{ eventLevelText(ev) }}</span>
              <span class="ev-source">{{ eventSourceText(ev) }}</span>
              <span class="ev-type">{{ ev.icon || 'WARN' }} {{ ev.type || '未知事件' }}</span>
            </div>
            <span class="ev-advice">{{ eventAdviceText(ev) }}</span>
          </div>

          <div class="ev-field ev-person">
            <span class="ev-k">人员</span>
            <span @click.stop="$emit('showPerson', ev)" class="ev-v ev-link" :title="ev.user || '未知人员'">
              {{ ev.user || '未知人员' }}
              <small v-if="ev.userCode">{{ ev.userCode }}</small>
            </span>
          </div>

          <div class="ev-field ev-dept">
            <span class="ev-k">部门</span>
            <span @click.stop="$emit('showDept', ev.dept)" class="ev-v ev-link" :title="eventDeptText(ev)">
              {{ eventDeptText(ev) }}
            </span>
          </div>

          <div class="ev-field ev-location">
            <span class="ev-k">位置</span>
            <span class="ev-v" :title="eventLocationText(ev)">{{ eventLocationText(ev) }}</span>
          </div>

          <div class="ev-field ev-duration">
            <span class="ev-k">时长</span>
            <span :class="['ev-v', 'ev-duration-value', { 'is-hot': isDurationHot(ev), 'is-warn': !isPriorityEvent(ev) }]">
              {{ eventDurationText(ev) }}
            </span>
          </div>

          <div class="ev-field ev-stage">
            <span class="ev-k">状态</span>
            <span :class="['ev-v', 'ev-stage-value', eventStageClass(ev)]">{{ eventStageText(ev) }}</span>
          </div>

          <div class="ev-field ev-action-field">
            <span class="ev-k">发生</span>
            <span class="ev-v ev-age">{{ ev.time || '刚刚' }}</span>
            <button type="button" class="ev-act" @click.stop="$emit('handle', ev)">处置</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch, type PropType } from 'vue'
import * as echarts from '@/utils/echarts-setup'
import {
  getEventLevelTone,
  getEventLevelText,
  getEventSourceText,
  getEventStatusText,
  getSlaStatusText
} from '../safety-command-view-model'

interface SafetyEvent {
  id?: number | string
  eventType?: string
  level?: string
  severity?: string
  status?: string
  statusLabel?: string
  slaStatus?: string
  sla?: string
  type?: string
  icon?: string
  user?: string
  userCode?: string
  durationMinutes?: number
  dept?: string
  location?: string
  eventSource?: string
  time?: string
  [key: string]: unknown
}

interface TrendItem {
  date?: string
  label?: string
  count?: number
  value?: number
  [key: string]: unknown
}

const props = defineProps({
  events: { type: Array as PropType<SafetyEvent[]>, default: () => [] },
  trendData: { type: Array as PropType<TrendItem[]>, default: () => [] },
  title: { type: String, default: '未闭环预警' }
})
defineEmits<{
  showAll: []
  showDetail: [event: SafetyEvent]
  showPerson: [event: SafetyEvent]
  showDept: [department: unknown]
  handle: [event: SafetyEvent]
}>()

const currentFilter = ref<'all' | 'sos' | 'fall' | 'static' | 'abnormal'>('all')
const trendRef = ref<HTMLElement | null>(null)
let trendChart: ReturnType<typeof echarts.init> | null = null

type EventFilter = 'all' | 'sos' | 'fall' | 'static' | 'abnormal'

const filterTabs = computed<Array<{ type: EventFilter; label: string; count: number }>>(() => {
  const e = props.events
  return [
    { type:'all',      label:'全部',  count: e.length },
    { type:'sos',      label:'SOS',   count: e.filter(x=>x.eventType==='sos').length },
    { type:'fall',     label:'跌倒',  count: e.filter(x=>x.eventType==='fall').length },
    { type:'static',   label:'静止',  count: e.filter(x=>x.eventType==='static').length },
    { type:'abnormal', label:'异常',  count: e.filter(x=>x.eventType==='abnormal').length }
  ]
})

const filteredEvents = computed(() =>
  currentFilter.value === 'all' ? props.events : props.events.filter(e => e.eventType === currentFilter.value)
)

const adviceTextMap: Record<string, string> = {
  sos: '定位派单 / 语音回呼',
  fall: '医疗联动 / 就近支援',
  static: '语音确认 / 区域巡检',
  abnormal: '指标复测 / 追踪班组'
}

const isPriorityEvent = (event?: SafetyEvent) => event?.level === 'critical' || event?.eventType === 'sos' || event?.eventType === 'fall'
const eventMinutes = (event?: SafetyEvent) => Math.max(0, Number(event?.durationMinutes) || 0)
const eventLevelText = (event?: SafetyEvent) => event?.level ? getEventLevelText(event.level) : '一般'
const eventDeptText = (event?: SafetyEvent) => event?.dept || '未分组'
const eventLocationText = (event?: SafetyEvent) => event?.location || event?.dept || '未接入定位'
const eventAdviceText = (event?: SafetyEvent) => adviceTextMap[event?.eventType || ''] || adviceTextMap.abnormal
const eventSourceText = (event?: SafetyEvent) => getEventSourceText(event?.eventSource || '')
const eventVisualTone = (event?: SafetyEvent) => getEventLevelTone(event?.level || 'medium')
const isDurationHot = (event?: SafetyEvent) => event?.slaStatus === 'OVERDUE' || eventMinutes(event) > (isPriorityEvent(event) ? 15 : 60)

const eventDurationText = (event?: SafetyEvent) => {
  const minutes = eventMinutes(event)
  if (!minutes) return '刚触发'
  if (minutes >= 60) return `${Math.floor(minutes / 60)}H ${minutes % 60}M`
  return `${minutes} MIN`
}

const eventStageText = (event?: SafetyEvent) => {
  if (event?.statusLabel) return String(event.statusLabel)
  if (event?.status) return getEventStatusText(event.status)
  return '待确认'
}

const eventStageClass = (event?: SafetyEvent) => {
  const status = String(event?.status || '').toUpperCase()
  if (status === 'RESOLVED' || status === 'FALSE_ALARM') return 'stage-safe'
  if (event?.slaStatus === 'OVERDUE') return 'stage-hot'
  if (event?.level === 'critical') return 'stage-priority'
  return 'stage-normal'
}

const buildTrendChart = () => {
  if (!trendRef.value || props.trendData.length === 0) return
  if (trendChart) trendChart.dispose()
  trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    grid:{left:0,right:0,top:2,bottom:0},
    xAxis:{type:'category',data:props.trendData.map(d=>d.date||d.label||''),show:false},
    yAxis:{type:'value',show:false},
    series:[{type:'line',data:props.trendData.map(d=>d.count||d.value||0),smooth:true,symbol:'none',lineStyle:{color:'#ff4757',width:1.5},areaStyle:{color:'rgba(255,71,87,.12)'}}],
    tooltip:{trigger:'axis',backgroundColor:'rgba(10,22,42,.9)',borderColor:'rgba(255,71,87,.3)',textStyle:{color:'#fff',fontSize:10},formatter:(p)=>`${p[0].name}: ${p[0].value}条`}
  })
}

watch(() => props.trendData, async () => { await nextTick(); buildTrendChart() }, { deep:true })
onUnmounted(() => { if (trendChart) trendChart.dispose() })
</script>

<style scoped lang="scss">
$white:#ffffff; $cyan:#00d4ff; $red:#ff4757; $orange:#ff6b35; $yellow:#ffd32a; $green:#2ed573;
$panel:rgba(10,22,42,.82); $border2:rgba(0,212,255,.07);
$dim:rgba(255,255,255,.45); $dim2:rgba(255,255,255,.22);

.ev-panel { flex:1.5; }

.panel { background:$panel; border:1px solid $border2; border-radius:10px; padding:12px 14px; display:flex; flex-direction:column; min-height:0; backdrop-filter:blur(10px); position:relative; overflow:hidden;
  &::before { content:''; position:absolute; top:0; left:14px; right:14px; height:1px; background:linear-gradient(90deg,transparent,rgba($cyan,.25),transparent); }
}
.ph { display:flex; justify-content:space-between; align-items:center; padding-bottom:10px; margin-bottom:10px; flex-shrink:0; border-bottom:1px solid rgba($cyan,.12); }
.pt { font-size:13px; font-weight:700; color:#fff; display:flex; align-items:center; gap:8px; letter-spacing:.5px; }
.pt-bar { width:3px; height:14px; border-radius:2px; flex-shrink:0; }
.red-bar { background:$red; box-shadow:0 0 8px $red; }
.pt-cnt { font-family:'JetBrains Mono','Courier New',monospace; color:$red; font-size:14px; font-weight:700; }
.tabs { display:flex; gap:4px; }
.tb { font-size:12px; padding:4px 10px; border-radius:4px; border:1px solid rgba($cyan,.15); color:rgba($white,.7); cursor:pointer; transition:all .15s;
  &.on, &:hover { background:rgba($cyan,.14); border-color:rgba($cyan,.45); color:$cyan; }
}
.ev-source { font-size:11px; padding:2px 6px; border:1px solid rgba($cyan,.25); color:$cyan; border-radius:3px; white-space:nowrap; }

.ev-trend { height:32px; flex-shrink:0; margin-bottom:6px; }
.ev-list { flex:1; overflow-y:auto; min-height:0; display:flex; flex-direction:column; gap:6px; }
.ev-empty { text-align:center; padding:24px; color:rgba($green,.8); font-size:13px; }

.ev {
  display:grid; grid-template-columns:4px minmax(0, 1fr); align-items:stretch; border-radius:6px; overflow:hidden;
  border:1px solid rgba($cyan,.2); background:rgba($cyan,.035);
  cursor:pointer; transition:background .15s; flex-shrink:0;
  &:hover { background:rgba($cyan,.08); border-color:rgba($cyan,.4); }
  &.ev-danger { border-color:rgba($red,.28); background:rgba($red,.05); }
  &.ev-danger:hover { background:rgba($red,.1); border-color:rgba($red,.45); }
  &.ev-warning { border-color:rgba($orange,.24); background:rgba($orange,.045); }
  &.ev-warning:hover { background:rgba($orange,.09); border-color:rgba($orange,.42); }
  &.ev-info { border-color:rgba($green,.2); background:rgba($green,.035); }
  &.ev-info:hover { background:rgba($green,.075); border-color:rgba($green,.36); }
}
.ev-side {
  width:4px; flex-shrink:0;
  background:linear-gradient(180deg,$cyan,rgba($cyan,.3));
}
.side-danger { background:linear-gradient(180deg,$red,rgba($red,.3)); }
.side-warning { background:linear-gradient(180deg,$orange,rgba($orange,.3)); }
.side-info { background:linear-gradient(180deg,$green,rgba($green,.3)); }

.ev-main {
  min-width:0;
  display:grid;
  grid-template-columns: minmax(180px, 1.35fr) minmax(112px, .78fr) minmax(124px, .9fr) minmax(132px, 1fr) minmax(82px, .52fr) minmax(104px, .72fr) minmax(84px, .48fr);
  align-items:center;
  gap:0 10px;
  padding:9px 12px;
}
.ev-primary,
.ev-field {
  min-width:0;
}
.ev-primary {
  display:flex;
  flex-direction:column;
  gap:4px;
}
.ev-title-row {
  min-width:0;
  display:flex;
  align-items:center;
  gap:6px;
}
.ev-level {
  flex-shrink:0;
  font-size:12px;
  line-height:1;
  padding:3px 6px;
  border-radius:3px;
  font-weight:700;
  font-family:'JetBrains Mono','Courier New',monospace;
}
.ev-level-critical { background:rgba($red,.22); color:#ff6b7b; border:1px solid rgba($red,.35); }
.ev-level-high { background:rgba($orange,.2); color:#ff9f43; border:1px solid rgba($orange,.32); }
.ev-level-medium { background:rgba($cyan,.15); color:#70dfff; border:1px solid rgba($cyan,.28); }
.ev-level-low { background:rgba($green,.12); color:#2ed573; border:1px solid rgba($green,.25); }
.ev-type {
  min-width:0;
  overflow:hidden;
  text-overflow:ellipsis;
  white-space:nowrap;
  font-size:13px;
  font-weight:700;
  color:#fff;
  letter-spacing:.3px;
}
.ev-advice {
  min-width:0;
  overflow:hidden;
  text-overflow:ellipsis;
  white-space:nowrap;
  font-size:11px;
  color:rgba($cyan,.8);
}
.ev-field {
  display:flex;
  flex-direction:column;
  gap:3px;
  padding-left:10px;
  border-left:1px solid rgba(255,255,255,.07);
}
.ev-k {
  font-size:11px;
  line-height:1;
  color:rgba(255,255,255,.45);
  letter-spacing:.5px;
}
.ev-v {
  min-width:0;
  overflow:hidden;
  text-overflow:ellipsis;
  white-space:nowrap;
  font-size:12px;
  line-height:1.25;
  color:rgba(255,255,255,.88);
  small {
    margin-left:5px;
    color:rgba(255,255,255,.45);
    font-family:'JetBrains Mono','Courier New',monospace;
    font-size:10px;
  }
}
.ev-link {
  cursor:pointer;
  &:hover { color:$cyan; text-decoration:underline; }
}
.ev-duration-value {
  font-family:'JetBrains Mono','Courier New',monospace;
  color:#ff9f43;
  &.is-hot { color:$red; font-weight:700; }
}
.ev-stage-value {
  font-weight:700;
  &.stage-hot { color:$red; }
  &.stage-priority { color:#ff9f43; }
  &.stage-safe { color:$green; }
  &.stage-normal { color:$cyan; }
}
@keyframes blink { 0%,100%{opacity:1} 50%{opacity:.3} }

.ev-action-field {
  align-items:flex-end;
  padding-left:8px;
}
.ev-age { color:rgba(255,255,255,.5); font-family:'JetBrains Mono','Courier New',monospace; font-size:11px; }
.ev-act {
  margin-top:2px; font-size:12px; line-height:1; padding:5px 10px; border-radius:4px; cursor:pointer; letter-spacing:.5px; font-weight:600;
  background:rgba($cyan,.1); border:1px solid rgba($cyan,.35); color:$cyan;
  transition:all .15s;
  &:hover { background:rgba($cyan,.22); border-color:rgba($cyan,.6); }
}

@media (max-width: 980px) {
  .ev-main {
    grid-template-columns:minmax(0, 1.2fr) repeat(2, minmax(0, 1fr));
    gap:7px 8px;
  }
  .ev-action-field { align-items:flex-start; }
}

@media (max-width: 640px) {
  .ph {
    align-items:flex-start;
    gap:8px;
    flex-direction:column;
  }
  .tabs {
    flex-wrap:wrap;
  }
  .ev-main {
    grid-template-columns:1fr 1fr;
  }
  .ev-primary {
    grid-column:1 / -1;
  }
  .ev-location,
  .ev-stage {
    grid-column:1 / -1;
  }
  .ev-field {
    padding-left:0;
    border-left:none;
  }
  .ev-action-field {
    grid-column:1 / -1;
    display:grid;
    grid-template-columns:auto minmax(0, 1fr) auto;
    align-items:center;
    gap:6px;
  }
  .ev-act {
    margin-top:0;
  }
}
</style>
