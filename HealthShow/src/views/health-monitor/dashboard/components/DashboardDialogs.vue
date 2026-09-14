<template>
  <el-drawer
    v-model="empDrawer.visible"
    title=""
    direction="rtl"
    size="520px"
    :append-to-body="true"
    :destroy-on-close="true"
    class="dm-emp-drawer"
    style="--el-bg-color:#080c20;--el-drawer-bg-color:#080c20"
    @closed="closeEmpDrawer"
  >
    <template #header>
      <div class="dm-drawer-hd">
        <div class="dm-drawer-avatar">{{ (empDrawer.data.empName || empDrawer.userName || '?').charAt(0) }}</div>
        <div>
          <div class="dm-drawer-name">{{ empDrawer.data.empName || empDrawer.userName || '--' }}</div>
          <div class="dm-drawer-meta">
            {{ empDrawer.data.deptName || '--' }} · {{ empDrawer.data.jobTypeName || '--' }}
          </div>
        </div>
        <div class="dm-drawer-warn-count">
          <span class="dm-dwc-num">{{ empDrawer.abnormalCount }}</span>
          <span class="dm-dwc-label">次异常</span>
        </div>
      </div>
    </template>

    <div v-loading="empDrawer.loading" class="dm-drawer-body">
      <div class="dm-drawer-vitals">
        <div class="dm-dv-card" v-for="v in empDrawer.vitals" :key="v.label">
          <div class="dm-dv-val" :style="{ color: v.color }">
            {{ v.val }}<span class="dm-dv-unit">{{ v.unit }}</span>
          </div>
          <div class="dm-dv-label">{{ v.label }}</div>
          <div class="dm-dv-status" :class="v.statusCls">{{ v.statusText }}</div>
        </div>
      </div>

      <div class="dm-drawer-section">
        <div class="dm-ds-title">{{ trendBlockTitle }}</div>
        <div id="empTrendChart" style="width:100%;height:200px;"></div>
      </div>

      <div class="dm-drawer-section">
        <div class="dm-ds-title">健康评分</div>
        <div id="empRadarChart" style="width:100%;height:180px;"></div>
      </div>

      <div class="dm-drawer-section">
        <div class="dm-ds-title">
          近期预警记录
          <span class="dm-ds-badge">{{ empDrawer.warnings.length }} 条</span>
        </div>
        <div class="dm-warn-table">
          <div class="dm-wt-row dm-wt-head">
            <span style="width:120px">时间</span>
            <span style="width:90px">类型</span>
            <span style="width:60px">数值</span>
            <span style="flex:1">描述</span>
            <span style="width:50px">级别</span>
          </div>
          <div class="dm-wt-row" v-for="(w, i) in empDrawer.warnings.slice(0, 15)" :key="w.id || w.createTime || i">
            <span style="width:120px;font-size:11px">{{ formatWarnTime(w.createTime) }}</span>
            <span style="width:90px">{{ alertTypeLabel(w.warningType) }}</span>
            <span style="width:60px;font-family:Consolas;color:#FFB84D">{{ w.warningValue }}</span>
            <span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#8ba6c8">
              {{ w.warningMessage || '--' }}
            </span>
            <span style="width:50px">
              <span :class="['dm-wt-level', dashboardWarningLevelDrawerClass(normalizeWarningLevel(w.warningLevel))]">
                {{ dashboardWarningLevelLabel(normalizeWarningLevel(w.warningLevel)) }}
              </span>
            </span>
          </div>
          <div v-if="!empDrawer.warnings.length" class="dm-empty" style="padding:20px">暂无预警记录</div>
        </div>
      </div>
    </div>
  </el-drawer>

  <el-dialog
    v-model="handleDialog.visible"
    title="处理预警"
    width="400px"
    :append-to-body="true"
    class="dm-handle-dialog"
  >
    <div class="dm-hd-info">
      <div class="dm-hd-row"><span class="dm-hd-key">人员</span><span class="dm-hd-val">{{ handleDialog.event?.userName }}</span></div>
      <div class="dm-hd-row"><span class="dm-hd-key">类型</span><span class="dm-hd-val">{{ handleDialog.event?.type }}</span></div>
      <div class="dm-hd-row"><span class="dm-hd-key">数值</span><span class="dm-hd-val" style="color:#FFB84D">{{ handleDialog.event?.value }}</span></div>
    </div>
    <el-input
      v-model="handleDialog.remark"
      type="textarea"
      :rows="3"
      placeholder="请输入处理备注（可选）"
      style="margin-top:12px"
    />
    <template #footer>
      <el-button @click="handleDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="handleDialog.submitting" @click="submitHandle">确认处理</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="deptPersonModal.visible"
    width="960px"
    :append-to-body="true"
    :destroy-on-close="false"
    :show-close="false"
    class="dm-dept-person-dialog"
    style="background:#0a1628;border:1px solid rgba(0,212,255,0.18);border-radius:10px"
    :header-style="{ display:'none' }"
    :body-style="{ padding:0, background:'#0a1628' }"
    @closed="closeDeptPersonModal"
    @opened="handleDeptPersonOpened"
  >
    <div class="dm-dp-header">
      <span class="dm-dp-header-title">部门检测人次</span>
      <button class="dm-dp-header-close" @click="deptPersonModal.visible = false">×</button>
    </div>
    <div class="dm-dp-toolbar">
      <el-date-picker
        v-model="deptPersonModal.dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width:300px;flex-shrink:0"
        @change="handleDeptPersonOpened"
      />
    </div>
    <div v-loading="deptPersonModal.loading" ref="deptPersonChartRef" style="width:100%;height:480px"></div>
  </el-dialog>

  <el-dialog
    v-model="deptDetailModal.visible"
    width="860px"
    :append-to-body="true"
    :destroy-on-close="false"
    :show-close="false"
    class="dm-dept-person-dialog"
    style="background:#0a1628;border:1px solid rgba(0,212,255,0.18);border-radius:10px"
    :header-style="{ display:'none' }"
    :body-style="{ padding:0, background:'#0a1628' }"
    @closed="closeDeptDetailModal"
    @opened="handleDeptDetailOpened"
  >
    <div class="dm-dp-header">
      <span class="dm-dp-header-title">{{ deptDetailModal.deptName }} — 检测 / 异常趋势</span>
      <button class="dm-dp-header-close" @click="deptDetailModal.visible = false">×</button>
    </div>
    <div class="dm-dp-toolbar">
      <el-date-picker
        v-model="deptDetailModal.dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width:300px;flex-shrink:0"
        @change="handleDeptDetailOpened"
      />
    </div>
    <div v-loading="deptDetailModal.loading" ref="deptDetailChartRef" style="width:100%;height:420px"></div>
  </el-dialog>

  <el-dialog
    v-model="metricDetailModal.visible"
    width="860px"
    :append-to-body="true"
    :destroy-on-close="false"
    :show-close="false"
    class="dm-dept-person-dialog"
    style="background:#0a1628;border:1px solid rgba(0,212,255,0.18);border-radius:10px"
    :header-style="{ display:'none' }"
    :body-style="{ padding:0, background:'#0a1628' }"
    @closed="closeMetricDetailModal"
    @opened="handleMetricDetailOpened"
  >
    <div class="dm-dp-header">
      <span class="dm-dp-header-title">{{ metricDetailModal.metricLabel }} — 检测 / 异常趋势</span>
      <button class="dm-dp-header-close" @click="metricDetailModal.visible = false">×</button>
    </div>
    <div class="dm-dp-toolbar">
      <el-date-picker
        v-model="metricDetailModal.dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width:300px;flex-shrink:0"
        @change="handleMetricDetailOpened"
      />
    </div>
    <div v-loading="metricDetailModal.loading" ref="metricDetailChartRef" style="width:100%;height:420px"></div>
  </el-dialog>

  <el-dialog
    v-model="warnCurveModal.visible"
    :title="warnCurveModal.title"
    width="780px"
    :append-to-body="true"
    :destroy-on-close="true"
    class="dm-warn-curve-dialog"
    @opened="handleWarnCurveOpened"
    @closed="closeWarnCurveModal"
  >
    <div class="dm-wc-info" v-if="warnCurveModal.event">
      <el-tag :type="dashboardWarningLevelTagType(warnCurveModal.event.level)" size="small" effect="dark">{{ dashboardWarningLevelLabel(warnCurveModal.event.level) }}</el-tag>
      <span class="dm-wc-type">{{ warnCurveModal.event.type }}</span>
      <span class="dm-wc-sep">·</span>
      <span class="dm-wc-val" style="color:#FFB84D">{{ warnCurveModal.event.indicator }}: {{ warnCurveModal.event.value }}</span>
      <span class="dm-wc-sep">·</span>
      <span class="dm-wc-time">{{ warnCurveModal.event.time }}</span>
    </div>
    <div v-loading="warnCurveModal.loading" ref="warnCurveChartRef" style="width:100%;height:360px;margin-top:12px"></div>
    <div v-if="!warnCurveModal.loading && !warnCurveModal.hasData" style="text-align:center;color:#4a6080;padding:60px 0;font-size:13px">该时段暂无健康记录数据</div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, type PropType } from 'vue'
