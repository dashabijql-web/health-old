/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              Axios HTTP 请求封装（新手必读）                          ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【为什么要封装 Axios？】
 *
 * 直接用 axios.get('/api/users') 有以下问题：
 *   1. 每次都要写完整 URL（baseURL 重复）
 *   2. 每次都要手动在请求头加 Token
 *   3. 每次都要写 .then().catch() 处理错误弹窗
 *   4. 401 处理逻辑（跳转登录）需要每个请求都写
 *
 * 封装后，所有请求通过拦截器统一处理：
 *   - 请求前自动加 Token（请求拦截器）
 *   - 响应后统一处理错误（响应拦截器）
 *   - 组件只需关心业务数据，不需要关心通用逻辑
 *
 * 【Axios 拦截器说明】
 *
 * 请求拦截器（interceptors.request.use）：
 *   每次发请求前执行 → 可以修改请求配置（加 Header、修改参数等）
 *
 * 响应拦截器（interceptors.response.use）：
 *   收到响应后执行 → 可以统一处理响应数据（提取 data、处理错误码等）
 *   有两个回调：
 *     第一个：HTTP 2xx 的响应（正常）
 *     第二个：HTTP 4xx/5xx 的响应（错误）
 *
 * 【Token 携带方式】
 *
 * 后端 Sa-Token 需要在请求头中携带 Token，Header 名为 "satoken"：
 *   请求头：satoken: eyJ0...（Token 字符串）
 *
 * 不用 Authorization: Bearer xxx 的原因：Sa-Token 默认用自己的 Header 名。
 * （可在 Sa-Token 配置中修改，但本项目用默认值）
 *
 * 【防重复跳转锁（isRedirectingToLogin）】
 *
 * 问题场景：
 *   页面有 5 个接口同时请求，Token 过期后 5 个都返回 401。
 *   如果每个 401 都执行跳转逻辑，会重复跳转 5 次，
 *   导致 router 历史栈混乱，还会在控制台看到 5 次错误。
 *
 * 解决方案：布尔锁（isRedirectingToLogin）
 *   第一个 401 来了 → isRedirectingToLogin = true → 执行跳转
 *   第2-5个 401 来了 → isRedirectingToLogin 已经是 true → 什么都不做
 *   跳转完成后 1 秒 → isRedirectingToLogin = false → 重置（为下次做准备）
 *
 * 【AUTH_PATHS 静默列表】
 *
 * /auth/info 是心跳检测和 permission.js 用的接口。
 * 如果这个接口失败（Token 过期），不应该弹错误提示（影响用户体验）。
 * 把它加入 AUTH_PATHS 后，响应拦截器对这些路径的失败静默处理（不弹 ElMessage）。
 *
 * 【只有 401 才清登录态】
 *
 * 后端重启后，Sa-Token 失效，业务接口和 /auth/info 都会返回 401，这时才跳登录。
 * HTTP 500/502/503、超时、网络错误是服务或查询失败，不能当成 Token 失效。
 * 监测页点菜单时如果 SQL 500 也清 Cookie，用户会看到整页卸掉并回到登录页。
 */

import axios from 'axios'
import { ElMessage } from 'element-plus'   // Element Plus 的消息提示组件
import store from '@/store'
import { getToken } from '@/utils/auth'
import router from '@/router'

/**
 * 创建 Axios 实例
 *
 * baseURL：所有请求的 URL 前缀
 *   开发环境：读取 VITE_BASE_API 环境变量（vite.config.mjs 配置代理）
 *   如果没有环境变量：默认用 '/dev-api'（代理到后端）
 *   例如：request({ url: '/auth/info' }) 实际请求 http://localhost:8080/health/auth/info
 *
 * timeout：10000 毫秒（10秒）超时
 *   超过10秒没有响应，自动取消请求并触发错误
 */
const service = axios.create({
  baseURL: import.meta.env.VITE_BASE_API || '/dev-api',
  timeout: 20000
})

