<template>
  <el-dialog v-model="eventDialogVisibleModel" :title="currentEvent?.type" width="550px">
    <div v-if="currentEvent" class="dlg">
      <div class="info-grid">
        <div><span class="ik">姓名</span>{{ currentEvent.user }}</div>
        <div><span class="ik">部门</span>{{ currentEvent.dept }}</div>
        <div><span class="ik">位置</span>{{ currentEvent.location }}</div>
        <div><span class="ik">时间</span>{{ currentEvent.time }}</div>
        <div><span class="ik">持续</span><b :class="{ red: currentEvent.durationMinutes > 5 }">{{ currentEvent.durationMinutes }}分钟</b></div>
        <div><span class="ik">等级</span><span :class="'lv-' + currentEvent.level">{{ getLevelText(currentEvent.level) }}</span></div>
        <div><span class="ik">责任人</span>{{ currentEvent.owner || '未分派' }}</div>
        <div><span class="ik">处置时限</span>{{ currentEvent.sla || '未配置' }}</div>
      </div>
      <div class="dlg-act">
        <el-button type="danger" @click="$emit('handleEvent', currentEvent)">处理</el-button>
        <el-button type="primary" @click="$emit('showPersonFromEvent', currentEvent)">查看人员</el-button>
      </div>
    </div>
  </el-dialog>

  <el-dialog v-model="areaDialogVisibleModel" :title="(currentArea?.name || '') + ' - 详细信息'" width="520px">
    <div v-if="currentArea" class="dlg">
      <div class="dlg-cards">
        <div class="dc"><div class="dc-l">当前人数</div><div class="dc-v big">{{ currentArea.count }}人</div></div>
        <div class="dc dg" v-if="currentArea.sos > 0"><div class="dc-l">SOS</div><div class="dc-v red">{{ currentArea.sos }}</div></div>
        <div class="dc wn" v-if="currentArea.fall > 0"><div class="dc-l">跌倒</div><div class="dc-v orange">{{ currentArea.fall }}</div></div>
        <div class="dc wn" v-if="currentArea.warning > 0"><div class="dc-l">预警</div><div class="dc-v orange">{{ currentArea.warning }}</div></div>
      </div>
      <div class="dlg-act">
        <el-button type="danger" @click="$emit('showInfo', '撤离', `确认启动 ${currentArea.name} 紧急撤离？`)">启动撤离</el-button>
      </div>
    </div>
  </el-dialog>

  <el-dialog v-model="deptDialogVisibleModel" :title="(currentDept?.name || '') + ' — 部门详情'" width="580px" @open="$emit('deptDialogOpen')">
    <div v-if="currentDept" class="dlg">
      <div class="dlg-cards">
        <div class="dc"><div class="dc-l">在线/总数</div><div class="dc-v big">{{ currentDept.online }}/{{ currentDept.total }}</div></div>
        <div class="dc"><div class="dc-l">健康率</div><div class="dc-v">{{ currentDept.healthRate }}%</div></div>
        <div class="dc dg" v-if="currentDept.sos > 0"><div class="dc-l">SOS</div><div class="dc-v red">{{ currentDept.sos }}</div></div>
      </div>
      <div class="dlg-ai-section">
        <div class="dlg-ai-hd">
          <span class="dlg-ai-title">AI 部门健康分析</span>
          <button class="dlg-ai-btn" :disabled="deptAiLoading" @click="$emit('handleDeptAi', false)">
            {{ deptAiLoading ? '分析中…' : (deptAiReport ? '刷新' : '生成分析') }}
          </button>
        </div>
        <div v-if="deptAiLoading" class="dlg-ai-loading">DeepSeek 正在分析，约15-30秒…</div>
        <div v-else-if="deptAiReport" class="dlg-ai-content" v-html="deptAiRendered"></div>
        <div v-else class="dlg-ai-empty">点击「生成分析」获取 AI 部门健康报告</div>
        <div v-if="deptAiTime" class="dlg-ai-ts">生成于 {{ deptAiTime }}</div>
      </div>
    </div>
  </el-dialog>

  <el-dialog v-model="broadcastDialogVisibleModel" title="紧急广播" width="440px">
    <el-input v-model="broadcastContentModel" type="textarea" :rows="4" placeholder="请输入广播内容…" maxlength="200" show-word-limit />
    <template #footer>
      <el-button @click="broadcastDialogVisibleModel = false">取消</el-button>
      <el-button type="warning" @click="$emit('confirmBroadcast')">确认广播</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="contactDialogVisibleModel" title="紧急联系人" width="420px">
    <div style="text-align:center;color:rgba(255,255,255,.4);padding:24px 0;font-size:13px">暂无联系人</div>
  </el-dialog>

  <el-dialog v-model="infoDialogVisibleModel" :title="infoDialogTitle" width="480px">
    <div class="info-html" v-html="infoDialogContent"></div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, type PropType } from 'vue'

