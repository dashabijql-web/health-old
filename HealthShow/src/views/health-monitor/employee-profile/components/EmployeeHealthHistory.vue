<template>
  <section class="ep-panel ep-history" v-loading="loading">
    <div class="ep-history-head">
      <div>
        <div class="ep-ph ep-history-title"><span class="ep-ph-bar"></span>历史健康数据</div>
        <p>{{ employeeName || '--' }} · {{ employeeCode || '--' }} · {{ rangeLabel }}</p>
      </div>
      <div class="ep-history-filters">
        <el-button-group>
          <el-button v-for="item in quickRanges" :key="item.days" :type="activeDays === item.days ? 'primary' : ''" @click="applyQuickRange(item.days)">{{ item.label }}</el-button>
        </el-button-group>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          :clearable="false"
          :disabled-date="disableFuture"
          @change="applyCustomRange"
        />
        <el-button
          type="primary"
          :loading="historyRefreshing"
          :disabled="historyRefreshing"
          @click="handleManualRefresh"
        >
          <el-icon><Refresh /></el-icon>
          查询
        </el-button>
      </div>
    </div>

    <div class="ep-history-summary">
      <div><span>完整样本</span><strong>{{ trend.totalSamples || 0 }} 条</strong></div>
      <div><span>曲线粒度</span><strong>{{ trend.granularity === 'record' ? '原始记录' : trend.granularity === 'hour' ? '小时均值' : '日均值' }}</strong></div>
      <div><span>明细总数</span><strong>{{ recordTotal }} 条</strong></div>
    </div>

    <el-tabs v-model="activeTab" class="ep-history-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="历史曲线" name="chart">
        <div class="ep-metric-switches">
          <div class="ep-metric-toggle-grid" role="group" aria-label="历史曲线指标">
            <label
              v-for="metric in metrics"
              :key="metric.key"
              class="ep-metric-toggle"
              :class="{ 'is-active': activeMetrics.includes(metric.key) }"
              :style="{ '--metric-color': metric.color }"
            >
              <input v-model="activeMetrics" type="checkbox" :value="metric.key" @change="renderChart" />
              <span class="ep-metric-toggle__state" aria-hidden="true">
                <el-icon><Check /></el-icon>
              </span>
              <span class="ep-metric-toggle__copy">
                <strong>{{ metric.label }}</strong>
                <small>{{ metric.unit || '指数' }}</small>
              </span>
              <span class="ep-metric-toggle__trace" aria-hidden="true"></span>
            </label>
          </div>
        </div>
        <div class="ep-history-chart-wrap">
          <div ref="chartRef" class="ep-history-chart"></div>
          <div v-if="!trend.points?.length" class="ep-history-empty">所选时间段暂无健康数据</div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="明细记录" name="records">
        <el-table :data="records" class="ep-history-table" height="420" empty-text="所选时间段暂无健康记录">
          <el-table-column prop="recordTime" label="采集时间" min-width="168">
            <template #default="{ row }">{{ row.recordTime || row.record_time || row.time || '--' }}</template>
          </el-table-column>
          <el-table-column prop="heartRate" label="心率" width="86"><template #default="{ row }">{{ value(row.heartRate) }}</template></el-table-column>
          <el-table-column prop="bloodOxygen" label="血氧" width="86"><template #default="{ row }">{{ unitValue(row.bloodOxygen, '%') }}</template></el-table-column>
          <el-table-column prop="temperature" label="体温" width="96"><template #default="{ row }">{{ formatHistoryTemperature(row.temperature) }}</template></el-table-column>
          <el-table-column label="血压" width="100"><template #default="{ row }">{{ formatHistoryBloodPressure(row) }}</template></el-table-column>
          <el-table-column prop="pressure" label="压力" width="78"><template #default="{ row }">{{ value(row.pressure) }}</template></el-table-column>
          <el-table-column prop="steps" label="步数" width="100"><template #default="{ row }">{{ value(row.steps) }}</template></el-table-column>
          <el-table-column prop="calories" label="热量" width="100"><template #default="{ row }">{{ unitValue(row.calories, 'kcal') }}</template></el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="recordPage"
          v-model:page-size="recordSize"
          :page-sizes="[20, 50, 100]"
          :total="recordTotal"
          layout="total, sizes, prev, pager, next"
          @current-change="loadRecords"
          @size-change="handleSizeChange"
        />
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Refresh } from '@element-plus/icons-vue'
import * as echarts from '@/utils/echarts-setup'
import { getEmployeeHealthHistory, getHealthRecords } from '@/api/health'
import {
  HISTORY_METRICS,
  buildHistoryChartOption,
  defaultHistoryRange,
  formatHistoryBloodPressure,
  formatDate,
  formatHistoryTemperature,
  normalizeHistoryRecords
} from '../employee-profile-history'

