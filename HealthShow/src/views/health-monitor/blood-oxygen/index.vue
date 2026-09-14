<template>
  <div class="bo-root">

    <!-- ══ Header ══ -->
    <header class="bo-hd">
      <div class="bo-hd-left">
        <span class="bo-live-dot"></span>
        <h1 class="bo-hd-title">血氧分析</h1>
      </div>
      <div class="bo-hd-kpis">
        <div class="bo-kpi" v-for="k in headerKpis" :key="k.key" role="button" tabindex="0" @click="openRiskMetric(k.key)" @keydown.enter="openRiskMetric(k.key)">
          <span class="bo-kpi-n" :class="k.cls">{{ k.val }}</span>
          <span class="bo-kpi-l">{{ k.label }}</span>
        </div>
      </div>
      <div class="bo-period-tabs">
        <span class="bo-period-label">统计周期</span>
        <span v-for="p in periodOptions" :key="p.value"
          :class="['bo-period-tab', activePeriod === p.value ? 'is-active' : '']"
          @click="switchPeriod(p.value)">{{ p.label }}</span>
      </div>

      <div class="bo-hd-time">{{ currentTime }}</div>
      <button class="hm-export-btn" @click="exportExcel" title="导出当前周期风险汇总">导出周期</button>
    </header>

    <!-- ══ 主体 ══ -->
    <section class="bo-bd" v-loading="pageLoading" element-loading-text="数据加载中..." element-loading-background="rgba(10,20,40,0.7)">

      <!-- ─ 左侧：TOP5 紧凑列表 + 部门统计 ─ -->
      <aside class="bo-aside">
        <div class="bo-panel bo-aside-top">
          <div class="bo-ph">
            <span class="bo-ph-bar"></span>
            <span class="bo-ph-title">{{ top5Title }}</span>
          </div>
          <div class="bo-top5-list" ref="top5ScrollRef">
            <div v-if="!top5Data.length" class="bo-top5-empty">暂无异常频次数据</div>
            <div class="bo-top5-row" v-for="(item, i) in displayedTop5" :key="i" role="button" tabindex="0" @click="openPeriodPortrait(item)" @keydown.enter="openPeriodPortrait(item)" style="cursor:pointer">
              <span class="bo-top5-rank" :class="i < 3 ? 'rank-'+(i+1) : 'rank-n'">{{ i+1 }}</span>
              <span class="bo-top5-name">{{ item.userName }}</span>
              <div class="bo-top5-bar-wrap">
                <div class="bo-top5-bar" :style="{width: (item.abnormalCount / top5Max * 100) + '%'}"></div>
              </div>
              <span class="bo-top5-val">{{ item.abnormalCount }}</span>
            </div>
          </div>
        </div>

        <div class="bo-panel bo-aside-bot">
          <div class="bo-ph">
            <span class="bo-ph-bar"></span>
            <span class="bo-ph-title">{{ metricPeriodLabel }}部门异常人数</span>
          </div>
          <div class="bo-pc">
            <div ref="deptRef" style="width:100%;height:100%"></div>
          </div>
        </div>
      </aside>

      <!-- ─ 中间 ─ -->
      <main class="bo-main">

        <!-- 当前人员口径 + 4 区间卡 -->
        <div class="bo-hero">
          <div class="bo-scope-card" role="button" tabindex="0" title="查看覆盖人员明细" @click="openRiskMetric('coveredUsers')" @keydown.enter="openRiskMetric('coveredUsers')">
            <span class="bo-scope-label">{{ metricPeriodLabel }}覆盖人员</span>
            <strong class="bo-scope-value">{{ riskSummary.coveredUsers }}<em>人</em></strong>
            <span class="bo-scope-note">周期内有有效血氧记录</span>
            <span class="bo-scope-time">{{ periodCoverageText }}</span>
          </div>
          <div class="bo-zone-cards">
            <div v-for="z in boZones" :key="z.key" :class="['bo-zone-card', z.cls]" role="button" tabindex="0" :title="`查看${z.label}人员`" @click="openRiskZone(z)" @keydown.enter="openRiskZone(z)">
              <span class="bo-zone-icon" :style="{color: z.color}">{{ z.icon }}</span>
              <span class="bo-zone-count" :style="{color: z.color}">{{ z.count }}<em>人</em></span>
              <span class="bo-zone-label">{{ z.label }}</span>
              <span class="bo-zone-range">{{ z.range }}</span>
              <div class="bo-zone-pct-bar">
                <div class="bo-zone-pct-fill" :style="{ width: z.pct + '%', background: z.color }"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 趋势图 -->
        <div class="bo-charts-row">
          <div class="bo-panel bo-panel-trend">
            <div class="bo-ph">
              <span class="bo-ph-bar"></span>
              <span class="bo-ph-title">{{ trendTitle }}</span>
              <div class="bo-trend-tags">
                <span class="bo-tag" style="color:#FFB84D;border-color:rgba(255,184,77,0.3)">▰ 异常人数</span>
                <span class="bo-tag" style="color:#00d4ff;border-color:rgba(0,212,255,0.3)">— 异常率</span>
              </div>
            </div>
            <div class="bo-pc">
              <div ref="trendRef" style="width:100%;height:100%"></div>
            </div>
          </div>
        </div>

        <!-- 当前异常血氧明细 -->
        <div class="bo-panel bo-panel-anomaly">
          <div class="bo-ph">
            <span class="bo-ph-bar"></span>
            <span class="bo-ph-title">实时血氧异常人员</span>
            <span class="bo-live-scope-badge">独立实时快照</span>
            <span class="bo-anomaly-count" v-if="boAnomalyList.length">
              共 <em>{{ boAnomalyList.length }}</em> 人异常
            </span>
          </div>
          <div v-if="!boAnomalyList.length" class="bo-anomaly-empty">
            当前无异常血氧人员
          </div>
          <div v-else class="bo-anomaly-body">
            <div class="bo-anomaly-hd">
              <span>姓名</span><span>部门</span><span>血氧</span><span>类型</span><span>时间</span>
            </div>
            <div class="bo-anomaly-list">
              <div
                class="bo-anomaly-row"
                v-for="(item, i) in displayedBoAnomalyList"
                :key="i"
                role="button"
                tabindex="0"
                :class="item.bloodOxygen < 90 ? 'anom-danger' : 'anom-low'"
                @click="showDetail(item)"
                @keydown.enter="showDetail(item)"
                style="cursor:pointer"
              >
                <span class="ba-name">{{ item.userName }}</span>
                <span class="ba-dept">{{ item.deptName || item.dept_name || '--' }}</span>
                <span class="ba-val">{{ item.bloodOxygen }}%</span>
                <span class="ba-type">{{ item.bloodOxygen < 90 ? '危险↓↓' : '偏低↓' }}</span>
                <span class="ba-time">{{ fmtTime(item.recordTime) }}</span>
              </div>
              <button v-if="boAnomalyList.length > 20" class="bo-anomaly-more" @click="anomalyExpanded = !anomalyExpanded">
                {{ anomalyExpanded ? '收起' : `展开全部 (${boAnomalyList.length} 人)` }}
              </button>
            </div>
          </div>
        </div>

      </main>

    </section>

    <el-dialog v-model="detailVisible" :title="`${detailItem?.userName || ''} 血氧详情`" width="400px" :append-to-body="true">
      <div v-if="detailItem" style="padding:8px 0">
        <div style="text-align:center;margin-bottom:20px">
          <span class="hm-detail-value">{{ detailItem.bloodOxygen }}</span>
          <span class="hm-detail-unit">%</span>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="状态">
            <el-tag :class="boLevel(detailItem.bloodOxygen)" size="small" effect="dark"
              :type="detailItem.bloodOxygen < 90 ? 'danger' : detailItem.bloodOxygen < 95 ? 'warning' : detailItem.bloodOxygen >= 99 ? 'info' : 'success'">
              {{ detailItem.bloodOxygen < 90 ? '危险' : detailItem.bloodOxygen < 95 ? '偏低' : detailItem.bloodOxygen >= 99 ? '优秀' : '正常' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="记录时间">{{ fmtTime(detailItem.recordTime) }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top:16px;text-align:right">
          <el-button type="primary" size="small" @click="openPeriodPortrait(detailItem);detailVisible=false">查看健康画像</el-button>
        </div>
      </div>
    </el-dialog>
    <PeriodRiskDrawer v-model="riskDrawer.visible" :title="riskDrawer.title" :kind="riskDrawer.kind"
      :loading="riskDrawer.loading" :rows="riskDrawer.rows" :daily-rows="riskDrawerDailyRows"
      :total="riskDrawer.total" :page="riskDrawer.page" :size="riskDrawer.size"
      :summary="riskDrawer.summary"
      unit="%" value-title="血氧范围" @page-change="loadRiskDrawerUsers" @open-person="openRiskDrawerPerson" />
  </div>
</template>

<script setup lang="ts">
import PeriodRiskDrawer from '@/views/health-monitor/metric-page/PeriodRiskDrawer.vue'
import { useBloodOxygenPage } from './use-blood-oxygen-page'

const {
  activePeriod,
  anomalyExpanded,
  boAnomalyList,
  boLevel,
  boZones,
  currentTime,
  detailItem,
  detailVisible,
  deptRef,
  displayedBoAnomalyList,
  displayedTop5,
  exportExcel,
  fmtTime,
  headerKpis,
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
  showDetail,
  switchPeriod,
  top5Data,
  top5Max,
  top5ScrollRef,
  top5Title,
  trendRef,
  trendTitle
} = useBloodOxygenPage()
</script>

<style lang="scss" scoped src="./blood-oxygen.scss"></style>
