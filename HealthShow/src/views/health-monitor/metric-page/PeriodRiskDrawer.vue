<template>
  <el-drawer v-model="drawerVisible" :title="title" size="760px" append-to-body class="period-risk-drawer" modal-class="metric-risk-drawer-overlay">
    <div class="prd-body" v-loading="loading">
      <div v-if="kind === 'daily'" class="prd-table">
        <div class="prd-row prd-head"><span>日期</span><span>覆盖人数</span><span>异常人数</span><span>异常记录</span><span>采样记录</span></div>
        <div v-for="row in dailyRows" :key="row.date" class="prd-row">
          <span>{{ row.date }}</span><span>{{ row.coveredUsers }} 人</span><span class="warn">{{ row.anomalyCount }} 人</span>
          <span class="danger">{{ row.abnormalRecords }} 条</span><span>{{ row.totalRecords }} 条</span>
        </div>
      </div>
      <template v-else>
        <div v-if="summary" class="prd-summary">
          <div><span>区间人数</span><strong :style="{ color: summary.color || '#00d4ff' }">{{ summary.count }}</strong><em>人</em></div>
          <div><span>覆盖占比</span><strong>{{ summary.pct }}%</strong></div>
          <div><span>判定区间</span><strong>{{ summary.range }}</strong></div>
          <p>{{ summary.basis }}</p>
        </div>
        <div v-if="!loading && !rows.length" class="prd-empty">当前周期暂无{{ summary?.label || '' }}人员数据</div>
        <div v-else class="prd-table">
          <div class="prd-row prd-head"><span>人员</span><span>部门</span><span>采样/异常</span><span>{{ valueTitle }}</span><span>{{ summary ? '最近采集' : '最近异常' }}</span></div>
          <button v-for="row in rows" :key="row.userCode" type="button" class="prd-row prd-user" @click="$emit('open-person', row)">
            <span><strong>{{ row.userName }}</strong><small>{{ row.userCode }}</small></span>
            <span>{{ row.deptName || '--' }}</span>
            <span>{{ row.sampleCount }} / <em>{{ row.abnormalCount }}</em><small>{{ row.anomalyDays }} 个异常日</small></span>
            <span>{{ formatRange(row) }}</span>
            <span>{{ (summary ? row.lastSampleTime : row.lastRecordTime) || row.lastSampleTime || '--' }}</span>
          </button>
        </div>
        <el-pagination v-if="total > size" class="prd-pagination" background layout="prev, pager, next"
          :current-page="page" :page-size="size" :total="total" @current-change="$emit('page-change', $event)" />
      </template>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, type PropType } from 'vue'

interface PeriodRiskUserRow {
  userCode?: string
  userName?: string
  deptName?: string
  sampleCount?: number
  abnormalCount?: number
  anomalyDays?: number
  primaryMin?: number | null
  primaryMax?: number | null
  secondaryMin?: number | null
  secondaryMax?: number | null
  lastSampleTime?: string
  lastRecordTime?: string
  [key: string]: unknown
}

interface PeriodRiskDailyRow {
  date?: string
  coveredUsers?: number
  anomalyCount?: number
  abnormalRecords?: number
  totalRecords?: number
}

interface PeriodRiskSummary {
  color?: string
  count?: number
  pct?: number
  range?: string
  basis?: string
  label?: string
}

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: '周期风险证据' },
  kind: { type: String as PropType<'users' | 'daily'>, default: 'users' },
  loading: Boolean,
  rows: { type: Array as PropType<PeriodRiskUserRow[]>, default: () => [] },
  dailyRows: { type: Array as PropType<PeriodRiskDailyRow[]>, default: () => [] },
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 12 },
  unit: { type: String, default: '' },
  valueTitle: { type: String, default: '指标范围' },
  dual: Boolean,
  summary: { type: Object as PropType<PeriodRiskSummary | null>, default: null }
})
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'page-change': [page: number]
  'open-person': [row: PeriodRiskUserRow]
}>()
const drawerVisible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })

