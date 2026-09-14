// User store：
// - 管理 token、用户信息和权限过滤后的菜单树
// - 路由权限过滤已收口到 app-route-access，只保留状态与接口调用职责

import { login, logout, getInfo } from '@/api/user'         // 登录/退出/获取信息 API
import { getToken, setToken, removeToken } from '@/utils/auth'  // Cookie 操作
import { resetRouter } from '@/router'
import { getPermittedAppRoutes } from '@/router/app-route-access'

const USER_PROFILE_CACHE_KEY = 'health:user-profile-cache'

function tokenFingerprint(token) {
  if (!token) return ''
  let hash = 2166136261
  for (let index = 0; index < token.length; index += 1) {
    hash = Math.imul(hash ^ token.charCodeAt(index), 16777619)
  }
  return `${token.length}:${(hash >>> 0).toString(16)}`
}

function getProfileStorage() {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

function readCachedUserProfile(token) {
  if (!token) return null
  try {
    const cached = JSON.parse(getProfileStorage()?.getItem(USER_PROFILE_CACHE_KEY) || 'null')
    if (!cached || cached.tokenFingerprint !== tokenFingerprint(token)) return null
    return {
      name: cached.name || '',
      avatar: cached.avatar || '',
      routes: Array.isArray(cached.routes) ? cached.routes : [],
      roles: Array.isArray(cached.roles) ? cached.roles : [],
      buttons: Array.isArray(cached.buttons) ? cached.buttons : []
    }
  } catch {
    return null
  }
}

function persistUserProfile(token, userInfo) {
  if (!token || !userInfo) return
  try {
    getProfileStorage()?.setItem(USER_PROFILE_CACHE_KEY, JSON.stringify({
      tokenFingerprint: tokenFingerprint(token),
      name: userInfo.name || '',
      avatar: userInfo.avatar || '',
      routes: userInfo.routes || [],
      roles: userInfo.roles || [],
      buttons: userInfo.buttons || []
    }))
  } catch {}
}

function clearCachedUserProfile() {
  try {
    getProfileStorage()?.removeItem(USER_PROFILE_CACHE_KEY)
  } catch {}
}

// ─── 状态初始化函数 ────────────────────────────────────────────────

const getDefaultState = () => {
  const token = getToken()
  const cachedProfile = readCachedUserProfile(token)
  return {
    token,                // 从 Cookie 读取（页面刷新后仍然有效）
    name: cachedProfile?.name || '',
    avatar: cachedProfile?.avatar || '',
    routes: cachedProfile?.routes || [],
    roles: cachedProfile?.roles || [],
    buttons: cachedProfile?.buttons || [],
    profileFromCache: Boolean(cachedProfile),
    resultAsyncRoutes: [], // 历史字段，当前保留仅为兼容旧 getter
    resultAllRoutes: cachedProfile ? getPermittedAppRoutes(cachedProfile.routes) : []
  }
}

const state = getDefaultState()

// ─── Mutations（同步修改 state）────────────────────────────────────

const mutations = {
  /**
   * 重置为初始状态
   * 退出登录时调用，清空所有用户数据
   * Object.assign(state, getDefaultState()) 将初始状态的所有属性覆盖到 state
   */
  RESET_STATE: (state) => {
    clearCachedUserProfile()
    Object.assign(state, getDefaultState())
  },

  /**
   * 设置 Token
   * @param {string} token - 从登录接口获取的 Token 字符串
   */
  SET_TOKEN: (state, token) => {
    state.token = token
  },

  /**
   * 设置用户信息（登录成功或 getInfo 成功后调用）
   * @param {Object} userInfo - 包含 name、avatar、roles、buttons、routes 的对象
   */
  SET_USERINFO: (state, userInfo) => {
    state.name    = userInfo.name
    state.avatar  = userInfo.avatar
    state.routes  = userInfo.routes  || []  // 后端返回的路由权限码数组
    state.buttons = userInfo.buttons || []  // 按钮权限码数组
    state.roles   = userInfo.roles   || []  // 角色数组
    state.profileFromCache = false
  },

  /**
   * 设置过滤后的路由（侧边栏菜单用）
   * @param {Array} routes - 已按权限过滤后的可见路由数组
   */
  SET_RESULTASYNCROUTES: (state, routes) => {
    state.resultAllRoutes = routes
  }
}

// ─── Actions（异步操作）────────────────────────────────────────────

const actions = {
  /**
   * 登录 Action
   *
   * 流程：
   *   1. 调用 /auth/login 接口，传用户名和密码
   *   2. 接口成功返回 Token
   *   3. 提交 SET_TOKEN mutation，更新 store 中的 token
   *   4. 调用 setToken() 把 Token 存入 Cookie（持久化）
   *   5. 返回 'ok'（让组件知道登录成功）
   *
   * 失败时：抛出 Error，组件的 catch 分支处理（如显示错误提示）
   *
   * @param {Object} userInfo - { username, password }
   */
  async login({ commit }, userInfo) {
    const { username, password } = userInfo
    // login() 是 api/user.js 中的函数，调用 POST /auth/login 接口
    const result = await login({ username: username.trim(), password })

    if (result.code == 200) {
      commit('SET_TOKEN', result.data.token)  // 更新 store
      setToken(result.data.token)             // 存入 Cookie
      // 登录接口已返回 roles/routes，立刻写入 store，避免再打一次 /auth/info
      commit('SET_USERINFO', result.data)
      commit('SET_RESULTASYNCROUTES', getPermittedAppRoutes(result.data.routes || []))
      persistUserProfile(result.data.token, result.data)
      return 'ok'                             // 告知调用方登录成功
    }

    // 业务状态码非 200，说明登录失败（用户名错误、密码错误等）
    return Promise.reject(new Error(result.message || '登录失败'))
  },

  /**
   * 获取用户信息 Action
   *
   * 触发时机：
   *   1. 首次登录成功后（permission.js 中获取权限然后放行路由）
   *   2. 页面刷新后（store 被清空，roles 为空，触发 getInfo 重新获取）
   *
   * 流程：
   *   1. 调用 /auth/info 接口（携带 Token）
   *   2. 接口返回用户名、头像、角色、权限码等
   *   3. 提交 SET_USERINFO，更新用户基本信息
   *   4. 提交 SET_RESULTASYNCROUTES，更新侧边栏和移动端导航数据源
   *
   * @returns {Promise} 解析后的用户信息
   */
  getInfo({ commit, state }) {
    return new Promise((resolve, reject) => {
      getInfo(state.token).then(async response => {
        const { data } = response
        if (!data) {
          reject('获取用户信息失败')
          return
        }

        // 存储用户信息到 state
        commit('SET_USERINFO', data)

        // 根据路由权限码过滤路由（更新侧边栏菜单）
        // data.routes 是后端返回的权限码数组，如 ['health:dashboard', 'user:list']
        commit('SET_RESULTASYNCROUTES', getPermittedAppRoutes(data.routes || []))
        persistUserProfile(getToken(), data)

        resolve(data)
      }).catch(reject)
    })
  },

  /**
   * 退出登录 Action
   *
   * 流程：
   *   1. 调用 /auth/logout 接口（服务端销毁 Token）
   *   2. 无论成功失败，都清理本地状态：
   *      - removeToken()：删除 Cookie 中的 Token
   *      - resetRouter()：重置路由（本项目是 no-op）
   *      - commit('RESET_STATE')：清空 store 中的用户数据
   *
   * 为什么失败了也要清理？
   *   即使服务端接口调用失败（网络错误），也要确保本地 Token 被清除，
   *   防止用户以为自己还在登录状态（即使实际上 Token 已失效）。
   */
  logout({ commit, state }) {
    return new Promise((resolve, reject) => {
      logout(state.token).then(() => {
        removeToken()           // 删除 Cookie Token
        resetRouter()           // 重置路由（本项目 no-op）
        commit('RESET_STATE')   // 清空 store
        resolve()
      }).catch(() => {
        // 退出接口失败（如网络断开），仍然清理本地状态
        removeToken()
        commit('RESET_STATE')
        resolve()  // 仍然 resolve（退出操作对用户来说"成功了"）
      })
    })
  },

  /**
   * 静默清除 Token Action（不调用服务端接口）
   *
   * 与 logout 的区别：
   *   logout：调用服务端 /auth/logout，然后清理本地
   *   resetToken：只清理本地（用于 Token 已经失效的情况，无需再通知服务端）
   *
   * 触发场景：
   *   - permission.js：getInfo 返回 401 时
   *   - request.js：业务或 HTTP 401 时
   */
  resetToken({ commit }) {
    return new Promise(resolve => {
      removeToken()           // 删除 Cookie Token
      commit('RESET_STATE')   // 清空 store
      resolve()
    })
  }
}

// 导出模块配置
export default {
  namespaced: true,  // 开启命名空间，调用时需要加 'user/' 前缀
  state,
  mutations,
  actions
}
