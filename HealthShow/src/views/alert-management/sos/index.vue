<template>
  <div class="sos-root">

    <!-- ══ 紧凑 Header ══ -->
    <header class="sos-hd">
      <div class="sos-hd-left">
        <span class="sos-alarm-dot" :class="{ 'is-active': hasCritical }"></span>
        <h1 class="sos-hd-title">设备紧急事件</h1>
      </div>
      <div class="sos-hd-kpis">
        <div class="sos-kpi" v-for="s in kpiItems" :key="s.label">
          <span class="sos-kpi-n" :class="s.cls">{{ s.value }}</span>
          <span class="sos-kpi-l">{{ s.label }}</span>
        </div>
      </div>
      <div class="sos-hd-time">{{ lastUpdateTime }}</div>
      <button class="sos-refresh-btn" :disabled="loading" @click="fetchAll">
        {{ loading ? '刷新中...' : '刷新' }}
      </button>
    </header>

    <!-- ══ 导航 ══ -->
    <WarningCenterNav />

    <!-- ══ 报警条 ══ -->
    <div :class="['sos-alarm-bar', hasCritical ? 'sos-alarm-bar--active' : '']">
      <span class="sos-alarm-icon">ALM</span>
      <span class="sos-alarm-text">
        {{ hasCritical
          ? `当前有 ${criticalCount} 条高危设备报警未处理！`
          : '当前无高危设备报警' }}
      </span>
      <span v-if="hasCritical" class="sos-alarm-blink">紧急处置</span>
    </div>

    <!-- ══ 紧凑 KPI 卡片 ══ -->
    <div class="sos-kpi-row">
      <div class="sos-kpi-card sos-kpi-card--red">
        <div class="sos-kpi-card-val">{{ criticalCount }}</div>
        <div class="sos-kpi-card-label">高危待处置</div>
      </div>
      <div class="sos-kpi-card sos-kpi-card--orange">
        <div class="sos-kpi-card-val">{{ todayTotal }}</div>
        <div class="sos-kpi-card-label">今日触发总计</div>
      </div>
      <div class="sos-kpi-card sos-kpi-card--green">
        <div class="sos-kpi-card-val">{{ todayHandled }}</div>
        <div class="sos-kpi-card-label">今日已处置</div>
      </div>
      <div class="sos-kpi-card sos-kpi-card--blue">
        <div class="sos-kpi-card-val">{{ affectedPersons }}</div>
        <div class="sos-kpi-card-label">当前页人员</div>
      </div>
    </div>

    <div class="sos-event-filters">
      <el-radio-group v-model="eventCode" size="small" @change="changeEventCode">
        <el-radio-button value="">全部设备报警</el-radio-button>
        <el-radio-button value="SOS">SOS</el-radio-button>
        <el-radio-button value="FALL">跌倒</el-radio-button>
        <el-radio-button value="AFIB">房颤</el-radio-button>
        <el-radio-button value="TAMPER">拆卸</el-radio-button>
      </el-radio-group>
    </div>

    <!-- ══ 设备报警列表 ══ -->
    <div class="sos-section-title">
      <span class="sos-st-bar"></span>
      {{ eventCode ? eventCodeLabel(eventCode) : '设备主动报警' }}
      <span class="sos-st-sub">（共 {{ pagination.total }} 条，按时间倒序）</span>
    </div>

    <div class="sos-list" v-loading="loading"
         element-loading-text="数据加载中..."
         element-loading-background="rgba(10,20,40,0.7)">
      <div
        v-for="item in criticalList"
        :key="item.id"
        :class="['sos-item', item.handled ? 'sos-item--done' : 'sos-item--active']"
      >
        <!-- 左：等级指示 -->
        <div class="sos-item-level">
          <span class="sos-level-icon">{{ item.handled ? 'DONE' : 'ACT' }}</span>
        </div>

        <!-- 中：事件信息 -->
        <div class="sos-item-body">
          <div class="sos-item-row1">
            <span class="sos-name">{{ item.empName || item.userName || '--' }}</span>
            <span class="sos-dept">{{ item.deptName || '--' }}</span>
            <span class="sos-code">工号 {{ item.userCode || item.empCode || '--' }}</span>
          </div>
          <div class="sos-item-row2">
            <span class="sos-metric">{{ eventCodeLabel(item.eventCode) }}</span>
            <span class="sos-value">{{ item.warningType || '--' }}</span>
            <span v-if="item.deviceImei" class="sos-code">设备 {{ item.deviceImei }}</span>
            <span class="sos-time">{{ formatTime(item.createTime) }}</span>
          </div>
        </div>

        <!-- 右：操作按钮 -->
        <div class="sos-item-actions">
          <el-button
            v-if="!item.handled"
            type="danger"
            size="small"
            :loading="item._loading"
            @click="handleItem(item)"
          >标记处置</el-button>
          <el-button
            size="small"
            plain
            @click="goToProfile(item)"
          >查看画像</el-button>
          <span v-if="item.handled" class="sos-handled-tag">已处置</span>
        </div>
      </div>

      <div v-if="!loading && criticalList.length === 0" class="sos-empty">
        <span class="sos-empty-icon">OK</span>
        <span>当前筛选条件下没有设备主动报警</span>
      </div>
    </div>

    <!-- ══ 分页 ══ -->
    <div class="sos-pagination" v-if="pagination.total > pagination.size">
      <el-pagination
        v-model:current-page="pagination.page"
        :page-size="pagination.size"
        :total="pagination.total"
        layout="prev, pager, next, total"
        background
        small
        @current-change="fetchCritical"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import WarningCenterNav from '@/components/WarningCenterNav.vue'
