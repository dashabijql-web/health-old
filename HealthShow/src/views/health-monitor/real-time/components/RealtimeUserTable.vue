<template>
  <div class="rt-panel rt-table-panel">
    <div class="rt-ph">
      <div class="rt-ph-left">
        <span class="rt-ph-dot"></span>
        <span class="rt-ph-title">在线人员状态</span>
        <span class="rt-badge-online">{{ totalCount }} 人</span>
      </div>
      <div class="rt-ph-right">
        <el-input
          v-model="searchName"
          placeholder="姓名/工号"
          clearable
          class="rt-inp"
          @clear="$emit('search')"
          @input="$emit('search')"
          @keyup.enter="$emit('search')"
        />
        <el-select
          v-model="searchDept"
          placeholder="全部部门"
          clearable
          class="rt-sel"
          @change="$emit('search')"
        >
          <el-option v-for="dept in deptList" :key="dept" :label="dept" :value="dept" />
        </el-select>
        <el-select
          v-model="searchStatus"
          placeholder="全部状态"
          clearable
          class="rt-sel-sm"
          @change="$emit('search')"
        >
          <el-option label="当前异常" value="warning" />
          <el-option label="状态正常" value="normal" />
          <el-option label="数据陈旧" value="stale" />
          <el-option label="体征待补" value="no_data" />
        </el-select>
        <button class="rt-btn rt-btn-g" title="重置" @click="$emit('reset')">
          <el-icon><RefreshLeft /></el-icon>
        </button>
        <button
          v-if="!isMobile"
          class="rt-btn rt-btn-g"
          :title="autoScrollEnabled ? '暂停滚动' : '开启滚动'"
          @click="$emit('toggleAutoScroll')"
        >
          <el-icon><component :is="autoScrollIcon" /></el-icon>
        </button>
      </div>
    </div>

    <div
      class="rt-tbl-wrap"
      @mouseenter="$emit('pauseAutoScroll')"
      @mouseleave="$emit('resumeAutoScroll')"
    >
      <div v-if="isMobile" class="rt-mobile-list">
        <div
          v-for="row in paginatedUserList"
          :key="row.imei || row.userCode || row.userName"
          role="button"
          tabindex="0"
          :class="['rt-mobile-card', `is-${row.status || 'normal'}`]"
          @click="$emit('showUserDetail', row)"
          @keydown.enter.prevent="$emit('showUserDetail', row)"
          @keydown.space.prevent="$emit('showUserDetail', row)"
        >
          <span class="rt-mobile-card-main">
            <span class="rt-mobile-name">{{ row.userName || '--' }}</span>
            <span class="rt-mobile-meta">{{ row.deptName || '未分组' }} · {{ row.userCode || '无工号' }}</span>
          </span>
          <span class="rt-mobile-vitals">
            <template v-if="row.status === 'warning' && row.warningReasons?.length">
              <span
                v-for="reason in row.warningReasons.slice(0, 2)"
                :key="reason"
                :class="row.severity === 'danger' ? 'c-danger' : 'c-warn'"
              >{{ reason }}</span>
            </template>
            <template v-else-if="row.status === 'stale'">
              <span class="c-warn">{{ formatRealtimeAge(row.dataAgeSeconds) }}未更新</span>
            </template>
            <template v-else-if="row.status === 'no_data'">
              <span class="c-dim">暂无有效体征</span>
            </template>
            <template v-else>
              <span :class="getMetricClass(row, 'heartRate')">心率 {{ row.heartRate || '--' }}</span>
              <span :class="getMetricClass(row, 'bloodOxygen')">血氧 {{ row.bloodOxygen ? `${row.bloodOxygen}%` : '--' }}</span>
            </template>
            <span :class="['rt-status', `st-${row.status || 'normal'}`]">
              {{ getRealtimeStatusLabel(row.status) }}
            </span>
          </span>
          <span class="rt-mobile-actions" v-if="row.imei">
            <button class="rt-msg-btn" title="文字消息" aria-label="发送文字消息" @click.stop="$emit('sendMessage', row)">
              <el-icon><ChatDotRound /></el-icon>
            </button>
            <button class="rt-msg-btn rt-voice-btn" title="语音提醒" aria-label="发送语音提醒" @click.stop="$emit('sendVoice', row)">
              <el-icon><Bell /></el-icon>
            </button>
          </span>
        </div>
      </div>
      <el-table
        v-else
        :data="paginatedUserList"
        v-loading="isLoading"
        element-loading-background="rgba(10,30,61,0.8)"
        element-loading-text="加载中..."
        :height="isMobile ? undefined : '100%'"
        style="width: 100%"
        :header-cell-style="tblHeadStyle"
        :cell-style="tblCellStyle"
        :row-class-name="getRealtimeRowClass"
        @row-click="$emit('showUserDetail', $event)"
      >
        <el-table-column prop="userName" label="姓名" width="80" align="center">
          <template #default="{ row }">
            <span class="c-name">{{ row.userName || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="userCode" label="工号" width="90" align="center">
          <template #default="{ row }">
            <span class="c-code">{{ row.userCode || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="部门" min-width="76" align="center">
          <template #default="{ row }">
            <span class="c-dept">{{ row.deptName || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="heartRate" label="心率" width="68" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="!row.heartRate" content="设备暂未上报该项数据" placement="top" :show-after="500">
              <span class="c-na">--</span>
            </el-tooltip>
            <span v-else :class="getMetricClass(row, 'heartRate')">{{ row.heartRate }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="bloodOxygen" label="血氧(%)" width="76" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="!row.bloodOxygen" content="设备暂未上报该项数据" placement="top" :show-after="500">
              <span class="c-na">--</span>
            </el-tooltip>
            <span v-else :class="getMetricClass(row, 'bloodOxygen')">{{ row.bloodOxygen }}%</span>
          </template>
        </el-table-column>
        <el-table-column prop="temperature" label="体温(°C)" width="80" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="!row.temperature" content="设备暂未上报该项数据" placement="top" :show-after="500">
              <span class="c-na">--</span>
            </el-tooltip>
            <span v-else :class="getMetricClass(row, 'temperature')">{{ row.temperature }}°</span>
          </template>
        </el-table-column>
        <el-table-column label="血压" width="90" align="center">
          <template #default="{ row }">
            <template v-if="row.bloodPressureHigh || row.bloodPressureLow">
              <span :class="getMetricClass(row, 'bloodPressureHigh')">{{ row.bloodPressureHigh || '--' }}</span>
              <span class="c-bp-sep">/</span>
              <span :class="getMetricClass(row, 'bloodPressureLow')">{{ row.bloodPressureLow || '--' }}</span>
            </template>
            <el-tooltip v-else content="设备暂未上报该项数据" placement="top" :show-after="500">
              <span class="c-na">--</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="pressure" label="压力" width="68" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.pressure == null" content="设备暂未上报该项数据" placement="top" :show-after="500">
              <span class="c-na">--</span>
            </el-tooltip>
            <span v-else :class="getMetricClass(row, 'pressure')">{{ row.pressure }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="68" align="center">
          <template #default="{ row }">
            <span :class="['rt-status', `st-${row.status || 'normal'}`]">
              {{ getRealtimeStatusLabel(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="lastUpdate" label="时间" width="88" align="center">
          <template #default="{ row }">
            <el-tooltip :content="formatRealtimeFullTime(row.lastUpdate)" placement="top" :show-after="400">
              <span class="c-time">{{ formatRealtimeTime(row.lastUpdate) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type PropType } from 'vue'
import {
  Bell,
  ChatDotRound,
  RefreshLeft,
  VideoPause,
  VideoPlay
} from '@element-plus/icons-vue'
import {
  formatRealtimeAge,
  formatRealtimeFullTime,
  formatRealtimeTime,
  getMetricClass,
  getRealtimeRowClass,
  getRealtimeStatusLabel
} from '../realtime-helpers'

interface RealtimeSearchForm {
  name: string
  dept: string
  status: string
}

interface RealtimeUser {
  imei?: string
  userCode?: string
  userName?: string
  deptName?: string
  status?: string
  severity?: string
  warningReasons?: string[]
  dataAgeSeconds?: number
  heartRate?: number | null
  bloodOxygen?: number | null
  bloodPressureHigh?: number | null
  bloodPressureLow?: number | null
  temperature?: number | null
  pressure?: number | null
  lastUpdate?: string
  [key: string]: unknown
}

type TableStyle = Record<string, string | number>

const props = defineProps({
  totalCount: { type: Number, default: 0 },
  deptList: { type: Array as PropType<string[]>, default: () => [] },
  searchForm: { type: Object as PropType<RealtimeSearchForm>, required: true },
  autoScrollEnabled: { type: Boolean, default: true },
  isMobile: { type: Boolean, default: false },
  paginatedUserList: { type: Array as PropType<RealtimeUser[]>, default: () => [] },
  isLoading: { type: Boolean, default: false },
  currentPage: { type: Number, default: 1 },
  pageSize: { type: Number, default: 50 },
  tblHeadStyle: { type: Object as PropType<TableStyle>, required: true },
  tblCellStyle: { type: Object as PropType<TableStyle>, required: true }
})

const emit = defineEmits<{
  'update:searchForm': [value: RealtimeSearchForm]
  search: []
  reset: []
  toggleAutoScroll: []
  pauseAutoScroll: []
  resumeAutoScroll: []
  showUserDetail: [user: RealtimeUser]
  sendMessage: [user: RealtimeUser]
  sendVoice: [user: RealtimeUser]
}>()

function updateSearchForm(patch: Partial<RealtimeSearchForm>) {
  emit('update:searchForm', { ...props.searchForm, ...patch })
}

const searchName = computed({
  get: () => props.searchForm.name,
  set: (name) => updateSearchForm({ name })
})

const searchDept = computed({
  get: () => props.searchForm.dept,
  set: (dept) => updateSearchForm({ dept })
})

const searchStatus = computed({
  get: () => props.searchForm.status,
  set: (status) => updateSearchForm({ status })
})

const autoScrollIcon = computed(() => props.autoScrollEnabled ? VideoPause : VideoPlay)
</script>
