<template>
  <div class="ea-page">
    <header class="ea-hd">
      <div class="ea-hd-left">
        <span class="ea-live-dot"></span>
        <h1 class="ea-hd-title">职工健康档案库</h1>
      </div>
      <div class="ea-hd-kpis">
        <div class="ea-kpi">
          <span class="ea-kpi-n">{{ loading ? '--' : filtered.length }}</span>
          <span class="ea-kpi-l">检索结果</span>
        </div>
        <div class="ea-kpi">
          <span class="ea-kpi-n">{{ deptList.length }}</span>
          <span class="ea-kpi-l">部门数</span>
        </div>
      </div>
      <div class="ea-hd-actions">
        <button v-if="selectedDept" type="button" class="ea-action-btn ea-action-btn--success" @click="openDeptReport" title="生成该部门AI健康报告">
          <el-icon><Document /></el-icon> AI 部门报告
        </button>
        <button type="button" class="ea-action-btn ea-action-btn--primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增职工
        </button>
      </div>
    </header>

    <!-- 搜索栏 -->
    <div class="ea-search-bar">
      <el-input v-model="keyword" placeholder="输入姓名或工号搜索" class="ea-input" clearable @clear="keyword = ''">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="selectedDept" placeholder="请选择部门" class="ea-dept-select" clearable>
        <el-option v-for="d in deptList" :key="d" :label="d" :value="d" />
      </el-select>
      <button class="ea-reset-btn" @click="keyword = ''; selectedDept = ''">重置</button>
      <div class="ea-search-meta">
        <span class="ea-total">共 <b>{{ loading ? '--' : filtered.length }}</b> 名员工</span>
      </div>
    </div>

    <!-- 员工卡片网格 -->
    <div v-loading="blockingLoading" class="ea-grid">
      <div v-if="loading && !blockingLoading" class="ea-background-loading" role="status">
        档案数据仍在加载，页面可以继续操作
      </div>
      <div v-for="emp in paginated" :key="emp.empCode" class="ea-card" @click="openPortrait(emp)">
        <div class="ea-card-top-bar"></div>
        <div class="ea-card-body">
          <div class="ea-info">
            <div class="ea-name-row">
              <span class="ea-name">{{ emp.empName }}</span>
              <span :class="['ea-gender', emp.gender === 2 ? 'f' : 'm']">{{ emp.gender === 2 ? '女' : '男' }}</span>
            </div>
            <div class="ea-fields">
              <div class="ea-field"><span class="ef-label">部门</span><span class="ef-val">{{ emp.deptName || '--' }}</span></div>
              <div class="ea-field"><span class="ef-label">岗位</span><span class="ef-val">{{ emp.jobTypeName || '--' }}</span></div>
              <div class="ea-field"><span class="ef-label">工号</span><span class="ef-val code">{{ emp.empCode || '--' }}</span></div>
              <div class="ea-field"><span class="ef-label">年龄</span><span class="ef-val">{{ calcAge(emp.birthDate) }}</span></div>
              <div class="ea-field full"><span class="ef-label">手机</span><span class="ef-val">{{ emp.phone || '--' }}</span></div>
            </div>
          </div>
        </div>
        <div class="ea-health-bar">
          <div class="ea-hb-label">健康状态</div>
          <div class="ea-hb-track">
            <div class="ea-hb-fill" :style="{ width: (emp._healthScore || 0) + '%', background: healthBarColor(emp._healthScore || 0) }"></div>
          </div>
          <div class="ea-hb-score" :style="{ color: healthBarColor(emp._healthScore || 0) }">{{ emp._healthScore || '--' }}</div>
        </div>
        <!-- 操作按钮区 -->
        <div class="ea-card-actions" @click.stop>
          <button class="ea-act-btn realtime" @click="openRealtime(emp)"><el-icon><Monitor /></el-icon> 实时</button>
          <button class="ea-act-btn report" @click="openAiReport(emp)"><el-icon><Document /></el-icon> AI报告</button>
          <button class="ea-act-btn edit" @click="handleEdit(emp)"><el-icon><Edit /></el-icon> 编辑</button>
          <button class="ea-act-btn delete" @click="handleDelete(emp)"><el-icon><Delete /></el-icon> 删除</button>
        </div>
      </div>

      <div v-if="!loading && filtered.length === 0" class="ea-empty">
        <div class="ea-empty-icon">ARCH</div>
        <div class="ea-empty-title">未找到符合条件的员工</div>
        <div class="ea-empty-desc">可以放宽关键词或切换部门筛选，继续检索职工档案和画像入口。</div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="ea-pagination" v-if="filtered.length > pageSize">
      <el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="filtered.length"
        layout="prev, pager, next, total" background class="ea-pager" />
    </div>

    <!-- AI 健康诊断报告 Dialog -->
    <el-dialog v-model="reportVisible" title="AI 健康诊断报告" width="820px"
      :close-on-click-modal="false" class="ea-dialog report-dialog">
      <div v-if="reportLoading" class="report-loading">
        <div class="report-dots"><span></span><span></span><span></span></div>
        <p>正在生成健康诊断报告，请稍候（约15~30秒）...</p>
      </div>
      <div v-else-if="reportContent" class="report-content" v-html="reportHtml"></div>
      <div v-else class="report-empty">生成失败，请重试</div>
      <template #footer>
        <el-button @click="reportVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!reportContent || reportLoading" @click="printReport">
          打印 / 导出 PDF
        </el-button>
      </template>
    </el-dialog>

    <!-- AI 部门报告 Dialog -->
    <el-dialog v-model="deptReportVisible" :title="'AI 部门健康报告 — ' + selectedDept" width="820px"
      :close-on-click-modal="false" class="ea-dialog report-dialog">
      <div v-if="deptReportLoading" class="report-loading">
        <div class="report-dots"><span></span><span></span><span></span></div>
        <p>正在生成部门健康报告，请稍候（约20~40秒）...</p>
      </div>
      <div v-else-if="deptReportContent" class="report-content" v-html="deptReportHtml"></div>
      <div v-else class="report-empty">生成失败，请重试</div>
      <template #footer>
        <el-button @click="deptReportVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!deptReportContent || deptReportLoading" @click="printDeptReport">
          打印 / 导出 PDF
        </el-button>
      </template>
    </el-dialog>

    <!-- CRUD Dialog -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px"
      :close-on-click-modal="false" class="ea-dialog" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="姓名" prop="empName"><el-input v-model="form.empName" placeholder="请输入姓名" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工号" prop="empCode"><el-input v-model="form.empCode" placeholder="请输入工号" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="form.gender">
                <el-radio :value="1">男</el-radio>
                <el-radio :value="2">女</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机" prop="phone"><el-input v-model="form.phone" placeholder="请输入手机号" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="部门" prop="deptId">
              <el-select v-model="form.deptId" placeholder="请选择部门" filterable style="width:100%">
                <el-option v-for="d in deptOptions" :key="d.id" :label="d.deptName" :value="d.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工种" prop="jobTypeId">
              <el-select v-model="form.jobTypeId" placeholder="请选择工种" filterable style="width:100%">
                <el-option v-for="j in jobTypeOptions" :key="j.id" :label="j.typeName" :value="j.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="出生日期">
              <el-date-picker v-model="form.birthDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入职日期">
              <el-date-picker v-model="form.hireDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="身高(cm)">
              <el-input-number v-model="form.height" :min="0" :max="300" :precision="1" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="体重(kg)">
              <el-input-number v-model="form.weight" :min="0" :max="500" :precision="1" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="血型">
              <el-select v-model="form.bloodType" placeholder="请选择" style="width:100%">
                <el-option label="A型" value="A" />
                <el-option label="B型" value="B" />
                <el-option label="AB型" value="AB" />
                <el-option label="O型" value="O" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="紧急联系人"><el-input v-model="form.emergencyContact" placeholder="联系人姓名" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="紧急电话"><el-input v-model="form.emergencyPhone" placeholder="联系人手机号" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="form.statusBool" active-text="在职" inactive-text="离职"
                active-color="#38ef7d" inactive-color="#ff6b6b" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="既往病史">
              <el-checkbox-group v-model="form.medicalHistory">
                <el-checkbox value="高血压">高血压</el-checkbox>
                <el-checkbox value="糖尿病">糖尿病</el-checkbox>
                <el-checkbox value="心脏病">心脏病</el-checkbox>
                <el-checkbox value="哮喘">哮喘</el-checkbox>
                <el-checkbox value="颈椎病">颈椎病</el-checkbox>
                <el-checkbox value="腰椎病">腰椎病</el-checkbox>
              </el-checkbox-group>
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
import { Search, Plus, Edit, Delete, Monitor, Document } from '@element-plus/icons-vue'
import { useEmployeeArchivePage } from './use-employee-archive-page'

const {
  blockingLoading,
  calcAge,
  currentPage,
  deptList,
  deptOptions,
  deptReportContent,
  deptReportHtml,
  deptReportLoading,
  deptReportVisible,
  dialogTitle,
  dialogVisible,
  filtered,
  form,
  formRef,
  handleAdd,
  handleDelete,
  handleEdit,
  healthBarColor,
  jobTypeOptions,
  keyword,
  loading,
  openAiReport,
  openDeptReport,
  openPortrait,
  openRealtime,
  pageSize,
  paginated,
  printDeptReport,
  printReport,
  reportContent,
  reportHtml,
  reportLoading,
  reportVisible,
  resetForm,
  rules,
  selectedDept,
  submitForm,
  submitting
} = useEmployeeArchivePage()
</script>

<style scoped>
@import './employee-archive.scss';
</style>
