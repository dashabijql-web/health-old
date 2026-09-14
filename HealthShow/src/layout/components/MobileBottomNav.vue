<template>
  <nav class="mobile-bottom-nav" v-if="isMobile">
    <router-link
      v-for="item in navItems"
      :key="item.path"
      :to="item.path"
      :class="['mbn-item', isActive(item) ? 'mbn-active' : '']"
    >
      <span class="mbn-icon">
        <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
      </span>
      <span class="mbn-label">{{ item.label }}</span>
    </router-link>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useStore } from 'vuex'
import { Bell, DataAnalysis, Monitor, Stamp, UserFilled } from '@element-plus/icons-vue'
import { buildMobileNavItems, isActiveNavigationTarget } from '@/layout/menu/navigation.mjs'

interface MobileNavItem {
  key: string
  path: string
  icon: string
  label: string
  matches: string[]
}

interface RootState {
  app: {
    device: string
  }
  user: {
    resultAllRoutes: unknown[]
  }
}

const iconMap = {
  Bell,
  DataAnalysis,
  Monitor,
  Stamp,
  UserFilled
}

defineOptions({ name: 'MobileBottomNav' })

const route = useRoute()
const store = useStore<RootState>()

const device = computed(() => store.state.app.device)
const resultAllRoutes = computed(() => store.state.user.resultAllRoutes)
const isMobile = computed(() => device.value === 'mobile')
const navItems = computed<MobileNavItem[]>(() => buildMobileNavItems(resultAllRoutes.value || []))

function isActive(item: MobileNavItem) {
  return isActiveNavigationTarget(route.path, item)
}

function resolveIcon(name: string) {
  return iconMap[name as keyof typeof iconMap] || DataAnalysis
}
</script>

<style scoped>
.mobile-bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  display: flex;
  align-items: stretch;
  padding: 8px 10px calc(env(safe-area-inset-bottom, 8px) + 8px);
  gap: 8px;
  background:
    linear-gradient(180deg, rgba(8, 16, 29, 0.76), rgba(8, 16, 29, 0.96));
  border-top: 1px solid rgba(133, 175, 220, 0.12);
  box-shadow: 0 -10px 28px rgba(2, 8, 20, 0.36);
  backdrop-filter: blur(18px);
}

.mbn-item {
  flex: 1 1 0;
  width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 56px;
  text-decoration: none;
  color: #7f96b2;
  border-radius: 16px;
  border: 1px solid transparent;
  transition: transform 0.2s ease, background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
  -webkit-tap-highlight-color: transparent;

  &:active {
    transform: scale(0.98);
  }
}

.mbn-active {
  color: #eaf5ff !important;
  background: rgba(62, 183, 255, 0.12);
  border-color: rgba(95, 189, 255, 0.18);
}

.mbn-active .mbn-icon {
  color: #3eb7ff;
}

.mbn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  line-height: 1;
}

.mbn-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
}
</style>
