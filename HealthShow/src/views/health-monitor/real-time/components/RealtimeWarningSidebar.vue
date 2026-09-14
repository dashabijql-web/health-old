<template>
  <aside class="rt-aside">
    <div class="rt-aside-head">
      <span class="rt-aside-dot"></span>
      <span class="rt-aside-title">当前异常体征</span>
      <span class="rt-aside-count">{{ warningCount }}</span>
    </div>

    <div v-if="warningUsers.length" class="rt-aside-list">
      <div
        v-for="user in warningUsers"
        :key="user.imei || user.userCode || user.userName"
        :class="['rt-warn-card', user.severity === 'danger' ? 'rt-warn-card--danger' : '']"
        @click="$emit('showDetail', user)"
      >
        <div class="rt-warn-card-head">
          <span class="rt-warn-card-name">{{ user.userName }}</span>
          <span class="rt-warn-card-dept">{{ user.deptName || '未分组' }}</span>
          <span class="rt-warn-card-time">{{ formatRealtimeTime(user.lastUpdate) }}</span>
        </div>
        <div class="rt-warn-card-vitals">
          <span
            v-for="reason in user.warningReasons?.slice(0, 3) || []"
            :key="reason"
            :class="['rt-vital-tag', user.severity === 'danger' ? 'vt-danger' : 'vt-warn']"
          >{{ reason }}</span>
        </div>
        <div v-if="user.imei" class="rt-warn-card-actions">
          <button class="rt-warn-action rt-warn-action--msg" @click.stop="$emit('sendMessage', user)">
            <el-icon><ChatDotRound /></el-icon> 消息
          </button>
          <button class="rt-warn-action rt-warn-action--voice" @click.stop="$emit('sendVoice', user)">
            <el-icon><Bell /></el-icon> 语音提醒
          </button>
        </div>
      </div>
    </div>

    <div v-else class="rt-aside-empty">
      <div class="rt-aside-empty-icon">&#10003;</div>
      <div class="rt-aside-empty-text">当前未发现异常体征</div>
      <div class="rt-aside-empty-sub">仅统计最近有上报且数据新鲜的人员</div>
    </div>

    <div class="rt-aside-footer">
      <button type="button" class="rt-aside-link" :disabled="warningCount === 0" @click="$emit('showAll')">
        查看全部异常
      </button>
      <button type="button" class="rt-aside-link" @click="$emit('openWarningCenter')">
        进入预警处置
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import { Bell, ChatDotRound } from '@element-plus/icons-vue'
import { formatRealtimeTime } from '../realtime-helpers'

interface RealtimeWarningUser {
  imei?: string
  userCode?: string
  userName?: string
  deptName?: string
  severity?: string
  lastUpdate?: string
  warningReasons?: string[]
  [key: string]: unknown
}

defineProps({
  warningUsers: { type: Array as PropType<RealtimeWarningUser[]>, default: () => [] },
  warningCount: { type: Number, default: 0 }
})

defineEmits<{
  showDetail: [user: RealtimeWarningUser]
  sendMessage: [user: RealtimeWarningUser]
  sendVoice: [user: RealtimeWarningUser]
  showAll: []
  openWarningCenter: []
}>()
</script>
