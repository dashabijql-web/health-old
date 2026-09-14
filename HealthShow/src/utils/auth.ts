/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              Token Cookie 管理工具（新手必读）                        ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【为什么把 Token 存到 Cookie？】
 *
 * Token 必须在以下两种情况下都能找到：
 *   1. 当前会话（用户打开浏览器的这段时间）
 *   2. 刷新页面后（页面重新加载，JavaScript 变量全部清空）
 *
 * 存储选择：
 *
 *   localStorage：
 *     - 优点：容量大（5MB），没有过期时间（长期保留）
 *     - 缺点：不能设置过期时间（Token 失效后还留着），跨标签页共享
 *
 *   sessionStorage：
 *     - 优点：标签页关闭自动清除
 *     - 缺点：不能跨标签页（新标签页打开链接会跳登录）
 *
 *   Cookie：
 *     - 优点：可以设置过期时间，随请求自动发送（可选），各浏览器支持好
 *     - 缺点：容量小（4KB），但 Token 通常 < 1KB 足够
 *
 * 本项目选择 Cookie：
 *   - js-cookie 库让 Cookie 操作简单（get/set/remove）
 *   - 不设置过期时间 → 关闭浏览器自动清除（Session Cookie）
 *   - Token 有效期由后端（Sa-Token）控制，前端只负责存取
 *
 * 【js-cookie 库说明】
 *
 * Cookies.get(key)     → 读取 Cookie 值（不存在返回 undefined）
 * Cookies.set(key, value) → 设置 Cookie（不指定 expires = Session Cookie）
 * Cookies.remove(key)  → 删除 Cookie
 *
 * Session Cookie（不设置 expires）的特点：
 *   - 浏览器标签页/窗口关闭时自动清除
 *   - 刷新页面不会清除
 *   - 适合登录 Token 的存储
 *
 * 【TokenKey 常量】
 *
 * 所有操作都使用同一个 key（'User-Token'），避免硬编码字符串带来的不一致风险。
 * 如果以后需要修改 key 名，只改这一处常量即可。
 */

import Cookies from 'js-cookie'

/**
 * Cookie 中存储 Token 的 key 名
 * 所有读/写/删操作都使用这个 key，保持一致
 */
const TokenKey = 'User-Token'
const TOKEN_COOKIE_OPTIONS = { path: '/', sameSite: 'Lax' as const }

/**
 * 读取 Token
 *
 * 在以下地方被调用：
 *   - request.js：请求拦截器中读取 Token 放入请求头
 *   - store/modules/user.js：初始化 state.token（刷新页面后恢复）
 *   - permission.js：判断是否已登录
 *   - heartbeat.js：判断是否需要检测（没有 Token 就不检测）
 *
 * @returns {string|undefined} Token 字符串，不存在时返回 undefined
 */
export function getToken(): string | undefined {
  return Cookies.get(TokenKey)
}

/**
 * 保存 Token
 *
 * 在以下地方被调用：
 *   - store/modules/user.js：login action 成功后保存
 *
 * 不设置 expires（过期时间）→ Session Cookie：
 *   用户关闭浏览器/标签页时自动清除，下次打开需要重新登录
 *   这符合安全要求（防止他人打开电脑看到已登录状态）
 *
 * @param {string} token - 后端返回的 Token 字符串
 */
export function setToken(token: string): string | undefined {
  return Cookies.set(TokenKey, token, TOKEN_COOKIE_OPTIONS)
}

/**
 * 删除 Token
 *
 * 在以下地方被调用：
 *   - store/modules/user.js：logout/resetToken action 中清除
 *
 * 删除后，下次请求不会携带 Token，服务器将拒绝未认证请求（401）
 *
 * @returns {void}
 */
export function removeToken(): void {
  Cookies.remove(TokenKey, TOKEN_COOKIE_OPTIONS)
}
