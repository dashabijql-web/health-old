<template>
  <section class="sc-war-support">
    <div class="sc-panel sc-closure-panel panel-enter" style="--delay:.2s">
      <div class="sc-panel-head">
        <div>
          <span class="sc-panel-kicker">CLOSURE</span>
          <h2>预警闭环进度</h2>
        </div>
        <strong>{{ warningHandledRate }}%</strong>
      </div>
      <div class="resp-body">
        <div class="resp-cards">
          <div class="rc rc-p">
            <div class="rc-n">{{ pendingCount }}</div>
            <div class="rc-l">待处置预警</div>
          </div>
          <div class="rc rc-d">
            <div class="rc-n">{{ handledCount }}</div>
            <div class="rc-l">已处置预警</div>
          </div>
        </div>
        <div>
          <div class="bar-lbl">
            <span>总处置率</span>
            <strong>{{ warningHandledRate }}%</strong>
          </div>
          <div class="bar-t">
            <div class="bar-d" :style="{ width: warningHandledRate + '%' }">
              <span>{{ warningHandledRate }}%</span>
            </div>
            <div class="bar-p" :style="{ left: warningHandledRate + '%', width: (100 - warningHandledRate) + '%' }"></div>
          </div>
        </div>
        <div class="closure-note">按今日权威待办与已处置口径计算</div>
      </div>
    </div>

    <div class="sc-panel sc-dist-panel panel-enter" style="--delay:.32s">
      <div class="sc-panel-head">
        <div>
          <span class="sc-panel-kicker">WARNING MIX</span>
          <h2>预警类型分布</h2>
        </div>
      </div>
      <div class="dist-body">
        <div style="flex-shrink:0">
          <svg width="150" height="150" viewBox="0 0 150 150">
            <circle cx="75" cy="75" r="54" fill="none" stroke="rgba(255,255,255,.04)" stroke-width="22"/>
            <circle
              v-for="(seg, si) in donutSegments" :key="seg.name"
              cx="75" cy="75" r="54" fill="none"
              :stroke="seg.color" stroke-width="22"
              :stroke-dasharray="`${seg.dash} ${seg.rem}`"
              :stroke-dashoffset="seg.offset"
              transform="rotate(-90 75 75)"
              class="donut-seg" :style="{ '--seg-delay': (si * 0.12) + 's', opacity: seg.dash > 0 ? '.88' : '0' }"
            />
            <text x="75" y="70" text-anchor="middle" fill="#dff0ff" font-size="17" font-weight="700" font-family="sans-serif">{{ loadedTypeTotal }}</text>
            <text x="75" y="86" text-anchor="middle" fill="#3a5268" font-size="10">已加载体征预警</text>
          </svg>
        </div>
        <div class="dist-lg">
          <div v-for="seg in donutSegments" :key="seg.name" class="dl-row">
            <div class="dl-dot" :style="{ background: seg.color }"></div>
            <span class="dl-name">{{ seg.name }}预警</span>
            <span class="dl-val" :style="{ color: seg.color }">{{ seg.cnt }}</span>
            <span class="dl-pct">{{ seg.pct }}%</span>
          </div>
        </div>
      </div>
    </div>

    <div class="sc-panel sc-trend-panel panel-enter" style="--delay:.36s">
      <div class="sc-panel-head">
        <div>
          <span class="sc-panel-kicker">7 DAY TREND</span>
          <h2>近7日预警趋势</h2>
        </div>
      </div>
      <div class="trend-body">
        <svg v-if="trendPath" width="100%" height="68%" viewBox="0 0 380 190" preserveAspectRatio="none">
          <defs>
            <linearGradient id="tga" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#ff3b3b" stop-opacity=".28"/>
              <stop offset="100%" stop-color="#ff3b3b" stop-opacity="0"/>
            </linearGradient>
          </defs>
          <line v-for="y in [48,96,144]" :key="y" x1="0" :y1="y" x2="380" :y2="y" stroke="rgba(255,255,255,.05)" stroke-width="1" stroke-dasharray="5,5"/>
          <path :d="trendPath.area" fill="url(#tga)"/>
          <path :d="trendPath.line" fill="none" stroke="#ff3b3b" stroke-width="2.5" stroke-linecap="round" class="trend-line"/>
          <circle
            v-for="(pt, i) in trendPath.pts" :key="i"
            :cx="pt.x" :cy="pt.y" r="4"
            fill="#ff3b3b" stroke="rgba(255,59,59,.25)" stroke-width="7"
            class="trend-dot" :style="{ '--td': (i * 0.08) + 's' }"
          />
          <text v-for="(d, i) in warningTrend" :key="i" :x="i / Math.max(warningTrend.length-1,1) * 376 + 2" y="188" fill="#3a5268" font-size="11">{{ (d.date||d.stat_date||'').slice(5) }}</text>
        </svg>
        <div v-else class="panel-empty" style="flex:1;display:flex;align-items:center;justify-content:center">暂无趋势数据</div>
        <div class="trend-stats">
          <div class="ts-card">
            <div class="ts-v" style="color:#00c8ff">{{ trend7dayTotal }}</div>
            <div class="ts-l">近7日总量</div>
          </div>
          <div class="ts-card">
            <div class="ts-v" style="color:#00e676">{{ warningHandledRate }}%</div>
            <div class="ts-l">今日处置率</div>
          </div>
          <div class="ts-card">
            <div class="ts-v" :style="{ color: trendChange >= 0 ? '#ffd600' : '#00e676' }">{{ trendChange >= 0 ? '▲' : '▼' }}{{ Math.abs(trendChange) }}%</div>
            <div class="ts-l">较昨日变化</div>
          </div>
        </div>
      </div>
    </div>

    <div class="sc-support-events panel-enter" style="--delay:.4s">
      <EventPanel
        :key="eventListTitle"
        :events="events"
        :title="eventListTitle"
        :trend-data="[]"
        @showDetail="emit('show-event', $event)"
        @showPerson="emit('show-person-from-event', $event)"
        @showDept="emit('show-dept', $event)"
        @handle="emit('handle-event', $event)"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, type PropType } from 'vue'