let navigationAbort = new AbortController()

export function abortPendingPageRequests() {
  navigationAbort.abort()
  navigationAbort = new AbortController()
}

function isCanceledRequest(error) {
  return error?.code === 'ERR_CANCELED' || error?.name === 'CanceledError' || axios.isCancel?.(error)
}

/**
 * 防重复跳转锁
 * false = 未在跳转中（正常状态）
 * true = 正在跳转到登录页（禁止重复触发）
 */
let isRedirectingToLogin = false


/**
 * 错误消息去重集合
 * 记录正在显示的错误消息，防止相同错误并发触发时重复弹窗
 */
const activeErrors = new Set()

/**
 * 静默跳转到登录页（不显示任何错误提示）
 *
 * 步骤：
 *   1. 加锁（防止并发请求重复触发）
 *   2. 清除 Token 和 store 状态（resetToken）
 *   3. 跳转到 /login
 *   4. 1秒后解锁（允许下次登录后的正常错误处理）
 */
function redirectToLogin() {
  if (isRedirectingToLogin) return  // 已经在跳转，直接返回

  isRedirectingToLogin = true  // 加锁

  store.dispatch('user/resetToken').then(() => {
    router.replace({ path: '/login' }).finally(() => {
      // 延迟1秒解锁（等待跳转动画完成，防止页面切换过程中的残留请求干扰）
      setTimeout(() => {
        isRedirectingToLogin = false
      }, 1000)
    })
  })
}

/**
 * 需要静默处理的鉴权路径
 * 这些路径的请求失败不弹 ElMessage 错误提示
 */
const AUTH_PATHS = ['/auth/info', '/auth/userInfo']
const UNAUTHORIZED_MESSAGE = '未登录或登录已过期'

export function isUnauthorizedError(error) {
  return error?.response?.status === 401 || error?.message === UNAUTHORIZED_MESSAGE
}

/**
 * 判断某个请求是否是鉴权请求（静默路径）
 * @param {Object} config - Axios 请求配置对象
 * @returns {boolean} true = 是静默路径
 */
function isAuthRequest(config) {
  return AUTH_PATHS.some(p => config?.url?.includes(p))
}

// ─── 请求拦截器 ───────────────────────────────────────────────────

/**
 * 请求拦截器：每次发送请求前执行
 *
 * 主要作用：在请求头中加入 Token
 * 如果 store 中有 Token → 在 Header 中添加 satoken: xxx
 * 后端 SaTokenConfig 的拦截器会读取此 Header 进行认证
 */
service.interceptors.request.use(
  config => {
    config.headers = config.headers || {}
    if (store.getters.token) {
      // 从 Cookie 读取最新 Token（保证使用的是当前有效 Token）
      config.headers['satoken'] = getToken()
    }
    if (!config.signal && !isAuthRequest(config) && !config.surviveNavigation) {
      config.signal = navigationAbort.signal
    }
    return config  // 必须返回 config，否则请求不会发送
  },
  error => {
    // 请求配置错误（极少发生，如 URL 格式错误）
    return Promise.reject(error)
  }
)

// ─── 响应拦截器 ───────────────────────────────────────────────────

/**
 * 响应拦截器：收到响应后执行
 *
 * 有两个参数：
 *   第一个函数：HTTP 2xx（成功响应）的处理
 *   第二个函数：HTTP 4xx/5xx（错误响应）或网络超时的处理
 */