import { getRiskWarningList, handleRiskWarning } from '@/api/risk-warning'
import { useIntervalTask } from '@/composables/useIntervalTask'

defineOptions({ name: 'SosPage' })

interface DeviceAlarmItem {
  id: number
  createTime?: string
  userCode?: string
  empCode?: string
  handled?: boolean
  _loading?: boolean
  [key: string]: unknown
}

interface RiskWarningPage {
  list?: DeviceAlarmItem[]
  records?: DeviceAlarmItem[]
  total?: number
}

interface ApiResult<T> {
  code: number
  data?: T
}

type RiskWarningQuery = Record<string, unknown>

const fetchRiskWarnings = getRiskWarningList as unknown as (
  query: RiskWarningQuery
) => Promise<ApiResult<RiskWarningPage>>
const updateRiskWarning = handleRiskWarning as unknown as (
  id: number,
  payload: { createTime?: string; handleRemark: string }
) => Promise<ApiResult<unknown>>

const router = useRouter()
const loading = ref(false)
const criticalList = ref<DeviceAlarmItem[]>([])
const criticalCount = ref(0)
const todayTotal = ref(0)
const todayHandled = ref(0)
const affectedPersons = ref(0)
const lastUpdateTime = ref('--')
const eventCode = ref('')
const pagination = reactive({ page: 1, size: 30, total: 0 })

const hasCritical = computed(() => criticalCount.value > 0)
const kpiItems = computed(() => [
  { label: '高危待处置', value: criticalCount.value, cls: 'kpi-red' },
  { label: '今日触发', value: todayTotal.value, cls: 'kpi-orange' },
  { label: '今日已处置', value: todayHandled.value, cls: 'kpi-green' },
  { label: '涉及人员', value: affectedPersons.value, cls: 'kpi-blue' }
])

async function fetchAll() {
  await Promise.allSettled([fetchCritical(), fetchTodayStats()])
  lastUpdateTime.value = dayjs().format('HH:mm:ss')
}

async function fetchCritical() {
  loading.value = true
  try {
    const response = await fetchRiskWarnings({
      page: pagination.page,
      size: pagination.size,
      eventSource: 'DEVICE_ALARM',
      eventCode: eventCode.value || undefined
    })
    if (response.code === 200) {
      const rows = response.data?.list || response.data?.records || []
      criticalList.value = rows.map((row) => ({ ...row, _loading: false }))
      pagination.total = response.data?.total || 0
    }
  } finally {
    loading.value = false
  }
}

async function fetchTodayStats() {
  try {
    const today = dayjs().format('YYYY-MM-DD')
    const [allResult, handledResult, criticalResult] = await Promise.allSettled([
      fetchRiskWarnings({ page: 1, size: 1, eventSource: 'DEVICE_ALARM', startDate: today, endDate: today }),
      fetchRiskWarnings({ page: 1, size: 1, eventSource: 'DEVICE_ALARM', handled: true, startDate: today, endDate: today }),
      fetchRiskWarnings({ page: 1, size: 1, eventSource: 'DEVICE_ALARM', level: '高危', handled: false })
    ])
    todayTotal.value = allResult.status === 'fulfilled' && allResult.value.code === 200
      ? allResult.value.data?.total || 0
      : 0
    todayHandled.value = handledResult.status === 'fulfilled' && handledResult.value.code === 200
      ? handledResult.value.data?.total || 0
      : 0
    criticalCount.value = criticalResult.status === 'fulfilled' && criticalResult.value.code === 200
      ? criticalResult.value.data?.total || 0
      : 0
    const persons = new Set(
      criticalList.value.map((row) => row.userCode || row.empCode).filter(Boolean)
    )
    affectedPersons.value = persons.size
  } catch {}
}

