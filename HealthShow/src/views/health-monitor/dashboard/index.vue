<template>
  <div class="unified-control-page" ref="dmScale" @transitionend.stop @animationend.stop>
    <!-- 顶部状态栏 -->
    <header class="uc-header">
      <div class="uc-brand">
        <span class="uc-live-dot"></span>
        <div class="uc-brand-copy">
          <h1 class="uc-brand-title">职业健康统一管控平台</h1>
          <div class="uc-brand-meta">
            <span class="uc-badge uc-badge--status" :class="snapshotStatusToneClass">{{ healthSnapshotStatusText }}</span>
            <span class="uc-badge uc-badge--refresh">更新于 {{ lastRefreshText }}</span>
          </div>
        </div>
      </div>

      <div class="uc-header-actions">
        <div class="uc-period" role="group" aria-label="统计周期">
          <button v-for="p in periodOptions" :key="p.value" type="button" :class="{ 'is-active': activePeriod === p.value }" @click="switchPeriod(p.value)">
            {{ p.label }}
          </button>
        </div>
        <button type="button" class="uc-icon-button" :class="{ 'is-spinning': isRefreshing }" title="立即刷新数据" @click="fetchData(true)">
          <el-icon><Refresh /></el-icon>
        </button>
        <button type="button" class="uc-icon-button" :title="isFullscreen ? '退出全屏' : '全屏展示'" @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
        </button>
        <div class="uc-clock">{{ currentTime }}</div>
      </div>
    </header>

    <!-- 检索与快捷处置工具栏 -->
    <div class="uc-search-row">
      <div class="uc-search-box">
        <DashboardPersonSearch @select="goToEmployeeProfile" />
      </div>
      <div class="uc-quick-toolbar">
        <button type="button" class="uc-tool-btn uc-tool-btn--ai" title="查看或生成全矿 AI 健康研判报告" @click="toggleMineAiPanel">
          <el-icon><MagicStick /></el-icon>
          <span>全矿 AI 研判</span>
        </button>
        <button type="button" class="uc-tool-btn" @click="$router.push('/alert-management/notifications')">
          <el-icon><FirstAidKit /></el-icon>
          <span>处置中心</span>
        </button>
        <button type="button" class="uc-tool-btn" @click="$router.push('/health-monitor/real-time')">
          <el-icon><Monitor /></el-icon>
          <span>实时大盘</span>
        </button>
        <button type="button" class="uc-tool-btn" @click="$router.push('/health-monitor/mine-entry')">
          <el-icon><Odometer /></el-icon>
          <span>班前准入</span>
        </button>
        <button type="button" class="uc-tool-btn" @click="$router.push('/admin/device-list')">
          <el-icon><Cpu /></el-icon>
          <span>设备管理</span>
        </button>
      </div>
    </div>

    <!-- 紧凑 KPI 状态行 -->
    <div class="uc-kpi-row uc-overview-kpi" aria-label="关键指标看板">
      <button v-for="item in headerMetricStripItems" :key="item.key" type="button" :class="['uc-kpi-item', `tone-${item.tone}`]" @click="onHeaderMetricSelect(item)">
        <div class="uc-kpi-meta">
          <span class="uc-kpi-label">{{ item.label }}</span>
          <small v-if="item.note" class="uc-kpi-note">{{ item.note }}</small>
        </div>
        <strong class="uc-kpi-val">{{ item.value }}</strong>
      </button>
    </div>

    <!-- 核心业务与图表工作区 -->
    <main class="uc-workspace" ref="dmBody">
      <!-- 1. 核心分析区: 大型趋势图 (68%) + 实时预警流 (32%) -->
      <section class="uc-core-analysis-band">
        <div class="uc-panel uc-trends-panel">
          <div class="uc-panel-head">
            <div class="uc-head-title">
              <span class="uc-pip"></span>
              <h2>体征异常与预警研判</h2>
              <span class="uc-head-sub">{{ periodLabel }}数据走势</span>
            </div>
            <div class="uc-trend-tabs" role="tablist">
              <button type="button" role="tab" :aria-selected="activeTrendTab === 'vitals'" :class="{ 'is-active': activeTrendTab === 'vitals' }" @click="switchTrendTab('vitals')">体征异常趋势</button>
              <button type="button" role="tab" :aria-selected="activeTrendTab === 'warnings'" :class="{ 'is-active': activeTrendTab === 'warnings' }" @click="switchTrendTab('warnings')">预警时段分布</button>
              <button type="button" role="tab" :aria-selected="activeTrendTab === 'environment'" :class="{ 'is-active': activeTrendTab === 'environment' }" @click="switchTrendTab('environment')">作业环境风险</button>
            </div>
          </div>
          <div class="uc-trend-canvas-wrap">
            <div v-show="activeTrendTab === 'vitals'" ref="unifiedTrendChart" class="uc-chart-canvas"></div>
            <div v-show="activeTrendTab === 'warnings'" id="hourDistChart" class="uc-chart-canvas"></div>
            <div v-show="activeTrendTab === 'environment'" ref="envChartRef" class="uc-chart-canvas"></div>
          </div>
        </div>

        <div class="uc-panel uc-live-panel">
          <div class="uc-panel-head">
            <div class="uc-head-title">
              <span class="uc-pip uc-pip--live"></span>
              <h2>实时预警流</h2>
            </div>
            <div class="uc-head-right">
              <span v-if="kpiUnhandledHigh > 0" class="uc-pill-alert">紧急 {{ kpiUnhandledHigh }}</span>
              <button type="button" class="uc-panel-link" @click="$router.push('/alert-management/records')">全部记录 ›</button>
            </div>
          </div>
          <div class="uc-live-body">
            <DashboardWarningStream
              class="uc-warning-stream"
              :warning-events="warningEvents"
              :latest-danger-event="latestDangerEvent"
              :format-time-ago="formatTimeAgo"
              :open-warn-curve="openWarnCurve"
              :open-handle-dialog="openHandleDialog"
              :open-command-incident="openCommandIncident"
            />
          </div>
        </div>
      </section>

      <!-- 2. 次级业务区: 健康异常快照 (50%) + 重点关注人员 (50%) -->
      <section class="uc-secondary-band">
        <div class="uc-panel uc-health-cards">
          <div class="uc-panel-head">
            <div class="uc-head-title">
              <span class="uc-pip uc-pip--health"></span>
              <h2>健康异常快照</h2>
              <span class="uc-head-sub">{{ healthExceptionCards.filter(item => item.exceptionCount > 0).length }} 项需要关注</span>
            </div>
            <span class="uc-snapshot-badge" :class="snapshotStatusToneClass">{{ healthSnapshotStatusText }}</span>
          </div>
          <div class="uc-exception-grid">
            <button v-for="v in healthExceptionCards.slice(0, 4)" :key="v.metricKey || v.label" type="button" :class="['uc-exception-card', `tone-${v.tone}`]" @click="v.route && $router.push(v.route)">
              <div class="uc-card-header">
                <span class="uc-card-label">{{ v.label }}</span>
                <span class="uc-card-unit">{{ v.unit || '异常/覆盖' }}</span>
              </div>
              <div class="uc-card-reading">
                <strong>{{ v.val === '0/0' ? '0/0' : v.val }}</strong>
              </div>
              <div class="uc-card-details">
                <span class="uc-card-tag">{{ v.tag || '暂无群体均值' }}</span>
                <small class="uc-card-text">{{ v.val === '0/0' ? '0 人覆盖 / 暂无新鲜体征' : (v.exceptionText || '暂无异常') }}</small>
              </div>
              <div class="uc-card-action"><span>专项分析 ›</span></div>
            </button>
          </div>
        </div>

        <div class="uc-panel uc-focus-panel">
          <div class="uc-panel-head">
            <div class="uc-head-title">
              <span class="uc-pip uc-pip--warn"></span>
              <h2>重点人员</h2>
              <span class="uc-head-sub">{{ focusWarningEvents.length }} 人持续观察</span>
            </div>
            <button type="button" class="uc-panel-link" @click="$router.push('/health-monitor/risk-warning')">更多人员 ›</button>
          </div>
          <div class="uc-focus-wrap">
            <div class="uc-focus-table">
              <div class="uc-focus-row uc-focus-header">
                <span>姓名 / 工号</span>
                <span>风险类型</span>
                <span>风险等级</span>
                <span>当前状态</span>
                <span>最近监测</span>
              </div>
              <button v-for="(item, idx) in focusWarningEvents.slice(0, 5)" :key="item.id || idx" type="button" class="uc-focus-row" @click="goToEmployeeProfile(item)">
                <span class="uc-person-col">
                  <b>{{ item.userName || '--' }}</b>
                  <small>{{ item.userCode || item.id || '--' }}</small>
                </span>
                <span class="uc-risk-type-col">{{ item.type || item.warningType || '体征预警' }}</span>
                <span>
                  <span class="uc-risk-tag" :class="item.level === 'danger' ? 'is-danger' : 'is-warning'">
                    {{ item.level === 'danger' ? '高危' : '重点' }}
                  </span>
                </span>
                <span class="is-abnormal">● 异常</span>
                <span class="uc-time-col">{{ formatTimeAgo(item.time) }}</span>
              </button>
              <div v-if="!focusWarningEvents.length" class="uc-focus-empty">当前时段无待处理重点人员</div>
            </div>
          </div>
        </div>
      </section>

      <!-- 3. 辅助区: 班前准入 (33%) + 设备状态 (33%) + 部门风险排名 (33%) -->
      <section class="uc-auxiliary-band uc-right-rail">
        <div class="uc-panel uc-admission-panel">
          <div class="uc-panel-head">
            <div class="uc-head-title">
              <span class="uc-pip"></span>
              <h2>班前准入</h2>
              <span class="uc-head-sub">达标审核</span>
            </div>
            <button type="button" class="uc-panel-link" @click="$router.push('/health-monitor/mine-entry')">准入名单 ›</button>
          </div>
          <div class="uc-admission-body">
            <div class="uc-admission-summary">
              <div class="uc-rate-box">
                <strong>{{ preShiftData.preShiftRate !== null ? preShiftData.preShiftRate + '%' : '--' }}</strong>
                <span>今日达标率</span>
              </div>
              <div class="uc-admission-stats">
                <div><span>达标</span><b class="tone-green">{{ preShiftData.qualifiedCount ?? '--' }}</b></div>
                <div><span>拦截</span><b class="tone-red">{{ preShiftData.failedCount ?? '--' }}</b></div>
                <div><span>应检</span><b>{{ preShiftData.totalToday ?? '--' }}</b></div>
              </div>
            </div>
            <div class="uc-admission-table">
              <button v-for="item in admissionQueueItems" :key="item.key" type="button" :class="['uc-admission-row', `tone-${item.tone}`]" :disabled="item.unavailable" @click="openAdmissionQueue(item)">
                <span>{{ item.label }}</span>
                <b>{{ item.value }}</b>
                <em>{{ item.note }}</em>
              </button>
            </div>
          </div>
        </div>

        <div class="uc-panel uc-devices-panel">
          <div class="uc-panel-head">
            <div class="uc-head-title">
              <span class="uc-pip"></span>
              <h2>设备状态</h2>
              <span class="uc-head-sub">实时在线</span>
            </div>
            <button type="button" class="uc-panel-link" @click="goToDeviceList({ route: '/admin/device-list' })">设备列表 ›</button>
          </div>
          <div class="uc-devices-body">
            <div class="uc-device-summary">
              <div><strong>{{ deviceCards[0]?.val ?? '--' }}</strong><span>设备总数</span></div>
              <div><strong class="is-good">{{ deviceCards[1]?.val ?? '--' }}</strong><span>在线设备</span></div>
              <div><strong class="is-warn">{{ deviceCards[3]?.val ?? '--' }}</strong><span>告警设备</span></div>
            </div>
            <div class="uc-device-table">
              <button v-for="(item, idx) in deviceCards.slice(0, 6)" :key="item.label || idx" type="button" @click="item.route && goToDeviceList(item)">
                <span>{{ item.label }}</span>
                <b :class="item.cls">{{ item.val }}</b>
                <em>{{ item.cls === 'dc-red' ? '告警' : item.cls === 'dc-orange' ? '注意' : '正常' }}</em>
              </button>
            </div>
          </div>
        </div>

        <div class="uc-panel uc-risk-panel">
          <div class="uc-panel-head">
            <div class="uc-head-title">
              <span class="uc-pip"></span>
              <h2>部门风险排名</h2>
              <span class="uc-head-sub">Top 5</span>
            </div>
            <button type="button" class="uc-panel-link" @click="$router.push('/health-monitor/trend-warning')">趋势预警 ›</button>
          </div>
          <div class="uc-dept-bars">
            <div v-for="(item, idx) in top5DisplayData.slice(0, 5)" :key="item.userCode || item.userName || idx" class="uc-dept-item" @click="openEmployeeDrawer(item)">
              <span class="uc-dept-rank">{{ Number(idx) + 1 }}</span>
              <span class="uc-dept-name">{{ item.userName || item.name || '--' }}</span>
              <div class="uc-dept-bar-track">
                <span class="uc-dept-bar-fill" :style="{ width: `${Math.max(8, (item.count / top5Max) * 100)}%` }"></span>
              </div>
              <em class="uc-dept-val">{{ item.count }} 次</em>
            </div>
            <div v-if="!top5DisplayData.length" class="uc-empty-line">当前时段无异常排名数据</div>
          </div>
        </div>
      </section>
    </main>

    <!-- 弹窗与抽屉 -->
    <DashboardDialogs
      :emp-drawer="empDrawer"
      :handle-dialog="handleDialog"
      :dept-person-modal="deptPersonModal"
      :dept-detail-modal="deptDetailModal"
      :metric-detail-modal="metricDetailModal"
      :warn-curve-modal="warnCurveModal"
      :trend-block-title="trendBlockTitle"
      :alert-type-label="alertTypeLabel"
      :format-warn-time="formatWarnTime"
      :load-dept-person-chart="loadDeptPersonChart"
      :load-dept-detail-chart="loadDeptDetailChart"
      :load-metric-detail-chart="loadMetricDetailChart"
      :init-warn-curve-chart="initWarnCurveChart"
      :submit-handle="submitHandle"
    />

    <IncidentCommandDrawer
      v-model:visible="incidentDrawerVisible"
      :event="currentIncidentEvent"
      source-page="dashboard"
      @updated="handleIncidentUpdated"
      @open-command-center="goToCommandIncident(currentIncidentEvent)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import {
  Cpu,
  FirstAidKit,
  FullScreen,
  MagicStick,
  Monitor,
  Odometer,
  Refresh
} from '@element-plus/icons-vue'
import DashboardPersonSearch from './components/DashboardPersonSearch.vue'
import DashboardDialogs from './components/DashboardDialogs.vue'
import DashboardWarningStream from './components/DashboardWarningStream.vue'
import IncidentCommandDrawer from '../../safety-command/components/IncidentCommandDrawer.vue'
import { useDashboardPage } from './use-dashboard-page'

