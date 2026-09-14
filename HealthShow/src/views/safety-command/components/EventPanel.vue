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
        :class="['ev', (ev.eventType==='sos'||ev.eventType==='fall') ? 'ev-crit' : 'ev-warn']"
        @click="$emit('showDetail', ev)"
      >
        <div :class="['ev-side', (ev.eventType==='sos'||ev.eventType==='fall') ? '' : 'side-w']"></div>
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
            <span class="ev-k">滞留</span>
            <span :class="['ev-v', 'ev-duration-value', { 'is-hot': isDurationHot(ev), 'is-warn': !isPriorityEvent(ev) }]">
              {{ eventDurationText(ev) }}
            </span>
          </div>

          <div class="ev-field ev-stage">
            <span class="ev-k">阶段</span>
            <span :class="['ev-v', 'ev-stage-value', eventStageClass(ev)]">{{ eventStageText(ev) }}</span>
          </div>

          <div class="ev-field ev-action-field">
            <span class="ev-k">发生</span>
            <span class="ev-v ev-age">{{ ev.time || '刚刚' }}</span>
            <span class="ev-act" @click.stop="$emit('handle', ev)">处理</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch, type PropType } from 'vue'
import * as echarts from '@/utils/echarts-setup'

interface SafetyEvent {
  id?: number | string
  eventType?: string
  level?: string
  type?: string
  icon?: string
  user?: string
  userCode?: string
  durationMinutes?: number
  dept?: string
  location?: string
  eventSource?: string
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

const levelTextMap = { critical: '特急', high: '紧急', medium: '一般', low: '轻微' }
const adviceTextMap = {
  sos: '定位派单 / 语音回呼',
  fall: '医疗联动 / 就近支援',
  static: '语音确认 / 区域巡检',
  abnormal: '复测指标 / 追踪班组'
}

const isPriorityEvent = (event?: SafetyEvent) => event?.eventType === 'sos' || event?.eventType === 'fall'
const eventMinutes = (event?: SafetyEvent) => Math.max(0, Number(event?.durationMinutes) || 0)
const eventLevelText = (event?: SafetyEvent) => levelTextMap[event?.level || ''] || levelTextMap.medium
const eventDeptText = (event?: SafetyEvent) => event?.dept || '未分组'
const eventLocationText = (event?: SafetyEvent) => event?.location || event?.dept || '未定位'
const eventAdviceText = (event?: SafetyEvent) => adviceTextMap[event?.eventType || ''] || adviceTextMap.abnormal
const eventSourceText = (event?: SafetyEvent) => ({
  HEALTH_THRESHOLD: '体征预警',
  DEVICE_ALARM: '设备报警',
  TREND_WARNING: '趋势风险'
}[event?.eventSource || ''] || '历史事件')
const isDurationHot = (event?: SafetyEvent) => eventMinutes(event) > (isPriorityEvent(event) ? 5 : 10)

const eventDurationText = (event?: SafetyEvent) => {
  const minutes = eventMinutes(event)
  if (!minutes) return '刚触发'
  if (minutes >= 60) return `${Math.floor(minutes / 60)}H ${minutes % 60}M`
  return `${minutes} MIN`
}

const eventStageText = (event?: SafetyEvent) => {
  const minutes = eventMinutes(event)
  if (!minutes) return '待确认'
  if (isPriorityEvent(event) && minutes > 10) return '升级联动'
  if (isPriorityEvent(event) && minutes > 5) return '超时响应'
  if (event?.eventType === 'static' && minutes > 15) return '复核超时'
  if (event?.eventType === 'abnormal' && minutes > 10) return '待复测'
  return '处置中'
}

const eventStageClass = (event?: SafetyEvent) => {
  if (isDurationHot(event)) return 'stage-hot'
  if (isPriorityEvent(event)) return 'stage-priority'
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
$cyan:#00d4ff; $red:#ff4757; $orange:#ff6b35; $yellow:#ffd32a; $green:#2ed573;
$panel:rgba(10,22,42,.82); $border2:rgba(0,212,255,.07);
$dim:rgba(255,255,255,.45); $dim2:rgba(255,255,255,.22);

.ev-panel { flex:1.5; }

.panel { background:$panel; border:1px solid $border2; border-radius:5px; padding:10px 12px; display:flex; flex-direction:column; min-height:0; backdrop-filter:blur(6px); position:relative; overflow:hidden;
  &::before { content:''; position:absolute; top:0; left:14px; right:14px; height:1px; background:linear-gradient(90deg,transparent,rgba($cyan,.15),transparent); }
}
.ph { display:flex; justify-content:space-between; align-items:center; padding-bottom:8px; margin-bottom:8px; flex-shrink:0; border-bottom:1px solid rgba($cyan,.08); }
.pt { font-size:11px; font-weight:600; color:#fff; display:flex; align-items:center; gap:7px; letter-spacing:.5px; text-transform:uppercase; }
.pt-bar { width:2px; height:12px; border-radius:1px; flex-shrink:0; }
.red-bar { background:$red; box-shadow:0 0 6px $red; }
.pt-cnt { font-family:'JetBrains Mono','Courier New',monospace; color:$red; font-size:12px; }
.tabs { display:flex; gap:2px; }
.tb { font-size:9px; padding:2px 7px; border-radius:2px; border:1px solid rgba($cyan,.12); color:$dim; cursor:pointer; transition:all .15s;
  &.on, &:hover { background:rgba($cyan,.1); border-color:rgba($cyan,.35); color:$cyan; }
}
.ev-source { font-size:9px; padding:2px 5px; border:1px solid rgba($cyan,.22); color:$cyan; border-radius:2px; white-space:nowrap; }

.ev-trend { height:32px; flex-shrink:0; margin-bottom:4px; }
.ev-list { flex:1; overflow-y:auto; min-height:0; display:flex; flex-direction:column; gap:4px; }
.ev-empty { text-align:center; padding:20px; color:rgba($green,.7); font-size:11px; }

.ev {
  display:grid; grid-template-columns:3px minmax(0, 1fr); align-items:stretch; border-radius:4px; overflow:hidden;
  border:1px solid rgba($red,.12); background:rgba($red,.03);
  cursor:pointer; transition:background .15s; flex-shrink:0;
  &.ev-warn { border-color:rgba($orange,.12); background:rgba($orange,.03); }
  &:hover { background:rgba($red,.08); }
  &.ev-warn:hover { background:rgba($orange,.08); }
}
.ev-side {
  width:3px; flex-shrink:0;
  background:linear-gradient(180deg,$red,rgba($red,.3));
}
.side-w { background:linear-gradient(180deg,$orange,rgba($orange,.3)); }

.ev-main {
  min-width:0;
  display:grid;
  grid-template-columns: minmax(180px, 1.35fr) minmax(112px, .78fr) minmax(124px, .9fr) minmax(132px, 1fr) minmax(82px, .52fr) minmax(104px, .72fr) minmax(84px, .48fr);
  align-items:center;
  gap:0 10px;
  padding:7px 10px;
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
  font-size:9px;
  line-height:1;
  padding:3px 5px;
  border-radius:2px;
  font-weight:700;
  font-family:'JetBrains Mono','Courier New',monospace;
}
.ev-level-critical { background:rgba($red,.18); color:#ff8a98; border:1px solid rgba($red,.28); }
.ev-level-high { background:rgba($orange,.18); color:#ffb06a; border:1px solid rgba($orange,.28); }
.ev-level-medium { background:rgba($yellow,.12); color:#ffe277; border:1px solid rgba($yellow,.2); }
.ev-level-low { background:rgba($green,.1); color:#75f0a4; border:1px solid rgba($green,.18); }
.ev-type {
  min-width:0;
  overflow:hidden;
  text-overflow:ellipsis;
  white-space:nowrap;
  font-size:11px;
  font-weight:700;
  color:#fff;
  letter-spacing:.3px;
}
.ev-advice {
  min-width:0;
  overflow:hidden;
  text-overflow:ellipsis;
  white-space:nowrap;
  font-size:9px;
  color:rgba($cyan,.72);
}
.ev-field {
  display:flex;
  flex-direction:column;
  gap:3px;
  padding-left:10px;
  border-left:1px solid rgba(255,255,255,.045);
}
.ev-k {
  font-size:8px;
  line-height:1;
  color:rgba(255,255,255,.28);
  letter-spacing:.8px;
}
.ev-v {
  min-width:0;
  overflow:hidden;
  text-overflow:ellipsis;
  white-space:nowrap;
  font-size:10px;
  line-height:1.2;
  color:rgba(255,255,255,.74);
  small {
    margin-left:5px;
    color:rgba(255,255,255,.32);
    font-family:'JetBrains Mono','Courier New',monospace;
    font-size:8px;
  }
}
.ev-link {
  cursor:pointer;
  &:hover { color:$cyan; }
}
.ev-duration-value {
  font-family:'JetBrains Mono','Courier New',monospace;
  color:#ff8a98;
  &.is-warn { color:#ffb06a; }
  &.is-hot { color:$red; animation:blink 1s infinite; }
}
.ev-stage-value {
  font-weight:700;
  &.stage-hot { color:$red; }
  &.stage-priority { color:#ffb06a; }
  &.stage-normal { color:rgba($green,.82); }
}
@keyframes blink { 0%,100%{opacity:1} 50%{opacity:.3} }

.ev-action-field {
  align-items:flex-end;
  padding-left:8px;
}
.ev-age { color:$dim2; font-family:'JetBrains Mono','Courier New',monospace; }
.ev-act {
  margin-top:2px; font-size:9px; line-height:1; padding:4px 8px; border-radius:2px; cursor:pointer; letter-spacing:.5px;
  background:rgba($cyan,.07); border:1px solid rgba($cyan,.25); color:$cyan;
  transition:all .15s;
  &:hover { background:rgba($cyan,.18); }
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