interface HistoryMetric {
  key: string
  label: string
  unit: string
  color: string
  yAxisIndex: number
}

interface HealthHistory {
  points: Record<string, unknown>[]
  totalSamples: number
  granularity: string
}

interface HistoryRecordPage {
  records: Record<string, unknown>[]
  total: number
}

const props = defineProps({
  employeeCode: { type: String, default: '' },
  employeeName: { type: String, default: '' },
  refreshToken: { type: Number, default: 0 }
})

const quickRanges = [{ label: '今日', days: 1 }, { label: '近7日', days: 7 }, { label: '近30日', days: 30 }]
const metrics = HISTORY_METRICS as HistoryMetric[]
const activeDays = ref(7)
const dateRange = ref<[string, string]>(defaultHistoryRange(7) as [string, string])
const activeTab = ref('chart')
const activeMetrics = ref(metrics.map((metric) => metric.key))
const loading = ref(false)
const trend = ref<HealthHistory>({ points: [], totalSamples: 0, granularity: 'record' })
const records = ref<Record<string, unknown>[]>([])
const recordTotal = ref(0)
const recordPage = ref(1)
const recordSize = ref(20)
const historyRefreshing = ref(false)
const chartRef = ref<HTMLElement | null>(null)
let chart: ReturnType<typeof echarts.init> | null = null
let historyRequestInFlight = false

const rangeLabel = computed(() => dateRange.value?.length === 2 ? `${dateRange.value[0]} 至 ${dateRange.value[1]}` : '--')
const disableFuture = (date: Date) => date.getTime() > Date.now()
const value = (input: unknown) => input === null || input === undefined || input === '' ? '--' : input
const unitValue = (input: unknown, unit: string) => value(input) === '--' ? '--' : `${String(input)} ${unit}`

function isCurrentRange() {
  return dateRange.value?.[1] === formatDate(new Date())
}

async function loadHistory({ background = false }: { background?: boolean } = {}) {
  if (!props.employeeCode || dateRange.value?.length !== 2) return
  if (!validateRange()) return
  if (historyRequestInFlight) return
  historyRequestInFlight = true
  historyRefreshing.value = true
  if (!background) loading.value = true
  try {
    const params = { userCode: props.employeeCode, startDate: dateRange.value[0], endDate: dateRange.value[1] }
    const [trendResult, recordResult] = await Promise.allSettled([
      getEmployeeHealthHistory(params),
      getHealthRecords({ userCode: props.employeeCode, startTime: dateRange.value[0], endTime: dateRange.value[1], current: 1, size: recordSize.value })
    ])
    trend.value = trendResult.status === 'fulfilled' && trendResult.value?.data
      ? trendResult.value.data
      : { points: [], totalSamples: 0, granularity: 'record' }
    if (recordResult.status === 'fulfilled') applyRecords(recordResult.value?.data)
    else applyRecords(null)
    recordPage.value = 1
    await nextTick()
    renderChart()
  } finally {
    historyRequestInFlight = false
    historyRefreshing.value = false
    if (!background) loading.value = false
  }
}