const {
  activePeriod, admissionQueueItems, alertTypeLabel, currentIncidentEvent, currentTime,
  deptDetailModal, deptPersonModal, deviceCards, empDrawer, fetchData, focusWarningEvents,
  formatTimeAgo, formatWarnTime, goToCommandIncident, goToDeviceList, goToEmployeeProfile,
  handleDialog, handleIncidentUpdated, healthExceptionCards, healthSnapshot, healthSnapshotStatusText,
  headerMetricStripItems, incidentDrawerVisible, initEnvHealthChart, initHourDistChart,
  initUnifiedTrendChart, initWarnCurveChart, isFullscreen, isRefreshing, kpiUnhandledHigh,
  lastRefreshText, latestDangerEvent, loadDeptDetailChart, loadDeptPersonChart,
  loadMetricDetailChart, metricDetailModal, onHeaderMetricSelect, openAdmissionQueue,
  openCommandIncident, openEmployeeDrawer, openHandleDialog, openWarnCurve, periodLabel,
  periodOptions, preShiftData, submitHandle, toggleMineAiPanel, switchPeriod, toggleFullscreen,
  top5DisplayData, top5Max, trendBlockTitle, unifiedTrendChart, warnCurveModal, warningEvents,
  $router, dmScale, dmBody, envChartRef
} = useDashboardPage()


const snapshotStatusToneClass = computed(() => {
  const st = healthSnapshot.value?.status
  if (st === 'NORMAL') return 'is-normal'
  if (st === 'PARTIAL') return 'is-partial'
  if (st === 'STALE') return 'is-stale'
  return 'is-empty'
})

const activeTrendTab = ref<'vitals' | 'warnings' | 'environment'>('vitals')
function switchTrendTab(tab: 'vitals' | 'warnings' | 'environment') {
  activeTrendTab.value = tab
  nextTick(() => {
    if (tab === 'vitals') {
      initUnifiedTrendChart?.()
    } else if (tab === 'warnings') {
      initHourDistChart?.()
    } else if (tab === 'environment') {
      initEnvHealthChart?.()
    }
  })
}
</script>

<style lang="scss">
@import './dashboard.scss';
</style>
