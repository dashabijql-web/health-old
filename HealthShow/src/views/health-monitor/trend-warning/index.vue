<template>
  <div class="tw-root">

    <!-- ══ 顶部 Header ══ -->
    <header class="tw-hd">
      <div class="tw-hd-left">
        <span class="tw-live-dot"></span>
        <h1 class="tw-hd-title">趋势预警</h1>
      </div>

      <div class="tw-hd-kpis">
        <div class="tw-kpi" v-for="k in headerKpis" :key="k.label">
          <span class="tw-kpi-n" :class="k.cls">{{ k.val }}</span>
          <span class="tw-kpi-l">{{ k.label }}</span>
        </div>
      </div>

      <div class="tw-hd-time">{{ lastUpdate || '--:--' }}</div>
      <button class="hm-export-btn" :disabled="loading" @click="fetchData">
        {{ loading ? '刷新中...' : '刷新' }}
      </button>
    </header>

    <!-- ══ 主体 ══ -->
    <section class="tw-bd" v-loading="loading" element-loading-text="数据加载中..." element-loading-background="rgba(10,20,40,0.7)">

      <!-- 风险摘要卡片 -->
      <div class="tw-summary">
        <div v-for="s in riskSummaryCards" :key="s.key"
             :class="['tw-sum-card', 'sum-' + s.key, filterLevel === s.level ? 'is-active' : '']"
             @click="toggleFilter(s.level)">
          <span class="tw-sum-icon" :style="{ color: s.color }">{{ s.icon }}</span>
          <span class="tw-sum-count" :style="{ color: s.color }">{{ s.count }}</span>
          <span class="tw-sum-label">{{ s.label }}</span>
          <span class="tw-sum-note">{{ s.note }}</span>
        </div>
      </div>

      <!-- 筛选栏 -->
      <div class="tw-filter">
        <el-select v-model="filterLevel" placeholder="风险等级" clearable size="small" style="width:120px">
          <el-option label="高风险" :value="3" />
          <el-option label="中风险" :value="2" />
          <el-option label="低风险" :value="1" />
        </el-select>
        <el-select v-model="filterDept" placeholder="所属部门" clearable size="small" style="width:140px">
          <el-option v-for="d in deptOptions" :key="d" :label="d" :value="d" />
        </el-select>
        <el-input v-model="filterName" placeholder="姓名搜索" clearable size="small" style="width:130px" />
        <span class="tw-filter-count">共 <em>{{ filteredList.length }}</em> 人次风险</span>
      </div>

      <!-- 风险列表 -->
      <div class="tw-list">
        <div v-if="!loading && filteredList.length === 0" class="tw-empty">
          <span class="tw-empty-icon">✓</span>
          <span class="tw-empty-title">暂无趋势风险人员</span>
          <span class="tw-empty-desc">可切换部门、风险等级或姓名条件继续排查</span>
        </div>

        <div v-for="emp in pagedList" :key="emp.empCode"
             :class="['tw-card', 'risk-' + emp.riskLevel]">
          <div class="tw-card-hd">
            <span class="tw-risk-badge" :class="'badge-' + emp.riskLevel">
              {{ riskLabel(emp.riskLevel) }}
            </span>
            <span class="tw-emp-name">{{ emp.empName }}</span>
            <span class="tw-emp-dept">{{ emp.deptName }}</span>
            <span class="tw-emp-code">{{ emp.empCode }}</span>
            <span class="tw-card-link" @click="goProfile(emp.empCode)">查看画像 →</span>
          </div>

          <div class="tw-metric" v-for="m in emp.riskMetrics" :key="m.metric">
            <span class="tw-m-name">{{ m.metricName }}</span>
            <span class="tw-m-cur">当前 {{ m.currentValue }}{{ m.unit }}</span>
            <span class="tw-m-proj" :class="projClass(m)">
              → 预测 {{ m.projectedValue }}{{ m.unit }}
            </span>
            <span class="tw-m-thresh">阈值 {{ m.threshold }}{{ m.unit }}</span>
            <span class="tw-m-tag" :class="'tag-' + m.riskLevel">
              {{ m.riskLevel === 3 ? '即将突破' : m.riskLevel === 2 ? '7日内风险' : '趋势预警' }}
            </span>
            <div class="tw-sparkline">
              <SparkLine :data="m.history" :threshold="m.threshold"
                         :risky="m.riskDir" :metric="m.metric" />
            </div>
          </div>
        </div>

        <div v-if="!loading && filteredList.length > pageSize" class="tw-pagination">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="filteredList.length"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            background
            size="small"
          />
        </div>
      </div>

    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getTrendWarningPrediction } from '@/api/trend-warning'