import EventPanel from './EventPanel.vue'

interface DonutSegment {
  name: string
  color: string
  dash: number
  rem: number
  offset: number
  cnt: number
  pct: number
}

interface TrendPoint {
  x: number
  y: number
}

interface TrendPath {
  area: string
  line: string
  pts: TrendPoint[]
}

interface SafetyEvent {
  id?: number | string
  [key: string]: unknown
}

interface WarningTrendItem {
  date?: string
  stat_date?: string
  [key: string]: unknown
}

const props = defineProps({
  donutSegments: { type: Array as PropType<DonutSegment[]>, default: () => [] },
  events: { type: Array as PropType<SafetyEvent[]>, default: () => [] },
  handledCount: { type: Number, default: 0 },
  pendingCount: { type: Number, default: 0 },
  trend7dayTotal: { type: Number, default: 0 },
  trendChange: { type: Number, default: 0 },
  trendPath: { type: Object as PropType<TrendPath | null>, default: null },
  warningHandledRate: { type: Number, default: 0 },
  warningTrend: { type: Array as PropType<WarningTrendItem[]>, default: () => [] },
  eventListTitle: { type: String, default: '未闭环预警' }
})

const loadedTypeTotal = computed(() => props.donutSegments.reduce((sum, item) => sum + Number(item.cnt || 0), 0))

const emit = defineEmits<{
  'show-dept': [department: unknown]
  'show-event': [event: SafetyEvent]
  'show-person-from-event': [event: SafetyEvent]
  'handle-event': [event: SafetyEvent]
}>()
</script>

<style scoped lang="scss">
@import '../safety-command.scss';
</style>
