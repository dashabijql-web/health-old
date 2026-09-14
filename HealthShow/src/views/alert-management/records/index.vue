<template>
  <div class="page-container">
    <WarningCenterNav />

    <header class="rc-hd">
      <div class="rc-hd-left">
        <span class="rc-live-dot"></span>
        <h1 class="rc-hd-title">处置记录</h1>
      </div>
      <div class="rc-hd-kpis">
        <div class="rc-kpi">
          <span class="rc-kpi-n">{{ overview.todayTotal || 0 }}</span>
          <span class="rc-kpi-l">今日预警</span>
        </div>
        <div class="rc-kpi warn">
          <span class="rc-kpi-n">{{ overview.pending || 0 }}</span>
          <span class="rc-kpi-l">待处理</span>
        </div>
        <div class="rc-kpi danger">
          <span class="rc-kpi-n">{{ overview.critical || 0 }}</span>
          <span class="rc-kpi-l">危急</span>
        </div>
        <div class="rc-kpi success">
          <span class="rc-kpi-n">{{ handleRate }}<small>%</small></span>
          <span class="rc-kpi-l">处理率</span>
        </div>
      </div>
      <div class="rc-hd-time">{{ currentTime }}</div>
    </header>

    <!-- Stat Cards -->
    <el-row :gutter="12" class="mb-16">
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-card-clickable" :class="{ 'card-active': activeCard === 'all' }" @click="filterByCard('all')">
          <div class="stat-icon-wrap primary"><el-icon size="26"><Bell /></el-icon></div>
          <div class="stat-body"><div class="stat-value">{{ overview.todayTotal || 0 }}</div><div class="stat-label">今日预警</div></div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-card-clickable" :class="{ 'card-active': activeCard === 'unhandled' }" @click="filterByCard('unhandled')">
          <div class="stat-icon-wrap danger"><el-icon size="26"><WarningFilled /></el-icon></div>
          <div class="stat-body"><div class="stat-value">{{ overview.pending || 0 }}</div><div class="stat-label">待处理</div></div>
          <div class="stat-badge" v-if="overview.pending > 0">urgent</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-card-clickable" :class="{ 'card-active': activeCard === 'critical' }" @click="filterByCard('critical')">
          <div class="stat-icon-wrap warning"><el-icon size="26"><WarnTriangleFilled /></el-icon></div>
          <div class="stat-body"><div class="stat-value">{{ overview.critical || 0 }}</div><div class="stat-label">危急预警</div></div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card stat-card-clickable" :class="{ 'card-active': activeCard === 'handled' }" @click="filterByCard('handled')">
          <div class="stat-icon-wrap success"><el-icon size="26"><CircleCheck /></el-icon></div>
          <div class="stat-body"><div class="stat-value">{{ handleRate }}<span class="unit">%</span></div><div class="stat-label">处理率</div></div>
        </div>
      </el-col>
    </el-row>

    <!-- Search Panel -->
    <div class="panel mb-16">
      <!-- 手机端折叠按钮 -->
      <div v-if="isMobile" class="mob-filter-toggle" @click="filterExpanded = !filterExpanded">
        <el-icon><Search /></el-icon> 筛选条件
        <span class="mob-filter-arrow">{{ filterExpanded ? '▲' : '▼' }}</span>
      </div>
      <el-form v-if="!isMobile || filterExpanded" :inline="!isMobile" :model="searchForm" class="search-form">
        <el-form-item><el-date-picker v-model="searchForm.dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" :style="isMobile?'width:100%':'width:260px'" /></el-form-item>
        <el-form-item>
          <el-select v-model="searchForm.warningType" placeholder="预警类型" clearable :style="isMobile?'width:100%':'width:140px'">
            <el-option label="心率异常" value="心率" />
            <el-option label="血氧异常" value="血氧" />
            <el-option label="体温异常" value="体温" />
            <el-option label="血压偏高" value="血压" />
            <el-option label="压力偏高" value="压力" />
            <el-option label="SOS求助"  value="SOS" />
            <el-option label="跌倒"    value="跌倒" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="searchForm.warningLevel" placeholder="预警级别" clearable :style="isMobile?'width:100%':'width:120px'">
            <el-option label="高危" value="高危" /><el-option label="中危" value="中危" /><el-option label="低危" value="低危" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="searchForm.handleStatus" placeholder="处理状态" clearable :style="isMobile?'width:100%':'width:120px'">
            <el-option label="全部" value="" /><el-option :label="warningHandledStatusLabel({ handled: true })" value="handled" /><el-option :label="warningHandledStatusLabel({ handled: false })" value="unhandled" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input v-model="searchForm.keyword" placeholder="搜索姓名/设备号" clearable :style="isMobile?'width:100%':'width:200px'" @keyup.enter="handleSearch"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        </el-form-item>
        <el-form-item :style="isMobile?'width:100%':''">
          <el-button type="primary" :icon="Search" @click="handleSearch" :style="isMobile?'width:50%':''">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset" :style="isMobile?'width:45%':''">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Data Table / Card List -->
    <div class="panel table-panel">
      <div class="panel-header">
        <div class="panel-title"><span class="title-bar"></span>事件处置明细</div>
        <div class="panel-header-right">
          <el-button v-if="selectedRows.length > 0" type="warning" size="small" :loading="batchLoading" @click="batchHandle">批量处理 ({{ selectedRows.length }})</el-button>
          <el-button v-if="!isMobile" type="success" size="small" :icon="Download" @click="exportExcel">导出Excel</el-button>
          <span class="total-badge">共 {{ pagination.total }} 条</span>
        </div>
      </div>

      <!-- 手机端：卡片列表 -->
      <div v-if="isMobile" class="mob-card-list" v-loading="loading">
        <div v-for="row in tableData" :key="row.id"
          class="mob-warn-card"
          :class="row.warningLevel==='高危'?'card-danger':row.warningLevel==='中危'?'card-warning':'card-info'"
          @click="openDetail(row)">
          <div class="mob-card-top">
            <span class="mob-card-name">{{ row.userName || '--' }}</span>
            <el-tag :type="levelTag(row.warningLevel)" size="small" effect="dark">{{ levelLabel(row.warningLevel) }}</el-tag>
            <el-tag :type="row.handled?'success':'danger'" size="small" effect="dark">{{ warningHandledStatusLabel(row) }}</el-tag>
          </div>
          <div class="mob-card-mid">
            <el-tag :type="typeTag(row.warningType)" size="small" effect="plain">{{ typeLabel(row.warningType) }}</el-tag>
            <span class="mob-card-val">{{ row.warningValue || '' }}</span>
          </div>
          <div class="mob-card-bot">
            <span class="mob-card-time">{{ formatDate(row.createTime) }}</span>
            <span class="records-sla-clock mob-sla-clock" :class="`is-${row.slaStatus || 'unknown'}`">
              <span>{{ row.slaClockLabel || '未知' }}</span>
              <strong>{{ row.slaClockText || '--' }}</strong>
            </span>
            <el-button v-if="!row.handled" type="warning" size="small" @click.stop="openHandle(row)">处理</el-button>
          </div>
        </div>
        <div v-if="!loading && tableData.length === 0" class="mob-empty">暂无预警记录</div>
      </div>

      <!-- 桌面端：表格 -->
      <div v-else class="table-body">
        <el-table :data="tableData" v-loading="loading" stripe height="100%" style="width:100%"
          :header-cell-style="{ background:'#141830', color:'#7eb8d4', fontWeight:'600', fontSize:'13px' }"
          :row-style="{ background:'#1a1f3a', cursor:'pointer' }"
          @row-click="openDetail"
          @selection-change="rows => selectedRows = rows">
          <el-table-column type="selection" width="46" align="center" @click.stop />
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="createTime" label="预警时间" width="150">
            <template #default="{row}">{{ formatDate(row.createTime) }}</template>
          </el-table-column>
          <el-table-column prop="userName" label="姓名" width="88" />
          <el-table-column label="性别" width="56" align="center">
            <template #default="{row}">{{ row.gender === 1 ? '男' : row.gender === 2 ? '女' : '-' }}</template>
          </el-table-column>
          <el-table-column label="年龄" width="56" align="center">
            <template #default="{row}">{{ row.age != null ? row.age : '-' }}</template>
          </el-table-column>
          <el-table-column label="预警类型" width="104" align="center">
            <template #default="{row}"><el-tag :type="typeTag(row.warningType)" size="small" effect="dark">{{ typeLabel(row.warningType) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="事件来源" width="96" align="center">
            <template #default="{row}">{{ sourceLabel(row.eventSource) }}</template>
          </el-table-column>
          <el-table-column prop="warningValue" label="预警值" width="88" align="center" />
          <el-table-column label="预警级别" width="88" align="center">
            <template #default="{row}"><el-tag :type="levelTag(row.warningLevel)" size="small" effect="dark">{{ levelLabel(row.warningLevel) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="处理状态" width="88" align="center">
            <template #default="{row}"><el-tag :type="row.handled?'success':'danger'" size="small" effect="dark">{{ warningHandledStatusLabel(row) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="SLA" width="96" align="center">
            <template #default="{row}">
              <span class="records-sla-clock" :class="`is-${row.slaStatus || 'unknown'}`">
                <span>{{ row.slaClockLabel || '未知' }}</span>
                <strong>{{ row.slaClockText || '--' }}</strong>
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="handleBy" label="处理人" min-width="90">
            <template #default="{row}">{{ row.handleBy||'-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="104" fixed="right" align="center">
            <template #default="{row}">
              <el-button v-if="!row.handled" type="warning" link size="small" @click.stop="openHandle(row)"><el-icon><Edit /></el-icon> 处理</el-button>
              <span v-else class="handled-text">{{ warningHandledStatusLabel(row) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="pagination-wrap">
        <el-pagination background
          :layout="isMobile ? 'prev,pager,next' : 'total,sizes,prev,pager,next,jumper'"
          :current-page="pagination.page" :page-sizes="[10,20,50,100]" :page-size="pagination.size"
          :total="pagination.total"
          @size-change="s=>{pagination.size=s;loadData()}"
          @current-change="p=>{pagination.page=p;loadData()}" />
      </div>
    </div>

    <!-- Detail Drawer -->
    <el-drawer v-model="detailVisible" title="预警详情" :width="isMobile?'100%':'480px'" direction="rtl" :destroy-on-close="true">
      <div v-if="detailRow" class="detail-body">
        <div class="detail-section">
          <div class="detail-row">
            <span class="detail-label">预警时间</span>
            <span class="detail-value">{{ formatDate(detailRow.createTime) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">姓名</span>
            <span class="detail-value">{{ detailRow.userName || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">预警类型</span>
            <el-tag :type="typeTag(detailRow.warningType)" size="small" effect="dark">{{ typeLabel(detailRow.warningType) }}</el-tag>
          </div>
          <div class="detail-row">
            <span class="detail-label">预警级别</span>
            <el-tag :type="levelTag(detailRow.warningLevel)" size="small" effect="dark">{{ levelLabel(detailRow.warningLevel) }}</el-tag>
          </div>
          <div class="detail-row">
            <span class="detail-label">预警值</span>
            <span class="detail-value">{{ detailRow.warningValue || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">处理状态</span>
            <el-tag :type="detailRow.handled?'success':'danger'" size="small" effect="dark">{{ warningHandledStatusLabel(detailRow) }}</el-tag>
          </div>
          <div class="detail-row">
            <span class="detail-label">SLA计时</span>
            <span class="records-sla-clock detail-sla-clock" :class="`is-${detailRow.slaStatus || 'unknown'}`">
              <span>{{ detailRow.slaClockLabel || '未知' }}</span>
              <strong>{{ detailRow.slaClockText || '--' }}</strong>
            </span>
          </div>
        </div>

        <template v-if="detailRow.handled">
          <div class="detail-divider"></div>
          <div class="detail-section">
            <div class="detail-section-title">处理信息</div>
            <div class="detail-row">
              <span class="detail-label">处理人</span>
              <span class="detail-value">{{ detailRow.handleBy || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">处理时间</span>
              <span class="detail-value">{{ formatDate(detailRow.handleTime) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">处理备注</span>
              <span class="detail-value detail-note">{{ detailRow.handleNote || '-' }}</span>
            </div>
          </div>
        </template>

        <div class="detail-footer" v-if="!detailRow.handled">
          <el-button type="warning" style="width:100%" @click="() => { openHandle(detailRow); detailVisible = false }">
            <el-icon><Edit /></el-icon> 处理此预警
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- Handle Dialog -->
    <el-dialog v-model="handleDialogVisible" title="处理预警" :width="isMobile?'95%':'520px'" :close-on-click-modal="false">
      <div class="handle-summary">
        <div class="summary-row"><span class="summary-label">预警人员</span><span class="summary-value">{{ currentRow?.userName||'-' }}</span></div>
        <div class="summary-row"><span class="summary-label">预警类型</span><el-tag :type="typeTag(currentRow?.warningType)" size="small" effect="dark">{{ typeLabel(currentRow?.warningType) }}</el-tag></div>
        <div class="summary-row"><span class="summary-label">预警时间</span><span class="summary-value">{{ formatDate(currentRow?.createTime) }}</span></div>
      </div>
      <el-form ref="handleFormRef" :model="handleForm" :rules="handleRules" label-width="90px" class="form-body">
        <el-form-item label="处理方式" prop="handleType">
          <el-radio-group v-model="handleForm.handleType">
            <el-radio :value="1">立即响应</el-radio><el-radio :value="2">远程指导</el-radio><el-radio :value="3">误报处理</el-radio><el-radio :value="4">其他</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理备注" prop="handleNote"><el-input v-model="handleForm.handleNote" type="textarea" :rows="3" placeholder="请输入处理说明" /></el-form-item>
        <el-form-item label="通知主管"><el-switch v-model="handleForm.notify" active-color="#38ef7d" inactive-color="#4a5578" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="handleSubmitting" @click="submitHandle">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import WarningCenterNav from '@/components/WarningCenterNav.vue'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, type LocationQuery } from 'vue-router'
import { Timer, Search, Refresh, Edit, Bell, WarningFilled, WarnTriangleFilled, CircleCheck, Download } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { formatDate } from '@/utils'
import { useClock } from '@/composables/useClock'
import { exportToExcel } from '@/utils/export-excel'
import { levelLabel, warningHandledStatusLabel, warningLevelFilterLabel } from '../common/warning-lifecycle'
import {
  buildExportRows,
  buildRecordsQuery,
  createRecordsHandleForm,
  createRecordsOverview,
  createRecordsPagination,
  createRecordsSearchForm,
  exportColumns,
  levelTag,
  typeLabel,
  typeTag,
  sourceLabel
} from './records-view-model'
import {
  fetchRecordsExportRows,
  fetchRecordsOverview,
  fetchRecordsPage,
  submitBatchRecordHandle,
  submitRecordHandle
} from './records-runtime'
import type {
  RecordsCardFilter,
  RecordsHandleForm,
  RecordsOverview,
  RecordsPagination,
  RecordsSearchForm,
  WarningRecord
} from './records-types'

const route = useRoute()

const { currentTime } = useClock()
const isMobile = ref(window.innerWidth < 768)
const filterExpanded = ref(false)

const overview = reactive<RecordsOverview>(createRecordsOverview())
const handleRate = computed(() => { const t = overview.todayTotal||0; return t===0?0:((overview.handled/t)*100).toFixed(1) })

const loadOverview = async () => {
  try {
    const nextOverview = await fetchRecordsOverview()
    if (nextOverview) Object.assign(overview, nextOverview)
  } catch(e) {}
}

const searchForm = reactive<RecordsSearchForm>(createRecordsSearchForm())
const loading = ref(false)
const tableData = ref<WarningRecord[]>([])
const pagination = reactive<RecordsPagination>(createRecordsPagination())

const loadData = async () => {
  loading.value = true
  try {
    const result = await fetchRecordsPage(buildRecordsQuery(searchForm, pagination))
    tableData.value = result.rows
    pagination.total = result.total
  } catch(e) { ElMessage.error('加载预警列表失败') }
  finally { loading.value = false }
}

const handleSearch = () => { pagination.page=1; loadData() }
const handleReset = () => { Object.assign(searchForm, createRecordsSearchForm()); handleSearch() }

function applyRouteFilters(query = route.query) {
  const filters = query as LocationQuery & Record<string, string | undefined>
  searchForm.warningType = filters.warningType || ''
  searchForm.warningLevel = warningLevelFilterLabel(query.warningLevel)
  searchForm.handleStatus = filters.handleStatus || ''
  searchForm.keyword = filters.keyword || filters.userCode || ''
  searchForm.dateRange = filters.startDate && filters.endDate
    ? [filters.startDate, filters.endDate]
    : null
}

// KPI card click filter
const activeCard = ref<RecordsCardFilter>('') // 当前高亮的 KPI 卡片
const filterByCard = (type: Exclude<RecordsCardFilter, ''>) => {
  activeCard.value = activeCard.value === type ? '' : type
  Object.assign(searchForm, { dateRange: null, warningType: '', warningLevel: '', handleStatus: '', keyword: '' })
  if (activeCard.value === 'unhandled') {
    searchForm.handleStatus = 'unhandled'
  } else if (activeCard.value === 'critical') {
    searchForm.warningLevel = '高危'
  } else if (activeCard.value === 'handled') {
    searchForm.handleStatus = 'handled'
  }
  handleSearch()
}

// Detail drawer
const detailVisible = ref(false)
const detailRow = ref<WarningRecord | null>(null)
const openDetail = (row: WarningRecord) => { detailRow.value = row; detailVisible.value = true }

// Batch selection
const selectedRows = ref<WarningRecord[]>([])
const batchLoading = ref(false)
const batchHandle = async () => {
  if (!selectedRows.value.length) return
  batchLoading.value = true
  try {
    const locators = selectedRows.value.map(r => ({ warningId: r.id, occurredAt: r.createTime }))
    const res = await submitBatchRecordHandle(locators)
    if (res.code === 200) {
      ElMessage.success(`已批量处理 ${locators.length} 条事件`)
      selectedRows.value = []
      loadData()
      loadOverview()
    } else {
      ElMessage.error(res.message || '批量处理失败')
    }
  } catch(e) { ElMessage.error('批量处理失败') }
  finally { batchLoading.value = false }
}

const handleDialogVisible = ref(false)
const handleSubmitting = ref(false)
const handleFormRef = ref<FormInstance | null>(null)
const currentRow = ref<WarningRecord | null>(null)
const handleForm = reactive<RecordsHandleForm>(createRecordsHandleForm())
const handleRules: FormRules<RecordsHandleForm> = { handleType:[{required:true,message:'请选择处理方式'}], handleNote:[{required:true,message:'请输入处理备注',trigger:'blur'}] }

const openHandle = (row: WarningRecord | null) => { currentRow.value=row; handleForm.handleType=1; handleForm.handleNote=''; handleForm.notify=false; handleDialogVisible.value=true }
const submitHandle = async () => {
  const valid = await handleFormRef.value?.validate().catch(()=>false)
  if(!valid) return
  handleSubmitting.value = true
  try {
    const res = await submitRecordHandle(currentRow.value!.id, { handleType:handleForm.handleType, handleNote:handleForm.handleNote, notify:handleForm.notify, createTime:currentRow.value!.createTime })
    if(res.code===200) { ElMessage.success('处理成功'); handleDialogVisible.value=false; loadData(); loadOverview() }
    else ElMessage.error(res.message||'处理失败')
  } catch(e) { ElMessage.error('处理失败') }
  finally { handleSubmitting.value=false }
}

const exportExcel = async () => {
  try {
    const rows = await fetchRecordsExportRows(buildRecordsQuery(searchForm, pagination, { includePage: false }))
    if (!rows.length) { ElMessage.warning('无数据可导出'); return }
    const data = buildExportRows(rows, { formatDate, levelLabel, warningHandledStatusLabel })
    await exportToExcel(data, exportColumns, `处置记录_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}`)
    ElMessage.success(`已导出 ${rows.length} 条记录`)
  } catch (e) { ElMessage.error('导出失败') }
}

onMounted(() => {
  applyRouteFilters()
  loadOverview(); loadData()
})
watch(() => route.fullPath, () => {
  if (route.name !== 'AlertRecords') return
  pagination.page = 1
  applyRouteFilters()
  loadOverview()
  loadData()
})
</script>

<style scoped lang="scss">
@import './records.scss';
</style>