import {
  dashboardWarningLevelDrawerClass,
  dashboardWarningLevelLabel,
  dashboardWarningLevelTagType
} from '../dashboard-warning-level'
import { normalizeWarningLevel } from '../../../alert-management/common/warning-lifecycle'

interface DisposableChart {
  dispose: () => void
}

type DateRange = [string, string] | null

interface EmployeeProfile {
  empName?: string
  deptName?: string
  jobTypeName?: string
}

interface EmployeeVital {
  label: string
  val?: string | number
  unit?: string
  color?: string
  statusCls?: string
  statusText?: string
}

interface WarningRecord {
  id?: string | number
  createTime?: string
  warningType?: string | number
  warningValue?: string | number
  warningMessage?: string
  warningLevel?: unknown
}

interface DashboardWarningEvent {
  userName?: string
  type?: string
  value?: string | number
  indicator?: string
  time?: string
  level?: string
}

interface EmployeeDrawerState {
  visible: boolean
  loading: boolean
  userName: string
  abnormalCount: number
  data: EmployeeProfile
  vitals: EmployeeVital[]
  warnings: WarningRecord[]
  trendChart: DisposableChart | null
  radarChart: DisposableChart | null
}

interface HandleDialogState {
  visible: boolean
  event: DashboardWarningEvent | null
  remark: string
  submitting: boolean
}