function handleManualRefresh() {
  void loadHistory()
}

async function loadRecords() {
  if (!props.employeeCode || dateRange.value?.length !== 2) return
  const response = await getHealthRecords({
    userCode: props.employeeCode,
    startTime: dateRange.value[0],
    endTime: dateRange.value[1],
    current: recordPage.value,
    size: recordSize.value
  })
  applyRecords(response?.data)
}

function applyRecords(payload: unknown) {
  const page = normalizeHistoryRecords(payload) as HistoryRecordPage
  records.value = page.records
  recordTotal.value = page.total
}

function applyQuickRange(days: number) {
  activeDays.value = days
  dateRange.value = defaultHistoryRange(days)
  void loadHistory()
}

function applyCustomRange() {
  activeDays.value = 0
  void loadHistory()
}

function validateRange() {
  const start = new Date(`${dateRange.value[0]}T00:00:00`)
  const end = new Date(`${dateRange.value[1]}T00:00:00`)
  const days = Math.floor((end.getTime() - start.getTime()) / 86400000) + 1
  if (!Number.isFinite(days) || days < 1) {
    ElMessage.warning('结束日期不能早于开始日期')
    return false
  }
  if (days > 365) {
    ElMessage.warning('历史数据查询范围不能超过365天')
    return false
  }
  return true
}

function handleSizeChange() {
  recordPage.value = 1
  void loadRecords()
}

function handleTabChange(name: string | number) {
  if (name === 'chart') nextTick(renderChart)
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  chart.setOption(buildHistoryChartOption(trend.value, activeMetrics.value), true)
  chart.resize()
}

watch(() => props.employeeCode, () => void loadHistory())
watch(() => props.refreshToken, (next, previous) => {
  if (next !== previous && next > 0 && isCurrentRange()) void loadHistory({ background: true })
})
onMounted(() => void loadHistory())
onBeforeUnmount(() => chart?.dispose())
</script>

