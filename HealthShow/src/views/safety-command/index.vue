<template>
  <div class="sc-war-room">
    <header :class="['sc-hd', !isSafe && 'is-danger']">
      <div class="sc-hd-left">
        <span :class="['sc-hd-beacon', isSafe ? 'is-safe' : 'is-danger']"></span>
        <h1 class="sc-hd-title">安全指挥中心</h1>
        <span :class="['sc-hd-badge', isSafe ? 'tone-safe' : 'tone-danger']">
          {{ isSafe ? '现场平稳' : '高危介入' }}
        </span>
      </div>
      <div class="sc-hd-right">
        <span class="sc-hd-clock">数据截至 {{ dataAsOf || `${currentDate} ${currentTime}` }}</span>
        <div class="sc-hd-actions">
          <button class="sc-hd-btn sc-hd-btn--outline" type="button" disabled title="请在具体事件中发起呼叫">呼叫 · 未配置</button>
          <button class="sc-hd-btn sc-hd-btn--outline" type="button" disabled title="请在具体事件中发起广播">广播 · 未配置</button>
          <button class="sc-hd-btn sc-hd-btn--danger" type="button" disabled title="请在具体事件中发起撤离">撤离 · 未配置</button>
        </div>
      </div>
    </header>

    <section class="sc-war-grid">
      <div class="sc-situation-stage sc-panel panel-enter" style="--delay:.05s">
        <div class="sc-panel-head">
          <div>
            <span class="sc-panel-kicker">SITUATION STAGE</span>
            <h2>{{ incidentTitle }}</h2>
          </div>
          <span :class="['sc-stage-severity', incidentTone]">{{ incidentStatus }}</span>
        </div>

        <div class="sc-stage-canvas">
          <MineGisMap class="sc-gis-map" />
          <div class="sc-stage-intel">
            <button
              v-for="item in stageIntelItems"
              :key="item.key"
              type="button"
              :disabled="item.key === 'freshness'"
              :class="['sc-stage-intel-card', `tone-${item.tone}`, { 'is-active': activeEventFilter === item.key, 'is-static': item.key === 'freshness' }]"
              @click="item.key !== 'freshness' && applyEventFilter(item.key, item.label)"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <em>{{ item.note }}</em>
            </button>
          </div>

          <div class="sc-stage-map-status">
            <span class="sc-stage-map-status__dot"></span>
            <span>CAD 巷道底图 · 位置数据按后端接入</span>
          </div>

          <div class="sc-stage-action-strip">
            <button
              v-for="action in stageActionItems"
              :key="action.key"
              type="button"
              :class="['sc-stage-action-cell', `tone-${action.tone}`, { 'is-active': activeEventFilter === action.key }]"
              @click="handleStageAction(action)"
            >
              <span>{{ action.label }}</span>
              <strong>{{ action.value }}</strong>
              <em>{{ action.note }}</em>
            </button>
          </div>
        </div>

      </div>

      <aside class="sc-response-queue sc-panel panel-enter" style="--delay:.1s">
        <div class="sc-panel-head">
          <div>
            <span class="sc-panel-kicker">RESPONSE QUEUE</span>
            <h2>现场处置队列</h2>
          </div>
          <span class="sc-queue-count">{{ activeEventFilter === 'all' ? authoritativePendingCount : `${filteredEvents.length}/${authoritativePendingCount}` }}</span>
        </div>

        <div v-if="activeEventFilter !== 'all'" class="sc-active-filter">
          <span>当前筛选：{{ activeEventFilterLabel }}</span>
          <button type="button" @click="clearEventFilter">清除</button>
        </div>

        <div class="sc-queue-list">
          <article
            v-for="(item, index) in commandQueue"
            :key="item.id"
            :class="['sc-queue-card', `tone-${item.tone}`]"
            @click="item.event && showEventDetail(item.event)"
          >
            <span class="sc-queue-order">{{ String(index + 1).padStart(2, '0') }}</span>
            <div class="sc-queue-main">
              <strong>{{ item.title }}</strong>
              <span>{{ item.meta }}</span>
            </div>
            <button
              v-if="item.event"
              type="button"
              class="sc-queue-action"
              @click.stop="item.event && onHandleEvent(item.event)"
            >{{ item.action }}</button>
          </article>
        </div>

        <RiskPersonPanel
          class="sc-risk-lane"
          :persons="top5Persons"
          @showPerson="onShowPerson"
        />
      </aside>
    </section>

    <SafetyCommandSupportGrid
      :donut-segments="donutSegments"
      :events="filteredEvents"
      :event-list-title="activeEventFilter === 'all' ? '未闭环预警' : activeEventFilterLabel"
      :handled-count="handledCount"
      :pending-count="authoritativePendingCount"
      :trend7day-total="trend7dayTotal"
      :trend-change="trendChange"
      :trend-path="trendPath"
      :warning-handled-rate="warningHandledRate"
      :warning-trend="warningTrend"
      @show-dept="openDepartment"
      @show-event="showEventDetail"
      @show-person-from-event="onShowPersonFromEvent"
      @handle-event="onHandleEvent"
    />

    <DepartmentIncidentDrawer
      v-model:visible="departmentDrawerVisible"
      :department="currentDepartment"
      :events="events"
      @show-event="openEventFromDepartment"
      @handle-event="queueDepartmentEventHandling"
      @show-profile="openEmployeeProfile"
    />

    <IncidentCommandDrawer
      v-model:visible="incidentDrawerVisible"
      :event="currentEvent"
      source-page="safety-command"
      :return-available="route.query.from === 'dashboard'"
      @updated="fetchAllData"
      @return-to-origin="returnToDashboard"
    />
    <PersonDetailDrawer
      v-model:visible="personDrawerVisible"
      :userCode="personDrawerUserCode"
      :userName="personDrawerUserName"
    />

  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCommandCenterIncident } from '@/api/command-center'
