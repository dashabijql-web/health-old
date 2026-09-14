<template>
  <div class="nf-root">
    <header class="nf-hd">
      <div class="nf-hd-left">
        <span class="nf-live-dot"></span>
        <h1 class="nf-hd-title">待办事件</h1>
      </div>
      <div class="nf-hd-kpis">
        <span class="nf-kpi">待处理 <em class="nf-kpi-val danger">{{ unhandledCount }}</em></span>
      </div>
      <div class="nf-hd-time">{{ lastFetchedAt || '--' }}</div>
      <div class="nf-hd-actions">
        <el-select v-model="filter.level" placeholder="全部级别" size="small" clearable style="width:110px" @change="fetchList">
          <el-option label="全部级别" value="" />
          <el-option label="高危" value="高危" />
          <el-option label="中危" value="中危" />
          <el-option label="低危" value="低危" />
        </el-select>
        <el-select v-model="filter.source" placeholder="全部来源" size="small" clearable style="width:130px" @change="fetchList">
          <el-option label="全部来源" value="" />
          <el-option label="体征预警" value="HEALTH_THRESHOLD" />
          <el-option label="设备报警" value="DEVICE_ALARM" />
          <el-option label="趋势风险" value="TREND_WARNING" />
        </el-select>
        <el-input v-model="filter.userCode" placeholder="搜索员工工号" size="small" clearable style="width:150px" @change="fetchList" />
        <el-button size="small" type="primary" plain @click="handleBatchConfirm" :disabled="list.length === 0">确认当前页</el-button>
        <el-button size="small" plain @click="goToRecords">处置记录</el-button>
        <el-button size="small" @click="fetchList" :loading="loading">刷新</el-button>
      </div>
    </header>

    <WarningCenterNav />

    <!-- ── 统计行 ── -->
    <div class="nf-stats-row">
      <div class="notif-stat notif-stat--danger" @click="setLevelFilter('高危')">
        <span class="ns-val">{{ levelCounts[3] || 0 }}</span>
        <span class="ns-label">危险</span>
      </div>
      <div class="notif-stat notif-stat--warn" @click="setLevelFilter('中危')">
        <span class="ns-val">{{ levelCounts[2] || 0 }}</span>
        <span class="ns-label">预警</span>
      </div>
      <div class="notif-stat notif-stat--info" @click="setLevelFilter('低危')">
        <span class="ns-val">{{ levelCounts[1] || 0 }}</span>
        <span class="ns-label">提示</span>
      </div>
      <div class="notif-stat notif-stat--ok" @click="setHandledFilter(false)">
        <span class="ns-val">{{ unhandledCount }}</span>
        <span class="ns-label">待处理</span>
      </div>
    </div>

    <!-- ── 通知列表 ── -->
    <div class="nf-list" v-loading="loading">
      <div
        v-for="item in list"
        :key="item.id"
        :class="['nf-item', `lv-${item.warningLevel}`, item.handled ? 'is-handled' : '']"
        @click="openDetail(item)"
      >
        <div class="ni-left">
          <span :class="['ni-badge', item.warningLevelClass]">{{ item.warningLevelLabel }}</span>
          <div class="ni-info">
            <span class="ni-user">{{ item.empName || item.userName || '--' }}</span>
            <span class="ni-dept">{{ item.deptName || '--' }}</span>
          </div>
        </div>
        <div class="ni-mid">
          <span class="ni-source">{{ sourceLabel(item.eventSource) }}</span>
          <span class="ni-type">{{ item.warningType || item.indicatorName || '--' }}</span>
          <span class="ni-val">{{ item.warningValue || item.indicatorValue || '--' }}</span>
          <span class="ni-sla-tag" :class="`is-${item.slaStatus || 'unknown'}`">{{ item.slaStatusText || '未知' }}</span>
          <span class="ni-sla-clock" :class="`is-${item.slaStatus || 'unknown'}`">
            <span>{{ item.slaClockLabel || '未知' }}</span>
            <strong>{{ item.slaClockText || '--' }}</strong>
          </span>
          <span class="ni-deadline">到期 {{ formatDeadline(item.slaDeadline) }}</span>
        </div>
        <div class="ni-time">{{ formatTime(item.createTime) }}</div>
        <div class="ni-actions" @click.stop>
          <el-button
            v-if="!item.handled"
            size="small"
            type="success"
            plain
            @click="handleSingle(item)"
            :loading="item._loading"
          >处理</el-button>
          <span v-else class="ni-done-tag">{{ warningHandledStatusLabel(item) }}</span>
          <el-button size="small" plain @click="goToUser(item)">画像</el-button>
        </div>
      </div>
      <div v-if="!loading && list.length === 0" class="nf-empty">
          <span>当前没有待办事件</span>
      </div>
    </div>

    <!-- ── 分页 ── -->
    <div class="nf-pagination">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        background
        size="small"
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import WarningCenterNav from '@/components/WarningCenterNav.vue'
import { getRiskWarningList, handleRiskWarning, handleBatchRiskWarning } from '@/api/risk-warning'
import dayjs from 'dayjs'
import { createIntervalTask } from '@/utils/task-timer'
import { buildWarningLifecycleItem, markWarningHandled, warningHandledStatusLabel } from '../common/warning-lifecycle'
import type {
  ApiResult,
  EventSource,
  IntervalTask,
  RiskWarningPage,
  RiskWarningQuery,
  WarningItem,
  WarningLevel,
  WarningLocator
} from './notifications-types'