function formatRange(row: PeriodRiskUserRow) {
  const primary = row.primaryMin == null ? '--' : `${row.primaryMin}~${row.primaryMax}`
  if (!props.dual) return `${primary}${props.unit ? ` ${props.unit}` : ''}`
  const secondary = row.secondaryMin == null ? '--' : `${row.secondaryMin}~${row.secondaryMax}`
  return `${primary} / ${secondary}${props.unit ? ` ${props.unit}` : ''}`
}
</script>

<style>
:global(.metric-risk-drawer-overlay) { background: rgba(0, 6, 18, 0.78) !important; backdrop-filter: blur(5px); }
:global(.period-risk-drawer.el-drawer) { background: rgba(9, 20, 36, 0.98) !important; border-left: 1px solid rgba(0, 212, 255, 0.28); box-shadow: -16px 0 42px rgba(0, 0, 0, 0.52), 0 0 22px rgba(0, 212, 255, 0.08); }
:global(.period-risk-drawer .el-drawer__header) { margin-bottom: 0; padding: 16px 20px 13px; border-bottom: 1px solid rgba(0, 212, 255, 0.16); color: #e8f4ff; background: linear-gradient(180deg, rgba(0, 212, 255, 0.1), rgba(0, 212, 255, 0.025)); }
:global(.period-risk-drawer .el-drawer__title) { color: #e8f4ff; font-size: 15px; font-weight: 700; }
:global(.period-risk-drawer .el-drawer__close) { color: #7eb8d4; }
:global(.period-risk-drawer .el-drawer__close:hover) { color: #00d4ff; }
:global(.period-risk-drawer .el-drawer__body) { padding: 16px; background: rgba(7, 16, 30, 0.72); }
.prd-body{min-height:240px;color:#c8d8e8}.prd-table{display:flex;flex-direction:column;border:1px solid #232b4d}
.prd-summary{display:grid;grid-template-columns:1fr 1fr 1.4fr;gap:8px;margin-bottom:14px;padding:10px;border:1px solid rgba(0,212,255,.16);border-radius:7px;background:rgba(0,212,255,.045)}
.prd-summary div{min-width:0}.prd-summary span,.prd-summary strong,.prd-summary em{display:inline-block}.prd-summary span{color:#7eb8d4;font-size:11px;margin-right:6px}.prd-summary strong{font:700 18px/1.2 Consolas,monospace;color:#e8f4ff;word-break:break-word}.prd-summary em{color:#8ba6c8;font-size:11px;font-style:normal;margin-left:3px}.prd-summary p{grid-column:1/-1;margin:3px 0 0;color:#7f9cbd;font-size:11px}
.prd-row{display:grid;grid-template-columns:1.1fr 1fr 1fr 1.15fr 1.25fr;align-items:center;gap:10px;min-height:42px;padding:6px 12px;border-bottom:1px solid #232b4d;text-align:left}
.prd-head{background:rgba(0,212,255,.06);color:#7eb8d4;font-size:12px;font-weight:700}.prd-user{width:100%;border:0;border-bottom:1px solid #232b4d;background:rgba(13,31,53,.84);color:#c8d8e8;cursor:pointer}
.prd-user:hover{background:#222947}.prd-row strong{display:block;color:#fff}.prd-row small{display:block;margin-top:2px;color:#7eb8d4}.prd-row em,.warn{color:#ffb84d;font-style:normal}.danger{color:#ff7070}.prd-empty{padding:70px 20px;text-align:center;color:#7eb8d4}.prd-pagination{justify-content:center;padding:14px}
:global(.period-risk-drawer .prd-pagination .el-pager li),:global(.period-risk-drawer .prd-pagination button){background:rgba(0,212,255,.07) !important;color:#7eb8d4 !important;border:1px solid rgba(0,212,255,.14) !important;}
:global(.period-risk-drawer .prd-pagination .el-pager li.is-active){background:rgba(0,212,255,.28) !important;color:#e8f4ff !important;border-color:rgba(0,212,255,.55) !important;}
@media(max-width:760px){.prd-row{grid-template-columns:1.2fr 1fr 1fr}.prd-row span:nth-child(4),.prd-row span:nth-child(5){display:none}.prd-summary{grid-template-columns:1fr 1fr}.prd-summary div:last-of-type{grid-column:1/-1}}
</style>