import { useTimeoutTask } from '@/composables/useTimeoutTask'
import RiskPersonPanel from './components/RiskPersonPanel.vue'
import MineGisMap from './components/MineGisMap.vue'
import SafetyCommandSupportGrid from './components/SafetyCommandSupportGrid.vue'
import DepartmentIncidentDrawer from './components/DepartmentIncidentDrawer.vue'
import IncidentCommandDrawer from './components/IncidentCommandDrawer.vue'
import PersonDetailDrawer from './components/PersonDetailDrawer.vue'
import {
  buildDeptRankData,
  buildDonutSegments,
  buildStageActionItems,
  buildStageIntelItems,
  buildTop5RiskPersons,
  buildTrendChange,
  buildTrendPath,
  buildTrend7dayTotal,
  mapWarningToEvent,
} from './safety-command-view-model'
import { useSafetyCommandInteractions } from './safety-command-interactions'
import { useSafetyCommandPageData } from './use-safety-command-page-data'
import { buildDashboardReturnQuery } from './safety-command-workflow'

const {
  areas,
  commandSummary,
  currentDate,
  currentTime,
  dataAsOf,
  departments,
  events,
  fetchAllData,
  handledCount,
  pendingWarnings,
  riskPersons,
  warningTrend
} = useSafetyCommandPageData()

const route = useRoute()
const router = useRouter()
const activeEventFilter = ref('all')
const activeEventFilterLabel = ref('全部未闭环预警')
const departmentDrawerVisible = ref(false)
const currentDepartment = ref(null)
const queuedDepartmentEvent = ref(null)
const {
  start: startDepartmentDrawerTransition,
  stop: stopDepartmentDrawerTransition
} = useTimeoutTask(() => {
  if (!queuedDepartmentEvent.value) return
  const queuedEvent = queuedDepartmentEvent.value
  queuedDepartmentEvent.value = null
  showEventDetail(queuedEvent)
}, 420)

// ── Derived KPI ───────────────────────────────────────────────────────────────
const isSafe = computed(() => Number(commandSummary.value.warning?.criticalPending || 0) === 0)
const authoritativePendingCount = computed(() => Number(pendingWarnings.value ?? events.value.length))
const warningHandledRate = computed(() => {
  const total = authoritativePendingCount.value + handledCount.value
  return total > 0 ? Math.round(handledCount.value / total * 100) : 0
})

// ── Dept ranking ──────────────────────────────────────────────────────────────
const deptsSorted = computed(() => buildDeptRankData(departments.value))

const incidentTone = computed(() => {
  if (Number(commandSummary.value.warning?.criticalPending || 0) > 0) return 'danger'
  if (authoritativePendingCount.value > 0) return 'warning'
  return 'safe'
})

const incidentStatus = computed(() => ({
  danger: '一级响应',
  warning: '处置跟进',
  safe: '值守巡查'
}[incidentTone.value]))

const incidentTitle = computed(() => {
  const first = events.value[0]
  if (!first) return '全矿态势稳定，保持值守'
  return `${first.location || first.dept || '现场'} · ${first.type || '风险预警'}`
})

const stageIntelItems = computed(() => buildStageIntelItems({
  pendingCount: authoritativePendingCount.value,
  handledCount: handledCount.value,
  criticalCount: commandSummary.value.warning?.criticalPending || 0,
  unassignedCount: commandSummary.value.warning?.unassignedTotal || 0,
  overdueCount: commandSummary.value.warning?.overdueTotal || 0,
  dataAsOf: dataAsOf.value,
  warningHandledRate: warningHandledRate.value,
  deptsSorted: deptsSorted.value
}))
const stageActionItems = computed(() => buildStageActionItems({
  todayNew: commandSummary.value.warning?.todayNew || 0,
  pendingCount: authoritativePendingCount.value,
  handledCount: handledCount.value,
  criticalCount: commandSummary.value.warning?.criticalPending || 0,
  warningHandledRate: warningHandledRate.value
}))
const filteredEvents = computed(() => {
  const list = events.value || []
  if (activeEventFilter.value === 'unassigned') return list.filter((event) => !event.owner || event.owner === '未分派')
  if (activeEventFilter.value === 'overdue') return list.filter((event) => event.slaStatus === 'OVERDUE')
  if (activeEventFilter.value === 'critical') return list.filter((event) => ['critical', 'high'].includes(event.level))
  return list
})

