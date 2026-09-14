<template>
  <aside class="dm-right">
    <div v-if="showRankPanel" class="db-panel dm-right-rank">
      <div class="db-track">
        <span class="db-track-title">异常人员排行</span>
        <span class="db-track-sub">{{ periodLabel }}累计</span>
      </div>
      <div class="dm-top5-list">
        <div
          v-for="(item, i) in rankDisplayData"
          :key="item.userCode || item.userName || i"
          class="dm-top5-row dm-top5-row--interactive"
          @click="emit('open-employee', item)"
        >
          <span class="dm-top5-rank" :class="'rk-' + (i + 1)">{{ i + 1 }}</span>
          <span class="dm-top5-name">{{ item.userName || item.name }}</span>
          <div class="dm-top5-bar-wrap">
            <div class="dm-top5-bar" :style="{ width: (item.count / top5Max * 100) + '%' }"></div>
          </div>
          <span class="dm-top5-val">{{ item.count }}</span>
        </div>
        <PageEmptyState
          v-if="!rankDisplayData.length"
          compact
          title="暂无排行数据"
          description="当前没有可展示的异常人员排行。"
        />
      </div>
    </div>

    <div v-if="showActionPanel" class="db-panel dm-right-action">
      <div class="db-track">
        <span class="db-track-title">值班提示</span>
        <span class="db-track-sub">优先处理最紧急闭环</span>
      </div>
      <div class="dm-action-cues">
        <button
          v-for="item in actionCueItems"
          :key="item.label"
          type="button"
          :class="['dm-action-cue', `tone-${item.tone}`]"
          @click="navigateTo(item.path)"
        >
          <div class="dm-action-cue-head">
            <span class="dm-action-cue-label">{{ item.label }}</span>
            <span class="dm-action-cue-value">{{ item.value }}</span>
          </div>
          <div class="dm-action-cue-detail">{{ item.detail }}</div>
        </button>
      </div>
    </div>

    <div v-if="showWarnRatePanel" class="db-panel dm-right-warnrate">
      <div class="db-track">
        <span class="db-track-title">指标预警率分析</span>
        <span class="db-track-sub">{{ periodLabel }}触发预警人员占比</span>
      </div>
      <div class="dm-warn-stats">
        <PageEmptyState
          v-if="!warningRateList.length"
          compact
          title="暂无预警率数据"
          description="当前时段还没有形成可展示的指标预警率。"
        />
        <div
          v-for="item in warningRateList"
          :key="item.name"
          class="dm-warn-item dm-warn-item--interactive"
          @click="goToWarningRecords(item)"
        >
          <div class="dm-warn-icon-wrap">
            <el-icon :size="15"><component :is="getWarningIcon(item.name)" /></el-icon>
          </div>
          <div class="dm-warn-body">
            <div class="dm-warn-top">
              <span class="dm-warn-name">{{ item.name }}</span>
              <span :class="['dm-warn-pct', `tone-${getWarnTone(item.rate)}`]">{{ item.rate }}%</span>
            </div>
            <div class="dm-warn-bar-bg">
              <div class="dm-warn-bar-fill" :style="{ width: `${item.rate}%`, background: getWarnGradient(item.rate) }"></div>
            </div>
          </div>
          <div class="dm-warn-tag" :class="getWarnTagClass(item.rate)">{{ getWarnTagLabel(item.rate) }}</div>
        </div>
      </div>
    </div>

    <div v-if="showPreShiftPanel" class="db-panel dm-right-preshift db-panel--interactive" @click="goMineEntry">
      <div class="db-track">
        <span class="db-track-title">班前健康准入</span>
        <span class="db-track-sub">{{ preShiftStatusText }}</span>
      </div>
      <div class="dm-preshift-body">
        <div class="dm-ps-ring-wrap">
          <svg viewBox="0 0 80 80" class="dm-ps-ring">
            <circle cx="40" cy="40" r="32" fill="none" stroke="#1a2a4d" stroke-width="8"/>
            <circle
              cx="40"
              cy="40"
              r="32"
              fill="none"
              :stroke="preShiftStrokeColor"
              stroke-width="8"
              stroke-linecap="round"
              :stroke-dasharray="`${(preShiftData.preShiftRate || 0) * 2.01} 201`"
              stroke-dashoffset="50"
            />
          </svg>
          <div class="dm-ps-ring-inner">
            <div :class="['dm-ps-rate', `tone-${preShiftTone}`]">
              {{ preShiftData.preShiftRate !== null ? preShiftData.preShiftRate + '%' : '--' }}
            </div>
            <div class="dm-ps-rate-label">达标率</div>
          </div>
        </div>
        <div class="dm-ps-stats">
          <div class="dm-ps-stat">
            <span class="dm-ps-stat-val">{{ preShiftData.totalToday }}</span>
            <span class="dm-ps-stat-label">今日检测</span>
          </div>
          <div class="dm-ps-stat dm-ps-ok">
            <span class="dm-ps-stat-val">{{ preShiftData.qualifiedCount }}</span>
            <span class="dm-ps-stat-label">准入通过</span>
          </div>
          <div class="dm-ps-stat dm-ps-fail">
            <span class="dm-ps-stat-val">{{ preShiftData.failedCount }}</span>
            <span class="dm-ps-stat-label">禁止入井</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showAiPanel" class="db-panel dm-right-ai">
      <div class="db-track">
        <span class="db-track-title">AI 全矿健康分析</span>
        <button class="dm-ai-btn" :disabled="mineAiLoading" @click="emit('toggle-ai', false)">
          {{ mineAiLoading ? '分析中…' : (mineAiReport ? '刷新' : '生成分析') }}
        </button>
      </div>
      <div v-if="mineAiLoading" class="dm-ai-loading">DeepSeek 分析中，请稍候…</div>
      <div v-else-if="mineAiReport" class="dm-ai-preview" @click="emit('show-ai')">
        {{ mineAiReport.replace(/#+\s*/g, '').slice(0, 120) }}…
        <span class="dm-ai-more">展开全文 ›</span>
      </div>
      <div v-else class="dm-ai-empty">
        <div class="dm-ai-empty-hint">点击"生成分析"，AI 将综合以下维度生成健康摘要</div>
        <div class="dm-ai-dims">
          <span class="dm-ai-dim"><i class="dm-ai-dim-dot" style="background:#38ef7d"></i>体征趋势</span>
          <span class="dm-ai-dim"><i class="dm-ai-dim-dot" style="background:#ff5252"></i>预警分布</span>
          <span class="dm-ai-dim"><i class="dm-ai-dim-dot" style="background:#ffd200"></i>异常人员</span>
          <span class="dm-ai-dim"><i class="dm-ai-dim-dot" style="background:#00c8ff"></i>班前准入</span>
          <span class="dm-ai-dim"><i class="dm-ai-dim-dot" style="background:#b388ff"></i>设备状态</span>
          <span class="dm-ai-dim"><i class="dm-ai-dim-dot" style="background:#ff8c00"></i>健康建议</span>
        </div>
        <div class="dm-ai-empty-footer">基于 DeepSeek 大模型 · 分析耗时约 10 秒</div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, type PropType } from 'vue'
import { useRouter } from 'vue-router'
import PageEmptyState from '@/components/health-shell/PageEmptyState.vue'

defineOptions({ name: 'DashboardRightSidebar' })

type SidebarMode = 'full' | 'primary' | 'secondary' | 'rank'
type WarningTone = 'danger' | 'warning' | 'success'

interface RankedEmployee {
  userCode?: string
  userName?: string
  name?: string
  count: number
  [key: string]: unknown
}

interface WarningRateItem {
  name: string
  rate: number
}

interface PreShiftData {
  totalToday: number
  qualifiedCount: number
  failedCount: number
  preShiftRate: number | null
}

interface DangerEvent {
  userName?: string
  type?: string
  [key: string]: unknown
}

interface ActionCueItem {
  label: string
  value: string
  detail: string
  path: string
  tone: string
}

const props = defineProps({
  mode: { type: String as PropType<SidebarMode>, default: 'full' },
  periodLabel: { type: String, required: true },
  top5DisplayData: { type: Array as PropType<RankedEmployee[]>, default: () => [] },
  top5Max: { type: Number, default: 1 },
  warningRateList: { type: Array as PropType<WarningRateItem[]>, default: () => [] },
  preShiftData: {
    type: Object as PropType<PreShiftData>,
    default: () => ({ totalToday: 0, qualifiedCount: 0, failedCount: 0, preShiftRate: null })
  },
  mineAiReport: { type: String, default: '' },
  mineAiLoading: { type: Boolean, default: false },
  latestDangerEvent: { type: Object as PropType<DangerEvent | null>, default: null },
  kpiUnhandledHigh: { type: Number, default: 0 },
  focusWarningCount: { type: Number, default: 0 }
})

const emit = defineEmits<{
  'open-employee': [employee: RankedEmployee]
  'toggle-ai': [force: boolean]
  'show-ai': []
}>()

const router = useRouter()

const warningIconMap: Record<string, string> = {
  '压力预警率': 'MagicStick',
  '体温预警率': 'Sunny',
  '心率预警率': 'Monitor',
  '血氧预警率': 'FirstAidKit'
}
const warningTypeMap: Record<string, string> = {
  '压力预警率': '压力',
  '体温预警率': '体温',
  '心率预警率': '心率',
  '血氧预警率': '血氧'
}

const getWarningIcon = (name: string) => warningIconMap[name] || 'Warning'
const getWarnTone = (rate: number): WarningTone => (rate >= 20 ? 'danger' : rate >= 10 ? 'warning' : 'success')
const getWarnGradient = (rate: number) => {
  return rate >= 20
    ? 'linear-gradient(90deg,#ff5252,#ff1744)'
    : rate >= 10
      ? 'linear-gradient(90deg,#ffd200,#ff9800)'
      : 'linear-gradient(90deg,#00d4ff,#38ef7d)'
}
const getWarnTagClass = (rate: number) => (rate >= 20 ? 'tag-danger' : rate >= 10 ? 'tag-warn' : 'tag-ok')
const getWarnTagLabel = (rate: number) => (rate >= 20 ? '偏高' : rate >= 10 ? '注意' : '正常')
const goToWarningRecords = (item: WarningRateItem) => {
  const type = warningTypeMap[item.name]
  void router.push({ path: '/alert-management/records', query: type ? { warningType: type } : {} })
}
const goMineEntry = () => void router.push('/health-monitor/mine-entry')
const navigateTo = (path: string) => {
  if (path) void router.push(path)
}

const preShiftTone = computed(() => {
  const rate = Number(props.preShiftData?.preShiftRate)
  if (!Number.isFinite(rate)) return 'primary'
  if (rate >= 90) return 'success'
  if (rate >= 70) return 'warning'
  return 'danger'
})
const preShiftStrokeColor = computed(() => {
  return preShiftTone.value === 'success'
    ? '#38ef7d'
    : preShiftTone.value === 'warning'
      ? '#ffd200'
      : '#ff5252'
})
const preShiftStatusText = computed(() => {
  const failed = props.preShiftData?.failedCount || 0
  return failed > 0 ? `${failed} 人需复核` : '当前准入平稳'
})
const rankDisplayData = computed(() => props.top5DisplayData.slice(0, 5))
const actionCueItems = computed<ActionCueItem[]>(() => {
  const items: ActionCueItem[] = []
  if (props.kpiUnhandledHigh > 0) {
    items.push({
      label: '高危闭环',
      value: `${props.kpiUnhandledHigh} 条`,
      detail: '优先进入待处理列表，确认现场处置结果。',
      path: '/alert-management/notifications',
      tone: 'danger'
    })
  }
  if ((props.preShiftData?.failedCount || 0) > 0) {
    items.push({
      label: '准入复核',
      value: `${props.preShiftData.failedCount} 人`,
      detail: '班前未通过人员需要复检或人工确认。',
      path: '/health-monitor/mine-entry',
      tone: 'warning'
    })
  }
  if (props.latestDangerEvent?.userName) {
    items.push({
      label: '重点跟进',
      value: props.latestDangerEvent.userName,
      detail: `${props.latestDangerEvent.type || '危险预警'}待闭环，优先查看人员画像。`,
      path: '/health-monitor/employee-archive',
      tone: 'primary'
    })
  }
  if (!items.length) {
    items.push({
      label: '值班状态',
      value: props.focusWarningCount > 0 ? `${props.focusWarningCount} 人` : '平稳',
      detail: props.focusWarningCount > 0
        ? '当前无高危闭环，但仍有重点人员需要持续观察。'
        : '当前无紧急闭环任务，建议持续关注趋势和设备在线情况。',
      path: '/health-monitor/report-center',
      tone: props.focusWarningCount > 0 ? 'primary' : 'success'
    })
  }
  return items.slice(0, 3)
})

const showRankPanel = computed(() => props.mode === 'full' || props.mode === 'primary' || props.mode === 'rank')
const showActionPanel = computed(() => props.mode === 'full' || props.mode === 'primary')
const showWarnRatePanel = computed(() => props.mode === 'full' || props.mode === 'secondary')
const showPreShiftPanel = computed(() => props.mode === 'full' || props.mode === 'secondary')
const showAiPanel = computed(() => props.mode === 'full' || props.mode === 'secondary')
</script>

<style scoped lang="scss">
@import '../dashboard.scss';
</style>
