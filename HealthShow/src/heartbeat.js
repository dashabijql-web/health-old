/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              会话心跳检测模块（新手必读）                             ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【解决什么问题？】
 *
 * 场景：用户已登录，正在使用系统。
 * 此时后端服务器重启（发布新版本、服务器故障等）。
 * 后端重启后，内存中的 Sa-Token 数据全部清空，所有用户的登录状态失效。
 *
 * 问题：
 *   前端 SPA（单页应用）只在首次加载时请求服务器，之后都在客户端渲染。
 *   用户不刷新页面的话，浏览器不知道服务器已重启，Token 已失效。
 *   此时用户点击任何需要权限的操作（查询数据、保存记录等），
 *   才会发现接口返回 401，然后弹出一堆错误提示——体验很差。
 *
 * 解决方案：主动心跳检测
 *   每隔固定时间（30秒）偷偷 ping 一次 /auth/info 接口：
 *   - 接口正常 → 说明 Token 有效，不做任何事
 *   - 接口返回 401 → request.js 清 Token 并跳登录
 *   - 500/超时/网络错误 → 保持登录态，不能把业务故障当成掉登录
 *
 * 【visibilitychange 事件】
 *
 * 用户可能把标签页切到后台，几十分钟后再切回来。
 * 此时 Token 极可能已过期，但下一次定时心跳可能还要等很久。
 *
 * 解决：监听 visibilitychange 事件（标签页可见性变化）：
 *   - 标签页从后台切到前台（visibilityState === 'visible'）
 *   - 立即补一次 ping 检测，快速发现 Token 是否失效
 *
 * 【与 request.js 的配合】
 *
 * /auth/info 接口被加入 request.js 的 AUTH_PATHS 静默列表。
 * 心跳失败不弹窗；真正掉登录只走 request.js 的 401 处理。
 *
 * 【模块化设计】
 *
 * startHeartbeat()：启动心跳（模块内部调用）
 * stopHeartbeat()：停止心跳（export 导出，退出登录时调用）
 * ping()：单次检测（内部函数）
 *
 * 通过路由 afterEach 钩子：
 *   - 跳到 /login → 停止心跳（已退出登录，不需要检测）
 *   - 跳到其他页且有 Token → 启动心跳
 */

import router from '@/router'
import { getToken } from '@/utils/auth'
import request from '@/utils/request'

/**
 * 心跳间隔：30秒
 * 含义：后端重启后，最多30秒内，前端会自动感知并跳转到登录页
 * 可以调小（如10秒）提高响应速度，但会增加服务器请求频率
 */
const HEARTBEAT_INTERVAL = 30 * 1000  // 30000 毫秒 = 30 秒

/**
 * 心跳定时器 ID
 * 由 setInterval 返回，clearInterval 时需要用到
 * null 表示心跳未启动
 */
let heartbeatTimer = null

/**
 * 执行一次心跳检测
 *
 * 条件：有 Token 且不在登录页才检测（避免无意义的请求）
 *
 * 请求 /auth/info：
 *   - 成功（code=200）：Token 有效，什么都不做
 *   - 401：由 request.js 跳登录
 *   - 其他失败：忽略，保持当前会话
 *
 * 注意：/auth/info 已加入 request.js 的 AUTH_PATHS 静默列表，
 *       无论成功失败都不会弹 ElMessage 错误提示
 */
async function ping() {
  // 如果没有 Token 或当前已在登录页，不发请求
  if (!getToken() || router.currentRoute.value.path === '/login') return

  try {
    // 发送 GET /auth/info 请求（携带 Cookie 中的 Token）
    await request({ url: '/auth/info', method: 'get' })
    // 请求成功：Token 有效，什么都不做（用户无感知）

  } catch {
    // 401 已由 request.js 清登录态并跳转。超时、500、网络错误不能在这里再清 Cookie。
  }
}

/**
 * 启动心跳检测
 *
 * 防重复启动：如果 heartbeatTimer 已有值，说明已经启动，直接返回
 *
 * 同时注册 visibilitychange 事件：
 *   用户切回标签页时立即补一次检测，不等下一个定时间隔
 */
// 命名函数引用，用于 removeEventListener（匿名函数无法移除）
function onVisible() {
  if (document.visibilityState === 'visible') ping()
}

function startHeartbeat() {
  if (heartbeatTimer) return  // 防止重复启动（如多次调用 startHeartbeat）

  // setInterval：每隔 HEARTBEAT_INTERVAL 毫秒执行一次 ping
  heartbeatTimer = setInterval(ping, HEARTBEAT_INTERVAL)

  // 监听标签页可见性变化
  // 当用户把浏览器切到后台再切回来时，立即检测一次
  document.addEventListener('visibilitychange', onVisible)
}

/**
 * 停止心跳检测
 *
 * 在以下情况调用：
 *   1. 用户主动退出登录（store.dispatch('user/logout')）
 *   2. 跳转到 /login 页面时（router.afterEach 触发）
 *
 * 停止后 heartbeatTimer 置 null，下次 startHeartbeat 可以重新启动
 *
 * 注意：export 导出，store/modules/user.js 的 logout action 中也可以调用
 */
export function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)  // 清除定时器，停止心跳
    heartbeatTimer = null           // 重置为 null，允许再次启动
  }
  document.removeEventListener('visibilitychange', onVisible)
}

// ─── 路由监听：根据路由状态自动启停心跳 ────────────────────────────

/**
 * 监听路由变化（afterEach：跳转完成后触发）
 *
 * 逻辑：
 *   - 跳到 /login → 停止心跳（用户已退出，不需要检测）
 *   - 跳到其他页且有 Token → 启动心跳（确保用户使用过程中持续检测）
 *
 * 注意：这里用 afterEach 而不是 beforeEach，
 *       是因为心跳不需要阻止路由跳转，只是观察路由变化做相应启停
 */
router.afterEach((to) => {
  if (to.path === '/login') {
    stopHeartbeat()               // 跳到登录页：停止心跳
  } else if (getToken()) {
    startHeartbeat()              // 跳到业务页且已有 Token：启动心跳
  }
})

// ─── 页面初始化：处理刷新页面的场景 ────────────────────────────────

/**
 * 如果用户刷新页面（F5）时已有 Token（之前已登录），
 * main.js 引入此文件时这段代码立即执行，启动心跳。
 *
 * 为什么需要这个？
 * 刷新页面时路由是重新初始化的，router.afterEach 的事件可能还没注册完毕，
 * 所以在这里额外检查一次，确保刷新后心跳能立即启动。
 */
if (getToken()) {
  startHeartbeat()
}
