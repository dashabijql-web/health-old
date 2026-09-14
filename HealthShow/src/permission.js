// 全局导航守卫：
// - 有 token 时恢复用户信息并放行业务路由
// - 无 token 时只允许白名单
// - 统一处理登录重定向和顶部进度条

import router from './router'
import store from './store'
import NProgress from 'nprogress'     // 页面顶部进度条库
import 'nprogress/nprogress.css'      // 进度条样式
import { getToken } from '@/utils/auth'
import { abortPendingPageRequests, isUnauthorizedError } from '@/utils/request'

// 不显示右侧转圈的 spinner（只显示顶部进度条）
NProgress.configure({ showSpinner: false })

let progressTimer = null
const PROGRESS_DELAY_MS = 180
const AUTH_RESTORE_BUDGET_MS = 900
let backgroundUserRefresh = null

function startProgress() {
  if (progressTimer) return
  progressTimer = setTimeout(() => {
    NProgress.start()
    progressTimer = null
  }, PROGRESS_DELAY_MS)
}

function stopProgress() {
  if (progressTimer) {
    clearTimeout(progressTimer)
    progressTimer = null
  }
  NProgress.done()
}

function refreshCachedUserInfo() {
  if (backgroundUserRefresh) return
  backgroundUserRefresh = store.dispatch('user/getInfo')
    .catch(async (error) => {
      if (isUnauthorizedError(error)) {
        await store.dispatch('user/resetToken')
        await router.replace('/login')
      }
    })
    .finally(() => {
      backgroundUserRefresh = null
    })
}

function waitForAuthRestore(promise) {
  return Promise.race([
    promise.then(() => ({ status: 'ready' })).catch(error => ({ status: 'error', error })),
    new Promise(resolve => setTimeout(() => resolve({ status: 'pending' }), AUTH_RESTORE_BUDGET_MS))
  ])
}

/**
 * 不需要登录即可访问的路径白名单
 * /login 登录页：未登录时可以直接访问
 */
const whiteList = ['/login']

// ─── 全局前置守卫 ──────────────────────────────────────────────────

router.beforeEach(async (to, from, next) => {
  if (from.matched.length) {
    abortPendingPageRequests()
  }
  startProgress()

  if (getToken()) {
    // ─── 情况1：有 Token（已登录或 Cookie 未过期）────────────────
    if (to.path === '/login') {
      // 已登录的用户访问登录页 → 重定向到首页（无需再次登录）
      next({ path: '/' })
      stopProgress()
    } else {
      if (store.getters.roles && store.getters.roles.length > 0) {
        // store 中已有用户信息（正常已登录状态）→ 直接放行
        next()
        if (store.state.user.profileFromCache) refreshCachedUserInfo()
      } else {
        // store 中没有用户信息（刷新页面后 Vuex 被清空）
        // → 携带 Token 重新请求 /auth/info 获取用户信息
        try {
          const restorePromise = store.dispatch('user/getInfo')
          const outcome = await waitForAuthRestore(restorePromise)
          if (outcome.status === 'pending') {
            next()
            void restorePromise.catch(async (error) => {
              if (isUnauthorizedError(error)) {
                await store.dispatch('user/resetToken')
                await router.replace('/login')
              }
            })
            return
          }
          if (outcome.status === 'error') throw outcome.error
          // getInfo 成功：用 replace:true 放行，避免产生多余的历史记录
          next({ ...to, replace: true })
        } catch (error) {
          // 只有明确未认证才清登录态。500/超时不能把已登录用户踢回登录页。
          if (isUnauthorizedError(error)) {
            await store.dispatch('user/resetToken')
            next('/login')
            stopProgress()
            return
          }
          next()
        }
      }
    }
  } else {
    // ─── 情况2：没有 Token（未登录或已退出）─────────────────────
    if (whiteList.indexOf(to.path) !== -1) {
      // 目标在白名单（如 /login）→ 直接放行
      next()
    } else {
      // 目标需要登录 → 重定向到登录页，并带上 redirect 参数
      // 登录成功后可以直接跳回原本要访问的页面
      next(`/login?redirect=${to.path}`)
      stopProgress()
    }
  }
})

// ─── 全局后置守卫 ──────────────────────────────────────────────────

/**
 * 路由跳转完成后执行（无论成功还是失败）
 * 隐藏进度条（beforeEach 中 done() 只覆盖了部分情况）
 */
router.afterEach(() => {
  stopProgress()
})