const fetchRiskWarnings = getRiskWarningList as unknown as (
  params: RiskWarningQuery
) => Promise<ApiResult<RiskWarningPage>>
const updateRiskWarning = handleRiskWarning as unknown as (
  id: number,
  data: { createTime?: string; handleRemark: string }
) => Promise<ApiResult<unknown>>
const updateRiskWarnings = handleBatchRiskWarning as unknown as (
  locators: WarningLocator[]
) => Promise<ApiResult<unknown>>

const router = useRouter()
const loading = ref(false)
const list = ref<WarningItem[]>([])
const filter = reactive({
  level: '' as WarningLevel,
  source: '' as EventSource,
  handled: false,
  userCode: ''
})
const pagination = reactive({ page: 1, size: 20, total: 0 })
const levelCounts = reactive<Record<1 | 2 | 3, number>>({ 1: 0, 2: 0, 3: 0 })
const unhandledCount = ref(0)
const lastFetchedAt = ref('')
let pollTask: IntervalTask | null = null

async function fetchList() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size,
      level: filter.level || undefined,
      handled: false,
      eventSource: filter.source || undefined,
      userCode: filter.userCode || undefined
    }
    const res = await fetchRiskWarnings(params)
    if (res.code === 200) {
      const rows = res.data?.list || res.data?.records || []
      list.value = rows.map((row: Record<string, unknown>) => (
        buildWarningLifecycleItem({ ...row, _loading: false }) as unknown as WarningItem
      ))
      pagination.total = res.data?.total || 0
      lastFetchedAt.value = dayjs().format('HH:mm:ss')
    }
  } finally {
    loading.value = false
  }
}

async function fetchSummary() {
  try {
    const [r1, r2, r3, rAll] = await Promise.allSettled([
      fetchRiskWarnings({ page: 1, size: 1, level: '低危', handled: false }),
      fetchRiskWarnings({ page: 1, size: 1, level: '中危', handled: false }),
      fetchRiskWarnings({ page: 1, size: 1, level: '高危', handled: false }),
      fetchRiskWarnings({ page: 1, size: 1, handled: false })
    ])
    levelCounts[1] = r1.status === 'fulfilled' && r1.value.code === 200 ? (r1.value.data?.total || 0) : 0
    levelCounts[2] = r2.status === 'fulfilled' && r2.value.code === 200 ? (r2.value.data?.total || 0) : 0
    levelCounts[3] = r3.status === 'fulfilled' && r3.value.code === 200 ? (r3.value.data?.total || 0) : 0
    unhandledCount.value = rAll.status === 'fulfilled' && rAll.value.code === 200 ? (rAll.value.data?.total || 0) : 0
  } catch (_) {}
}

async function handleSingle(item: WarningItem) {
  item._loading = true
  try {
    await updateRiskWarning(item.id, { createTime: item.createTime, handleRemark: '待办事件确认处置' })
    markWarningHandled(item)
    unhandledCount.value = Math.max(0, unhandledCount.value - 1)
    ElMessage.success('已标记处理')
  } catch (_) {
    ElMessage.error('操作失败')
  } finally {
    item._loading = false
  }
}

async function handleBatchConfirm() {
  const locators = list.value
    .filter((item) => !item.handled)
    .map((item) => ({ warningId: item.id, occurredAt: item.createTime }))
  if (!locators.length) return
  try {
    await updateRiskWarnings(locators)
    list.value.forEach((item) => {
      if (!item.handled) markWarningHandled(item)
    })
    await fetchSummary()
    ElMessage.success(`已确认处置 ${locators.length} 条`)
  } catch (_) {
    ElMessage.error('批量处理失败')
  }
}

function goToUser(item: WarningItem) {
  void router.push({
    path: '/health-monitor/employee-profile',
    query: { userCode: String(item.userCode || item.empCode || '') }
  })
}

function goToRecords() {
  void router.push({
    path: '/alert-management/records',
    query: {
      warningLevel: filter.level,
      handleStatus: filter.handled ? 'handled' : 'unhandled',
      keyword: filter.userCode
    }
  })
}

function openDetail(item: WarningItem) {
  if (item.userCode || item.empCode) goToUser(item)
}

function setLevelFilter(level: WarningLevel) {
  filter.level = filter.level === level ? '' : level
  pagination.page = 1
  void fetchList()
}

function setHandledFilter(value: boolean) {
  filter.handled = value
  pagination.page = 1
  void fetchList()
}

function sourceLabel(source: unknown) {
  const labels: Record<string, string> = {
    HEALTH_THRESHOLD: '体征预警',
    DEVICE_ALARM: '设备报警',
    TREND_WARNING: '趋势风险'
  }
  return labels[String(source || '')] || '历史事件'
}

function formatTime(value: unknown) {
  if (!value) return '--'
  return dayjs(value as string).format('MM-DD HH:mm')
}

function formatDeadline(value: unknown) {
  if (!value || value === '--') return '--'
  return dayjs(value as string).format('HH:mm')
}

onMounted(() => {
  void fetchList()
  void fetchSummary()
  const task = createIntervalTask(() => {
    void fetchList()
    void fetchSummary()
  }, 30000) as IntervalTask
  pollTask = task
  task.start()
})

onBeforeUnmount(() => {
  pollTask?.stop()
})
</script>

<style scoped lang="scss">
@import './notifications.scss';
</style>