interface ChartModalState {
  visible: boolean
  loading: boolean
  dateRange: DateRange
  chart: DisposableChart | null
}

interface DeptDetailModalState extends ChartModalState {
  deptName: string
}

interface MetricDetailModalState extends ChartModalState {
  metricType: string
  metricLabel: string
  metricColor: string
}

interface WarnCurveModalState {
  visible: boolean
  loading: boolean
  hasData: boolean
  title: string
  event: DashboardWarningEvent | null
  records: unknown[]
  chart: DisposableChart | null
}

type ChartLoader = (element: HTMLElement | null) => void | Promise<void>

const props = defineProps({
  alertTypeLabel: { type: Function as PropType<(type: string | number | undefined) => string>, required: true },
  deptDetailModal: { type: Object as PropType<DeptDetailModalState>, required: true },
  deptPersonModal: { type: Object as PropType<ChartModalState>, required: true },
  formatWarnTime: { type: Function as PropType<(time?: string) => string>, required: true },
  handleDialog: { type: Object as PropType<HandleDialogState>, required: true },
  initWarnCurveChart: { type: Function as PropType<ChartLoader>, required: true },
  loadDeptDetailChart: { type: Function as PropType<ChartLoader>, required: true },
  loadDeptPersonChart: { type: Function as PropType<ChartLoader>, required: true },
  loadMetricDetailChart: { type: Function as PropType<ChartLoader>, required: true },
  metricDetailModal: { type: Object as PropType<MetricDetailModalState>, required: true },
  submitHandle: { type: Function as PropType<() => void | Promise<void>>, required: true },
  trendBlockTitle: { type: String, required: true },
  warnCurveModal: { type: Object as PropType<WarnCurveModalState>, required: true },
  empDrawer: { type: Object as PropType<EmployeeDrawerState>, required: true }
})

