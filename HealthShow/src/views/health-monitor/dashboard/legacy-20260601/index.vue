<template>
  <div class="hm-page-shell db" ref="dmScale" @transitionend.stop @animationend.stop>

    <!-- ══════════ HEADER ══════════ -->
    <header class="db-hd">
      <PageHeroHeader
        class="db-hd-hero"
        variant="cockpit"
        eyebrow="Cockpit Dashboard"
        title="统一管控"
        :description="dashboardHeroDescription"
      >
        <template #meta>
          <div class="dm-hd-meta">
            <span class="dm-live-dot"></span>
            <span class="dm-hd-meta-label">实时运行</span>
            <span class="dm-hd-time">{{ currentTime }}</span>
            <button type="button" class="dm-refresh-info" @click="fetchData(true)" title="点击立即刷新">
              <span class="dm-refresh-icon" :class="{ 'is-spinning': isRefreshing }">↻</span>
              <span class="dm-refresh-time">{{ lastRefreshText }}</span>
            </button>
          </div>
        </template>
        <template #actions>
          <div class="dm-period-tabs">
            <button
              v-for="p in periodOptions"
              type="button"
              :key="p.value"
              :class="['dm-period-tab', activePeriod === p.value ? 'is-active' : '']"
              @click="switchPeriod(p.value)"
            >{{ p.label }}</button>
          </div>
          <button
            type="button"
            class="dm-fullscreen-btn"
            @click="toggleFullscreen"
            :title="isFullscreen ? '退出全屏' : '全屏展示'"
          >
            <span>{{ isFullscreen ? '⊡' : '⛶' }}</span>
          </button>
        </template>
      </PageHeroHeader>

      <MetricStrip
        class="dm-hd-kpis"
        :items="headerMetricStripItems"
        dense
        @select="onHeaderMetricSelect"
      />
    </header>

    <!-- ══════════ BODY ══════════ -->
    <div class="dm-bd" ref="dmBody">
      <div class="db-panel dm-main-metrics db-panel--interactive" @click="openDeptPersonModal">
        <div class="db-track">
          <span class="db-track-title">{{ periodLabel }}检测人数</span>
          <span class="db-track-sub">
            <span class="dm-main-metrics-total">共 {{ totalPersons !== null ? totalPersons.toLocaleString() : '--' }} 人次</span>
            <span class="dm-inline-action">查看部门详情</span>
          </span>
        </div>
        <div class="dm-metrics-row">
          <div
            v-for="m in metricCards"
            :key="m.label"
            class="dm-metric-card"
            :style="{ '--metric-tone': m.color }"
            @click.stop="onMetricCardClick(m)"
          >
            <div class="dm-metric-val">
              {{ m.val.toLocaleString() }}
            </div>
            <div class="dm-metric-label">{{ m.label }}</div>
            <div class="dm-metric-bar-wrap">
              <div class="dm-metric-bar" :style="{ width: `${m.pct}%` }"></div>
            </div>
          </div>
        </div>
      </div>

      <section class="dm-command-band">
        <div class="db-panel dm-command-overview">
          <div class="db-track">
            <span class="db-track-title">态势概览</span>
            <span class="db-track-sub">{{ periodLabel }}核心体征均值与健康状态</span>
          </div>
          <div class="dm-command-overview-body">
            <div class="dm-command-section dm-command-section--vitals">
              <div class="dm-command-section-hd">体征健康评估</div>
              <div class="dm-vitals-grid">
                <div
                  v-for="v in primaryVitalCards"
                  :key="v.label"
                  :class="['dm-vital-card', v.route ? 'is-clickable' : '']"
                  :style="{ '--vital-tone': v.color, '--vital-tone-soft': `${v.color}12`, '--vital-tone-border': `${v.color}33` }"
                  @click="v.route && $router.push(v.route)"
                >
                  <div class="dm-vital-head">
                    <div class="dm-vital-icon">
                      <el-icon :size="15"><component :is="v.icon" /></el-icon>
                    </div>
                    <div class="dm-vital-label">{{ v.label }}</div>
                  </div>
                  <div class="dm-vital-reading">
                    <span class="dm-vital-val">{{ v.val }}</span>
                    <span v-if="v.unit" class="dm-vital-unit">{{ v.unit }}</span>
                  </div>
                  <div class="dm-vital-foot">
                    <div class="dm-vital-tag" :class="v.tagCls">{{ v.tag }}</div>
                  </div>
                </div>
              </div>
              <div class="dm-vitals-supplemental">
                <button
                  v-for="v in supplementalVitalCards"
                  :key="`${v.label}-supplemental`"
                  type="button"
                  :class="['dm-vitals-supplemental__item', v.route ? 'is-clickable' : '']"
                  :style="{ '--vital-tone': v.color, '--vital-tone-soft': `${v.color}14`, '--vital-tone-border': `${v.color}2b` }"
                  @click="v.route && $router.push(v.route)"
                >
                  <span class="dm-vitals-supplemental__label">{{ v.label }}</span>
                  <span class="dm-vitals-supplemental__value">{{ v.val }}<em v-if="v.unit">{{ v.unit }}</em></span>
                  <span :class="['dm-vitals-supplemental__tag', v.tagCls]">{{ v.tag }}</span>
                </button>
              </div>
              <div class="dm-assess-bars">
                <div
                  v-for="item in healthAssess"
                  :key="`health-assess-${item.label}`"
                  class="dm-assess-row"
                >
                  <span class="dm-assess-label">{{ item.label }}</span>
                  <span class="dm-assess-track">
                    <span class="dm-assess-fill" :style="{ width: `${item.pct}%`, background: item.color }"></span>
                  </span>
                  <span class="dm-assess-tag" :style="{ color: item.color }">{{ item.tag }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="db-panel dm-main-dispatch dm-command-dispatch-shell">
          <div class="db-track">
            <span class="db-track-title">值班决策面板</span>
            <span class="db-track-sub">先看高危闭环，再看趋势变化</span>
          </div>
          <DashboardDispatchPanel
            :dispatch-priority="dispatchPriority"
            :dispatch-action-items="dispatchActionItems"
            :kpi-unhandled-high="kpiUnhandledHigh"
            :pre-shift-data="preShiftData"
            :focus-warning-events="focusWarningEvents"
            :mine-ai-report="mineAiReport"
            :mine-ai-loading="mineAiLoading"
            :dashboard-ai-summary="dashboardAiSummary"
            :risk-dept-list="riskDeptList"
            :latest-danger-event="latestDangerEvent"
            @navigate="$router.push($event)"
            @person-click="goToEmployeeProfile"
            @toggle-ai="toggleMineAiPanel"
          />
        </div>

        <DashboardRightSidebar
          class="dm-command-sidebar"
          mode="primary"
          :period-label="periodLabel"
          :top5-display-data="top5DisplayData"
          :top5-max="top5Max"
          :warning-rate-list="warningRateList"
          :pre-shift-data="preShiftData"
          :mine-ai-report="mineAiReport"
          :mine-ai-loading="mineAiLoading"
          :latest-danger-event="latestDangerEvent"
          :kpi-unhandled-high="kpiUnhandledHigh"
          :focus-warning-count="focusWarningEvents.length"
          @open-employee="openEmployeeDrawer"
          @toggle-ai="toggleMineAiPanel"
          @show-ai="mineAiDialogVisible = true"
        />
      </section>

      <section class="dm-monitor-band">
        <div class="db-panel dm-main-model">
          <div class="db-track">
            <span class="db-track-title">健康监测中心</span>
            <span class="db-track-sub">实时体征综合分析</span>
          </div>
          <div class="dm-model-body">
            <DashboardWarningStream
              :warning-events="warningEvents"
              :latest-danger-event="latestDangerEvent"
              :format-time-ago="formatTimeAgo"
              :open-warn-curve="openWarnCurve"
              :open-handle-dialog="openHandleDialog"
              :open-command-incident="openCommandIncident"
            />

            <div class="dm-model-data-col">
              <div class="dm-data-block dm-data-block-trend">
                <div class="dm-block-hd">
                  <span class="dm-block-title">{{ trendBlockTitle }}</span>
                  <span class="dm-block-sub">异常率变化</span>
                </div>
                <div ref="unifiedTrendChart" class="dm-chart-flex"></div>
              </div>

              <div class="dm-data-block">
                <div class="dm-block-hd">
                  <span class="dm-block-title">{{ hourDistTitle }}</span>
                </div>
                <div id="hourDistChart" class="dm-chart-flex"></div>
              </div>
            </div>
          </div>

          <div class="dm-model-footer">
            <div class="dm-mf-group is-handled">
              <div class="dm-mf-dot"></div>
              <span class="dm-mf-label">{{ periodLabel }}已处理</span>
              <span class="dm-mf-val">{{ warningEvents.filter(e=>e.handled).length }}</span>
              <span class="dm-mf-unit">件</span>
            </div>
            <div class="dm-mf-sep"></div>
            <div class="dm-mf-group is-pending">
              <div class="dm-mf-dot"></div>
              <span class="dm-mf-label">待处理</span>
              <span class="dm-mf-val">{{ warningEvents.filter(e=>!e.handled).length }}</span>
              <span class="dm-mf-unit">件</span>
            </div>
          </div>
        </div>

        <DashboardRightSidebar
          class="dm-monitor-sidebar"
          mode="secondary"
          :period-label="periodLabel"
          :top5-display-data="top5DisplayData"
          :top5-max="top5Max"
          :warning-rate-list="warningRateList"
          :pre-shift-data="preShiftData"
          :mine-ai-report="mineAiReport"
          :mine-ai-loading="mineAiLoading"
          :latest-danger-event="latestDangerEvent"
          :kpi-unhandled-high="kpiUnhandledHigh"
          :focus-warning-count="focusWarningEvents.length"
          @open-employee="openEmployeeDrawer"
          @toggle-ai="toggleMineAiPanel"
          @show-ai="mineAiDialogVisible = true"
        />
      </section>

      <section class="dm-support-band">
        <DashboardDevicePanel
          class="dm-support-device"
          :device-cards="deviceCards"
          @go-device="goToDeviceList"
        />

        <div class="db-panel dm-main-env">
          <div class="db-track">
            <span class="db-track-title">环境健康关联</span>
            <span class="db-track-sub">CO浓度/粉尘 vs 血氧趋势（模拟）</span>
          </div>
          <div ref="envChartRef" class="dm-env-chart"></div>
        </div>
      </section>

    </div><!-- /dm-bd -->

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

  </div><!-- /hm-page-shell -->

</template>

<script setup lang="ts">
import DashboardDispatchPanel from '../components/DashboardDispatchPanel.vue'
import DashboardRightSidebar from '../components/DashboardRightSidebar.vue'
import DashboardDevicePanel from '../components/DashboardDevicePanel.vue'
import DashboardDialogs from '../components/DashboardDialogs.vue'
import DashboardWarningStream from '../components/DashboardWarningStream.vue'
import PageHeroHeader from '@/components/health-shell/PageHeroHeader.vue'
import MetricStrip from '@/components/health-shell/MetricStrip.vue'
import { useDashboardPage } from '../use-dashboard-page'

const {
  activePeriod,
  alertTypeLabel,
  dashboardHeroDescription,
  dashboardAiSummary,
  currentTime,
  deptDetailModal,
  deptPersonModal,
  deviceCards,
  dispatchActionItems,
  dispatchPriority,
  empDrawer,
  envChartRef,
  fetchData,
  focusWarningEvents,
  formatTimeAgo,
  formatWarnTime,
  goToDeviceList,
  goToEmployeeProfile,
  handleDialog,
  headerMetricStripItems,
  hourDistTitle,
  healthAssess,
  initWarnCurveChart,
  isFullscreen,
  isRefreshing,
  kpiUnhandledHigh,
  lastRefreshText,
  latestDangerEvent,
  loadDeptDetailChart,
  loadDeptPersonChart,
  loadMetricDetailChart,
  metricCards,
  metricDetailModal,
  mineAiLoading,
  mineAiReport,
  onMetricCardClick,
  onHeaderMetricSelect,
  openCommandIncident,
  openDeptPersonModal,
  openEmployeeDrawer,
  openHandleDialog,
  openWarnCurve,
  periodLabel,
  periodOptions,
  preShiftData,
  primaryVitalCards,
  riskDeptList,
  submitHandle,
  switchPeriod,
  toggleMineAiPanel,
  mineAiDialogVisible,
  totalPersons,
  supplementalVitalCards,
  toggleFullscreen,
  top5DisplayData,
  top5Max,
  trendBlockTitle,
  unifiedTrendChart,
  warnCurveModal,
  warningEvents,
  warningRateList,
  $router
} = useDashboardPage()
</script>

<style lang="scss">
@import '../dashboard.scss';
</style>