service.interceptors.response.use(

  // ─── 成功响应处理（HTTP 2xx）──────────────────────────────────
  response => {
    const res = response.data  // 提取响应体（后端 Result 对象）

    if (res.code !== 200) {
      // HTTP 请求成功（状态码 2xx），但业务逻辑失败（code !== 200）

      if (res.code === 401) {
        // 业务 401：Token 无效（过期/未登录）→ 静默跳转登录
        redirectToLogin()
        return Promise.reject(new Error(UNAUTHORIZED_MESSAGE))
      }

      if (isAuthRequest(response.config)) {
        // 鉴权路径失败（如 /auth/info 返回 500）→ 静默处理，不弹窗
        return Promise.reject(new Error(res.message || 'Error'))
      }

      if (isRedirectingToLogin) {
        // 正在跳转到登录页中 → 不再弹任何提示（避免用户看到残留的错误弹窗）
        return Promise.reject(new Error(res.message || 'Error'))
      }

      if (response.config?.silentError) {
        return Promise.reject(new Error(res.message || res.msg || 'Error'))
      }

      // 其他业务错误 → 弹出错误提示（5秒后自动消失），同一消息去重
      const errMsg = res.message || res.msg || '请求失败'
      if (!activeErrors.has(errMsg)) {
        activeErrors.add(errMsg)
        ElMessage({
          message: errMsg,
          type: 'error',
          duration: 5000,
          onClose: () => activeErrors.delete(errMsg)
        })
      }
      return Promise.reject(new Error(errMsg))
    }

    // 业务成功（code === 200）→ 直接返回 res（包含 code, message, data）
    return res
  },

  // ─── 错误响应处理（HTTP 4xx/5xx，或网络超时）──────────────────
  error => {
    const status = error.response?.status   // HTTP 状态码（可能为 undefined：网络错误时）
    const config = error.config              // 请求配置（含 URL 等信息）

    // Keep request failures diagnosable without logging tokens or payloads.
    console.error('[http] request failed', {
      method: config?.method?.toUpperCase(),
      url: config?.url,
      status,
      message: error?.message
    })

    // 特殊情况：已在登录页且没有 Token
    // 这是来自未卸载组件的残留请求，静默丢弃即可
    if (router.currentRoute.value.path === '/login' && !getToken()) {
      return Promise.reject(error)
    }

    // HTTP 401：服务器明确告知未认证 → 静默跳转
    if (status === 401) {
      redirectToLogin()
      return Promise.reject(error)
    }

    // 鉴权路径的任何错误 → 静默（心跳失败不弹窗）
    if (isAuthRequest(config)) {
      return Promise.reject(error)
    }

    if (isCanceledRequest(error)) {
      return Promise.reject(error)
    }

    // 正在跳转登录中 → 不再弹提示
    if (isRedirectingToLogin) {
      return Promise.reject(error)
    }

    // ─── 处理其他 HTTP 错误 ───────────────────────────────────────

    let message = '请求失败'

    if (error.response) {
      // 有 HTTP 响应（服务器返回了错误码）
      switch (status) {
        case 403:
          message = '没有权限访问'
          break
        case 404:
          message = '请求的资源不存在'
          break
        case 500:
          message = error.response.data?.message || error.response.data?.msg || '服务器错误'
          break
        case 502:
        case 503:
          // 502/503 = 网关错误 / 服务暂时不可用（后端未启动或代理不可达）
          // 不应清除 Token 或跳转登录，只提示用户稍后重试
          message = '服务暂时不可用，请稍后重试'
          break
        default:
          message = error.response.data?.message || error.response.data?.msg || '请求失败'
      }
    } else if (error.message?.includes('timeout')) {
      message = '请求超时，请稍后重试'
    } else if (error.message?.includes('Network Error')) {
      message = '网络连接失败，请检查网络'
    }

    if (config?.silentError) {
      return Promise.reject(error)
    }

    // 其他错误 → 弹出提示（5秒可关闭），同一消息去重防止并发弹多条
    if (!activeErrors.has(message)) {
      activeErrors.add(message)
      ElMessage({
        message,
        type: 'error',
        duration: 5000,
        showClose: true,
        onClose: () => activeErrors.delete(message)
      })
    }

    return Promise.reject(error)
  }
)

// 导出封装好的 Axios 实例（全项目通用）
export default service
