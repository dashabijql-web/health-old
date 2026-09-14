// 路由总入口：
// - Hash 模式，避免本地和内网环境下的服务器重写依赖
// - 业务路由静态注册，减少首跳竞态和 /404 类问题

import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '@/utils/auth'
import appRoutes from './app-routes.mjs'
import Layout from './layout-component.mjs'

function resolveRootEntry() {
  return getToken() ? '/health-monitor/dashboard' : '/login'
}

// Keep one Layout instance mounted for the entire authenticated application.
// The route definitions still retain their module-level Layout components for
// permissions/menu metadata, but Router registration strips those wrappers so
// switching between /health-monitor, /alert-management and /admin does not
// recreate the sidebar and navbar.
const layoutChildren = appRoutes.map(({ component, ...route }) => route)
const layoutRoute = {
  path: '/',
  component: Layout,
  children: [
    {
      path: '',
      redirect: resolveRootEntry,
      hidden: true
    },
    ...layoutChildren
  ]
}


/**
 * 静态路由配置（所有用户都有的路由，无需权限控制）
 *
 * 路由结构说明：
 *   /login          → 登录页（hidden=true，不在侧边栏显示）
 *   /404            → 404 页面（hidden=true）
 *   /               → 重定向到 /health-monitor/dashboard
 *   /health-monitor → 健康监测模块（从 health-monitor.mjs 引入）
 *   /user-management → 用户管理模块
 *   /permission-management → 权限管理模块
 *   /:pathMatch(.*) → 通配符，任何未匹配路径都重定向到 /404
 */
export const constantRoutes = [
  // 登录页（不在菜单中显示）
  {
    path: '/login',
    component: () => import('@/views/login/index.vue'),
    hidden: true  // 自定义属性：不在侧边栏中渲染此路由
  },

  // 404 页面（不在菜单中显示）
  {
    path: '/404',
    component: () => import('@/views/404.vue'),
    hidden: true
  },

  layoutRoute
]

/**
 * 创建路由实例
 *
 * history：使用 Hash 模式（URL 包含 #）
 * scrollBehavior：每次路由切换后滚动到页面顶部
 */
const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),  // 切换路由时自动滚动到顶部
  routes: constantRoutes
})

// Keep lazy-load failures visible in the browser console instead of making a
// route switch look like a silent full-page refresh.
router.onError((error, to) => {
  console.error('[router] navigation failed', {
    path: to?.fullPath,
    message: error?.message,
    error
  })
})

/**
 * 动态路由（本项目暂未使用）
 * 保留定义是为了与其他项目的架构保持一致，方便后续扩展
 */
export const asyncRoutes = []  // 需要根据用户权限动态加载的路由
export const anyRoutes = []    // 所有用户都有但需要放在动态路由后面的路由

/**
 * 重置路由函数
 *
 * 在某些架构中，退出登录后需要删除动态添加的路由（防止权限泄露）。
 * 本项目全部使用 constantRoutes（静态路由），退出登录不需要重置路由，
 * 所以这是一个空实现（no-op）。
 *
 * 这样做还避免了一个坑：如果删除路由，下次登录时路由丢失，
 * 需要重新添加，实现更复杂。静态路由方案更简单可靠。
 */
export function resetRouter() {
  // no-op：本项目使用静态路由，无需重置
}

export default router
