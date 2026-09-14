<template>
  <header class="rt-hd">
    <div class="rt-hd-bar">
      <div class="rt-hd-bar-left">
        <span class="rt-live-dot"></span>
        <span class="rt-hd-bar-title">实时健康监控</span>
        <span class="rt-hd-scope">最近 {{ summary.onlineWindowMinutes || 15 }} 分钟有上报</span>
      </div>

      <div class="rt-hd-bar-kpis">
        <div class="rt-hd-kpi">
          <span class="rt-hd-kpi-val kpi-primary">{{ summary.onlineCount || 0 }}</span>
          <span class="rt-hd-kpi-label">在线人员</span>
        </div>
        <div class="rt-hd-kpi-sep"></div>
        <div class="rt-hd-kpi">
          <span class="rt-hd-kpi-val kpi-danger">{{ summary.warningCount || 0 }}</span>
          <span class="rt-hd-kpi-label">当前异常</span>
        </div>
        <div class="rt-hd-kpi-sep"></div>
        <div class="rt-hd-kpi">
          <span class="rt-hd-kpi-val kpi-warning">{{ dataIssueCount }}</span>
          <span class="rt-hd-kpi-label">数据待补</span>
        </div>
      </div>

      <div class="rt-hd-refresh" :class="{ 'is-error': refreshError, 'is-stale': stale }">
        <button
          type="button"
          class="rt-icon-btn"
          :disabled="loading"
          title="立即刷新"
          aria-label="立即刷新实时数据"
          @click="$emit('refresh')"
        >
          <el-icon :class="{ 'is-spinning': loading }"><Refresh /></el-icon>
        </button>
        <span>{{ refreshLabel }}</span>
      </div>
    </div>

    <div v-if="refreshError || stale" class="rt-data-notice" role="status">
      {{ refreshError || '数据服务异常，当前显示缓存' }}
    </div>
  </header>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import { Refresh } from '@element-plus/icons-vue'

interface RealtimeSummary {
  onlineWindowMinutes?: number
  onlineCount?: number
  warningCount?: number
  [key: string]: unknown
}

defineProps({
  summary: { type: Object as PropType<RealtimeSummary>, default: () => ({}) },
  dataIssueCount: { type: Number, default: 0 },
  refreshLabel: { type: String, default: '' },
  refreshError: { type: String, default: '' },
  stale: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

defineEmits<{
  refresh: []
}>()
</script>
