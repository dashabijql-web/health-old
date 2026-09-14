<template>
  <div class="device-management-container">
    <header class="dm-hd">
      <div class="dm-hd-left">
        <span class="dm-live-dot"></span>
        <h1 class="dm-hd-title">设备管理</h1>
      </div>
      <div class="dm-hd-kpis">
        <div class="dm-kpi"><span class="dm-kpi-n">{{ deviceStats.total }}</span><span class="dm-kpi-l">设备总数</span></div>
        <div class="dm-kpi"><span class="dm-kpi-n success">{{ deviceStats.online }}</span><span class="dm-kpi-l">在线</span></div>
        <div class="dm-kpi"><span class="dm-kpi-n warning">{{ deviceStats.bound }}</span><span class="dm-kpi-l">已绑定</span></div>
        <div class="dm-kpi"><span class="dm-kpi-n info">{{ deviceStats.bufferTotal }}</span><span class="dm-kpi-l">缓冲数据</span></div>
      </div>
      <div class="dm-hd-time"><el-icon><Timer /></el-icon>{{ currentTime }}</div>
    </header>

    <!-- 设备列表 -->
    <div class="panel table-panel">
      <div class="panel-header">
        <div class="panel-title"><span class="title-bar"></span>设备列表</div>
        <div class="panel-header-right">
          <el-input v-model="searchImei" placeholder="搜索IMEI/用户" clearable size="small"
            class="search-input" @clear="resetFilters" @keyup.enter="pagination.page = 1">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="filterOnline" placeholder="在线状态" clearable size="small" class="filter-select" @change="pagination.page = 1">
            <el-option label="在线" :value="1" />
            <el-option label="离线" :value="0" />
          </el-select>
          <el-select v-model="filterBind" placeholder="绑定状态" clearable size="small" class="filter-select" @change="pagination.page = 1">
            <el-option label="已绑定" :value="true" />
            <el-option label="未绑定" :value="false" />
          </el-select>
          <el-select v-model="filterWarningVal" placeholder="预警状态" clearable size="small" class="filter-select" @change="v => { filterWarning = v === 'warning'; pagination.page = 1 }">
            <el-option label="有预警" value="warning" />
            <el-option label="无预警" value="normal" />
          </el-select>
          <el-select v-model="filterBatteryVal" placeholder="电量" clearable size="small" class="filter-select" @change="v => { filterLowBattery = v === 'low'; pagination.page = 1 }">
            <el-option label="电量不足(<20%)" value="low" />
            <el-option label="电量充足(≥20%)" value="ok" />
          </el-select>
          <el-select v-model="filterOperationalVal" placeholder="运行异常" clearable size="small" class="filter-select" @change="pagination.page = 1">
            <el-option label="全部异常" value="abnormal" />
            <el-option label="数据中断" value="dataInterrupted" />
            <el-option label="设备故障" value="faulted" />
          </el-select>
          <el-button size="small" @click="resetFilters">重置</el-button>
          <span class="total-badge">{{ filteredDeviceList.length }} / {{ deviceList.length }} 台</span>
          <el-button type="primary" size="small" :icon="Refresh" @click="refreshDevices">刷新</el-button>
        </div>
      </div>

      <div class="table-body">
      <el-table :data="paginatedDeviceList" v-loading="loading" stripe height="100%" style="width:100%"
        :header-cell-style="{ background:'#141830', color:'#7eb8d4', fontWeight:'600', fontSize:'13px' }"
        :row-style="{ background:'#1a1f3a', cursor:'pointer' }"
        @row-click="openDetail">
        <el-table-column type="index" label="#" width="50" align="center" :index="getTableIndex" />
        <el-table-column prop="imei" label="设备IMEI" width="160" />
        <el-table-column label="在线状态" width="110" align="center" sortable :sort-method="(a,b) => deviceOnlineLevel(b) - deviceOnlineLevel(a)">
          <template #default="{ row }">
            <span :class="['online-dot', deviceOnlineLevel(row) === 2 ? 'online' : deviceOnlineLevel(row) === 1 ? 'recent' : 'offline']"></span>
            <span class="online-text">{{ deviceOnlineLabel(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="绑定状态" width="100" align="center" sortable :sort-method="(a,b) => (a.bindStatus ? 1 : 0) - (b.bindStatus ? 1 : 0)">
          <template #default="{ row }">
            <el-tag :type="row.bindStatus ? 'success' : 'warning'" size="small" effect="dark">
              {{ row.bindStatus ? '已绑定' : '未绑定' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预警状态" width="100" align="center" sortable :sort-method="(a,b) => (b.hasWarning ? 1 : 0) - (a.hasWarning ? 1 : 0)">
          <template #default="{ row }">
            <el-tag v-if="row.hasWarning" type="danger" size="small" effect="dark">有预警</el-tag>
            <el-tag v-else type="success" size="small" effect="dark">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="绑定用户" min-width="150">
          <template #default="{ row }">
            <div v-if="row.bindStatus" class="user-cell">
              <div class="user-avatar">{{ (row.userName || '?')[0] }}</div>
              <div>
                <div class="user-name">{{ row.userName }}</div>
                <div class="user-dept">{{ row.deptName || '-' }}</div>
              </div>
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="缓冲数据" width="110" align="center" sortable :sort-method="(a,b) => (a.bufferCount||0) - (b.bufferCount||0)">
          <template #default="{ row }">
            <el-tag v-if="row.bufferCount > 0" type="warning" size="small" effect="dark">
              {{ row.bufferCount }} 条
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="电量" width="90" align="center" sortable :sort-method="(a,b) => (a.batteryLevel??-1) - (b.batteryLevel??-1)">
          <template #default="{ row }">
            <span v-if="row.batteryLevel != null" :class="['battery-text', row.batteryLevel < 20 ? 'battery-low' : row.batteryLevel < 50 ? 'battery-mid' : 'battery-ok']">
              {{ row.batteryLevel }}%
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="运行异常" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="deviceAbnormalTagType(row)" size="small" effect="dark">
              {{ deviceAbnormalLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastOnlineTime" label="最后在线时间" width="155" sortable :sort-method="(a,b) => new Date(a.lastOnlineTime||0) - new Date(b.lastOnlineTime||0)">
          <template #default="{ row }">{{ formatDate(row.lastOnlineTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-ops" @click.stop>
              <el-button v-if="!row.bindStatus" type="primary" link size="small" @click="handleBind(row)">
                <el-icon><Link /></el-icon> 绑定
              </el-button>
              <el-button v-else type="warning" link size="small" @click="handleUnbind(row)">
                <el-icon><Unlock /></el-icon> 解绑
              </el-button>
              <el-button v-if="row.bufferCount > 0" type="success" link size="small" @click="handleTransfer(row)">
                <el-icon><Upload /></el-icon> 转移
              </el-button>
              <el-button v-if="row.bufferCount > 0" type="danger" link size="small" @click="handleDeleteBuffer(row)">
                <el-icon><Delete /></el-icon> 清空
              </el-button>
              <el-button v-if="row.status === 1" type="info" link size="small" @click="handleSendMessage(row)">
                <el-icon><ChatDotRound /></el-icon> 发消息
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :current-page="pagination.page"
          :page-sizes="[10, 20, 50, 100]"
          :page-size="pagination.size"
          :total="filteredDeviceList.length"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 设备详情抽屉 -->
    <el-drawer v-model="detailDrawerVisible" title="设备详情" direction="rtl" size="380px" class="dark-drawer">
      <div v-if="detailDevice" class="detail-content">
        <div class="detail-header">
          <div class="detail-device-icon">
            <el-icon size="36"><Monitor /></el-icon>
          </div>
          <div class="detail-device-title">
            <div class="detail-imei">{{ detailDevice.imei }}</div>
            <div class="detail-status-row">
              <span :class="['online-dot', detailDevice.status === 1 ? 'online' : 'offline']"></span>
              <span class="online-text">{{ detailDevice.status === 1 ? '在线' : '离线' }}</span>
              <el-tag class="ml-8" :type="detailDevice.bindStatus ? 'success' : 'warning'" size="small" effect="dark">
                {{ detailDevice.bindStatus ? '已绑定' : '未绑定' }}
              </el-tag>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">设备信息</div>
          <div class="detail-row">
            <span class="detail-label">设备IMEI</span>
            <span class="detail-value mono">{{ detailDevice.imei || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">设备ID</span>
            <span class="detail-value mono">{{ detailDevice.id || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">在线状态</span>
            <span class="detail-value">
              <span :class="['online-dot', detailDevice.status === 1 ? 'online' : 'offline']"></span>
              {{ detailDevice.status === 1 ? '在线' : '离线' }}
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">最后在线</span>
            <span class="detail-value">{{ formatDate(detailDevice.lastOnlineTime) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">失联时长</span>
            <span class="detail-value">{{ formatLostDuration(detailDevice.lostDurationSeconds) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">当前异常</span>
            <span class="detail-value">
              <el-tag :type="deviceAbnormalTagType(detailDevice)" size="small" effect="dark">
                {{ deviceAbnormalLabel(detailDevice) }}
              </el-tag>
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">电量</span>
            <span class="detail-value">{{ detailDevice.batteryLevel == null ? '--' : detailDevice.batteryLevel + '%' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">缓冲数据</span>
            <span class="detail-value">
              <el-tag v-if="detailDevice.bufferCount > 0" type="warning" size="small" effect="dark">{{ detailDevice.bufferCount }} 条</el-tag>
              <span v-else class="text-muted">无</span>
            </span>
          </div>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">故障处置</div>
          <div class="detail-row">
            <span class="detail-label">故障说明</span>
            <span class="detail-value">{{ detailDevice.faultDescription || '无已登记故障' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">责任人</span>
            <span class="detail-value">{{ detailDevice.ownerName || '--' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">处置状态</span>
            <span class="detail-value">{{ detailDevice.handlingStatus === 'ASSIGNED' ? '已分派' : detailDevice.handlingStatus === 'OPEN' ? '待处理' : '已关闭' }}</span>
          </div>
          <div class="detail-actions">
            <el-button
              v-if="detailDevice.currentAbnormal !== 'FAULT'"
              type="danger"
              :disabled="!detailDevice.id"
              @click="handleMarkFault(detailDevice)"
            >登记故障</el-button>
            <el-button
              v-else
              type="success"
              @click="handleResolveFault(detailDevice)"
            >确认恢复</el-button>
          </div>
        </div>

        <div class="detail-section" v-if="detailDevice.bindStatus">
          <div class="detail-section-title">绑定员工信息</div>
          <div class="detail-row">
            <span class="detail-label">员工姓名</span>
            <span class="detail-value">{{ detailDevice.userName || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">所属部门</span>
            <span class="detail-value">{{ detailDevice.deptName || '-' }}</span>
          </div>
        </div>
        <div class="detail-section" v-else>
          <div class="detail-section-title">绑定状态</div>
          <div class="detail-empty">该设备尚未绑定员工</div>
        </div>
      </div>
    </el-drawer>

    <!-- 绑定用户对话框 -->
    <el-dialog v-model="bindDialogVisible" title="绑定用户" width="600px"
      :close-on-click-modal="false" class="dark-dialog">
      <el-form :model="bindForm" label-width="100px">
        <el-form-item label="设备IMEI">
          <el-input v-model="currentDevice.imei" disabled />
        </el-form-item>
        <el-form-item label="选择用户">
          <el-autocomplete
            v-model="bindForm.searchKey"
            :fetch-suggestions="searchUsers"
            placeholder="输入用户姓名、手机号搜索"
            clearable
            style="width: 100%"
            :trigger-on-focus="false"
            @select="handleSelectUser"
          >
            <template #default="{ item }">
              <div class="user-suggestion-item">
                <span class="user-name-suggestion">{{ item.realName }}</span>
                <span class="user-dept-suggestion">{{ item.deptName || '未分配部门' }}</span>
                <span class="user-phone-suggestion">{{ item.phone }}</span>
              </div>
            </template>
          </el-autocomplete>
          <div v-if="bindForm.userId" class="selected-user-info">
            已选择：<el-tag type="success" size="small">{{ bindForm.userName }}</el-tag>
            <span v-if="bindForm.userDept" style="margin-left: 10px; color: #7eb8d4;">{{ bindForm.userDept }}</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBind" :disabled="!bindForm.userId">确定</el-button>
      </template>
    </el-dialog>

    <!-- 发消息对话框 -->
    <el-dialog v-model="messageDialogVisible" title="发送消息到手表" width="480px"
      :close-on-click-modal="false" class="dark-dialog">
      <div class="msg-dialog-meta">
        <span class="msg-meta-label">设备 IMEI：</span>
        <span class="msg-meta-value mono">{{ currentDevice.imei }}</span>
        <span v-if="currentDevice.userName" class="msg-meta-user">（{{ currentDevice.userName }}）</span>
      </div>
      <el-form :model="messageForm" label-width="0">
        <el-form-item>
          <el-input
            v-model="messageForm.text"
            type="textarea"
            :rows="4"
            placeholder="请输入要推送到手表的消息内容（最多 50 个字符）"
            :maxlength="50"
            show-word-limit
            resize="none"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="messageDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSendMessage" :disabled="!messageForm.text.trim()">发送</el-button>
      </template>
    </el-dialog>

    <!-- 转移数据对话框 -->
    <el-dialog v-model="transferDialogVisible" title="转移缓冲数据" width="600px"
      :close-on-click-modal="false" class="dark-dialog">
      <el-alert
        title="提示：将设备的缓冲数据转移到指定用户的健康记录中"
        type="info"
        :closable="false"
        style="margin-bottom: 20px"
      />
      <el-form :model="transferForm" label-width="120px">
        <el-form-item label="设备IMEI">
          <el-input v-model="currentDevice.imei" disabled />
        </el-form-item>
        <el-form-item label="缓冲数据量">
          <el-tag type="warning">{{ currentDevice.bufferCount }} 条</el-tag>
        </el-form-item>
        <el-form-item label="目标用户">
          <el-autocomplete
            v-model="transferForm.searchKey"
            :fetch-suggestions="searchUsers"
            placeholder="输入用户姓名、手机号搜索"
            clearable
            style="width: 100%"
            :trigger-on-focus="false"
            @select="handleSelectTransferUser"
          >
            <template #default="{ item }">
              <div class="user-suggestion-item">
                <span class="user-name-suggestion">{{ item.realName }}</span>
                <span class="user-dept-suggestion">{{ item.deptName || '未分配部门' }}</span>
                <span class="user-phone-suggestion">{{ item.phone }}</span>
              </div>
            </template>
          </el-autocomplete>
          <div v-if="transferForm.userId" class="selected-user-info">
            已选择：<el-tag type="success" size="small">{{ transferForm.userName }}</el-tag>
            <span v-if="transferForm.userDept" style="margin-left: 10px; color: #7eb8d4;">{{ transferForm.userDept }}</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmTransfer" :disabled="!transferForm.userId">确定转移</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { Refresh, Monitor, Timer, CircleCheck, Connection, Document, Link, Unlock, Upload, Delete, ChatDotRound, Search } from '@element-plus/icons-vue'
import { useDeviceManagementPage } from './use-device-management-page'

const {
  bindDialogVisible,
  bindForm,
  confirmBind,
  confirmSendMessage,
  confirmTransfer,
  currentDevice,
  currentTime,
  detailDevice,
  detailDrawerVisible,
  deviceList,
  deviceAbnormalLabel,
  deviceAbnormalTagType,
  deviceOnlineLabel,
  deviceOnlineLevel,
  deviceStats,
  filteredDeviceList,
  formatDate,
  formatLostDuration,
  getTableIndex,
  handleBind,
  handleCurrentChange,
  handleDeleteBuffer,
  handleMarkFault,
  handleResolveFault,
  handleSelectTransferUser,
  handleSelectUser,
  handleSendMessage,
  handleSizeChange,
  handleTransfer,
  handleUnbind,
  loading,
  messageDialogVisible,
  messageForm,
  openDetail,
  paginatedDeviceList,
  pagination,
  refreshDevices,
  resetFilters,
  searchImei,
  searchUsers,
  filterOnline,
  filterBind,
  filterWarning,
  filterLowBattery,
  filterWarningVal,
  filterBatteryVal,
  filterOperationalVal,
  transferDialogVisible,
  transferForm
} = useDeviceManagementPage()
</script>

<style scoped lang="scss">
@import './device-management.scss';
</style>
