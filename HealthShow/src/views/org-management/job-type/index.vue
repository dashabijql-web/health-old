<template>
  <div class="page-container">
    <header class="jt-hd">
      <div class="jt-hd-left">
        <span class="jt-live-dot"></span>
        <h1 class="jt-hd-title">工种管理</h1>
      </div>
      <div class="jt-hd-kpis">
        <div class="jt-kpi"><span class="jt-kpi-n">{{ stats.total }}</span><span class="jt-kpi-l">工种总数</span></div>
        <div class="jt-kpi"><span class="jt-kpi-n danger">{{ stats.highRisk }}</span><span class="jt-kpi-l">高危</span></div>
        <div class="jt-kpi"><span class="jt-kpi-n success">{{ stats.active }}</span><span class="jt-kpi-l">启用</span></div>
      </div>
      <div class="jt-hd-time"><el-icon><Timer /></el-icon>{{ currentTime }}</div>
    </header>

    <!-- Data Panel -->
    <div class="panel table-panel">
      <div class="panel-header">
        <div class="panel-title"><span class="title-bar"></span>工种列表</div>
        <div class="panel-header-right">
          <span class="total-badge">共 {{ pagination.total }} 条</span>
          <el-button type="primary" size="small" :icon="Plus" @click="handleAdd">新增工种</el-button>
        </div>
      </div>

      <!-- Search -->
      <div class="search-form">
        <el-form :inline="true" :model="searchForm">
          <el-form-item>
            <el-input v-model="searchForm.keyword" placeholder="搜索工种名称 / 编码"
              clearable style="width:260px" @clear="handleSearch" @keyup.enter="handleSearch">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="table-body">
      <el-table :data="tableData" v-loading="loading" stripe height="100%" style="width:100%"
        :header-cell-style="{ background:'#141830', color:'#7eb8d4', fontWeight:'600', fontSize:'13px' }"
        :row-style="{ background:'#1a1f3a' }">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="typeName" label="工种名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="typeCode" label="工种编码" width="130" />
        <el-table-column label="风险等级" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.riskLevel)" size="small" effect="dark">
              {{ riskLabel(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small" effect="dark">
              {{ row.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-ops">
              <el-button type="primary" link size="small" @click="openEmployeeDrawer(row)">
                <el-icon><User /></el-icon> 查看员工
              </el-button>
              <el-button type="warning" link size="small" @click="handleEdit(row)">
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
              <el-button type="danger" link size="small" @click="handleDelete(row)">
                <el-icon><Delete /></el-icon> 删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="pagination-wrap">
        <el-pagination background layout="total, sizes, prev, pager, next, jumper"
          :current-page="pagination.page" :page-sizes="[10, 20, 50, 100]"
          :page-size="pagination.size" :total="pagination.total"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </div>

    <!-- Employee Drawer -->
    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="600px" direction="rtl" class="dark-drawer">
      <div v-loading="drawerLoading" style="height:100%">
        <div style="margin-bottom:12px;color:#8ba6c8;font-size:13px">
          共 <span style="color:#00d4ff;font-weight:700">{{ drawerEmployees.length }}</span> 名员工
        </div>
        <el-table :data="drawerEmployees" stripe style="width:100%"
          :header-cell-style="{ background:'#141830', color:'#7eb8d4', fontWeight:'600', fontSize:'13px' }"
          :row-style="{ background:'#1a1f3a' }">
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="empCode" label="工号" width="110" />
          <el-table-column prop="empName" label="姓名" width="100" />
          <el-table-column label="性别" width="70" align="center">
            <template #default="{ row }">{{ row.gender === 1 ? '男' : row.gender === 2 ? '女' : '--' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small" effect="dark">
                {{ row.status === 0 ? '在职' : '离职' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="联系电话" min-width="130" />
        </el-table>
      </div>
    </el-drawer>

    <!-- CRUD Dialog -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px"
      :close-on-click-modal="false" class="dark-dialog" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="form-body">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工种名称" prop="typeName">
              <el-input v-model="form.typeName" placeholder="请输入工种名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工种编码" prop="typeCode">
              <el-input v-model="form.typeCode" placeholder="请输入工种编码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="风险等级" prop="riskLevel">
          <el-radio-group v-model="form.riskLevel">
            <el-radio :value="1">
              <el-tag type="success" size="small" effect="dark">低风险</el-tag>
            </el-radio>
            <el-radio :value="2">
              <el-tag type="warning" size="small" effect="dark">中风险</el-tag>
            </el-radio>
            <el-radio :value="3">
              <el-tag type="danger" size="small" effect="dark">高风险</el-tag>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入工种描述" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="form.statusBool"
            active-text="正常" inactive-text="停用"
            active-color="#38ef7d" inactive-color="#ff6b6b" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import {
  Timer, Search, Refresh, Plus, Edit, Delete,
  Suitcase, WarningFilled, CircleCheck, User
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getJobTypeList, createJobType, updateJobType, deleteJobType } from '@/api/job-type'
import { getEmployeeList } from '@/api/employee'
import { useClock } from '@/composables/useClock'

const { currentTime } = useClock()

// ── Stats ──
const stats = reactive({ total: 0, highRisk: 0, active: 0 })

// ── Search & Pagination ──
const searchForm = reactive({ keyword: '' })
const pagination = reactive({ page: 1, size: 20, total: 0 })
const loading = ref(false)
const tableData = ref([])

const riskTagType = (level) => {
  const map = { 1: 'success', 2: 'warning', 3: 'danger' }
  return map[level] || 'info'
}
const riskLabel = (level) => {
  const map = { 1: '低风险', 2: '中风险', 3: '高风险' }
  return map[level] || '未知'
}

const loadTableData = async () => {
  loading.value = true
  try {
    const res = await getJobTypeList({
      keyword: searchForm.keyword,
      page: pagination.page,
      size: pagination.size
    })
    if (res.code === 200) {
      const data = res.data
      tableData.value = data.list || data || []
      pagination.total = data.total || tableData.value.length
      // compute stats from full data or response
      if (data.stats) {
        Object.assign(stats, data.stats)
      } else {
        stats.total = pagination.total
        stats.highRisk = tableData.value.filter(r => r.riskLevel === 3).length
        stats.active = tableData.value.filter(r => r.status === 0).length
      }
    }
  } catch (e) {
    ElMessage.error('加载工种列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.page = 1; loadTableData() }
const handleReset = () => { searchForm.keyword = ''; handleSearch() }
const handleSizeChange = (size) => { pagination.size = size; loadTableData() }
const handleCurrentChange = (page) => { pagination.page = page; loadTableData() }

// ── CRUD Dialog ──
const dialogVisible = ref(false)
const dialogTitle = ref('新增工种')
const submitting = ref(false)
const formRef = ref(null)
const isEdit = ref(false)

const emptyForm = () => ({
  id: null,
  typeName: '',
  typeCode: '',
  riskLevel: 1,
  description: '',
  statusBool: true
})

const form = reactive(emptyForm())

const rules = {
  typeName: [{ required: true, message: '请输入工种名称', trigger: 'blur' }],
  typeCode: [{ required: true, message: '请输入工种编码', trigger: 'blur' }],
  riskLevel: [{ required: true, message: '请选择风险等级', trigger: 'change' }]
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增工种'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑工种'
  Object.assign(form, {
    id: row.id,
    typeName: row.typeName || '',
    typeCode: row.typeCode || '',
    riskLevel: row.riskLevel ?? 1,
    description: row.description || '',
    statusBool: row.status === 0
  })
  dialogVisible.value = true
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const data = {
      ...form,
      status: form.statusBool ? 0 : 1
    }
    delete data.statusBool
    const res = isEdit.value
      ? await updateJobType(data)
      : await createJobType(data)
    if (res.code === 200) {
      ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
      formRef.value?.clearValidate()
      dialogVisible.value = false
      loadTableData()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败，请重试')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除工种「${row.typeName}」吗？删除后不可恢复。`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    const res = await deleteJobType(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadTableData()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败，请重试')
  }
}

const resetForm = () => formRef.value?.resetFields()

// ── Employee Drawer ──
const drawerVisible = ref(false)
const drawerTitle = ref('')
const drawerLoading = ref(false)
const drawerEmployees = ref([])

const openEmployeeDrawer = async (row) => {
  drawerTitle.value = `${row.typeName} · 员工列表`
  drawerEmployees.value = []
  drawerVisible.value = true
  drawerLoading.value = true
  try {
    const res = await getEmployeeList({ jobTypeId: row.id, size: 10000 })
    if (res.code === 200) drawerEmployees.value = res.data?.list || res.data || []
  } catch {
    ElMessage.error('加载员工列表失败')
  } finally {
    drawerLoading.value = false
  }
}

// ── Init ──
onMounted(() => {
  loadTableData()
})
</script>

<style scoped lang="scss">
@import '@/styles/dark-admin.scss';

.page-container {
  height: calc(100vh - 50px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 20px;
  box-sizing: border-box;
  background: $da-bg;
  color: $da-text;
}
.jt-hd {
  height: 56px; flex-shrink: 0; display: flex; align-items: center;
  padding: 0 22px; gap: 16px;
  background: rgba(0, 6, 24, 0.65);
  border-bottom: 1px solid rgba(0,212,255,0.12);
  border-radius: 10px; margin-bottom: 16px;
}
.jt-hd-left { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.jt-live-dot {
  width: 9px; height: 9px; border-radius: 50%; background: #00d4ff;
  box-shadow: 0 0 8px rgba(0,212,255,0.7);
  animation: jtPulse 2s ease-in-out infinite;
}
@keyframes jtPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.45; transform: scale(0.75); }
}
.jt-hd-title {
  font-size: 20px; font-weight: 700; margin: 0; letter-spacing: 2px;
  background: linear-gradient(90deg, #00d4ff, #4facfe);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;
}
.jt-hd-kpis { flex: 1; display: flex; justify-content: center; gap: 0; }
.jt-kpi {
  display: flex; flex-direction: column; align-items: center; padding: 0 20px;
  border-right: 1px solid rgba(0,212,255,0.12);
  &:first-child { border-left: 1px solid rgba(0,212,255,0.12); }
}
.jt-kpi-n {
  font-size: 18px; font-weight: 700; font-family: 'Consolas', monospace; line-height: 1.1;
  color: #00d4ff;
  &.success { color: #38ef7d; }
  &.danger { color: #ff5252; }
}
.jt-kpi-l { font-size: 10px; color: #8ba6c8; margin-top: 2px; white-space: nowrap; }
.jt-hd-time {
  flex-shrink: 0; font-size: 12px; color: #8ba6c8;
  display: flex; align-items: center; gap: 4px;
}

.mb-16 { margin-bottom: 16px; flex-shrink: 0; }

.stat-icon-wrap {
  @include da-icon-wrap;
  &.primary { background: $da-grad-primary; }
  &.success { background: $da-grad-success; }
  &.warning { background: $da-grad-warning; }
  &.danger  { background: $da-grad-danger; }
  &.info    { background: $da-grad-info; }
}
.stat-body { flex: 1; }
.stat-value { font-size: 28px; font-weight: 700; color: $da-text-bright; line-height: 1.1; }
.stat-label { font-size: 12px; color: $da-text-dim; margin-top: 4px; }

.panel { @include da-panel; }
.panel.table-panel { flex: 1; min-height: 0; overflow: hidden; display: flex; flex-direction: column; }
.table-body { flex: 1; min-height: 0; overflow: hidden; }
.search-form { flex-shrink: 0; padding: 16px 20px; }
.panel-header {
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px; border-bottom: 1px solid $da-border;
}
.panel-header-right { display: flex; align-items: center; gap: 12px; }
.panel-title { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; color: $da-text; }
.title-bar { display: inline-block; width: 3px; height: 16px; background: $da-accent; border-radius: 2px; }
.total-badge {
  font-size: 12px; color: $da-text-dim;
  background: $da-accent-dim; border: 1px solid $da-accent-border;
  padding: 3px 12px; border-radius: 12px;
}

.table-ops { display: flex; align-items: center; justify-content: center; gap: 4px; flex-wrap: wrap; }

.pagination-wrap {
  flex-shrink: 0;
  display: flex; justify-content: flex-end;
  padding: 14px 20px; border-top: 1px solid $da-border;
}

.form-body { padding: 8px 0; }

@include da-el-overrides;
</style>