interface DialogEvent {
  type?: string
  user?: string
  dept?: string
  location?: string
  time?: string
  durationMinutes: number
  level?: string
  owner?: string
  sla?: string
  [key: string]: unknown
}

interface DialogArea {
  name?: string
  count: number
  sos: number
  fall: number
  warning: number
  [key: string]: unknown
}

interface DialogDepartment {
  name?: string
  online: number
  total: number
  healthRate: number
  sos: number
  [key: string]: unknown
}

const props = defineProps({
  eventDialogVisible: { type: Boolean, default: false },
  areaDialogVisible: { type: Boolean, default: false },
  deptDialogVisible: { type: Boolean, default: false },
  broadcastDialogVisible: { type: Boolean, default: false },
  contactDialogVisible: { type: Boolean, default: false },
  infoDialogVisible: { type: Boolean, default: false },
  broadcastContent: { type: String, default: '' },
  infoDialogTitle: { type: String, default: '' },
  infoDialogContent: { type: String, default: '' },
  currentEvent: { type: Object as PropType<DialogEvent | null>, default: null },
  currentArea: { type: Object as PropType<DialogArea | null>, default: null },
  currentDept: { type: Object as PropType<DialogDepartment | null>, default: null },
  deptAiReport: { type: String, default: '' },
  deptAiRendered: { type: String, default: '' },
  deptAiLoading: { type: Boolean, default: false },
  deptAiTime: { type: String, default: '' }
})

const emit = defineEmits<{
  'update:eventDialogVisible': [value: boolean]
  'update:areaDialogVisible': [value: boolean]
  'update:deptDialogVisible': [value: boolean]
  'update:broadcastDialogVisible': [value: boolean]
  'update:contactDialogVisible': [value: boolean]
  'update:infoDialogVisible': [value: boolean]
  'update:broadcastContent': [value: string]
  handleEvent: [event: DialogEvent]
  showPersonFromEvent: [event: DialogEvent]
  showInfo: [title: string, content: string]
  deptDialogOpen: []
  handleDeptAi: [force: boolean]
  confirmBroadcast: []
}>()

const eventDialogVisibleModel = computed({
  get: () => props.eventDialogVisible,
  set: (value) => emit('update:eventDialogVisible', value)
})
const areaDialogVisibleModel = computed({
  get: () => props.areaDialogVisible,
  set: (value) => emit('update:areaDialogVisible', value)
})
const deptDialogVisibleModel = computed({
  get: () => props.deptDialogVisible,
  set: (value) => emit('update:deptDialogVisible', value)
})
const broadcastDialogVisibleModel = computed({
  get: () => props.broadcastDialogVisible,
  set: (value) => emit('update:broadcastDialogVisible', value)
})
const contactDialogVisibleModel = computed({
  get: () => props.contactDialogVisible,
  set: (value) => emit('update:contactDialogVisible', value)
})
const infoDialogVisibleModel = computed({
  get: () => props.infoDialogVisible,
  set: (value) => emit('update:infoDialogVisible', value)
})
const broadcastContentModel = computed({
  get: () => props.broadcastContent,
  set: (value) => emit('update:broadcastContent', value)
})

const getLevelText = (level?: string) => ({ critical: '特急', high: '紧急', medium: '一般', low: '轻微' }[level || ''] || level)
</script>
