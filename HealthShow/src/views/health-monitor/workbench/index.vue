<template>
  <div class="wb-page">
    <header class="wb-hd">
      <div class="wb-hd-left">
        <span class="wb-live-dot"></span>
        <h1 class="wb-hd-title">工作台日历</h1>
      </div>
      <div class="wb-hd-kpis">
        <div class="wb-kpi">
          <span class="wb-kpi-n">{{ summary.totalDays }}</span>
          <span class="wb-kpi-l">有数据天数</span>
        </div>
        <div class="wb-kpi good">
          <span class="wb-kpi-n">{{ summary.goodDays }}</span>
          <span class="wb-kpi-l">健康天数</span>
        </div>
        <div class="wb-kpi warn">
          <span class="wb-kpi-n">{{ summary.warnDays }}</span>
          <span class="wb-kpi-l">预警天数</span>
        </div>
        <div class="wb-kpi danger">
          <span class="wb-kpi-n">{{ summary.totalWarnings.toLocaleString() }}</span>
          <span class="wb-kpi-l">月度预警人次</span>
        </div>
      </div>
      <div class="wb-hd-nav">
        <el-button :icon="ArrowLeft" circle size="small" @click="prevMonth" />
        <span class="wb-month-label">{{ yearLabel }}年 {{ monthLabel }}月</span>
        <el-button :icon="ArrowRight" circle size="small" @click="nextMonth" :disabled="isCurrentMonth" />
      </div>
      <div class="wb-hd-actions">
        <div class="wb-legend">
          <span class="leg-dot leg-good"></span><span>健康</span>
          <span class="leg-dot leg-warn"></span><span>预警</span>
          <span class="leg-dot leg-empty"></span><span>无数据</span>
        </div>
        <button v-if="route.query.empCode" class="wb-profile-btn" @click="backToProfile">返回画像</button>
        <el-button class="wb-pdf-btn" :loading="pdfExporting" @click="exportPDF" size="small">
          {{ pdfExporting ? '生成中...' : '导出月度PDF' }}
        </el-button>
      </div>
    </header>

    <!-- 统计卡片 -->
    <div class="wb-stats">
      <div class="stat-card">
        <div class="sc-val">{{ summary.totalDays }}</div>
        <div class="sc-lbl">有数据天数</div>
      </div>
      <div class="stat-card">
        <div class="sc-val text-green">{{ summary.goodDays }}</div>
        <div class="sc-lbl">健康天数</div>
      </div>
      <div class="stat-card">
        <div class="sc-val text-orange">{{ summary.warnDays }}</div>
        <div class="sc-lbl">有预警天数</div>
      </div>
      <div class="stat-card">
        <div class="sc-val">{{ summary.avgHR || '--' }}</div>
        <div class="sc-lbl">月均心率 (bpm)</div>
      </div>
      <div class="stat-card">
        <div class="sc-val">{{ summary.avgBO || '--' }}</div>
        <div class="sc-lbl">月均血氧 (%)</div>
      </div>
      <div class="stat-card">
        <div class="sc-val text-red">{{ summary.totalWarnings.toLocaleString() }}</div>
        <div class="sc-lbl">月度预警人次</div>
      </div>
    </div>

    <!-- 日历主体 -->
    <div class="wb-calendar" v-loading="loading">
      <!-- 星期标题 -->
      <div class="cal-week-row">
        <div class="cal-week-cell" v-for="w in weekDays" :key="w">{{ w }}</div>
      </div>
      <!-- 日期格子 -->
      <div class="cal-body">
        <div
          v-for="(cell, idx) in calCells"
          :key="idx"
          class="cal-cell"
          :class="cellClass(cell)"
          @click="cell.day && selectDay(cell)"
        >
          <template v-if="cell.day">
            <div class="cell-day">{{ cell.day }}</div>
            <template v-if="cell.data">
              <div class="cell-metrics">
                <span class="cm-item cm-hr" title="心率"><span class="cm-key">HR</span>{{ cell.data.avgHeartRate || '--' }}</span>
                <span class="cm-item cm-bo" title="血氧"><span class="cm-key">SpO2</span>{{ cell.data.avgBloodOxygen || '--' }}</span>
              </div>
              <div class="cell-metrics cell-metrics-2" v-if="cell.data.avgSteps || cell.data.avgPressure">
                <span class="cm-item cm-steps" v-if="cell.data.avgSteps" title="步数"><span class="cm-key">STEP</span>{{ cell.data.avgSteps >= 1000 ? (cell.data.avgSteps/1000).toFixed(1)+'k' : cell.data.avgSteps }}</span>
                <span class="cm-item cm-pres" v-if="cell.data.avgPressure" title="压力指数"><span class="cm-key">PRS</span>{{ Math.round(cell.data.avgPressure) }}</span>
              </div>
              <div class="cell-warn" v-if="cell.data.warningCount > 0">
                <span :class="['warn-badge-custom', cell.data.warningCount >= 100 ? 'wbc-high' : cell.data.warningCount >= 30 ? 'wbc-mid' : 'wbc-low']">
                  {{ cell.data.warningCount >= 1000 ? Math.round(cell.data.warningCount/100)/10+'k' : cell.data.warningCount }}
                </span>
              </div>
            </template>
            <div v-else class="cell-no-data">无数据</div>
          </template>
        </div>
      </div>
    </div>

    <!-- 选中日期详情 -->
    <div class="wb-detail" v-if="selected">
      <div class="detail-header">
        <span class="detail-date">{{ selected.dateStr }} 详情</span>
        <el-button text :icon="Close" @click="selected = null; activeRank = null" />
      </div>
      <div class="detail-body">
        <div class="detail-item di-clickable" :class="{ 'di-active': activeRank === 'heartRate' }" @click="toggleRank('heartRate')">
          <span class="di-label">平均心率</span>
          <span class="di-val">{{ selected.data.avgHeartRate || '--' }} <small>bpm</small></span>
          <span class="di-hint">排行 ▾</span>
        </div>
        <div class="detail-item di-clickable" :class="{ 'di-active': activeRank === 'bloodOxygen' }" @click="toggleRank('bloodOxygen')">
          <span class="di-label">平均血氧</span>
          <span class="di-val">{{ selected.data.avgBloodOxygen || '--' }} <small>%</small></span>
          <span class="di-hint">排行 ▾</span>
        </div>
        <div class="detail-item di-clickable" :class="{ 'di-active': activeRank === 'steps' }" @click="toggleRank('steps')">
          <span class="di-label">平均步数</span>
          <span class="di-val">{{ selected.data.avgSteps || '--' }} <small>步</small></span>
          <span class="di-hint">排行 ▾</span>
        </div>
        <div class="detail-item di-clickable" :class="{ 'di-active': activeRank === 'warnings' }" @click="toggleRank('warnings')">
          <span class="di-label">预警次数</span>
          <span class="di-val" :class="selected.data.warningCount > 0 ? 'text-red' : 'text-green'">{{ selected.data.warningCount }}</span>
          <span class="di-hint">列表 ▾</span>
        </div>
      </div>

      <!-- 排行榜 / 预警列表 -->
      <div class="rank-panel" v-if="activeRank" v-loading="rankLoading">
        <div class="rank-title">{{ rankTitle }}</div>
        <div v-if="!rankLoading && rankData.length === 0" class="rank-empty">暂无数据</div>
        <!-- 心率 / 血氧 / 步数排行 -->
        <template v-if="activeRank !== 'warnings'">
          <div class="rank-row" v-for="(row, i) in rankData" :key="i">
            <span class="rank-no" :class="i < 3 ? 'rank-top' : ''">{{ i + 1 }}</span>
            <span class="rank-name">{{ row.empName }}</span>
            <span class="rank-dept">{{ row.deptName }}</span>
            <span class="rank-val" :class="rankValClass(row)">
              <template v-if="activeRank === 'heartRate'">{{ row.avgHeartRate }} <small>bpm</small></template>
              <template v-else-if="activeRank === 'bloodOxygen'">{{ row.avgBloodOxygen }} <small>%</small></template>
              <template v-else>{{ row.avgSteps }} <small>步</small></template>
            </span>
          </div>
        </template>
        <!-- 预警列表 -->
        <template v-else>
          <div class="rank-row warn-row" v-for="(row, i) in rankData" :key="i">
            <span class="rank-no" :class="i < 3 ? 'rank-top' : ''">{{ i + 1 }}</span>
            <span class="rank-name">{{ row.empName }}</span>
            <span class="rank-dept">{{ row.deptName }}</span>
            <span class="warn-type">{{ row.warningType || row.indicatorName }}</span>
            <span class="warn-val">{{ row.warningValue }}</span>
            <span class="warn-level" :class="warnLevelClass(row.warningLevel)">{{ warnLevelLabel(row.warningLevel) }}</span>
          </div>
        </template>
      </div>
    </div>

    <!-- 部门健康对比 -->
    <div class="wb-dept-compare">
      <div class="wdc-header">
        <span class="wdc-title">部门健康对比</span>
        <div class="wdc-tabs">
          <span :class="['wdc-tab', deptDays === 7 && 'wdc-tab-active']" @click="setDeptDays(7)">近7天</span>
          <span :class="['wdc-tab', deptDays === 30 && 'wdc-tab-active']" @click="setDeptDays(30)">近30天</span>
        </div>
        <span class="wdc-hint">{{ deptCompareData.length ? `共${deptCompareData.length}个部门` : '加载中...' }}</span>
      </div>
      <div class="wdc-body">
        <div ref="deptRadarRef" class="wdc-radar"></div>
        <div class="wdc-table">
          <div class="wdt-head">
            <span class="wdt-c wdt-dept">部门</span>
            <span class="wdt-c">人数</span>
            <span class="wdt-c">心率</span>
            <span class="wdt-c">血氧%</span>
            <span class="wdt-c">收缩压</span>
            <span class="wdt-c wdt-right">压力</span>
          </div>
          <div v-for="(row, i) in deptCompareData" :key="i" class="wdt-row">
            <span class="wdt-c wdt-dept">
              <span class="wdt-color" :style="{ background: deptColors[i % deptColors.length] }"></span>
              {{ row.deptName }}
            </span>
            <span class="wdt-c">{{ row.memberCount }}</span>
            <span class="wdt-c" :class="hrClass(row.avgHeartRate)">{{ row.avgHeartRate || '--' }}</span>
            <span class="wdt-c" :class="boClass(row.avgBloodOxygen)">{{ row.avgBloodOxygen || '--' }}</span>
            <span class="wdt-c" :class="sbpClass(row.avgSystolic)">{{ row.avgSystolic || '--' }}</span>
            <span class="wdt-c wdt-right" :class="pressClass(row.avgPressure)">{{ row.avgPressure || '--' }}</span>
          </div>
          <div v-if="!deptCompareData.length" class="wdt-empty">暂无跨部门数据（需有≥2人部门的健康记录）</div>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Close } from '@element-plus/icons-vue'
