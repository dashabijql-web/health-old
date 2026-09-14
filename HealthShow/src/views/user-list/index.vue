<template>
  <div class="user-list-container">
    <header class="ul-hd">
      <div class="ul-hd-left">
        <span class="ul-live-dot"></span>
        <h1 class="ul-hd-title">用户管理</h1>
      </div>
      <div class="ul-hd-kpis">
        <div class="ul-kpi"><span class="ul-kpi-n">{{ stats.totalUsers }}</span><span class="ul-kpi-l">总用户</span></div>
        <div class="ul-kpi"><span class="ul-kpi-n success">{{ stats.activeUsers }}</span><span class="ul-kpi-l">活跃</span></div>
        <div class="ul-kpi"><span class="ul-kpi-n warning">{{ stats.onlineUsers }}</span><span class="ul-kpi-l">在线</span></div>
        <div class="ul-kpi"><span class="ul-kpi-n info">{{ stats.onlineRate }}%</span><span class="ul-kpi-l">在线率</span></div>
      </div>
      <div class="ul-hd-time"><el-icon><Timer /></el-icon>{{ currentTime }}</div>
    </header>

    <!-- 搜索区 -->
    <div class="panel mb-16">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item>
          <el-input v-model="searchForm.keyword" placeholder="搜索用户名 / 姓名 / 手机号"
            clearable style="width:260px" @clear="handleSearch" @keyup.enter="handleSearch">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-select v-model="searchForm.status" placeholder="账号状态" clearable style="width:130px">
            <el-option label="正常" :value="0" />
            <el-option label="禁用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="searchForm.deptId" placeholder="所属部门" clearable style="width:140px">
            <el-option v-for="d in deptList" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 用户列表 -->
    <div class="panel table-panel">
      <div class="panel-header">
        <div class="panel-title"><span class="title-bar"></span>用户列表</div>
        <div class="panel-header-right">
          <span class="total-badge">共 {{ pagination.total }} 人</span>
          <el-button v-if="hasPerm('user:create')" type="primary" size="small" :icon="Plus" @click="handleAdd">新增用户</el-button>
        </div>
      </div>

      <div class="table-body">
      <el-table :data="userList" v-loading="loading" stripe height="100%" style="width:100%"
        :header-cell-style="{ background:'#141830', color:'#7eb8d4', fontWeight:'600', fontSize:'13px' }"
        :row-style="{ background:'#1a1f3a' }">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="用户" min-width="150">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar">{{ (row.realName || row.nickname || '?')[0] }}</div>
              <div>
                <div class="user-name">{{ row.realName }}</div>
                <div class="user-code">{{ row.userCode }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="email" label="邮箱" min-width="170" show-overflow-tooltip />
        <el-table-column prop="deptName" label="部门" width="110" />
        <el-table-column label="账号状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-if="hasPerm('user:status')"
              :model-value="row.status === 0"
              active-color="#38ef7d"
              inactive-color="#4a5578"
              size="small"
              @change="handleStatusChange(row)"
            />
            <el-tag v-else :type="row.status === 0 ? 'success' : 'danger'" size="small" effect="dark">
              {{ row.status === 0 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="在线" width="80" align="center">
          <template #default="{ row }">
            <span :class="['online-dot', row.isOnline ? 'online' : 'offline']"></span>
            <span class="online-text">{{ row.isOnline ? '在线' : '离线' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="155">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-ops">
              <el-button type="primary" link size="small" @click="handleViewDetail(row)">
                <el-icon><InfoFilled /></el-icon> 详情
              </el-button>
              <el-button v-if="hasPerm('user:update')" type="warning" link size="small" @click="handleEdit(row)">
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
              <el-button v-if="hasPerm('user:delete')" type="danger" link size="small" @click="handleDelete(row)">
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

    <!-- ══ 新增 / 编辑用户弹窗 ══ -->
    <el-dialog v-model="formDialog.visible"
      :title="formDialog.isEdit ? '编辑用户' : '新增用户'"
      width="620px" :close-on-click-modal="false" class="dark-dialog"
      @closed="resetForm">
      <el-form ref="userFormRef" :model="formDialog.form" :rules="formRules"
        label-width="90px" class="form-body">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="姓名" prop="realName">
              <el-input v-model="formDialog.form.realName" placeholder="请输入真实姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="formDialog.form.nickname" placeholder="请输入昵称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="formDialog.form.phone" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="formDialog.form.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input v-model="formDialog.form.password" type="password"
                :placeholder="formDialog.isEdit ? '留空表示不修改' : '请输入密码'"
                show-password />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门" prop="deptId">
              <el-select v-model="formDialog.form.deptId" placeholder="请选择部门" style="width:100%">
                <el-option v-for="d in deptList" :key="d.id" :label="d.name" :value="d.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="formDialog.form.gender">
                <el-radio :value="0">男</el-radio>
                <el-radio :value="1">女</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="账号状态" prop="status">
              <el-switch v-model="formDialog.form.statusBool"
                active-text="正常" inactive-text="禁用"
                active-color="#38ef7d" inactive-color="#ff6b6b" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="角色" prop="roleIds">
              <el-select v-model="formDialog.form.roleIds" multiple placeholder="请选择角色"
                style="width:100%" :loading="rolesLoading">
                <el-option v-for="r in availableRoles" :key="r.id" :label="r.roleName" :value="r.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="formDialog.form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="formDialog.submitting" @click="handleFormSubmit">
          {{ formDialog.isEdit ? '保存修改' : '确认新增' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ══ 用户详情弹窗 ══ -->
    <el-dialog v-model="detailDialog.visible" title="用户详情" width="580px"
      :close-on-click-modal="false" class="dark-dialog">
      <div v-if="detailDialog.data" class="detail-body">
        <div class="detail-avatar-row">
          <div class="detail-avatar">{{ (detailDialog.data.realName || '?')[0] }}</div>
          <div>
            <div class="detail-name">{{ detailDialog.data.realName }}</div>
            <el-tag :type="detailDialog.data.status === 0 ? 'success' : 'danger'" size="small" effect="dark">
              {{ detailDialog.data.status === 0 ? '正常' : '禁用' }}
            </el-tag>
          </div>
          <el-button type="primary" size="small" plain style="margin-left:auto"
            @click="openHealthDialog(detailDialog.data)">
            <el-icon><DataLine /></el-icon> 健康统计
          </el-button>
        </div>
        <el-descriptions :column="2" border class="detail-desc">
          <el-descriptions-item label="用户编码">{{ detailDialog.data.userCode }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ detailDialog.data.nickname }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ detailDialog.data.gender === 0 ? '男' : '女' }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ detailDialog.data.deptName }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detailDialog.data.phone }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detailDialog.data.email }}</el-descriptions-item>
          <el-descriptions-item label="注册时间" :span="2">{{ formatDate(detailDialog.data.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="最后在线" :span="2">{{ formatDate(detailDialog.data.lastOnlineTime) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailDialog.data.remark || '无' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <!-- ══ 健康统计弹窗 ══ -->
    <el-dialog v-model="healthDialog.visible" title="健康统计" width="560px"
      :close-on-click-modal="false" class="dark-dialog">
      <div v-if="healthDialog.data" class="health-grid">
        <div class="health-item">
          <el-icon class="hi-icon" color="#00d4ff"><DataAnalysis /></el-icon>
          <div class="hi-value">{{ healthDialog.data.recordCount || 0 }}</div>
          <div class="hi-label">记录总数</div>
        </div>
        <div class="health-item">
          <el-icon class="hi-icon" color="#ff6b6b"><Histogram /></el-icon>
          <div class="hi-value">{{ (healthDialog.data.avgHeartRate || 0).toFixed(0) }}<span class="hi-unit">次/分</span></div>
          <div class="hi-label">平均心率</div>
        </div>
        <div class="health-item">
          <el-icon class="hi-icon" color="#4fc3f7"><TrendCharts /></el-icon>
          <div class="hi-value">{{ (healthDialog.data.avgBloodOxygen || 0).toFixed(0) }}<span class="hi-unit">%</span></div>
          <div class="hi-label">平均血氧</div>
        </div>
        <div class="health-item">
          <el-icon class="hi-icon" color="#81c784"><Moon /></el-icon>
          <div class="hi-value">{{ (healthDialog.data.avgSleepHours || 0).toFixed(1) }}<span class="hi-unit">h</span></div>
          <div class="hi-label">平均睡眠</div>
        </div>
        <div class="health-item-wide">
          <span class="hi-label-sm">最后记录时间</span>
          <span class="hi-value-sm">{{ formatDate(healthDialog.data.lastRecordTime) }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  Search,
  Refresh,
  Plus,
  UserFilled,
  Timer,
  User,
  CircleCheck,
  Monitor,
  TrendCharts,
  InfoFilled,
  Edit,
  Delete,
  DataLine,
  DataAnalysis,
  Histogram,
  Moon
} from '@element-plus/icons-vue'
import { useUserListPage } from './use-user-list-page'

defineOptions({ name: 'UserList' })

const {
  availableRoles,
  currentTime,
  deptList,
  detailDialog,
  formDialog,
  formRules,
  formatDate,
  handleAdd,
  handleCurrentChange,
  handleDelete,
  handleEdit,
  handleFormSubmit,
  handleReset,
  handleSearch,
  handleSizeChange,
  handleStatusChange,
  handleViewDetail,
  hasPerm,
  healthDialog,
  loading,
  openHealthDialog,
  pagination,
  resetForm,
  rolesLoading,
  searchForm,
  stats,
  userFormRef,
  userList
} = useUserListPage()
</script>

<style scoped lang="scss">
@import './user-list.scss';
</style>
