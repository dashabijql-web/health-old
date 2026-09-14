<template>
  <div class="hr-root">

    <!-- ══ 顶部 Header ══ -->
    <header class="hr-hd">
      <div class="hr-hd-left">
        <span class="hr-live-dot"></span>
        <h1 class="hr-hd-title">心率分析</h1>
      </div>

      <div class="hr-hd-kpis">
        <button
          class="hr-kpi"
          v-for="k in headerKpis"
          :key="k.key"
          type="button"
          :title="`查看${k.label}明细`"
          @click="openHeaderMetric(k.key)"
        >
          <span class="hr-kpi-n" :class="k.cls">{{ k.val }}</span>
          <span class="hr-kpi-l">{{ k.label }}</span>
        </button>
      </div>

      <div class="hr-period-tabs">
        <span class="hr-period-label">统计周期</span>
        <span v-for="p in periodOptions" :key="p.value"
          :class="['hr-period-tab', activePeriod === p.value ? 'is-active' : '']"
          @click="switchPeriod(p.value)">{{ p.label }}</span>
      </div>

      <div class="hr-hd-time">{{ currentTime }}</div>
      <button class="hm-export-btn" @click="exportExcel" title="导出当前周期风险汇总">导出周期</button>
    </header>

    <!-- ══ 主体 ══ -->
    <section class="hr-bd" v-loading="pageLoading" element-loading-text="数据加载中..." element-loading-background="rgba(10,20,40,0.7)">

      <!-- ─ 左侧：TOP5(小) + 部门统计(大) ─ -->
      <aside class="hr-aside">
        <!-- TOP5：紧凑列表替代大图表 -->
        <div class="hr-panel hr-aside-top">
          <div class="hr-ph">
            <span class="hr-ph-bar"></span>
            <span class="hr-ph-title">{{ metricPeriodLabel }}异常频次 Top 10</span>
          </div>
          <div class="hr-top5-list" ref="top5ScrollRef"
               @mouseenter="_top5Paused=true" @mouseleave="_top5Paused=false">
            <div v-if="!top5Data.length" class="hr-top5-empty">暂无异常频次数据</div>
            <div class="hr-top5-row" v-for="(item, i) in displayedTop5" :key="i" role="button" tabindex="0" @click="openHeartRatePortrait(item)" @keydown.enter="openHeartRatePortrait(item)" style="cursor:pointer">
              <span class="hr-top5-rank" :class="i < 3 ? 'rank-'+(i+1) : 'rank-n'">{{ i+1 }}</span>
              <span class="hr-top5-name">{{ item.userName }}</span>
              <div class="hr-top5-bar-wrap">
                <div class="hr-top5-bar" :style="{width: (item.count / top5Max * 100) + '%'}"></div>
              </div>
              <span class="hr-top5-val">{{ item.count }}</span>
              <span class="hr-top5-days" v-if="item.anomalyDays">{{ item.anomalyDays }}天</span>
            </div>
          </div>
        </div>

        <!-- 部门统计：占剩余全部空间 -->
        <div class="hr-panel hr-aside-bot">
          <div class="hr-ph">
            <span class="hr-ph-bar"></span>
            <span class="hr-ph-title">{{ metricPeriodLabel }}部门异常记录数</span>
          </div>
          <div class="hr-pc">
            <div ref="deptRef" style="width:100%;height:100%"></div>
          </div>
        </div>
      </aside>

      <!-- ─ 主区域 ─ -->
      <main class="hr-main">

        <!-- 周期人员口径 + 4 个互斥风险区间 -->
        <div class="hr-hero">
          <div class="hr-scope-card" role="button" tabindex="0" title="查看有数据日明细" @click="openScopeDetail" @keydown.enter="openScopeDetail">
            <span class="hr-scope-label">有数据日</span>
            <strong class="hr-scope-value">{{ coverageSummary.days }}<em>天</em></strong>
            <span class="hr-scope-note">所选周期共 {{ coverageSummary.periodDays }} 天</span>
            <span class="hr-scope-time">日均覆盖 {{ coverageSummary.avgCovered }} 人</span>
          </div>
          <div class="hr-zone-cards">
            <div v-for="z in hrZones" :key="z.key" :class="['hr-zone-card', z.cls]" role="button" tabindex="0" :title="`查看${z.label}人员`" @click="openZoneDetail(z)" @keydown.enter="openZoneDetail(z)">
              <span class="hr-zone-icon" :style="{color: z.color}">{{ z.icon }}</span>
              <span class="hr-zone-count" :style="{color: z.color}">{{ z.count }}<em>人</em></span>
              <span class="hr-zone-label">{{ z.label }}</span>
              <span class="hr-zone-range">{{ z.range }}</span>
              <div class="hr-zone-pct-bar">
                <div class="hr-zone-pct-fill" :style="{ width: z.pct + '%', background: z.color }"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 周期风险趋势 -->
        <div class="hr-charts-row">
          <div class="hr-panel hr-panel-trend">
            <div class="hr-ph">
              <span class="hr-ph-bar"></span>
              <span class="hr-ph-title">{{ trendTitle }}</span>
              <span class="hr-trend-scope">{{ trendScopeText }}</span>
              <div class="hr-trend-tags">
                <span class="hr-tag" style="color:#FFB84D;border-color:rgba(255,184,77,0.3)">▰ 异常人数</span>
                <span class="hr-tag" style="color:#00d4ff;border-color:rgba(0,212,255,0.3)">— 异常率</span>
                <span class="hr-tag hr-tag-muted">按人去重 · 有效覆盖</span>
              </div>
            </div>
            <div class="hr-pc">
              <div ref="trendRef" style="width:100%;height:100%"></div>
            </div>
          </div>
        </div>

        <!-- 当前异常心率明细 -->
        <div
          ref="currentAnomalyPanel"
          :class="['hr-panel', 'hr-panel-anomaly', isDashboardDrilldown && 'is-drilldown-focus']"
        >
          <div class="hr-ph">
            <span class="hr-ph-bar"></span>
            <span class="hr-ph-title">实时心率异常人员</span>
            <span class="hr-live-scope-badge">独立实时快照</span>
            <span class="hr-anomaly-scope">{{ anomalyScopeText }}</span>
            <span class="hr-anomaly-count" v-if="snapshotAnomalyTotal">
              共 <em>{{ snapshotAnomalyTotal }}</em> 人异常
            </span>
          </div>
          <div v-if="!anomalyList.length" class="hr-anomaly-empty">
            {{ anomalyEmptyText }}
          </div>
          <div v-else class="hr-anomaly-body">
            <div class="hr-anomaly-hd">
              <span>姓名</span><span>性别/年龄</span><span>部门</span><span>工号</span><span>心率</span><span>状态</span><span>时间</span>
            </div>
            <div class="hr-anomaly-list">
              <div
                class="hr-anomaly-row"
                v-for="item in anomalyList"
                :key="item.userCode"
                role="button"
                tabindex="0"
                :class="item.heartRateState === 'danger' ? 'anom-danger' : 'anom-warning'"
                @click="showDetail(item)"
                @keydown.enter="showDetail(item)"
                style="cursor:pointer"
              >
                <span class="ha-name">{{ item.userName }}</span>
                <span class="ha-gender">
                  <em :class="item.gender === '男' ? 'g-m' : 'g-f'">{{ item.gender || '--' }}</em>
                  <i v-if="item.age">{{ item.age }}岁</i>
                </span>
                <span class="ha-dept">{{ item.deptName || item.dept_name || '--' }}</span>
                <span class="ha-job">{{ item.userCode || '--' }}</span>
                <span class="ha-val">{{ item.heartRate }} bpm</span>
                <span class="ha-type">{{ item.heartRateState === 'danger' ? '高危' : '异常' }}</span>
                <span class="ha-time">{{ fmtTime(item.recordTime) }}</span>
              </div>
            </div>
            <el-pagination
              v-if="snapshotAnomalyTotal > snapshotAnomalySize"
              class="hr-anomaly-pagination"
              background
              small
              layout="prev, pager, next"
              :current-page="snapshotAnomalyPage"
              :page-size="snapshotAnomalySize"
              :total="snapshotAnomalyTotal"
              @current-change="changeSnapshotAnomalyPage"
            />
          </div>
        </div>

      </main>

    </section>

    <el-dialog v-model="detailVisible" :title="`${detailItem?.userName || ''} 心率详情`" width="400px" :append-to-body="true">
      <div v-if="detailItem" style="padding:8px 0">
        <div style="text-align:center;margin-bottom:20px">
          <span class="hm-detail-value">{{ detailItem.heartRate }}</span>
          <span class="hm-detail-unit">bpm</span>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="状态">
            <el-tag :type="detailItem.heartRateState === 'danger' ? 'danger' : 'warning'" size="small" effect="dark">
              {{ detailItem.heartRateState === 'danger' ? '高危' : '异常' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="记录时间">{{ fmtTime(detailItem.recordTime) }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top:16px;text-align:right">
          <el-button type="primary" size="small" @click="goToPortrait(detailItem);detailVisible=false">查看健康画像</el-button>
        </div>
      </div>
    </el-dialog>

    <el-drawer
      v-model="departmentDrawerVisible"
      :title="departmentDrawerTitle"
      size="620px"
      direction="rtl"
      append-to-body
      class="hr-dept-drawer"
      @closed="closeDepartmentDrilldown"
    >
      <div class="hr-dept-drawer-body" v-loading="departmentDetail.loading">
        <div class="hr-dept-summary">
          <div class="hr-dept-summary-item">
            <span>涉及人员</span>
            <strong>{{ departmentDetail.total }}</strong><em>人</em>
          </div>
          <div class="hr-dept-summary-item">
            <span>异常记录</span>
            <strong>{{ departmentDetail.abnormalCount }}</strong><em>条</em>
          </div>
          <div class="hr-dept-summary-item is-low">
            <span>偏低记录</span>
            <strong>{{ departmentDetail.lowCount }}</strong><em>条</em>
          </div>
          <div class="hr-dept-summary-item is-high">
            <span>偏高记录</span>
            <strong>{{ departmentDetail.highCount }}</strong><em>条</em>
          </div>
        </div>

        <div v-if="!departmentDetail.loading && !departmentDetail.list.length" class="hr-dept-empty">
          当前周期暂无部门异常人员
        </div>
        <div v-else class="hr-dept-user-list">
          <div class="hr-dept-user-head">
            <span>人员</span><span>异常/天数</span><span>偏低/偏高</span><span>心率范围</span><span>最近异常</span>
          </div>
          <div
            v-for="item in departmentDetail.list"
            :key="item.userCode"
            class="hr-dept-user-row"
            role="button"
            tabindex="0"
            @click="openDepartmentUser(item)"
            @keydown.enter="openDepartmentUser(item)"
          >
            <span class="hr-du-person"><strong>{{ item.userName }}</strong><em>{{ item.userCode }}</em></span>
            <span><strong>{{ item.abnormalCount }}</strong> 条 / {{ item.anomalyDays }} 天</span>
            <span><i class="is-low">{{ item.lowCount }}</i> / <i class="is-high">{{ item.highCount }}</i></span>
            <span>{{ item.minHeartRate }}–{{ item.maxHeartRate }} bpm</span>
            <span>{{ fmtTime(item.lastRecordTime) }}</span>
          </div>
        </div>

        <el-pagination
          v-if="departmentDetail.total > departmentDetail.size"
          class="hr-dept-pagination"
          background
          layout="prev, pager, next"
          :current-page="departmentDetail.page"
          :page-size="departmentDetail.size"
          :total="departmentDetail.total"
          @current-change="changeDepartmentPage"
        />
      </div>
    </el-drawer>

    <el-drawer
      v-model="metricDrawer.visible"
      :title="metricDrawerTitle"
      size="760px"
      direction="rtl"
      append-to-body
      class="hr-dept-drawer"
    >
      <div class="hr-dept-drawer-body" v-loading="metricDrawer.loading">
        <div class="hr-metric-evidence">
          <span>统计周期</span>
          <strong>{{ periodRange.startDate }} 至 {{ periodRange.endDate }}</strong>
          <em>数据源跟随当前页面选择</em>
        </div>

        <div v-if="metricDrawer.summary" class="hr-zone-evidence">
          <div><span>区间人数</span><strong :style="{ color: metricDrawer.summary.color }">{{ metricDrawer.summary.count }}</strong><em>人</em></div>
          <div><span>覆盖占比</span><strong>{{ metricDrawer.summary.pct }}%</strong></div>
          <div><span>判定区间</span><strong>{{ metricDrawer.summary.range }}</strong></div>
          <p>周期内按人员最高风险等级去重</p>
        </div>

        <template v-if="metricDrawerKind === 'users'">
          <div v-if="!metricDrawer.loading && !metricDrawer.list.length" class="hr-dept-empty">
            当前周期暂无人员数据
          </div>
          <div v-else class="hr-dept-user-list">
            <div class="hr-metric-user-head">
              <span>人员</span><span>部门</span><span>采样/异常</span><span>异常天数</span><span>心率范围</span><span>最近采集</span>
            </div>
            <div
              v-for="item in metricDrawer.list"
              :key="item.userCode"
              class="hr-metric-user-row"
              role="button"
              tabindex="0"
              @click="openMetricUser(item)"
              @keydown.enter="openMetricUser(item)"
            >
              <span class="hr-du-person"><strong>{{ item.userName }}</strong><em>{{ item.userCode }}</em></span>
              <span>{{ item.deptName || '--' }}</span>
              <span><strong>{{ item.sampleCount }}</strong> / <i class="is-high">{{ item.abnormalCount }}</i></span>
              <span>{{ item.anomalyDays }} 天</span>
              <span>{{ item.minHeartRate }}–{{ item.maxHeartRate }} bpm</span>
              <span>{{ item.lastSampleTime || '--' }}</span>
            </div>
          </div>
          <el-pagination
            v-if="metricDrawer.total > metricDrawer.size"
            class="hr-dept-pagination"
            background
            layout="prev, pager, next"
            :current-page="metricDrawer.page"
            :page-size="metricDrawer.size"
            :total="metricDrawer.total"
            @current-change="changeMetricPage"
          />
        </template>

        <template v-else>
          <div v-if="!metricDailyRows.length" class="hr-dept-empty">当前周期暂无每日记录</div>
          <div v-else class="hr-daily-evidence-list">
            <div class="hr-daily-evidence-head">
              <span>日期</span><span>覆盖人数</span><span>异常人数</span><span>异常记录</span><span>采样记录</span>
            </div>
            <div v-for="row in metricDailyRows" :key="row.date" class="hr-daily-evidence-row">
              <span>{{ row.date }}</span>
              <span>{{ row.coveredUsers }} 人</span>
              <span>{{ row.anomalyCount }} 人</span>
              <span class="is-high">{{ row.abnormalRecords }} 条</span>
              <span>{{ row.totalRecords }} 条</span>
            </div>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { useHeartRatePage } from './use-heart-rate-page'

defineOptions({ name: 'HeartRateAnalysis' })

const {
  _top5Paused,
  activePeriod,
  anomalyEmptyText,
  anomalyList,
  anomalyScopeText,
  changeDepartmentPage,
  changeMetricPage,
  changeSnapshotAnomalyPage,
  closeDepartmentDrilldown,
  coverageSummary,
  currentAnomalyPanel,
  currentTime,
  departmentDetail,
  departmentDrawerTitle,
  departmentDrawerVisible,
  deptRef,
  detailItem,
  detailVisible,
  displayedTop5,
  exportExcel,
  fmtTime,
  goToPortrait,
  headerKpis,
  hrZones,
  isDashboardDrilldown,
  metricDailyRows,
  metricDrawer,
  metricDrawerKind,
  metricDrawerTitle,
  metricPeriodLabel,
  openDepartmentUser,
  openHeaderMetric,
  openHeartRatePortrait,
  openMetricUser,
  openScopeDetail,
  openZoneDetail,
  overview,
  pageLoading,
  periodOptions,
  periodRange,
  showDetail,
  snapshotAnomalyPage,
  snapshotAnomalySize,
  snapshotAnomalyTotal,
  switchPeriod,
  top5Data,
  top5Max,
  top5ScrollRef,
  trendRef,
  trendScopeText,
  trendTitle
} = useHeartRatePage()
</script>

<style lang="scss" scoped src="./heart-rate.scss"></style>