import { getCalendarData, getDayHeartRateRank, getDayBloodOxygenRank, getDayStepsRank, getDayWarnings } from '@/api/workbench'
import { getDeptHealthComparison } from '@/api/health'
import { getHtml2Canvas, getJsPDF } from '@/utils/lazy-vendors'
import { createEventBinding } from '@/utils/task-timer'
import {
  DEPT_COLORS,
  WEEK_DAYS,
  calendarCellClass,
  createCalendarCells,
  healthMetricClass,
  rankTitleFor,
  rankValueClass,
  summarizeCalendarRows,
  warningLevelClass,
  warningLevelLabel
} from './workbench-view-model'
import { disposeDeptRadarChart, renderDeptRadarChart, resizeDeptRadarChart } from './workbench-chart'

const route = useRoute()
const router = useRouter()
const weekDays = WEEK_DAYS

// ── 当前月份 ────────────────────────────────────────────────────────
const today    = new Date()
const curYear  = ref(today.getFullYear())
const curMonth = ref(today.getMonth() + 1)   // 1-based

const yearLabel  = computed(() => curYear.value)
const monthLabel = computed(() => String(curMonth.value).padStart(2, '0'))
const isCurrentMonth = computed(() =>
  curYear.value === today.getFullYear() && curMonth.value === today.getMonth() + 1
)

