<template>
  <div class="bp-root">

    <!-- ══ 顶部 Header ══ -->
    <header class="bp-hd">
      <div class="bp-hd-left">
        <span class="bp-live-dot"></span>
        <h1 class="bp-hd-title">血压分析</h1>
      </div>

      <div class="bp-hd-kpis">
        <div class="bp-kpi" v-for="k in headerKpis" :key="k.key" role="button" tabindex="0" @click="openRiskMetric(k.key)" @keydown.enter="openRiskMetric(k.key)">
          <span class="bp-kpi-n" :class="k.cls">{{ k.val }}</span>
          <span class="bp-kpi-l">{{ k.label }}</span>
        </div>
      </div>

      <div class="bp-period-tabs">
        <span class="bp-period-label">统计周期</span>
        <span v-for="p in periodOptions" :key="p.value"
          :class="['bp-period-tab', activePeriod === p.value ? 'is-active' : '']"
          @click="switchPeriod(p.value)">{{ p.label }}</span>
      </div>

      <div class="bp-hd-time">{{ currentTime }}</div>
      <button class="hm-export-btn" @click="exportExcel" title="导出当前周期风险汇总">导出周期</button>
    </header>

    <!-- ══ 主体 ══ -->
    <section class="bp-bd" v-loading="pageLoading" element-loading-text="数据加载中..." element-loading-background="rgba(10,20,40,0.7)">

      <!-- ─ 左侧：TOP5 + 部门统计 ─ -->
      <aside class="bp-aside">
        <!-- 高收缩压排行 -->
        <div class="bp-panel bp-aside-top">
          <div class="bp-ph">
            <span class="bp-ph-bar"></span>
            <span class="bp-ph-title">{{ top5Title }}</span>
          </div>
          <div class="bp-top5-list" ref="top5ScrollRef">
            <div v-if="!top5Data.length" class="bp-top5-empty">暂无血压风险数据</div>
            <div class="bp-top5-row" v-for="(item, i) in displayedTop5" :key="i" role="button" tabindex="0" @click="openPeriodPortrait(item)" @keydown.enter="openPeriodPortrait(item)" style="cursor:pointer">
              <span class="bp-top5-rank" :class="i < 3 ? 'rank-'+(i+1) : 'rank-n'">{{ i+1 }}</span>
              <span class="bp-top5-name">{{ item.userName }}</span>
              <div class="bp-top5-bar-wrap">
                <div class="bp-top5-bar" :style="{width: (item.abnormalCount / top5Max * 100) + '%'}"></div>
              </div>
              <span class="bp-top5-val">{{ item.abnormalCount }}</span>
            </div>
          </div>
        </div>

        <!-- 部门统计柱状图 -->
        <div class="bp-panel bp-aside-bot">
          <div class="bp-ph">
            <span class="bp-ph-bar"></span>
            <span class="bp-ph-title">{{ metricPeriodLabel }}部门异常人数</span>
          </div>
          <div class="bp-pc">
            <div ref="deptRef" style="width:100%;height:100%"></div>
          </div>
        </div>
      </aside>

      <!-- ─ 中间主体 ─ -->
      <main class="bp-main">

        <!-- 当前人员口径 + 4 区间卡 -->
        <div class="bp-hero">
          <div class="bp-scope-card" role="button" tabindex="0" title="查看覆盖人员明细" @click="openRiskMetric('coveredUsers')" @keydown.enter="openRiskMetric('coveredUsers')">
            <span class="bp-scope-label">{{ metricPeriodLabel }}覆盖人员</span>
            <strong class="bp-scope-value">{{ riskSummary.coveredUsers }}<em>人</em></strong>
            <span class="bp-scope-note">周期内有有效血压记录</span>
            <span class="bp-scope-time">{{ periodCoverageText }}</span>
          </div>
          <div class="bp-zone-cards">
            <div v-for="z in bpZones" :key="z.key" :class="['bp-zone-card', z.cls]" role="button" tabindex="0" :title="`查看${z.label}人员`" @click="openRiskZone(z)" @keydown.enter="openRiskZone(z)">
              <span class="bp-zone-icon" :style="{color: z.color}">{{ z.icon }}</span>
              <span class="bp-zone-count" :style="{color: z.color}">{{ z.count }}<em>人</em></span>
              <span class="bp-zone-label">{{ z.label }}</span>
              <span class="bp-zone-range">{{ z.range }}</span>
              <div class="bp-zone-pct-bar">
                <div class="bp-zone-pct-fill" :style="{ width: z.pct + '%', background: z.color }"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 图表行：趋势 + 24h小时波动 -->
        <div class="bp-charts-row">
          <div class="bp-panel bp-panel-trend">
            <div class="bp-ph">
              <span class="bp-ph-bar"></span>
              <span class="bp-ph-title">{{ trendTitle }}</span>
              <div class="bp-trend-tags">
                <span class="bp-tag" style="color:#ff7043;border-color:rgba(255,112,67,0.3)">▰ 异常人数</span>
                <span class="bp-tag" style="color:#38bdf8;border-color:rgba(56,189,248,0.3)">— 异常率</span>
              </div>
            </div>
            <div class="bp-pc">
              <div ref="trendRef" style="width:100%;height:100%"></div>
            </div>
          </div>
          <div class="bp-panel bp-panel-hourly">
            <div class="bp-ph">
              <span class="bp-ph-bar"></span>
              <span class="bp-ph-title">今日24小时血压波动</span>
              <span class="bp-live-scope-badge">独立当日数据</span>
            </div>
            <div class="bp-pc">
              <div ref="hourlyRef" style="width:100%;height:100%"></div>
            </div>
          </div>
        </div>

        <!-- 当前异常血压明细 -->
        <div class="bp-panel bp-panel-anomaly">
          <div class="bp-ph">
            <span class="bp-ph-bar"></span>
            <span class="bp-ph-title">实时血压异常人员</span>
            <span class="bp-live-scope-badge">独立实时快照</span>
            <span class="bp-anomaly-count" v-if="bpAnomalyList.length">
              共 <em>{{ bpAnomalyList.length }}</em> 人异常
            </span>
          </div>
          <div v-if="!bpAnomalyList.length" class="bp-anomaly-empty">
            当前无异常血压人员
          </div>
          <div v-else class="bp-anomaly-body">
            <div class="bp-anomaly-hd">
              <span>姓名</span><span>部门</span><span>收缩压</span><span>舒张压</span><span>等级</span><span>时间</span>
            </div>
            <div class="bp-anomaly-list">
              <div
                class="bp-anomaly-row"
                v-for="(item, i) in displayedBpAnomalyList"
                :key="i"
                role="button"
                tabindex="0"
                :class="item.systolic >= 160 || item.diastolic >= 100 ? 'anom-danger' : 'anom-stage1'"
                @click="openPeriodPortrait(item)"
                @keydown.enter="openPeriodPortrait(item)"
                style="cursor:pointer"
              >
                <span class="ba-name">{{ item.userName }}</span>
                <span class="ba-dept">{{ item.deptName || '--' }}</span>
                <span class="ba-sys">{{ item.systolic }}</span>
                <span class="ba-dia">{{ item.diastolic }}</span>
                <span class="ba-level">
                  {{ item.systolic < 90 || item.diastolic < 60 ? '偏低' :
                     item.systolic >= 160 || item.diastolic >= 100 ? '2级高血压' :
                     item.systolic >= 140 || item.diastolic >= 90  ? '1级高血压' : '偏高' }}
                </span>
                <span class="ba-time">{{ fmtTime(item.recordTime) }}</span>
              </div>
              <div v-if="bpAnomalyList.length > 20" class="bp-anomaly-more" @click="anomalyExpanded = !anomalyExpanded">
                {{ anomalyExpanded ? '▲ 收起' : `▼ 展开全部 (${bpAnomalyList.length} 人)` }}
              </div>
            </div>
          </div>
        </div>

      </main>

    </section>
    <PeriodRiskDrawer v-model="riskDrawer.visible" :title="riskDrawer.title" :kind="riskDrawer.kind"
      :loading="riskDrawer.loading" :rows="riskDrawer.rows" :daily-rows="riskDrawerDailyRows"
      :total="riskDrawer.total" :page="riskDrawer.page" :size="riskDrawer.size" dual
      :summary="riskDrawer.summary"
      unit="mmHg" value-title="血压范围" @page-change="loadRiskDrawerUsers" @open-person="openRiskDrawerPerson" />
  </div>
</template>

<script setup lang="ts">
import PeriodRiskDrawer from '@/views/health-monitor/metric-page/PeriodRiskDrawer.vue'
import { useBloodPressurePage } from './use-blood-pressure-page'

const {
  activePeriod,
  anomalyExpanded,
  bpAnomalyList,
  bpZones,
  currentTime,
  deptRef,
  displayedBpAnomalyList,
  displayedTop5,
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
  riskDrawer,
  riskDrawerDailyRows,
  riskSummary,
  loadRiskDrawerUsers,
  switchPeriod,
  top5Data,
  top5Max,
  top5ScrollRef,
  top5Title,
  trendRef,
  trendTitle
} = useBloodPressurePage()
</script>

<style lang="scss" scoped src="./blood-pressure.scss"></style>
