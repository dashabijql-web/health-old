<template>
  <div class="ps-root">

    <!-- ══ 顶部 Header ══ -->
    <header class="ps-hd">
      <div class="ps-hd-left">
        <span class="ps-live-dot"></span>
        <h1 class="ps-hd-title">压力指数分析</h1>
      </div>

      <div class="ps-hd-kpis">
        <div class="ps-kpi" v-for="k in headerKpis" :key="k.key" role="button" tabindex="0" @click="openRiskMetric(k.key)" @keydown.enter="openRiskMetric(k.key)">
          <span class="ps-kpi-n" :class="k.cls">{{ k.val }}</span>
          <span class="ps-kpi-l">{{ k.label }}</span>
        </div>
      </div>

      <div class="ps-period-tabs">
        <span class="ps-period-label">统计周期</span>
        <span v-for="p in periodOptions" :key="p.value"
          :class="['ps-period-tab', activePeriod === p.value ? 'is-active' : '']"
          @click="switchPeriod(p.value)">{{ p.label }}</span>
      </div>

      <div class="ps-hd-time">{{ currentTime }}</div>
      <button class="hm-export-btn" @click="exportExcel" title="导出当前周期风险汇总">导出周期</button>
    </header>

    <!-- ══ 主体 ══ -->
    <section class="ps-bd" v-loading="pageLoading" element-loading-text="数据加载中..." element-loading-background="rgba(10,20,40,0.7)">

      <!-- ─ 左侧：TOP5 紧凑列表 + 部门柱状图 ─ -->
      <aside class="ps-aside">
        <!-- 高压力排行 -->
        <div class="ps-panel ps-aside-top">
          <div class="ps-ph">
            <span class="ps-ph-bar"></span>
            <span class="ps-ph-title">{{ top5Title }}</span>
          </div>
          <div class="ps-top5-list" ref="top5ScrollRef">
            <div v-if="!top5Data.length" class="ps-top5-empty">暂无高压力数据</div>
            <div class="ps-top5-row" v-for="(item, i) in displayedTop5" :key="i" role="button" tabindex="0" @click="openPeriodPortrait(item)" @keydown.enter="openPeriodPortrait(item)" style="cursor:pointer">
              <span class="ps-top5-rank" :class="i < 3 ? 'rank-'+(i+1) : 'rank-n'">{{ i+1 }}</span>
              <span class="ps-top5-name">{{ item.userName }}</span>
              <div class="ps-top5-bar-wrap">
                <div class="ps-top5-bar"
                  :style="{
                    width: (top5Max > 0 ? (item.abnormalCount / top5Max * 100) : 0) + '%',
                    background: top5BarColor(item.primaryMax)
                  }"></div>
              </div>
              <span class="ps-top5-val" :style="{ color: top5ValColor(item.primaryMax) }">{{ item.abnormalCount }}</span>
            </div>
          </div>
        </div>

        <!-- 部门平均压力柱状图 -->
        <div class="ps-panel ps-aside-bot">
          <div class="ps-ph">
            <span class="ps-ph-bar"></span>
            <span class="ps-ph-title">{{ metricPeriodLabel }}部门异常人数</span>
          </div>
          <div class="ps-pc">
            <div ref="deptRef" style="width:100%;height:100%"></div>
          </div>
        </div>
      </aside>

      <!-- ─ 中间 ─ -->
      <main class="ps-main">

        <!-- 当前人员口径 + 4 区间卡 -->
        <div class="ps-hero">
          <div class="ps-scope-card" role="button" tabindex="0" title="查看覆盖人员明细" @click="openRiskMetric('coveredUsers')" @keydown.enter="openRiskMetric('coveredUsers')">
            <span class="ps-scope-label">{{ metricPeriodLabel }}覆盖人员</span>
            <strong class="ps-scope-value">{{ riskSummary.coveredUsers }}<em>人</em></strong>
            <span class="ps-scope-note">周期内有有效压力记录</span>
            <span class="ps-scope-time">{{ periodCoverageText }}</span>
          </div>
          <div class="ps-zone-cards">
            <div v-for="z in psZones" :key="z.key" :class="['ps-zone-card', z.cls]" role="button" tabindex="0" :title="`查看${z.label}人员`" @click="openRiskZone(z)" @keydown.enter="openRiskZone(z)">
              <span class="ps-zone-icon" :style="{color: z.color}">{{ z.icon }}</span>
              <span class="ps-zone-count" :style="{color: z.color}">{{ z.count }}<em>人</em></span>
              <span class="ps-zone-label">{{ z.label }}</span>
              <span class="ps-zone-range">{{ z.range }}</span>
              <div class="ps-zone-pct-bar">
                <div class="ps-zone-pct-fill" :style="{ width: z.pct + '%', background: z.color }"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 图表行：趋势 + 分布饼图 -->
        <div class="ps-charts-row">
          <div class="ps-panel ps-panel-hourly">
            <div class="ps-ph">
              <span class="ps-ph-bar"></span>
              <span class="ps-ph-title">{{ trendTitle }}</span>
              <div class="ps-trend-tags">
                <span class="ps-tag" style="color:#FFB84D;border-color:rgba(255,184,77,0.3)">▰ 异常人数</span>
                <span class="ps-tag" style="color:#00d4ff;border-color:rgba(0,212,255,0.3)">— 异常率</span>
              </div>
            </div>
            <div class="ps-pc">
              <div ref="hourlyRef" style="width:100%;height:100%"></div>
            </div>
          </div>
          <div class="ps-panel ps-panel-dist">
            <div class="ps-ph">
              <span class="ps-ph-bar"></span>
              <span class="ps-ph-title">{{ metricPeriodLabel }}记录区间分布</span>
            </div>
            <div class="ps-dist-body">
              <div class="ps-dist-legend">
                <div class="ps-dist-row" v-for="d in distLegend" :key="d.name">
                  <div class="ps-dist-dot" :style="{background: d.color}"></div>
                  <span class="ps-dist-name">{{ d.name }}</span>
                  <div class="ps-dist-bar-wrap">
                    <div class="ps-dist-bar" :style="{width: d.value + '%', background: d.color}"></div>
                  </div>
                  <span class="ps-dist-pct" :style="{color: d.color}">{{ d.value }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 当前异常压力明细 -->
        <div class="ps-panel ps-panel-anomaly">
          <div class="ps-ph">
            <span class="ps-ph-bar"></span>
            <span class="ps-ph-title">实时压力异常人员</span>
            <span class="ps-live-scope-badge">独立实时快照</span>
            <span class="ps-anomaly-count" v-if="psAnomalyList.length">
              共 <em>{{ psAnomalyList.length }}</em> 人异常
            </span>
          </div>
          <div v-if="!psAnomalyList.length" class="ps-anomaly-empty">
            当前无异常压力人员
          </div>
          <div v-else class="ps-anomaly-body">
            <div class="ps-anomaly-hd">
              <span>姓名</span><span>部门</span><span>压力指数</span><span>等级</span><span>时间</span>
            </div>
            <div class="ps-anomaly-list">
              <div
                class="ps-anomaly-row"
                v-for="(item, i) in displayedAnomalyList"
                :key="i"
                role="button"
                tabindex="0"
                :class="item.pressure >= 85 ? 'anom-high' : 'anom-elevated'"
                @click="openPeriodPortrait(item)"
                @keydown.enter="openPeriodPortrait(item)"
                style="cursor:pointer"
              >
                <span class="pa-name">{{ item.userName }}</span>
                <span class="pa-dept">{{ item.deptName || '--' }}</span>
                <span class="pa-val">{{ item.pressure }}</span>
                <span class="pa-type">{{ item.pressure >= 85 ? '高压需关注' : '偏高' }}</span>
                <span class="pa-time">{{ fmtTime(item.recordTime) }}</span>
              </div>
              <div v-if="psAnomalyList.length > 20" class="ps-anomaly-more" @click="anomalyExpanded = !anomalyExpanded">
                {{ anomalyExpanded ? '▲ 收起' : `▼ 展开全部 (${psAnomalyList.length} 人)` }}
              </div>
            </div>
          </div>
        </div>

      </main>

    </section>
    <PeriodRiskDrawer v-model="riskDrawer.visible" :title="riskDrawer.title" :kind="riskDrawer.kind"
      :loading="riskDrawer.loading" :rows="riskDrawer.rows" :daily-rows="riskDrawerDailyRows"
      :total="riskDrawer.total" :page="riskDrawer.page" :size="riskDrawer.size"
      :summary="riskDrawer.summary"
      unit="" value-title="压力范围" @page-change="loadRiskDrawerUsers" @open-person="openRiskDrawerPerson" />
  </div>
</template>

<script setup lang="ts">
import PeriodRiskDrawer from '@/views/health-monitor/metric-page/PeriodRiskDrawer.vue'
import { usePressurePage } from './use-pressure-page'

const {
  activePeriod,
  anomalyExpanded,
  currentTime,
  deptRef,
  displayedAnomalyList,
  displayedTop5,
  distLegend,
  exportExcel,
  fmtTime,
  headerKpis,
  hourlyRef,
  metricPeriodLabel,
  openPeriodPortrait,
  openRiskDrawerPerson,
  openRiskMetric,
  openRiskZone,
  pageLoading,
  periodCoverageText,
  periodOptions,
  psAnomalyList,
  psZones,
  riskDrawer,
  riskDrawerDailyRows,
  riskSummary,
  loadRiskDrawerUsers,
  switchPeriod,
  top5BarColor,
  top5Data,
  top5Max,
  top5ScrollRef,
  top5Title,
  top5ValColor,
  trendTitle
} = usePressurePage()
</script>

<style lang="scss" scoped src="./pressure.scss"></style>