function prevMonth() {
  if (curMonth.value === 1) { curYear.value--; curMonth.value = 12 }
  else curMonth.value--
}
function nextMonth() {
  if (isCurrentMonth.value) return
  if (curMonth.value === 12) { curYear.value++; curMonth.value = 1 }
  else curMonth.value++
}

function backToProfile() {
  if (!route.query.empCode) return
  router.push({
    path: '/health-monitor/employee-profile',
    query: {
      empCode: route.query.empCode,
      empName: route.query.empName || ''
    }
  })
}

// ── 数据加载 ────────────────────────────────────────────────────────
const loading  = ref(false)
const dayData  = ref({})   // { "2026-03-05": { avgHeartRate, avgBloodOxygen, avgSteps, warningCount } }

async function loadData() {
  loading.value = true
  try {
    const res = await getCalendarData(curYear.value, curMonth.value)
    const map = {}
    if (res.code === 200 && Array.isArray(res.data)) {
      for (const row of res.data) {
        map[row.date] = row
      }
    }
    dayData.value = map
  } catch (e) {
    ElMessage.error('加载日历数据失败')
  } finally {
    loading.value = false
  }
}

watch([curYear, curMonth], loadData)

// ── 日历格子生成 ─────────────────────────────────────────────────────
const calCells = computed(() => {
  return createCalendarCells({
    year: curYear.value,
    month: curMonth.value,
    dayData: dayData.value,
    today
  })
})

