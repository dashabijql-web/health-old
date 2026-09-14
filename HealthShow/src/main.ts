// Vue 应用入口：
// - 只负责创建应用实例、安装 store/router 和全局服务
// - 路由守卫与心跳检测通过副作用 import 注册

// ─── Vue 3 核心 ───────────────────────────────────────────────────
import { createApp } from 'vue'

// ─── Element Plus 服务 ────────────────────────────────────────────
// UI 组件和 v-loading 等指令通过 unplugin-vue-components 按需引入。
// 这里只保留全局消息服务，兼容历史代码中的 this.$message。
import { ElMessage } from 'element-plus/es/components/message/index'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'

// ─── 样式文件 ─────────────────────────────────────────────────────
// normalize.css：各浏览器默认样式不一致，normalize.css 将它们统一
// 例如：不同浏览器的 margin/padding 默认值不同，normalize 清除这些差异
import 'normalize.css/normalize.css'

// 项目自定义全局样式（变量、重置样式、通用工具类）
import '@/styles/index.scss'

// ─── 应用核心模块 ─────────────────────────────────────────────────
import App from './App.vue'         // 根组件（所有组件的父级容器）
import store from './store'         // Vuex 状态管理（用户信息、Token 等全局状态）
import router from './router'       // Vue Router（页面路由，URL 与组件的对应关系）

// ─── SVG 图标注册 ────────────────────────────────────────────────
// vite-plugin-svg-icons 插件：
// 将 src/icons/svg/ 目录下所有 .svg 文件打包为 SVG Sprite
// 使用方式：<svg-icon icon-class="user" />
import 'virtual:svg-icons-register'

// ─── 路由守卫（权限控制）────────────────────────────────────────────
// 引入即执行，注册全局路由守卫（beforeEach/afterEach）
// 守卫逻辑：检查 Token → 获取用户信息 → 过滤路由权限
// 必须在 router 和 store 之后引入（因为 permission.js 依赖它们）
import './permission'

// ─── 会话心跳检测 ────────────────────────────────────────────────
// 引入即执行，启动定时心跳机制（每30秒 ping /auth/info）
// 作用：后端重启后，前端自动检测并跳转到登录页，无需用户手动刷新
// 必须在 permission.js 之后引入（心跳依赖路由守卫已就绪）
import './heartbeat'

// ─── 全局自定义组件（已清理未使用的 CategorySelect / HintButton）──

// ════════════════════════════════════════════════════════════════
// 创建并配置 Vue 应用实例
// ════════════════════════════════════════════════════════════════

// 1. 创建 Vue 3 应用实例，以 App.vue 根组件为起点
const app = createApp(App)

if (typeof window !== 'undefined' && 'scrollRestoration' in window.history) {
  window.history.scrollRestoration = 'manual'
}

async function cleanupLocalDevServiceWorkers() {
  if (typeof window === 'undefined' || !('serviceWorker' in navigator)) return

  const pendingCleanup = window.__HEALTH_DEV_SW_CLEANUP__
  if (pendingCleanup && typeof pendingCleanup.then === 'function') {
    await pendingCleanup
    return
  }

  const isDev = import.meta.env.DEV
  const hostname = window.location.hostname
  const isLocalHost = hostname === 'localhost' || hostname === '127.0.0.1'

  if (!isDev && !isLocalHost) return

  try {
    const registrations = await navigator.serviceWorker.getRegistrations()
    if (!registrations.length) return

    await Promise.all(registrations.map((registration) => registration.unregister().catch(() => false)))

    if ('caches' in window) {
      const cacheKeys = await caches.keys()
      await Promise.all(cacheKeys.map((key) => caches.delete(key).catch(() => false)))
    }

    // Reload once so the page detaches from an old SW-controlled client.
    if (navigator.serviceWorker.controller && !window.sessionStorage.getItem('__health_dev_sw_reset__')) {
      window.sessionStorage.setItem('__health_dev_sw_reset__', '1')
      window.location.reload()
      return
    }
  } catch (error) {
    console.warn('[health-dev-sw-cleanup] failed to clear local service worker state', error)
  }
}

async function bootstrap() {
  await cleanupLocalDevServiceWorkers()

  // 2. 挂载 Element Plus 全局消息服务
  app.config.globalProperties.$message = ElMessage

  // 3. 安装 Vuex 状态管理
  //    store 中包含：用户信息（user module）、侧边栏状态（app module）等
  app.use(store)

  // 4. 安装 Vue Router
  //    安装后组件内可以用 this.$router（路由跳转）、this.$route（当前路由信息）
  app.use(router)

  // 5. 将 Vue 应用挂载到 public/index.html 中 id="app" 的 div 元素
  //    挂载后，Vue 接管该 div 内的所有 DOM，开始渲染
  app.mount('#app')
}

void bootstrap()
