<template>
  <div :class="{'has-logo':showLogo}" @click.capture="preventNativeMenuNavigation">
    <logo v-if="showLogo" :collapse="isCollapse" />
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :background-color="variables.menuBg"
        :text-color="variables.menuText"
        :unique-opened="false"
        :active-text-color="variables.menuActiveText"
        :collapse-transition="false"
        mode="vertical"
        @select="onMenuSelect"
      >
        <sidebar-item
          v-for="route in routes"
          :key="route.path"
          :item="route"
          :base-path="route.path"
          :is-collapse="isCollapse"
        />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import Logo from './Logo.vue'
import SidebarItem from './SidebarItem.vue'
import { buildGroupedMenuRoutes } from '@/layout/menu/navigation.mjs'
import { isExternal } from '@/utils/validate'

interface SidebarState {
  opened: boolean
}

interface RootState {
  user: {
    resultAllRoutes: unknown[]
  }
  settings: {
    sidebarLogo: boolean
  }
}

const menuVariables = {
  menuBg: '#07111d',
  menuText: '#8fa7c3',
  menuActiveText: '#eef7ff'
}

const store = useStore<RootState>()
const route = useRoute()
const router = useRouter()
const sidebar = computed<SidebarState>(() => store.getters.sidebar)
const routes = computed(() => buildGroupedMenuRoutes(store.state.user.resultAllRoutes))
const activeMenu = computed(() => typeof route.meta.activeMenu === 'string' ? route.meta.activeMenu : route.path)
const showLogo = computed(() => store.state.settings.sidebarLogo)
const variables = menuVariables
const isCollapse = computed(() => !sidebar.value.opened)

function preventNativeMenuNavigation(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof Element)) return
  const anchor = target.closest('a')
  if (!anchor) return
  const href = anchor.getAttribute('href') || ''
  if (!href || isExternal(href)) return
  event.preventDefault()
}

function onMenuSelect(index: string) {
  if (!index || index.startsWith('/__nav__/')) return
  if (isExternal(index)) {
    window.open(index, '_blank', 'noopener')
    return
  }
  if (index === route.path) return
  void router.push(index).catch(() => undefined)
}
</script>

<style lang="scss" scoped>
:deep(.el-menu--collapse) {
  .menu-title {
    display: none !important;
  }

  .el-submenu__icon-arrow,
  .el-sub-menu__icon-arrow {
    display: none !important;
  }

  .el-menu-item,
  .el-submenu__title,
  .el-sub-menu__title {
    display: flex !important;
    align-items: center !important;
    justify-content: center !important;
    padding: 0 !important;
    width: 36px !important;

    .sub-el-icon,
    .svg-icon,
    svg {
      margin: 0 !important;
      font-size: 18px;
    }
  }
}

:deep(.el-menu--vertical.el-menu--collapse) {
  .el-submenu > .el-submenu__title,
  .el-sub-menu > .el-sub-menu__title {
    overflow: hidden;
    white-space: nowrap;
  }
}
</style>