// ── 统计摘要 ─────────────────────────────────────────────────────────
const summary = computed(() => {
  return summarizeCalendarRows(dayData.value)
})

// ── 格子样式 ─────────────────────────────────────────────────────────
function cellClass(cell) {
  return calendarCellClass(cell, selected.value?.dateStr)
}

// ── 选中详情 ─────────────────────────────────────────────────────────
const selected   = ref(null)
const activeRank = ref(null)   // 'heartRate' | 'bloodOxygen' | 'steps' | 'warnings' | null
const rankData   = ref([])
const rankLoading = ref(false)

function selectDay(cell) {
  if (!cell.data) { selected.value = null; activeRank.value = null; return }
  if (selected.value?.dateStr !== cell.dateStr) activeRank.value = null
  selected.value = cell
}

const rankTitle = computed(() => {
  return rankTitleFor(activeRank.value)
})

async function toggleRank(type) {
  if (activeRank.value === type) { activeRank.value = null; return }
  activeRank.value = type
  rankData.value = []
  rankLoading.value = true
  try {
    const date = selected.value.dateStr
    const apiFn = { heartRate: getDayHeartRateRank, bloodOxygen: getDayBloodOxygenRank, steps: getDayStepsRank, warnings: getDayWarnings }[type]
    const res = await apiFn(date)
    rankData.value = res.code === 200 ? res.data : []
  } finally {
    rankLoading.value = false
  }
}

function rankValClass(row) {
  return rankValueClass(activeRank.value, row)
}

function warnLevelLabel(level) {
  return warningLevelLabel(level)
}

function warnLevelClass(level) {
  return warningLevelClass(level)
}

// ── 部门健康对比 ──────────────────────────────────────────────────────
const deptRadarRef    = ref(null)
const deptCompareData = ref([])
const deptDays        = ref(7)
let   deptChart       = null
let   resizeBinding   = null

const deptColors = DEPT_COLORS

async function loadDeptComparison() {
  try {
    const res = await getDeptHealthComparison(deptDays.value)
    deptCompareData.value = res.code === 200 ? (res.data || []) : []
  } catch {
    deptCompareData.value = []
  }
  await nextTick()
  renderDeptRadar()
}

function setDeptDays(d) {
  deptDays.value = d
  loadDeptComparison()
}

function renderDeptRadar() {
  deptChart = renderDeptRadarChart({
    chart: deptChart,
    element: deptRadarRef.value,
    data: deptCompareData.value,
    deptColors
  })
}

function resizeDeptRadar() {
  resizeDeptRadarChart(deptChart)
}

function hrClass(v) { return healthMetricClass('heartRate', v) }
function boClass(v) { return healthMetricClass('bloodOxygen', v) }
function sbpClass(v) { return healthMetricClass('systolic', v) }
function pressClass(v) { return healthMetricClass('pressure', v) }

onMounted(() => {
  loadData()
  loadDeptComparison()
  resizeBinding = createEventBinding(() => window, 'resize', resizeDeptRadar)
  resizeBinding.start()
})

onUnmounted(() => {
  resizeBinding?.stop?.()
  disposeDeptRadarChart(deptChart)
  deptChart = null
})

const pdfExporting = ref(false)

async function exportPDF() {
  pdfExporting.value = true
  try {
    const html2canvas = await getHtml2Canvas()
    const JsPDF = await getJsPDF()
    const el = document.querySelector('.wb-page')
    if (!el) { ElMessage.error('页面元素未找到'); return }
    const canvas = await html2canvas(el, {
      backgroundColor: '#080d23',
      scale: 1.5,
      useCORS: true,
      logging: false
    })
    const imgW = 210  // A4 width mm
    const imgH = canvas.height * imgW / canvas.width
    const pdf = new JsPDF({ orientation: imgH > imgW ? 'p' : 'l', unit: 'mm', format: [imgW, imgH] })
    pdf.addImage(canvas.toDataURL('image/jpeg', 0.92), 'JPEG', 0, 0, imgW, imgH)
    pdf.save(`健康工作台_${yearLabel.value}年${monthLabel.value}月.pdf`)
    ElMessage.success('PDF 已生成')
  } catch (e) {
    ElMessage.error('PDF 生成失败：' + e.message)
  } finally {
    pdfExporting.value = false
  }
}
</script>

<style scoped lang="scss" src="./workbench.scss"></style>
