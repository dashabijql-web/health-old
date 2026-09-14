<template>
  <div class="role-mgmt-container">
    <header class="rm-hd">
      <div class="rm-hd-left">
        <span class="rm-live-dot"></span>
        <h1 class="rm-hd-title">角色管理</h1>
      </div>
      <div class="rm-hd-kpis">
        <div class="rm-kpi"><span class="rm-kpi-n">{{ stats.totalRoles }}</span><span class="rm-kpi-l">角色总数</span></div>
        <div class="rm-kpi"><span class="rm-kpi-n success">{{ stats.activeRoles }}</span><span class="rm-kpi-l">已启用</span></div>
        <div class="rm-kpi"><span class="rm-kpi-n danger">{{ stats.inactiveRoles }}</span><span class="rm-kpi-l">已禁用</span></div>
      </div>
      <div class="rm-hd-time"><el-icon><Timer /></el-icon>{{ currentTime }}</div>
    </header>

    <!-- 搜索区 -->
    <div class="panel mb-16">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item>
          <el-input v-model="searchForm.keyword" placeholder="搜索角色名称 / 编码"
            clearable style="width:260px" @clear="handleSearch" @keyup.enter="handleSearch">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-select v-model="searchForm.status" placeholder="角色状态" clearable style="width:130px">
            <el-option label="启用" :value="0" />
            <el-option label="禁用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 角色列表 -->
    <div class="panel table-panel">
      <div class="panel-header">
        <div class="panel-title"><span class="title-bar"></span>角色列表</div>
        <div class="panel-header-right">
          <span class="total-badge">共 {{ pagination.total }} 个角色</span>
          <el-button v-if="hasPerm('role:create')" type="primary" size="small" :icon="Plus" @click="handleAdd">新增角色</el-button>
        </div>
      </div>

      <div class="table-body">
      <el-table :data="roleList" v-loading="loading" stripe height="100%" style="width:100%"
        :header-cell-style="{ background:'#141830', color:'#7eb8d4', fontWeight:'600', fontSize:'13px' }"
        :row-style="{ background:'#1a1f3a' }">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="角色信息" min-width="200">
          <template #default="{ row }">
            <div class="role-cell">
              <div class="role-icon-wrap"><el-icon><Key /></el-icon></div>
              <div>
                <div class="role-name">{{ row.roleName }}</div>
                <div class="role-code">{{ row.roleCode }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 0"
              active-color="#38ef7d"
              inactive-color="#4a5578"
              size="small"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="用户数" width="110" align="center">
          <template #default="{ row }">
            <el-tooltip content="点击查看该角色的用户列表" placement="top">
              <el-button type="primary" link size="small" @click="handleViewUsers(row)" class="user-count-btn">
                <el-icon><User /></el-icon> {{ row.userCount }} 人
              </el-button>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="desc-text">{{ row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="155">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-ops">
              <el-button v-if="hasPerm('role:update')" type="warning" link size="small" @click="handleEdit(row)">
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
              <el-button v-if="hasPerm('role:assign')" type="primary" link size="small" @click="handleAssignPermissions(row)">
                <el-icon><Lock /></el-icon> 权限
              </el-button>
              <el-button v-if="hasPerm('role:delete')" type="danger" link size="small" @click="handleDelete(row)">
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

    <!-- ══ 新增 / 编辑角色弹窗 ══ -->
    <el-dialog v-model="formDialog.visible"
      :title="formDialog.isEdit ? '编辑角色' : '新增角色'"
      width="500px" :close-on-click-modal="false" class="dark-dialog"
      @closed="resetForm">
      <el-form ref="roleFormRef" :model="formDialog.form" :rules="formRules"
        label-width="90px" class="form-body">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="formDialog.form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="formDialog.form.roleCode" placeholder="请输入角色编码，如 ADMIN" :disabled="formDialog.isEdit" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formDialog.form.description" type="textarea" :rows="3" placeholder="请输入角色描述" />
        </el-form-item>
        <el-form-item label="状态" prop="statusBool">
          <el-switch v-model="formDialog.form.statusBool"
            active-text="启用" inactive-text="禁用"
            active-color="#38ef7d" inactive-color="#ff6b6b" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="formDialog.submitting" @click="handleFormSubmit">
          {{ formDialog.isEdit ? '保存修改' : '确认新增' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ══ 分配权限弹窗 ══ -->
    <el-dialog v-model="permDialog.visible" title="分配权限" width="520px"
      :close-on-click-modal="false" class="dark-dialog">
      <div class="perm-header">
        <div class="perm-role-info">
          <el-icon><Key /></el-icon>
          <span class="perm-role-name">{{ permDialog.roleName }}</span>
          <span class="perm-role-code">{{ permDialog.roleCode }}</span>
        </div>
        <div class="perm-actions">
          <el-button type="primary" link size="small" @click="checkAll">全选</el-button>
          <el-divider direction="vertical" />
          <el-button type="danger" link size="small" @click="uncheckAll">清空</el-button>
          <el-divider direction="vertical" />
          <el-button type="warning" link size="small" @click="expandAll">展开全部</el-button>
        </div>
      </div>
      <div v-loading="permDialog.loading" class="perm-tree-wrap">
        <div v-if="!permDialog.treeData || permDialog.treeData.length === 0" class="empty-tree">
          <el-icon size="48" color="#4a5578"><FolderOpened /></el-icon>
          <p>暂无权限数据</p>
        </div>
        <el-tree
          v-else
          ref="permTreeRef"
          :data="permDialog.treeData"
          show-checkbox
          node-key="id"
          :default-checked-keys="permDialog.checkedKeys"
          :default-expanded-keys="permDialog.expandedKeys"
          :props="{ label: 'name', children: 'children' }"
          class="perm-tree"
          @check="updatePermCheckedCount"
        >
          <template #default="{ node, data }">
            <span class="tree-node">
              <el-icon v-if="data.type === 'menu'" class="tree-icon menu"><Menu /></el-icon>
              <el-icon v-else-if="data.type === 'button'" class="tree-icon btn"><Operation /></el-icon>
              <el-icon v-else class="tree-icon folder"><FolderOpened /></el-icon>
              <span>{{ node.label }}</span>
              <el-tag v-if="data.type === 'button'" size="small" type="info" class="tree-tag">按钮</el-tag>
            </span>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <div class="perm-footer">
          <span class="perm-tip">已选 <strong>{{ permDialog.checkedCount }}</strong> 项权限</span>
          <div>
            <el-button @click="permDialog.visible = false">取消</el-button>
            <el-button type="primary" :loading="permDialog.submitting" @click="handleSavePermissions">保存权限</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- ══ 角色用户列表弹窗 ══ -->
    <el-dialog v-model="usersDialog.visible" :title="`「${usersDialog.roleName}」的用户列表`"
      width="760px" :close-on-click-modal="false" class="dark-dialog">
      <el-table :data="usersDialog.users" v-loading="usersDialog.loading" stripe
        style="width:100%" max-height="420"
        :header-cell-style="{ background:'#141830', color:'#7eb8d4', fontWeight:'600', fontSize:'13px' }">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="用户" min-width="140">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar-sm">{{ (row.realName || '?')[0] }}</div>
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
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small" effect="dark">
              {{ row.status === 0 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  Search,
  Refresh,
  Plus,
  Key,
  Timer,
  Setting,
  CircleCheck,
  CircleClose,
  User,
  Edit,
  Lock,
  Delete,
  FolderOpened,
  Menu,
  Operation
} from '@element-plus/icons-vue'
import { useRoleManagementPage } from './use-role-management-page'

defineOptions({ name: 'RoleManagement' })

const {
  checkAll,
  currentTime,
  expandAll,
  formDialog,
  formRules,
  formatDate,
  handleAdd,
  handleAssignPermissions,
  handleCurrentChange,
  handleDelete,
  handleEdit,
  handleFormSubmit,
  handleReset,
  handleSavePermissions,
  handleSearch,
  handleSizeChange,
  handleStatusChange,
  handleViewUsers,
  hasPerm,
  loading,
  pagination,
  permDialog,
  permTreeRef,
  resetForm,
  roleFormRef,
  roleList,
  searchForm,
  stats,
  uncheckAll,
  updatePermCheckedCount,
  usersDialog
} = useRoleManagementPage()
</script>

<style scoped lang="scss">
@import './role-management.scss';
</style>