async function handleItem(item: DeviceAlarmItem) {
  item._loading = true
  try {
    await updateRiskWarning(item.id, {
      createTime: item.createTime,
      handleRemark: '设备报警确认处置'
    })
    item.handled = true
    criticalCount.value = Math.max(0, criticalCount.value - 1)
    ElMessage.success('已标记处置')
  } catch {
    ElMessage.error('操作失败')
  } finally {
    item._loading = false
  }
}

function goToProfile(item: DeviceAlarmItem) {
  void router.push({
    path: '/health-monitor/employee-profile',
    query: { userCode: item.userCode || item.empCode }
  })
}

function changeEventCode() {
  pagination.page = 1
  void fetchCritical()
}

function eventCodeLabel(code: unknown) {
  const labels: Record<string, string> = {
    SOS: 'SOS 求救',
    FALL: '跌倒报警',
    AFIB: '房颤报警',
    TAMPER: '拆卸报警',
    INFRARED: '红外报警',
    DEVICE_UNKNOWN: '其他设备报警'
  }
  return labels[String(code || '')] || '设备报警'
}

const formatTime = (value: unknown) => value ? dayjs(value as string).format('MM-DD HH:mm:ss') : '--'
const { start: startPolling } = useIntervalTask(fetchAll, 15000)

onMounted(() => {
  void fetchAll()
  startPolling()
})
</script>

<style lang="scss" scoped>
// ── Root ──
.sos-root {
  width: 100%;
  height: calc(100vh - 50px) !important;
  min-height: 600px;
  background: #0a1628;
  background-image:
    radial-gradient(circle at 18% 28%, rgba(255,82,82,0.04) 0%, transparent 48%),
    radial-gradient(circle at 82% 72%, rgba(42,82,152,0.07) 0%, transparent 48%);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  font-family: 'Microsoft YaHei', sans-serif;
  color: #c8d8e8;
}

.sos-event-filters {
  flex-shrink: 0;
  padding: 0 22px;

  :deep(.el-radio-group) {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 4px;
    background: rgba(20, 24, 48, 0.82);
    border: 1px solid #232b4d;
    border-radius: 8px;
  }

  :deep(.el-radio-button) {
    margin: 0;
  }

  :deep(.el-radio-button__inner) {
    min-width: 58px;
    padding: 6px 12px;
    border: 1px solid transparent !important;
    border-radius: 5px !important;
    background: transparent;
    box-shadow: none !important;
    color: #7eb8d4;
    font-size: 12px;
    line-height: 18px;
    transition: background .2s ease, border-color .2s ease, color .2s ease, box-shadow .2s ease;
  }

  :deep(.el-radio-button__inner:hover) {
    background: rgba(0, 212, 255, 0.08);
    border-color: rgba(0, 212, 255, 0.22) !important;
    color: #d9f3ff;
  }

  :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
    background: rgba(0, 212, 255, 0.14);
    border-color: rgba(0, 212, 255, 0.5) !important;
    box-shadow: inset 0 0 0 1px rgba(0, 212, 255, 0.12), 0 0 12px rgba(0, 212, 255, 0.12) !important;
    color: #d9f7ff;
    font-weight: 700;
  }

  :deep(.el-radio-button__original-radio:focus-visible + .el-radio-button__inner) {
    outline: 2px solid rgba(0, 212, 255, 0.65);
    outline-offset: 2px;
  }

  :deep(.el-radio-button__original-radio:disabled + .el-radio-button__inner) {
    background: transparent;
    color: #4a5578;
  }
}

// ── Header ──
.sos-hd {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0 22px;
  gap: 20px;
  background: rgba(0, 6, 24, 0.65);
  border-bottom: 1px solid rgba(255,82,82,0.12);
}
.sos-hd-left { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }

.sos-alarm-dot {
  width: 9px; height: 9px; border-radius: 50%;
  background: #666;
  transition: all 0.3s;
  &.is-active {
    background: #ff5252;
    box-shadow: 0 0 12px rgba(255,82,82,0.8);
    animation: sosPulse 1s ease-in-out infinite;
  }
}
@keyframes sosPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.7); }
}