const deptPersonChartRef = ref<HTMLElement | null>(null)
const deptDetailChartRef = ref<HTMLElement | null>(null)
const metricDetailChartRef = ref<HTMLElement | null>(null)
const warnCurveChartRef = ref<HTMLElement | null>(null)

function disposeChart(chart: DisposableChart | null | undefined) {
  if (chart) chart.dispose()
}

function closeEmpDrawer() {
  disposeChart(props.empDrawer.trendChart)
  disposeChart(props.empDrawer.radarChart)
  props.empDrawer.trendChart = null
  props.empDrawer.radarChart = null
}

function closeDeptPersonModal() {
  disposeChart(props.deptPersonModal.chart)
  props.deptPersonModal.chart = null
}

function closeDeptDetailModal() {
  disposeChart(props.deptDetailModal.chart)
  props.deptDetailModal.chart = null
}

function closeMetricDetailModal() {
  disposeChart(props.metricDetailModal.chart)
  props.metricDetailModal.chart = null
}

function closeWarnCurveModal() {
  disposeChart(props.warnCurveModal.chart)
  props.warnCurveModal.chart = null
}

function handleDeptPersonOpened() {
  props.loadDeptPersonChart(deptPersonChartRef.value)
}

function handleDeptDetailOpened() {
  props.loadDeptDetailChart(deptDetailChartRef.value)
}

function handleMetricDetailOpened() {
  props.loadMetricDetailChart(metricDetailChartRef.value)
}

function handleWarnCurveOpened() {
  props.initWarnCurveChart(warnCurveChartRef.value)
}
</script>

<style lang="scss">
.dm-handle-dialog.el-dialog,
.dm-warn-curve-dialog.el-dialog {
  --el-bg-color: #080c20;
  --el-bg-color-overlay: #0a1628;
  --el-fill-color-blank: rgba(8, 20, 38, .96);
  --el-fill-color-light: rgba(0, 212, 255, .08);
  --el-border-color: rgba(0, 212, 255, .24);
  --el-border-color-light: rgba(0, 212, 255, .16);
  --el-text-color-primary: #e8f4ff;
  --el-text-color-regular: #a8c4dc;
  --el-text-color-secondary: #7696b2;
  background: #080c20;
  border: 1px solid rgba(0, 212, 255, .24);
  border-radius: 8px;
  box-shadow: 0 18px 54px rgba(0, 0, 0, .58), 0 0 28px rgba(0, 180, 255, .08);
  overflow: hidden;
}

.dm-handle-dialog .el-dialog__header,
.dm-warn-curve-dialog .el-dialog__header {
  margin: 0;
  padding: 18px 20px 14px;
  background: #0a1628;
  border-bottom: 1px solid rgba(0, 212, 255, .14);
}

.dm-handle-dialog .el-dialog__title,
.dm-warn-curve-dialog .el-dialog__title {
  color: #e8f4ff;
  font-weight: 700;
}

.dm-handle-dialog .el-dialog__headerbtn .el-dialog__close,
.dm-warn-curve-dialog .el-dialog__headerbtn .el-dialog__close {
  color: #82a8c8;
}

.dm-handle-dialog .el-dialog__headerbtn:hover .el-dialog__close,
.dm-warn-curve-dialog .el-dialog__headerbtn:hover .el-dialog__close {
  color: #00d4ff;
}

.dm-handle-dialog .el-dialog__body,
.dm-warn-curve-dialog .el-dialog__body {
  padding: 18px 20px 20px;
  color: #a8c4dc;
  background: #080c20;
}

.dm-handle-dialog .el-dialog__footer {
  padding: 14px 20px 18px;
  background: #080c20;
  border-top: 1px solid rgba(0, 212, 255, .1);
}

.dm-handle-dialog .el-textarea__inner {
  color: #d8e8f5;
  background: rgba(0, 30, 56, .72);
  box-shadow: 0 0 0 1px rgba(0, 212, 255, .22) inset;
}

.dm-handle-dialog .el-textarea__inner::placeholder {
  color: #587690;
}

@media (max-width: 820px) {
  .dm-warn-curve-dialog.el-dialog {
    width: calc(100% - 24px);
  }

  .dm-handle-dialog.el-dialog {
    width: min(400px, calc(100% - 24px));
  }
}
</style>
