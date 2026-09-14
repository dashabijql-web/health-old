<template>
  <div :class="classObj" class="app-wrapper">
    <div class="app-wrapper__bg"></div>
    <div v-if="device === 'mobile' && sidebarState.opened" class="drawer-bg" @click="handleClickOutside" />
    <!-- Keep the global sidebar available on the dashboard as well. The
         unified-control page owns its dense header, so only Navbar is hidden
         there to avoid rendering two competing top bars. -->
    <Sidebar class="sidebar-container" />
    <div class="main-container">
      <div v-show="!isUnifiedControl" :class="{ 'fixed-header': fixedHeader }">
        <Navbar />
      </div>
      <AppMain />
    </div>
    <MobileBottomNav v-if="!isUnifiedControl" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeMount, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useStore } from 'vuex'
import { Navbar, Sidebar, AppMain, MobileBottomNav } from './components'

defineOptions({ name: 'Layout' })

type DeviceType = 'desktop' | 'mobile'

interface SidebarState {
  opened: boolean
  withoutAnimation: boolean
}

interface RootState {
  app: {
    sidebar: SidebarState
    device: DeviceType
  }
  settings: {
    fixedHeader: boolean
  }
}

const MOBILE_BREAKPOINT = 992
const store = useStore<RootState>()
const route = useRoute()

const sidebarState = computed(() => store.state.app.sidebar)
const device = computed(() => store.state.app.device)
const fixedHeader = computed(() => store.state.settings.fixedHeader)
const isUnifiedControl = computed(() => route.path === '/health-monitor/dashboard')
const classObj = computed(() => ({
  hideSidebar: !sidebarState.value.opened,
  openSidebar: sidebarState.value.opened,
  withoutAnimation: sidebarState.value.withoutAnimation,
  mobile: device.value === 'mobile',
  'unified-control-shell': isUnifiedControl.value
}))

function isMobile() {
  return document.body.getBoundingClientRect().width - 1 < MOBILE_BREAKPOINT
}

function closeSidebar(withoutAnimation: boolean) {
  void store.dispatch('app/closeSideBar', { withoutAnimation })
}

function openDashboardSidebar() {
  if (device.value === 'desktop' && isUnifiedControl.value && !sidebarState.value.opened) {
    void store.dispatch('app/toggleSideBar')
  }
}

function handleClickOutside() {
  closeSidebar(false)
}

function handleResize() {
  if (document.hidden) return

  const nextDevice: DeviceType = isMobile() ? 'mobile' : 'desktop'
  void store.dispatch('app/toggleDevice', nextDevice)
  if (nextDevice === 'mobile') {
    closeSidebar(true)
  } else {
    openDashboardSidebar()
  }
}

watch(
  () => route.fullPath,
  () => {
    if (device.value === 'mobile' && sidebarState.value.opened) {
      closeSidebar(false)
    } else {
      openDashboardSidebar()
    }
  }
)

onBeforeMount(() => window.addEventListener('resize', handleResize))
onMounted(() => {
  if (isMobile()) {
    void store.dispatch('app/toggleDevice', 'mobile')
    closeSidebar(true)
  } else {
    openDashboardSidebar()
  }
})
onBeforeUnmount(() => window.removeEventListener('resize', handleResize))
</script>

<style lang="scss" scoped>
@import "@/styles/mixin.scss";
@import "@/styles/variables.scss";

.app-wrapper {
  @include clearfix;
  position: relative;
  min-height: 100%;
  width: 100%;

  &.mobile.openSidebar {
    position: fixed;
    top: 0;
  }
}

.app-wrapper__bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at top left, rgba(62, 183, 255, 0.08), transparent 22%),
    radial-gradient(circle at bottom right, rgba(54, 211, 153, 0.06), transparent 26%);
}

.drawer-bg {
  background: rgba(3, 8, 18, 0.62);
  backdrop-filter: blur(6px);
  width: 100%;
  top: 0;
  height: 100%;
  position: absolute;
  z-index: 999;
}

.main-container {
  position: relative;
  z-index: 1;
}

.fixed-header {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 9;
  width: calc(100% - #{$sideBarWidth});
  transition: width 0.28s;
}

.hideSidebar .fixed-header {
  width: calc(100% - 36px);
}

.app-wrapper.unified-control-shell .fixed-header {
  display: none;
}

.mobile .fixed-header {
  width: 100%;
}

:global(#app .app-wrapper.unified-control-shell .app-wrapper__bg) {
  display: none;
}

:global(#app .app-wrapper.unified-control-shell .app-main) {
  min-height: 100vh;
  padding-top: 0;
}
</style>