import SparkLine from './TrendSparkLine.js'

defineOptions({ name: 'TrendWarning' })

type RiskLevel = 1 | 2 | 3

interface TrendSummary {
  highRisk?: number
  mediumRisk?: number
  lowRisk?: number
  normal?: number
}

interface RiskMetric {
  metric: string
  metricName: string
  currentValue: string | number
  projectedValue: string | number
  threshold: number
  unit: string
  riskLevel: RiskLevel
  riskDir?: string
  history?: number[]
}

interface TrendEmployee {
  empCode: string
  empName?: string
  deptName?: string
  riskLevel: RiskLevel
  riskMetrics: RiskMetric[]
}

interface TrendPredictionData {
  summary?: TrendSummary
  list?: TrendEmployee[]
}

interface ApiResult<T> {
  code: number
  data?: T
}

const fetchTrendPrediction = getTrendWarningPrediction as unknown as () => Promise<ApiResult<TrendPredictionData>>
const router = useRouter()

const loading = ref(false)
const summary = ref<TrendSummary>({})
const list = ref<TrendEmployee[]>([])
const filterLevel = ref<RiskLevel | null>(null)
const filterDept = ref('')
const filterName = ref('')
const currentPage = ref(1)
const pageSize = ref(50)
const lastUpdate = ref('')

const headerKpis = computed(() => [
  { label: '高风险', val: summary.value.highRisk || 0, cls: 'kpi-red' },
  { label: '中风险', val: summary.value.mediumRisk || 0, cls: 'kpi-orange' },
  { label: '低风险', val: summary.value.lowRisk || 0, cls: 'kpi-yellow' },
  { label: '正常', val: summary.value.normal || 0, cls: 'kpi-green' }
])
const riskSummaryCards = computed(() => [
  { key: 'high', level: 3 as RiskLevel, label: '高风险', count: summary.value.highRisk || 0, note: '≤3天可能超标', color: '#ff5252', icon: 'ALERT' },
  { key: 'medium', level: 2 as RiskLevel, label: '中风险', count: summary.value.mediumRisk || 0, note: '4~7天可能超标', color: '#ffa726', icon: 'WARN' },
  { key: 'low', level: 1 as RiskLevel, label: '低风险', count: summary.value.lowRisk || 0, note: '趋势逼近阈值', color: '#ffe040', icon: '↑' },
  { key: 'normal', level: null, label: '正常', count: summary.value.normal || 0, note: '暂无异常趋势', color: '#52c41a', icon: 'OK' }
])
const deptOptions = computed(() => {
  return [...new Set(list.value.map((employee) => employee.deptName).filter((value): value is string => Boolean(value)))].sort()
})
const filteredList = computed(() => {
  return list.value.filter((employee) => {
    if (filterLevel.value && employee.riskLevel !== filterLevel.value) return false
    if (filterDept.value && employee.deptName !== filterDept.value) return false
    if (filterName.value && !String(employee.empName || '').includes(filterName.value)) return false
    return true
  })
})
const pagedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

watch([filterLevel, filterDept, filterName], () => {
  currentPage.value = 1
})

async function fetchData() {
  loading.value = true
  try {
    const response = await fetchTrendPrediction()
    if (response.code === 200) {
      summary.value = response.data?.summary || {}
      list.value = response.data?.list || []
      const now = new Date()
      lastUpdate.value = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
    }
  } catch {
    ElMessage.error('获取趋势预警数据失败')
  } finally {
    loading.value = false
  }
}

function riskLabel(level: RiskLevel) {
  return { 3: '高风险', 2: '中风险', 1: '低风险' }[level] || '未知'
}

function projClass(metric: RiskMetric) {
  return metric.riskLevel >= 3 ? 'proj-danger' : metric.riskLevel >= 2 ? 'proj-warn' : 'proj-low'
}

function toggleFilter(level: RiskLevel | null) {
  filterLevel.value = filterLevel.value === level ? null : level
}

function goProfile(empCode: string) {
  void router.push({ path: '/health-monitor/employee-profile', query: { empCode } })
}

onMounted(() => void fetchData())
</script>

<style scoped lang="scss">
@import './trend-warning.scss';
</style>