const commandQueue = computed(() => {
  const queue = filteredEvents.value.slice(0, 5).map((event) => ({
    id: event.id || `${event.user}-${event.time}-${event.type}`,
    title: `${event.user || '未知人员'} · ${event.type || '预警'}`,
    meta: `${event.dept || event.location || '未知区域'} / ${event.time || '刚刚'}`,
    action: '处理',
    tone: event.eventType === 'sos' || event.eventType === 'fall' ? 'danger' : 'warning',
    event
  }))

  if (queue.length) return queue

  return [{
    id: 'safe-duty',
    title: '当前无紧急事件',
    meta: activeEventFilter.value === 'all' ? '当前已加载范围内无未闭环预警' : `“${activeEventFilterLabel.value}”暂无匹配事件`,
    action: '',
    tone: 'safe',
    event: null
  }]
})

// ── TOP5 ──────────────────────────────────────────────────────────────────────
const top5Persons = computed(() => buildTop5RiskPersons(riskPersons.value))

// ── Donut ─────────────────────────────────────────────────────────────────────
const donutSegments = computed(() => buildDonutSegments(events.value))

// ── Handling progress per type ────────────────────────────────────────────────

// ── 7-day trend ───────────────────────────────────────────────────────────────
const trendPath = computed(() => buildTrendPath(warningTrend.value))
const trend7dayTotal = computed(() => buildTrend7dayTotal(warningTrend.value))
const trendChange = computed(() => buildTrendChange(warningTrend.value))

const {
  currentEvent,
  incidentDrawerVisible,
  onHandleEvent,
  onShowPerson,
  onShowPersonFromEvent,
  personDrawerUserCode,
  personDrawerUserName,
  personDrawerVisible,
  showEventDetail
} = useSafetyCommandInteractions()

function applyEventFilter(key, label) {
  activeEventFilter.value = key
  activeEventFilterLabel.value = key === 'active-risk' ? '全部未闭环预警' : label
}

function clearEventFilter() {
  activeEventFilter.value = 'all'
  activeEventFilterLabel.value = '全部未闭环预警'
}

function handleStageAction(action) {
  if (action.key === 'today-new') {
    router.push({
      path: '/alert-management/records',
      query: { startDate: currentDate.value, endDate: currentDate.value }
    })
    return
  }
  if (action.key === 'handled' || action.key === 'review') {
    router.push({
      path: '/alert-management/records',
      query: { handleStatus: 'handled', startDate: currentDate.value, endDate: currentDate.value }
    })
    return
  }
  applyEventFilter(action.key, action.label)
}

function openDepartment(department) {
  currentDepartment.value = typeof department === 'string'
    ? (deptsSorted.value.find((item) => item.name === department) || department)
    : department
  departmentDrawerVisible.value = true
}

function openEventFromDepartment(event) {
  queueDepartmentEventHandling(event)
}

function queueDepartmentEventHandling(event) {
  queuedDepartmentEvent.value = event
  departmentDrawerVisible.value = false
  // Wait for Element Plus to remove the first drawer before opening the next overlay.
  stopDepartmentDrawerTransition()
  startDepartmentDrawerTransition()
}

function openEmployeeProfile(event) {
  departmentDrawerVisible.value = false
  router.push({
    path: '/health-monitor/employee-profile',
    query: {
      empCode: event.userCode || '',
      empName: event.user || '',
      deptName: event.dept || '',
      from: route.fullPath
    }
  })
}

function returnToDashboard() {
  router.push({
    path: '/health-monitor/dashboard',
    query: buildDashboardReturnQuery(currentEvent.value || events.value[0])
  })
}

let routeIncidentKey = ''
watch(
  () => [route.query.warningId, route.query.occurredAt],
  async ([warningId, occurredAt]) => {
    const nextKey = `${warningId || ''}:${occurredAt || ''}`
    if (!warningId || !occurredAt || nextKey === routeIncidentKey) return
    routeIncidentKey = nextKey
    try {
      const response = await getCommandCenterIncident(warningId, occurredAt)
      if (response.code === 200 && response.data) showEventDetail(mapWarningToEvent(response.data))
    } catch {
      // The incident may no longer exist after switching data sources.
    }
  },
  { immediate: true }
)

</script>

<style scoped lang="scss">
@import './safety-command.scss';
</style>