.sos-hd-title {
  font-size: 20px; font-weight: 700; margin: 0;
  letter-spacing: 2px;
  background: linear-gradient(90deg, #ff5252, #ff9800);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  filter: drop-shadow(0 0 8px rgba(255,82,82,0.5));
}

.sos-hd-kpis { flex: 1; display: flex; justify-content: center; }
.sos-kpi {
  display: flex; flex-direction: column; align-items: center;
  padding: 0 28px;
  border-right: 1px solid rgba(255,82,82,0.12);
  &:first-child { border-left: 1px solid rgba(255,82,82,0.12); }
}
.sos-kpi-n {
  font-size: 20px; font-weight: 700; font-family: 'Consolas', monospace; line-height: 1.1;
  &.kpi-red    { color: #ff5252; text-shadow: 0 0 10px rgba(255,82,82,0.4); }
  &.kpi-orange { color: #ff9800; text-shadow: 0 0 10px rgba(255,152,0,0.4); }
  &.kpi-green  { color: #52c41a; text-shadow: 0 0 10px rgba(82,196,26,0.35); }
  &.kpi-blue   { color: #00d4ff; text-shadow: 0 0 10px rgba(0,212,255,0.4); }
}
.sos-kpi-l { font-size: 11px; color: #8ba6c8; margin-top: 2px; white-space: nowrap; }
.sos-hd-time { flex-shrink: 0; font-family: 'Consolas', monospace; font-size: 13px; color: #8ba6c8; }
.sos-refresh-btn {
  padding: 6px 16px; border-radius: 6px;
  background: rgba(255,82,82,0.08); border: 1px solid rgba(255,82,82,0.25);
  color: #ff5252; font-size: 12px; cursor: pointer;
  transition: all 0.2s;
  &:hover:not(:disabled) { background: rgba(255,82,82,0.15); }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

// ── 报警条 ──
.sos-alarm-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 20px;
  margin: 0 10px;
  flex-shrink: 0;
  border-radius: 8px;
  background: rgba(0, 212, 255, 0.06);
  border: 1px solid rgba(0, 212, 255, 0.15);
  transition: all .4s;

  &--active {
    background: rgba(255, 40, 40, 0.12);
    border-color: rgba(255, 40, 40, 0.4);
    animation: sos-bar-pulse 2s infinite;
  }

  .sos-alarm-icon { font-size: 22px; font-weight: 800; }
  .sos-alarm-text {
    font-size: 15px;
    font-weight: 700;
    color: #c8d8e8;
    flex: 1;
  }
  &--active .sos-alarm-text { color: #ff5252; }

  .sos-alarm-blink {
    font-size: 12px;
    font-weight: 700;
    color: #fff;
    background: #ff2828;
    padding: 3px 12px;
    border-radius: 20px;
    animation: blink-bg 1s infinite;
  }
}

// ── 紧凑 KPI 卡片 4 列 ──
.sos-kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  flex-shrink: 0;
  padding: 0 10px;
}
.sos-kpi-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 8px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: rgba(255,255,255,0.03);
  transition: background 0.2s, transform 0.15s;
  &:hover { background: rgba(255,255,255,0.06); transform: translateY(-1px); }

  .sos-kpi-card-val { font-size: 26px; font-weight: 800; font-family: 'Consolas', monospace; line-height: 1.1; }
  .sos-kpi-card-label { font-size: 11px; color: #8ba6c8; margin-top: 4px; }

  &--red {
    border-color: rgba(255,82,82,0.25);
    .sos-kpi-card-val { color: #ff5252; text-shadow: 0 0 10px rgba(255,82,82,0.3); }
  }
  &--orange {
    border-color: rgba(255,152,0,0.25);
    .sos-kpi-card-val { color: #ff9800; text-shadow: 0 0 10px rgba(255,152,0,0.3); }
  }
  &--green {
    border-color: rgba(82,196,26,0.25);
    .sos-kpi-card-val { color: #52c41a; text-shadow: 0 0 10px rgba(82,196,26,0.25); }
  }
  &--blue {
    border-color: rgba(0,212,255,0.2);
    .sos-kpi-card-val { color: #00d4ff; text-shadow: 0 0 10px rgba(0,212,255,0.3); }
  }
}

// ── 章节标题 ──
.sos-section-title {
  font-size: 14px;
  font-weight: 700;
  color: #c8d8e8;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding: 0 10px;

  .sos-st-bar {
    width: 3px; height: 16px;
    background: #ff5252;
    border-radius: 2px;
  }
  .sos-st-sub { font-size: 12px; color: #4a6080; font-weight: 400; }
}

// ── 预警列表（内部滚动） ──
.sos-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 10px;
  min-height: 0;

  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-thumb { background: rgba(255,82,82,0.25); border-radius: 2px; }
  &::-webkit-scrollbar-track { background: rgba(255,82,82,0.05); }

  .sos-item {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 12px 18px;
    border-radius: 10px;
    border: 1px solid transparent;
    transition: all .2s;
    flex-shrink: 0;

    &--active {
      background: rgba(255, 40, 40, 0.08);
      border-color: rgba(255, 40, 40, 0.25);
      &:hover { background: rgba(255, 40, 40, 0.12); }
    }

    &--done {
      background: rgba(76, 175, 80, 0.06);
      border-color: rgba(76, 175, 80, 0.15);
      opacity: 0.7;
    }
  }

  .sos-item-level {
    .sos-level-icon {
      font-size: 20px;
      line-height: 1;
      font-weight: 700;
    }
  }

  .sos-item-body {
    flex: 1;

    .sos-item-row1, .sos-item-row2 {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .sos-item-row1 { margin-bottom: 4px; }

    .sos-name   { font-size: 15px; font-weight: 700; color: #fff; }
    .sos-dept   { font-size: 11px; color: #8ba6c8; background: rgba(255,82,82,0.08); padding: 1px 8px; border-radius: 10px; }
    .sos-code   { font-size: 11px; color: #4a6080; font-family: 'Consolas', monospace; }
    .sos-metric { font-size: 13px; color: #8ba6c8; }
    .sos-value  { font-size: 17px; font-weight: 800; color: #ff5252; }
    .sos-time   { font-size: 12px; color: #4a6080; font-family: 'Consolas', monospace; }
  }

  .sos-item-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }

  .sos-handled-tag {
    font-size: 12px;
    color: #52c41a;
    padding: 3px 10px;
    border: 1px solid rgba(82,196,26,0.3);
    border-radius: 4px;
  }
}

.sos-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 0;
  color: #4a6080;

  .sos-empty-icon {
    width: 48px; height: 48px; border-radius: 50%;
    background: rgba(82,196,26,0.1); border: 1px solid rgba(82,196,26,0.3);
    display: flex; align-items: center; justify-content: center;
    font-size: 20px; color: #52c41a;
  }
}

// ── 分页 ──
.sos-pagination {
  display: flex;
  justify-content: center;
  flex-shrink: 0;
  padding: 6px 0;

  :deep(.el-pagination) {
    --el-pagination-bg-color: rgba(13,40,71,0.6);
    --el-pagination-button-bg-color: rgba(13,40,71,0.6);
    --el-pagination-text-color: #8ba6c8;
    --el-pagination-hover-color: #ff5252;
    --el-pagination-button-disabled-bg-color: rgba(13,40,71,0.3);
  }
}

// ── 动画 ──
@keyframes sos-bar-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255,40,40,0.3); }
  50%       { box-shadow: 0 0 20px 4px rgba(255,40,40,0.15); }
}

@keyframes blink-bg {
  0%, 100% { background: #ff2828; }
  50%       { background: #cc0000; }
}

// ── 间距 ──
.sos-root > * + * { margin-top: 8px; }
.sos-root > .sos-hd { margin-top: 0; }

// ── Mobile ──
@media (max-width: 768px) {
  .sos-root {
    height: auto !important;
    min-height: calc(100vh - 50px);
    overflow-y: auto !important;
    padding-bottom: 64px;
  }
  .sos-hd {
    height: auto;
    flex-wrap: wrap;
    padding: 8px 12px;
    gap: 6px;
  }
  .sos-hd-kpis {
    order: 3; width: 100%;
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 4px;
  }
  .sos-kpi { padding: 4px 12px; }
  .sos-hd-time { display: none; }
  .sos-kpi-row { grid-template-columns: repeat(2, 1fr); }
  .sos-event-filters {
    padding: 0 12px;

    :deep(.el-radio-group) {
      display: flex;
      width: 100%;
      flex-wrap: wrap;
    }

    :deep(.el-radio-button) {
      flex: 1 1 calc(50% - 3px);
    }

    :deep(.el-radio-button__inner) {
      width: 100%;
    }
  }
  .sos-alarm-bar { margin: 0 6px; padding: 8px 14px; }
  .sos-list {
    overflow: visible !important;
    padding: 0 6px;
  }
  .sos-item {
    flex-wrap: wrap;
  }
  .sos-item-actions {
    width: 100%;
    justify-content: flex-end;
    padding-top: 6px;
  }
}
</style>
