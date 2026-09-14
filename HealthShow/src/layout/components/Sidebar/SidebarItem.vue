<template>
  <div v-if="!item.hidden">
    <template v-if="hasOneShowingChild(item.children,item) && (!onlyOneChild.children||onlyOneChild.noShowingChildren)&&!item.alwaysShow">
      <el-menu-item
        v-if="onlyOneChild.meta"
        :index="resolvePath(onlyOneChild.path)"
        :class="{'submenu-title-noDropdown':!isNest}"
        @mouseenter="preloadRoute(resolvePath(onlyOneChild.path))"
      >
        <item :icon="onlyOneChild.meta.icon||(item.meta&&item.meta.icon)" :title="onlyOneChild.meta.title" />
      </el-menu-item>
    </template>

    <el-sub-menu v-else ref="subMenu" :index="resolvePath(item.path)" popper-append-to-body>
      <template #title>
        <!-- 收缩时只显示图标（不用Item组件，直接渲染避免命名冲突） -->
        <el-icon v-if="isCollapse && item.meta && item.meta.icon" style="font-size:18px;">
          <component :is="item.meta.icon" />
        </el-icon>
        <item v-else-if="item.meta" :icon="item.meta && item.meta.icon" :title="item.meta.title" />
      </template>
      <sidebar-item
        v-for="child in item.children"
        :key="child.path"
        :is-nest="true"
        :item="child"
        :base-path="resolvePath(child.path)"
        :is-collapse="isCollapse"
        class="nest-menu"
      />
    </el-sub-menu>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { isExternal } from '@/utils/validate'
import Item from './Item.vue'

interface SidebarRoute {
  path: string
  hidden?: boolean
  alwaysShow?: boolean
  noShowingChildren?: boolean
  children?: SidebarRoute[]
  meta?: {
    title?: string
    icon?: string
    [key: string]: unknown
  }
  [key: string]: unknown
}

interface RootState {
  app: {
    device: string
  }
}

interface SubMenuInstance {
  handleMouseleave: (event: MouseEvent) => void
}

function joinPath(basePath: string, routePath?: string) {
  if (!routePath) return basePath
  if (routePath.startsWith('/')) return routePath
  const base = basePath.endsWith('/') ? basePath : basePath + '/'
  return base + routePath
}

defineOptions({ name: 'SidebarItem' })

const props = defineProps({
  item: { type: Object as () => SidebarRoute, required: true },
  isNest: { type: Boolean, default: false },
  basePath: { type: String, default: '' },
  isCollapse: { type: Boolean, default: false }
})

const store = useStore<RootState>()
const router = useRouter()
const onlyOneChild = ref<SidebarRoute>({ path: '', meta: {} })
const subMenu = ref<SubMenuInstance | null>(null)

function hasOneShowingChild(children: SidebarRoute[] = [], parent: SidebarRoute) {
  const showingChildren = children.filter(item => {
    if (item.hidden) return false
    onlyOneChild.value = item
    return true
  })
  if (showingChildren.length === 1) return true
  if (showingChildren.length === 0) {
    onlyOneChild.value = { ...parent, path: '', noShowingChildren: true }
    return true
  }
  return false
}

function resolvePath(routePath: string) {
  if (isExternal(routePath)) return routePath
  if (isExternal(props.basePath)) return props.basePath
  return joinPath(props.basePath, routePath)
}

function preloadRoute(path: string) {
  if (!path || isExternal(path)) return
  try {
    const view = router.resolve(path).matched.at(-1)
    const loader = view?.components?.default
    if (typeof loader === 'function') {
      const result = (loader as () => unknown)()
      if (result && typeof (result as Promise<unknown>).catch === 'function') {
        void (result as Promise<unknown>).catch(() => undefined)
      }
    }
  } catch {
    // Preload is best-effort; navigation still loads the chunk on click.
  }
}

onMounted(() => {
  // Fix the iOS menu mouseleave behavior retained from FixiOSBug.js.
  if (!subMenu.value) return
  const handleMouseleave = subMenu.value.handleMouseleave
  subMenu.value.handleMouseleave = (event: MouseEvent) => {
    if (store.state.app.device === 'mobile') return
    handleMouseleave(event)
  }
})
</script>
