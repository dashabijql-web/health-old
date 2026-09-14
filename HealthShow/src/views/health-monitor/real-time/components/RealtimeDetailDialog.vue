<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    class="rt-dialog rt-detail-dialog"
    width="480px"
    :append-to-body="true"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:visible', $event)"
  >
    <template v-if="user">
      <div class="rt-detail-user">
        <span class="rt-detail-user-name">{{ user.userName }}</span>
        <span class="rt-detail-user-dept">{{ user.deptName || '未分组' }}</span>
        <span class="rt-detail-user-code">{{ user.userCode || '无工号' }}</span>
        <span v-if="user.imei" class="rt-detail-user-imei">{{ user.imei }}</span>
      </div>

      <div v-if="user.warningReasons?.length" class="rt-detail-reasons">
        <span v-for="reason in user.warningReasons" :key="reason">{{ reason }}</span>
      </div>

      <div v-for="group in groupedItems" :key="group.title" class="rt-detail-group">
        <div class="rt-detail-group-title">{{ group.title }}</div>
        <div class="rt-detail-grid">
          <div
            v-for="item in group.items"
            :key="item.label"
            :class="['rt-detail-card', item.status]"
          >
            <div class="rt-detail-label">{{ item.label }}</div>
            <div class="rt-detail-value" :style="{ color: item.color }">{{ item.value }}</div>
            <div v-if="item.tag" :class="['rt-detail-tag', item.tagCls]">{{ item.tag }}</div>
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="rt-detail-footer">
        <div v-if="user?.imei" class="rt-detail-actions">
          <el-button size="small" @click="$emit('sendMessage', user)">
            <el-icon><ChatDotRound /></el-icon> 发送消息
          </el-button>
          <el-button size="small" type="warning" @click="$emit('sendVoice', user)">
            <el-icon><Bell /></el-icon> 语音提醒
          </el-button>
        </div>
        <el-button type="primary" autofocus aria-label="关闭实时体征详情" @click="$emit('update:visible', false)">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, type PropType } from 'vue'
import { Bell, ChatDotRound } from '@element-plus/icons-vue'
import {
  formatRealtimeAge,
  formatRealtimeTime,
  getMetricClass,
  getRealtimeStatusLabel
} from '../realtime-helpers'

interface RealtimeDetailUser {
  userName?: string
  deptName?: string
  userCode?: string
  imei?: string
  warningReasons?: string[]
  heartRate?: number | null
  bloodOxygen?: number | null
  bloodPressureHigh?: number | null
  bloodPressureLow?: number | null
  temperature?: number | null
  pressure?: number | null
  steps?: number | null
  lastUpdate?: string
  status?: string
  dataAgeSeconds?: number
  [key: string]: unknown
}

const props = defineProps({
  visible: { type: Boolean, default: false },
  user: { type: Object as PropType<RealtimeDetailUser | null>, default: null }
})

defineEmits<{
  'update:visible': [value: boolean]
  sendMessage: [user: RealtimeDetailUser]
  sendVoice: [user: RealtimeDetailUser]
}>()

const title = computed(() => `${props.user?.userName || ''} 实时体征`)

function hasValue(v: unknown) {
  return v !== null && v !== undefined && v !== ''
}

function statusFromClass(cls: string) {
  if (cls.includes('danger')) return 'is-danger'
  if (cls.includes('warn')) return 'is-warn'
  return ''
}

function tagFromClass(cls: string, value: unknown) {
  if (!value && value !== 0) return { tag: '', tagCls: '' }
  if (cls.includes('danger')) return { tag: '异常', tagCls: 'tag-danger' }
  if (cls.includes('warn')) return { tag: '偏离', tagCls: 'tag-warn' }
  return { tag: '正常', tagCls: 'tag-ok' }
}

