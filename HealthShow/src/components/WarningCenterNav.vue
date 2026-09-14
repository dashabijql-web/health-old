<template>
  <div class="wcn-wrap">
    <div class="wcn-head">
      <div>
        <div class="wcn-title">风险事件中心</div>
        <div class="wcn-sub">体征预警、设备报警与趋势风险共用处置入口</div>
      </div>
    </div>
    <div class="wcn-tabs">
      <router-link
        v-for="item in items"
        :key="item.path"
        :to="item.path"
        :class="['wcn-tab', isActive(item) ? 'is-active' : '']"
      >
        <span class="wcn-icon">
          <el-icon><component :is="item.icon" /></el-icon>
        </span>
        <span>{{ item.label }}</span>
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { useRoute } from 'vue-router'
import { Bell, Document, Setting, TrendCharts, WarningFilled } from '@element-plus/icons-vue'

interface WarningCenterNavItem {
  path: string
  label: string
  icon: Component
  matches: string[]
}

const route = useRoute()

const items: WarningCenterNavItem[] = [
  { path: '/health-monitor/risk-warning', label: '总览', icon: TrendCharts, matches: ['/health-monitor/risk-warning'] },
  { path: '/alert-management/notifications', label: '待办事件', icon: Bell, matches: ['/alert-management/notifications'] },
  { path: '/alert-management/records', label: '处置记录', icon: Document, matches: ['/alert-management/records'] },
  { path: '/alert-management/config', label: '规则配置', icon: Setting, matches: ['/alert-management/config'] },
  { path: '/alert-management/sos', label: '紧急事件', icon: WarningFilled, matches: ['/alert-management/sos'] }
]

function isActive(item: WarningCenterNavItem): boolean {
  return item.matches.some(prefix => route.path.startsWith(prefix))
}
</script>

<style scoped lang="scss">
.wcn-wrap {
  margin-bottom: 12px;
  padding: 14px 16px 16px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-soft);
  background: var(--bg-surface);
  box-shadow: var(--shadow-soft), var(--shadow-inner);
}

.wcn-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.wcn-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: 0.06em;
}

.wcn-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.wcn-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.wcn-tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  border-radius: 999px;
  border: 1px solid rgba(95, 189, 255, 0.16);
  background: rgba(12, 24, 42, 0.72);
  color: #98b2cc;
  text-decoration: none;
  font-size: 13px;
  transition: all 0.2s ease;

  &:hover {
    color: #eaf5ff;
    border-color: rgba(95, 189, 255, 0.28);
    background: rgba(62, 183, 255, 0.08);
    transform: translateY(-1px);
  }
}

.wcn-tab.is-active {
  color: #06111f;
  background: linear-gradient(135deg, #3eb7ff, #7dcfff);
  border-color: transparent;
  box-shadow: 0 10px 24px rgba(62, 183, 255, 0.16);
}

.wcn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  line-height: 1;
}

@media (max-width: 768px) {
  .wcn-wrap {
    padding: 12px;
  }

  .wcn-sub {
    display: none;
  }

  .wcn-tabs {
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-bottom: 2px;
  }

  .wcn-tab {
    flex: 0 0 auto;
    white-space: nowrap;
  }
}
</style>
