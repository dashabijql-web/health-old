<template>
  <div class="hm-page-shell cc sc-page">
    <PageHeroHeader
      class="sc-hero"
      variant="cockpit"
      eyebrow="Command Center"
      title="安全指挥中心"
      :description="safetyHeroDescription"
    >
      <template #meta>
        <div class="sc-hero-meta">
          <span :class="['sc-live-dot', isSafe ? 'is-safe' : 'is-danger']"></span>
          <span :class="['hm-status-chip', isSafe ? 'hm-status-chip--success' : 'hm-status-chip--danger']">
            {{ isSafe ? '当前安全' : '存在高危' }}
          </span>
          <span class="sc-hero-date">{{ currentDate }}</span>
          <span class="sc-hero-time">{{ currentTime }}</span>
        </div>
      </template>
      <template #actions>
        <div class="sc-emergency-actions">
          <button class="eb eb-o" @click="emergencyCall">呼叫</button>
          <button class="eb eb-o" @click="emergencyBroadcast">广播</button>
          <button class="eb eb-r" @click="emergencyEvacuate">撤离</button>
        </div>
      </template>
    </PageHeroHeader>

    <MetricStrip
      class="sc-summary-strip"
      :items="safetyMetricItems"
      dense
      clickable
      @select="handleSafetyMetricSelect"
    />

    <!-- BODY GRID -->
    <div class="body">

      <!-- Col1: Dept Risk (row 2-3) -->
      <DeptRankTable
        class="dept-panel panel-enter"
        style="--delay:.05s"
        :departments="deptsSorted"
        @showDept="showDeptDetail"
      />

      <!-- Col2, Row2: Handling Progress -->
      <div class="panel handle-panel panel-enter" style="--delay:.1s">
        <div class="ph">
          <div class="phb phb-g"></div>
          <span class="ph-t">预警处置进度</span>
          <span class="ph-bx ph-bx-g">实时</span>
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
          <div class="rtypes">
            <div v-for="tp in typeHandleProgress" :key="tp.key" class="rt-row">
              <span class="rt-l">{{ tp.key }}预警</span>
              <div class="rt-t">
                <div class="rt-f" :style="{ width: tp.rate + '%', background: `linear-gradient(90deg,${tp.color},${tp.color}88)` }"></div>
              </div>
              <span class="rt-p" :style="{ color: tp.color }">{{ tp.rate }}%</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Col3, Row2: TOP5 -->
      <RiskPersonPanel
        :persons="top5Persons"
        @callAll="emergencyCall"
        @showPerson="onShowPerson"
      />

      <!-- Col4, Row2: Vitals Sparklines -->
      <div class="panel vitals-panel panel-enter" style="--delay:.2s">
        <div class="ph">
          <div class="phb phb-c"></div>
          <span class="ph-t">体征群体基线（今日真实数据）</span>
        </div>
        <div class="vitals-body">
          <div v-for="v in vitalsRows" :key="v.key" class="vrow">
            <span class="v-lbl">{{ v.label }}</span>
            <div class="v-spk">
              <span v-if="!v.hasTrend" class="panel-empty">数据不足</span>
              <svg viewBox="0 0 260 48" preserveAspectRatio="none">
                <defs>
                  <linearGradient :id="v.gradId" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" :stop-color="v.color" stop-opacity=".35"/>
                    <stop offset="100%" :stop-color="v.color" stop-opacity="0"/>
                  </linearGradient>
                </defs>
                <line x1="0" y1="40" x2="260" y2="40" stroke="rgba(255,255,255,.05)" stroke-width="1"/>
                <path v-if="v.hasTrend && v.areaPath" :d="v.areaPath" :fill="`url(#${v.gradId})`" opacity=".7"/>
                <path v-if="v.hasTrend && v.linePath" :d="v.linePath" fill="none" :stroke="v.color" stroke-width="2" stroke-linecap="round" class="spk-line"/>
                <circle v-if="v.hasTrend && v.endX != null" :cx="v.endX" :cy="v.endY" r="4" :fill="v.color" :stroke="v.color + '44'" stroke-width="8" class="spk-dot"/>
              </svg>
            </div>
            <div class="v-val-wrap">
              <div class="v-val" :style="{ color: v.color }">{{ v.val }}</div>
              <div class="v-unit">{{ v.unit }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Col2, Row3: Area Distribution -->
      <AreaMapGrid
        class="area-panel panel-enter"
        style="--delay:.25s"
        :areas="areas"
        title="部门预警分布"
        @showArea="showAreaDetail"
      />

      <!-- Col3, Row3: Warning Type Donut -->
      <div class="panel dist-panel panel-enter" style="--delay:.3s">
        <div class="ph">
          <div class="phb phb-o"></div>
          <span class="ph-t">预警类型分布</span>
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
              <text x="75" y="70" text-anchor="middle" fill="#dff0ff" font-size="17" font-weight="700" font-family="sans-serif">{{ warningTotal }}</text>
              <text x="75" y="86" text-anchor="middle" fill="#3a5268" font-size="10">今日预警数</text>
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

      <!-- Col4, Row3: 7-day Trend -->
      <div class="panel trend-panel panel-enter" style="--delay:.35s">
        <div class="ph">
          <div class="phb phb-p"></div>
          <span class="ph-t">近7日预警趋势</span>
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
            <!-- data point dots -->
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
              <div class="ts-l">7日处理率</div>
            </div>
            <div class="ts-card">
              <div class="ts-v" :style="{ color: trendChange >= 0 ? '#ffd600' : '#00e676' }">{{ trendChange >= 0 ? '▲' : '▼' }}{{ Math.abs(trendChange) }}%</div>
              <div class="ts-l">较昨日变化</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Col5: Realtime Alert List (full height) -->
      <div class="rcol panel-enter" style="--delay:.05s">
        <EventPanel
          :events="events"
          :trend-data="[]"
          @showDetail="showEventDetail"
          @showPerson="onShowPersonFromEvent"
          @showDept="showDeptDetail"
          @handle="onHandleEvent"
        />
        <div class="rbot">
          <div class="rbot-t">7 日 汇 总</div>
          <div class="rbot-g">
            <div class="rbc"><div class="rbc-v" style="color:#00c8ff">{{ trend7dayTotal }}</div><div class="rbc-l">近7日总量</div></div>
            <div class="rbc"><div class="rbc-v" style="color:#00e676">{{ warningHandledRate }}%</div><div class="rbc-l">处理率</div></div>
            <div class="rbc"><div class="rbc-v" style="color:#a855f7">{{ vitalAvg.temperature > 0 ? vitalAvg.temperature.toFixed(1) + '°' : '--' }}</div><div class="rbc-l">群体平均体温</div></div>
            <div class="rbc"><div class="rbc-v" style="color:#ffd600">{{ vitalAvg.bloodOxygen > 0 ? Math.round(vitalAvg.bloodOxygen) + '%' : '--' }}</div><div class="rbc-l">群体平均血氧</div></div>
          </div>
        </div>
      </div>

    </div><!-- /body -->

    <!-- DIALOGS -->
    <SafetyCommandDialogs
      v-model:eventDialogVisible="eventDialogVisible"
      v-model:areaDialogVisible="areaDialogVisible"
      v-model:deptDialogVisible="deptDialogVisible"
      v-model:broadcastDialogVisible="broadcastDialogVisible"
      v-model:contactDialogVisible="contactDialogVisible"
      v-model:infoDialogVisible="infoDialogVisible"
      v-model:broadcastContent="broadcastContent"
      :currentEvent="currentEvent"
      :currentArea="currentArea"
      :currentDept="currentDept"
      :deptAiReport="deptAiReport"
      :deptAiRendered="deptAiRendered"
      :deptAiLoading="deptAiLoading"
      :deptAiTime="deptAiTime"
      :infoDialogTitle="infoDialogTitle"
      :infoDialogContent="infoDialogContent"
      @handleEvent="closeEventDialogAfterHandle"
      @showPersonFromEvent="closeEventDialogAfterShowPerson"
      @showInfo="showInfoDialog"
      @deptDialogOpen="onDeptDialogOpen"
      @handleDeptAi="handleDeptAi"
      @confirmBroadcast="confirmBroadcast"
    />

    <EventHandleDialog
      v-model:visible="handleDialogVisible"
      :event="handleEvent"
      @handled="onEventHandled"
    />
    <PersonDetailDrawer
      v-model:visible="personDrawerVisible"
      :userCode="personDrawerUserCode"
      :userName="personDrawerUserName"
      @emergency="showEventDetail"
    />

  </div><!-- /cc -->
</template>

<script setup>
import { computed } from 'vue'
import PageHeroHeader from '@/components/health-shell/PageHeroHeader.vue'
import MetricStrip from '@/components/health-shell/MetricStrip.vue'
import DeptRankTable from '../components/DeptRankTable.vue'
import RiskPersonPanel from '../components/RiskPersonPanel.vue'
import AreaMapGrid from '../components/AreaMapGrid.vue'
import EventPanel from '../components/EventPanel.vue'
import SafetyCommandDialogs from '../components/SafetyCommandDialogs.vue'
import EventHandleDialog from '../components/EventHandleDialog.vue'
import PersonDetailDrawer from '../components/PersonDetailDrawer.vue'
import {
  buildAreasFromDepartments,
  buildDeptRankData,
  buildDonutSegments,
  buildTop5RiskPersons,
  buildTrendChange,
  buildTrendPath,
  buildTrend7dayTotal,
  buildTypeHandleProgress,
  buildVitalsRows,
} from '../safety-command-view-model'
import { useSafetyCommandInteractions } from '../safety-command-interactions'
import { useSafetyCommandPageData } from '../use-safety-command-page-data'

const {
  areas,
  currentDate,
  currentTime,
  departments,
  events,
  handledCount,
  processWarningData,
  riskPersons,
  stats,
  vitalAvg,
  vitalsHistory,
  watchStatus,
  warningTrend
} = useSafetyCommandPageData({ legacy: true })

// ── Derived KPI ───────────────────────────────────────────────────────────────
const isSafe = computed(() => stats.value.sos === 0 && stats.value.fall === 0)
const pendingCount = computed(() => events.value.length)
const safetyHeroDescription = computed(() =>
  `当前待处置 ${pendingCount.value} 条，已闭环 ${handledCount.value} 条，手表在线 ${watchStatus.value.online}/${watchStatus.value.total || '--'}。`
)
const safetyMetricItems = computed(() => [
  {
    key: 'underground',
    label: '井下人数',
    value: stats.value.underground,
    note: `正常 ${stats.value.normal} / 总数 ${stats.value.total}`,
    tone: 'primary'
  },
  {
    key: 'sos',
    label: 'SOS 求救',
    value: stats.value.sos,
    note: stats.value.sos > 0 ? '立即处置' : '无紧急求救',
    tone: stats.value.sos > 0 ? 'danger' : 'success'
  },
  {
    key: 'fall',
    label: '跌倒检测',
    value: stats.value.fall,
    note: stats.value.fall > 0 ? '需要复核' : '无跌倒事件',
    tone: stats.value.fall > 0 ? 'danger' : 'success'
  },
  {
    key: 'alerts',
    label: '其他预警',
    value: stats.value.static + stats.value.abnormal,
    note: `静止 ${stats.value.static} / 异常 ${stats.value.abnormal}`,
    tone: stats.value.static + stats.value.abnormal > 0 ? 'warning' : 'primary'
  },
  {
    key: 'watch',
    label: '手表状态',
    value: `${watchStatus.value.online}/${watchStatus.value.total || '--'}`,
    note: `离线 ${watchStatus.value.offline} / 低电 ${watchStatus.value.lowBattery}`,
    tone: watchStatus.value.offline > 20 || watchStatus.value.lowBattery > 30 ? 'warning' : 'success'
  }
])
// 优先用 watchStatus.online（来自 getStatistics().onlineUsers）
const warningHandledRate = computed(() => {
  const total = pendingCount.value + handledCount.value
  return total > 0 ? Math.round(handledCount.value / total * 100) : 0
})
const warningTotal = computed(() => pendingCount.value + handledCount.value)
const handleSafetyMetricSelect = (item) => handleKpiDetail(item.key)

// ── Dept ranking ──────────────────────────────────────────────────────────────
const deptsSorted = computed(() => buildDeptRankData(departments.value))

// ── TOP5 ──────────────────────────────────────────────────────────────────────
const top5Persons = computed(() => buildTop5RiskPersons(riskPersons.value))

// ── Donut ─────────────────────────────────────────────────────────────────────
const donutSegments = computed(() => buildDonutSegments(events.value))

// ── Handling progress per type ────────────────────────────────────────────────
const typeHandleProgress = computed(() => buildTypeHandleProgress(events.value, handledCount.value))
const vitalsRows = computed(() => buildVitalsRows(vitalAvg.value, vitalsHistory))

// ── 7-day trend ───────────────────────────────────────────────────────────────
const trendPath = computed(() => buildTrendPath(warningTrend.value))
const trend7dayTotal = computed(() => buildTrend7dayTotal(warningTrend.value))
const trendChange = computed(() => buildTrendChange(warningTrend.value))

const {
  areaDialogVisible,
  broadcastContent,
  broadcastDialogVisible,
  closeEventDialogAfterHandle,
  closeEventDialogAfterShowPerson,
  confirmBroadcast,
  contactDialogVisible,
  currentArea,
  currentDept,
  currentEvent,
  deptAiLoading,
  deptAiRendered,
  deptAiReport,
  deptAiTime,
  deptDialogVisible,
  emergencyBroadcast,
  emergencyCall,
  emergencyEvacuate,
  eventDialogVisible,
  handleDeptAi,
  handleDialogVisible,
  handleEvent,
  handleKpiDetail,
  infoDialogContent,
  infoDialogTitle,
  infoDialogVisible,
  onDeptDialogOpen,
  onEventHandled,
  onHandleEvent,
  onShowPerson,
  onShowPersonFromEvent,
  personDrawerUserCode,
  personDrawerUserName,
  personDrawerVisible,
  showAreaDetail,
  showDeptDetail,
  showEventDetail,
  showInfoDialog
} = useSafetyCommandInteractions({
  statsRef: stats,
  watchStatusRef: watchStatus,
  handledCountRef: handledCount,
  eventsRef: events,
  riskPersonsRef: riskPersons
})

</script>

<style scoped lang="scss">
@import '../safety-command.scss';
</style>