const groupedItems = computed(() => {
  const u = props.user
  if (!u) return []

  const hrCls = getMetricClass(u, 'heartRate')
  const spo2Cls = getMetricClass(u, 'bloodOxygen')
  const bpHCls = getMetricClass(u, 'bloodPressureHigh')
  const bpLCls = getMetricClass(u, 'bloodPressureLow')
  const tempCls = getMetricClass(u, 'temperature')
  const presCls = getMetricClass(u, 'pressure')

  return [
    {
      title: '心血管',
      items: [
        {
          label: '心率',
          value: hasValue(u.heartRate) ? `${u.heartRate} bpm` : '--',
          color: hrCls.includes('danger') ? '#ff5252' : hrCls.includes('warn') ? '#ffd200' : '#52c41a',
          status: statusFromClass(hrCls),
          ...tagFromClass(hrCls, u.heartRate)
        },
        {
          label: '血氧',
          value: hasValue(u.bloodOxygen) ? `${u.bloodOxygen}%` : '--',
          color: spo2Cls.includes('danger') ? '#ff5252' : spo2Cls.includes('warn') ? '#ffd200' : '#52c41a',
          status: statusFromClass(spo2Cls),
          ...tagFromClass(spo2Cls, u.bloodOxygen)
        },
        {
          label: '收缩压',
          value: hasValue(u.bloodPressureHigh) ? `${u.bloodPressureHigh} mmHg` : '--',
          color: bpHCls.includes('danger') ? '#ff5252' : bpHCls.includes('warn') ? '#ffd200' : '#a78bfa',
          status: statusFromClass(bpHCls),
          ...tagFromClass(bpHCls, u.bloodPressureHigh)
        },
        {
          label: '舒张压',
          value: hasValue(u.bloodPressureLow) ? `${u.bloodPressureLow} mmHg` : '--',
          color: bpLCls.includes('danger') ? '#ff5252' : bpLCls.includes('warn') ? '#ffd200' : '#a78bfa',
          status: statusFromClass(bpLCls),
          ...tagFromClass(bpLCls, u.bloodPressureLow)
        }
      ]
    },
    {
      title: '体征 & 压力',
      items: [
        {
          label: '体温',
          value: hasValue(u.temperature) ? `${u.temperature}°C` : '--',
          color: tempCls.includes('danger') ? '#ff5252' : tempCls.includes('warn') ? '#ffd200' : '#52c41a',
          status: statusFromClass(tempCls),
          ...tagFromClass(tempCls, u.temperature)
        },
        {
          label: '压力指数',
          value: hasValue(u.pressure) ? `${u.pressure}` : '--',
          color: presCls.includes('danger') ? '#ff5252' : presCls.includes('warn') ? '#ffd200' : '#fb923c',
          status: statusFromClass(presCls),
          ...tagFromClass(presCls, u.pressure)
        }
      ]
    },
    {
      title: '活动与数据',
      items: [
        {
          label: '步数',
          value: hasValue(u.steps) ? `${u.steps} 步` : '--',
          color: '#22c55e',
          status: '',
          tag: '', tagCls: ''
        },
        {
          label: '最近上报',
          value: formatRealtimeTime(u.lastUpdate),
          color: '#38bdf8',
          status: '',
          tag: '', tagCls: ''
        },
        {
          label: '数据状态',
          value: u.status === 'stale'
            ? `${formatRealtimeAge(u.dataAgeSeconds)}未更新`
            : getRealtimeStatusLabel(u.status),
          color: '#a8c5e6',
          status: '',
          tag: '', tagCls: ''
        }
      ]
    }
  ]
})
</script>

<style scoped>
.rt-detail-user {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 14px;
  margin-bottom: 14px;
  background: rgba(0, 212, 255, 0.06);
  border: 1px solid rgba(0, 212, 255, 0.15);
  border-radius: 8px;
}

.rt-detail-reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: -4px 0 14px;
}

.rt-detail-reasons span {
  padding: 3px 8px;
  border: 1px solid rgba(245, 108, 108, 0.35);
  border-radius: 4px;
  background: rgba(245, 108, 108, 0.1);
  color: #ff8a8a;
  font-size: 12px;
}

.rt-detail-user-name {
  font-size: 15px;
  font-weight: 700;
  color: #e0f0ff;
}

.rt-detail-user-dept {
  font-size: 12px;
  color: #7eb8d4;
}

.rt-detail-user-code {
  font-size: 12px;
  color: #8ba6c8;
  font-family: Consolas, monospace;
}

.rt-detail-user-imei {
  font-size: 11px;
  color: #5a6a80;
  font-family: Consolas, monospace;
}

.rt-detail-group {
  margin-bottom: 14px;
}

.rt-detail-group-title {
  font-size: 12px;
  color: #5a8ab5;
  font-weight: 600;
  margin-bottom: 8px;
  padding-left: 2px;
  letter-spacing: 0.5px;
}

.rt-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.rt-detail-card {
  background: rgba(0, 212, 255, 0.04);
  border: 1px solid rgba(0, 212, 255, 0.12);
  border-radius: 8px;
  padding: 10px 14px;
  position: relative;
}

.rt-detail-card.is-danger {
  background: rgba(245, 108, 108, 0.08);
  border-color: rgba(245, 108, 108, 0.25);
}

.rt-detail-card.is-warn {
  background: rgba(255, 210, 0, 0.06);
  border-color: rgba(255, 210, 0, 0.2);
}

.rt-detail-label {
  font-size: 11px;
  color: #8ba6c8;
  margin-bottom: 4px;
}

.rt-detail-value {
  font-size: 20px;
  font-weight: 700;
  font-family: Consolas, monospace;
  line-height: 1.2;
}

.rt-detail-tag {
  position: absolute;
  top: 8px;
  right: 10px;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
}

.tag-ok {
  color: #22c55e;
  background: rgba(34, 197, 94, 0.12);
}

.tag-warn {
  color: #ffd200;
  background: rgba(255, 210, 0, 0.12);
}

.tag-danger {
  color: #ff5252;
  background: rgba(255, 82, 82, 0.12);
}

.rt-detail-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.rt-detail-actions {
  display: flex;
  gap: 8px;
}
</style>