<style scoped lang="scss">
.ep-history {
  margin: 0 14px;
  padding: 16px;
  background: #0a1428;
  border: 1px solid #1a3153;
  border-radius: 8px;
  color: #c8d8f0;
}
.ep-history-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
.ep-history-title { display: flex; align-items: center; gap: 8px; min-height: 28px; color: #d4e7fa; font-size: 14px; font-weight: 700; }
.ep-ph-bar { width: 3px; height: 15px; background: #00b4ff; border-radius: 2px; }
.ep-history-head p { margin: 7px 0 0; color: #6f91ad; font-size: 11px; }
.ep-history-filters { display: flex; align-items: center; justify-content: flex-end; gap: 10px; flex-wrap: wrap; }
.ep-history-filters :deep(.el-date-editor) { width: 260px; }
.ep-history-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin: 14px 0 6px; }
.ep-history-summary div { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; padding: 10px 12px; background: #0d192d; border: 1px solid #213653; border-radius: 6px; color: #7191ad; font-size: 11px; }
.ep-history-summary strong { color: #dceeff; font-family: var(--font-mono); }
.ep-history-tabs :deep(.el-tabs__header) { margin: 0 0 12px; }
.ep-history-tabs :deep(.el-tabs__item) { color: #7897b2; }
.ep-history-tabs :deep(.el-tabs__item.is-active) { color: #40c4ff; }
.ep-metric-switches {
  margin-bottom: 12px;
  padding: 10px;
  overflow: hidden;
  border: 1px solid #193554;
  border-radius: 6px;
  background:
    linear-gradient(rgba(20, 61, 91, 0.16) 1px, transparent 1px),
    #071326;
  background-size: 100% 12px;
}
.ep-metric-toggle-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(108px, 1fr));
  gap: 8px;
}
.ep-metric-toggle {
  position: relative;
  min-width: 0;
  min-height: 48px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 10px 10px;
  overflow: hidden;
  border: 1px solid #24415f;
  border-radius: 5px;
  background: rgba(10, 25, 45, 0.92);
  color: #7e9bb5;
  cursor: pointer;
  transition: border-color 160ms ease, background-color 160ms ease, box-shadow 160ms ease, color 160ms ease;
}
.ep-metric-toggle:hover {
  border-color: color-mix(in srgb, var(--metric-color) 58%, #24415f);
  background: color-mix(in srgb, var(--metric-color) 7%, #0a192d);
  color: #c8def0;
}
.ep-metric-toggle:focus-within {
  outline: 2px solid color-mix(in srgb, var(--metric-color) 72%, #ffffff);
  outline-offset: 2px;
}
.ep-metric-toggle.is-active {
  border-color: color-mix(in srgb, var(--metric-color) 68%, #26496c);
  background: color-mix(in srgb, var(--metric-color) 13%, #0a192d);
  color: #edf8ff;
  box-shadow: inset 0 0 16px color-mix(in srgb, var(--metric-color) 10%, transparent), 0 0 12px color-mix(in srgb, var(--metric-color) 12%, transparent);
}
.ep-metric-toggle input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}
.ep-metric-toggle__state {
  width: 19px;
  height: 19px;
  flex: 0 0 19px;
  display: grid;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--metric-color) 42%, #31506d);
  border-radius: 50%;
  background: #09172a;
  color: transparent;
  box-shadow: inset 0 0 0 3px #09172a;
  transition: background-color 160ms ease, color 160ms ease, box-shadow 160ms ease;
}
.ep-metric-toggle__state .el-icon { font-size: 12px; }
.ep-metric-toggle.is-active .ep-metric-toggle__state {
  background: var(--metric-color);
  color: #06111f;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--metric-color) 80%, #ffffff), 0 0 10px color-mix(in srgb, var(--metric-color) 48%, transparent);
}
.ep-metric-toggle__copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ep-metric-toggle__copy strong {
  overflow: hidden;
  color: inherit;
  font-size: 12px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ep-metric-toggle__copy small {
  color: #587894;
  font: 9px/1.2 var(--font-mono);
}
.ep-metric-toggle.is-active .ep-metric-toggle__copy small { color: color-mix(in srgb, var(--metric-color) 68%, #a9c4dc); }
.ep-metric-toggle__trace {
  position: absolute;
  right: 9px;
  bottom: 5px;
  left: 38px;
  height: 2px;
  overflow: hidden;
  background: #203a55;
}
.ep-metric-toggle__trace::after {
  position: absolute;
  inset: 0;
  background: var(--metric-color);
  box-shadow: 0 0 8px var(--metric-color);
  content: '';
  opacity: 0;
  transform: translateX(-72%);
  transition: opacity 160ms ease, transform 200ms ease;
}
.ep-metric-toggle.is-active .ep-metric-toggle__trace::after { opacity: 1; transform: translateX(0); }
.ep-history-chart-wrap { position: relative; }
.ep-history-chart { width: 100%; height: 390px; background: #081224; border: 1px solid #192d48; border-radius: 6px; }
.ep-history-empty { position: absolute; inset: 0; display: grid; place-items: center; color: #56718d; font-size: 12px; pointer-events: none; }
.ep-history-table { width: 100%; }
.ep-history-tabs :deep(.el-pagination) { justify-content: flex-end; margin-top: 12px; }

@media (max-width: 767px) {
  .ep-history { margin: 0 12px; padding: 14px; }
  .ep-history-head { align-items: stretch; flex-direction: column; }
  .ep-history-filters { justify-content: flex-start; }
  .ep-history-filters :deep(.el-date-editor) { width: 100%; }
  .ep-history-summary { grid-template-columns: 1fr; gap: 6px; }
  .ep-history-chart { height: 330px; }
  .ep-metric-switches { padding: 8px; }
  .ep-metric-toggle-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .ep-history-tabs :deep(.el-pagination) { justify-content: flex-start; overflow-x: auto; }
}
</style>
