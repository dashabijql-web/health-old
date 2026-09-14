<template>
  <div class="page-container">
    <header class="dp-hd">
      <div class="dp-hd-left">
        <span class="dp-live-dot"></span>
        <h1 class="dp-hd-title">部门管理</h1>
      </div>
      <div class="dp-hd-kpis">
        <div class="dp-kpi"><span class="dp-kpi-n">{{ stats.total }}</span><span class="dp-kpi-l">部门总数</span></div>
        <div class="dp-kpi"><span class="dp-kpi-n success">{{ stats.active }}</span><span class="dp-kpi-l">正常</span></div>
        <div class="dp-kpi"><span class="dp-kpi-n danger">{{ stats.inactive }}</span><span class="dp-kpi-l">停用</span></div>
      </div>
      <div class="dp-hd-time"><el-icon><Timer /></el-icon>{{ currentTime }}</div>
    </header>

    <!-- Data Panel -->
    <div class="panel table-panel">
      <div class="panel-header">
        <div class="panel-title"><span class="title-bar"></span>部门列表</div>
        <div class="panel-header-right">
          <el-input
            v-model="keyword" placeholder="搜索部门名称/编码" clearable size="small"
            style="width:220px" @keyup.enter="handleSearch" @clear="handleSearch">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button size="small" @click="handleSearch">查询</el-button>
          <span class="total-badge">共 {{ pagination.total }} 个部门</span>
          <el-button type="primary" size="small" :icon="Plus" @click="handleAdd">新增部门</el-button>
        </div>
      </div>

      <div class="table-body">
      <el-table :data="tableData" v-loading="loading" stripe height="100%" style="width:100%"
        :header-cell-style="{ background:'#141830', color:'#7eb8d4', fontWeight:'600', fontSize:'13px' }"
        :row-style="{ background:'#1a1f3a' }">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="deptName" label="部门名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="deptCode" label="部门编码" width="130" />
        <el-table-column prop="leader" label="负责人" width="110">
          <template #default="{ row }"><span :style="row.leader?'':'color:#4b5563'">{{ row.leader || '--' }}</span></template>
        </el-table-column>
        <el-table-column prop="phone" label="联系电话" width="150" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small" effect="dark">
              {{ row.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
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
            <el-form-item label="部门名称" prop="deptName">
              <el-input v-model="form.deptName" placeholder="请输入部门名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门编码" prop="deptCode">
              <el-input v-model="form.deptCode" placeholder="请输入部门编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-input v-model="form.leader" placeholder="请输入负责人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.phone" placeholder="请输入联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" :max="999" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="form.statusBool"
                active-text="正常" inactive-text="停用"
                active-color="#38ef7d" inactive-color="#ff6b6b" />
            </el-form-item>
          </el-col>
        </el-row>
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
import { Timer, Search, Plus, Edit, Delete, OfficeBuilding, CircleCheck, CircleClose, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDepartmentList, createDepartment, updateDepartment, deleteDepartment } from '@/api/department'
import { getEmployeeList } from '@/api/employee'
import { useClock } from '@/composables/useClock'

const { currentTime } = useClock()

// ── Search ──
const keyword = ref('')
const handleSearch = () => {
  pagination.page = 1
  loadTableData()
}

// ── Stats ──
const stats = reactive({ total: 0, active: 0, inactive: 0 })

// ── Table ──
const loading = ref(false)
const tableData = ref([])
const pagination = reactive({ page: 1, size: 20, total: 0 })

const loadTableData = async () => {
  loading.value = true
  try {
    const res = await getDepartmentList({
      page: pagination.page,
      size: pagination.size,
      keyword: keyword.value || undefined
    })
    if (res.code === 200) {
      const data = res.data
      tableData.value = data.list || data || []
      pagination.total = data.total || tableData.value.length
      // 只在无关键字时更新统计（反映全量数据）
      if (!keyword.value) {
        stats.total    = pagination.total
        stats.active   = tableData.value.filter(r => r.status === 0).length
        stats.inactive = tableData.value.filter(r => r.status === 1).length
      }
    }
  } catch {
    ElMessage.error('加载部门列表失败')
  } finally {
    loading.value = false
  }
}

const handleSizeChange = (size) => { pagination.size = size; loadTableData() }
const handleCurrentChange = (page) => { pagination.page = page; loadTableData() }

// ── CRUD ──
const dialogVisible = ref(false)
const dialogTitle = ref('新增部门')
const submitting = ref(false)
const formRef = ref(null)
const isEdit = ref(false)

const emptyForm = () => ({ id: null, deptName: '', deptCode: '', leader: '', phone: '', sortOrder: 0, statusBool: true })
const form = reactive(emptyForm())

const rules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  deptCode: [{ required: true, message: '请输入部门编码', trigger: 'blur' }]
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增部门'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑部门'
  Object.assign(form, {
    id: row.id, deptName: row.deptName || '', deptCode: row.deptCode || '',
    leader: row.leader || '', phone: row.phone || '',
    sortOrder: row.sortOrder ?? 0, statusBool: row.status === 0
  })
  dialogVisible.value = true
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const data = { ...form, status: form.statusBool ? 0 : 1 }
    delete data.statusBool
    const res = isEdit.value ? await updateDepartment(data) : await createDepartment(data)
    if (res.code === 200) {
      ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
      formRef.value?.clearValidate()
      dialogVisible.value = false
      loadTableData()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch {
    ElMessage.error('操作失败，请重试')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除部门「${row.deptName}」吗？删除后不可恢复。`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    const res = await deleteDepartment(row.id)
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
  drawerTitle.value = `${row.deptName} · 员工列表`
  drawerEmployees.value = []
  drawerVisible.value = true
  drawerLoading.value = true
  try {
    const res = await getEmployeeList({ deptId: row.id, size: 10000 })
    if (res.code === 200) drawerEmployees.value = res.data?.list || res.data || []
  } catch {
    ElMessage.error('加载员工列表失败')
  } finally {
    drawerLoading.value = false
  }
}

onMounted(() => loadTableData())
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
.dp-hd {
  height: 56px; flex-shrink: 0; display: flex; align-items: center;
  padding: 0 22px; gap: 16px;
  background: rgba(0, 6, 24, 0.65);
  border-bottom: 1px solid rgba(0,212,255,0.12);
  border-radius: 10px; margin-bottom: 16px;
}
.dp-hd-left { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.dp-live-dot {
  width: 9px; height: 9px; border-radius: 50%; background: #00d4ff;
  box-shadow: 0 0 8px rgba(0,212,255,0.7);
  animation: dpPulse 2s ease-in-out infinite;
}
@keyframes dpPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.45; transform: scale(0.75); }
}
.dp-hd-title {
  font-size: 20px; font-weight: 700; margin: 0; letter-spacing: 2px;
  background: linear-gradient(90deg, #00d4ff, #4facfe);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;
}
.dp-hd-kpis { flex: 1; display: flex; justify-content: center; gap: 0; }
.dp-kpi {
  display: flex; flex-direction: column; align-items: center; padding: 0 20px;
  border-right: 1px solid rgba(0,212,255,0.12);
  &:first-child { border-left: 1px solid rgba(0,212,255,0.12); }
}
.dp-kpi-n {
  font-size: 18px; font-weight: 700; font-family: 'Consolas', monospace; line-height: 1.1;
  color: #00d4ff;
  &.success { color: #38ef7d; }
  &.danger { color: #ff5252; }
}
.dp-kpi-l { font-size: 10px; color: #8ba6c8; margin-top: 2px; white-space: nowrap; }
.dp-hd-time {
  flex-shrink: 0; font-size: 12px; color: #8ba6c8;
  display: flex; align-items: center; gap: 4px;
}

.mb-16 { margin-bottom: 16px; flex-shrink: 0; }

.stat-icon-wrap {
  @include da-icon-wrap;
  &.primary { background: $da-grad-primary; }
  &.success { background: $da-grad-success; }
  &.danger  { background: $da-grad-danger; }
}
.stat-body { flex: 1; }
.stat-value { font-size: 28px; font-weight: 700; color: $da-text-bright; line-height: 1.1; }
.stat-label { font-size: 12px; color: $da-text-dim; margin-top: 4px; }

.panel { @include da-panel; }
.panel.table-panel { flex: 1; min-height: 0; overflow: hidden; display: flex; flex-direction: column; }
.table-body { flex: 1; min-height: 0; overflow: hidden; }
.panel-header {
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px; border-bottom: 1px solid $da-border;
}
.panel-header-right { display: flex; align-items: center; gap: 10px; }
.panel-title { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; color: $da-text; }
.title-bar { display: inline-block; width: 3px; height: 16px; background: $da-accent; border-radius: 2px; }
.total-badge {
  font-size: 12px; color: $da-text-dim;
  background: $da-accent-dim; border: 1px solid $da-accent-border;
  padding: 3px 12px; border-radius: 12px;
}

.table-ops { display: flex; align-items: center; justify-content: center; gap: 4px; }

.pagination-wrap {
  flex-shrink: 0;
  display: flex; justify-content: flex-end;
  padding: 14px 20px; border-top: 1px solid $da-border;
}

.form-body { padding: 8px 0; }

@include da-el-overrides;
</style>
