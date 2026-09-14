/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              Vuex 状态管理入口（新手必读）                            ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【什么是 Vuex？为什么需要状态管理？】
 *
 * 问题场景：
 *   用户登录后，Token、用户名、角色权限等信息需要在多个组件中使用：
 *   - 顶部导航栏：显示"欢迎，张三"
 *   - 侧边栏：根据角色过滤显示哪些菜单
 *   - 请求拦截器：在请求头中携带 Token
 *
 *   如果每个组件都自己保存一份，就会出现数据不一致的问题。
 *   如果通过父子组件传 props，层级深了就变成"prop drilling"噩梦。
 *
 * 解决方案：Vuex（集中式状态管理）
 *   把共享状态提取到一个全局 Store 中，所有组件都可以读取和修改。
 *   状态变更是响应式的：store 中数据变化，所有使用它的组件自动更新。
 *
 * 【Vuex 核心概念】
 *
 * state：状态（数据），相当于组件的 data
 * getters：计算属性，相当于组件的 computed（从 state 派生数据）
 * mutations：同步修改 state 的方法（只能是同步！）
 * actions：异步操作（如调用 API），完成后 commit mutation 更新 state
 *
 * 数据流：
 *   组件 dispatch(action) → action 调用 API → 成功后 commit(mutation) → mutation 更新 state → 组件自动刷新
 *
 * 【模块化（Modules）】
 *
 * 随着项目增长，把所有状态放在一个文件会变得难以维护。
 * Vuex 支持模块化，把不同功能的状态分开管理：
 *
 *   app 模块：侧边栏收起/展开状态、屏幕尺寸（移动端/PC）
 *   settings 模块：主题颜色、系统设置
 *   user 模块：Token、用户名、头像、角色、权限路由
 *
 * 每个模块在独立的文件中定义，这里统一注册。
 *
 * 【namespaced: true 说明】
 *
 * 模块启用命名空间后，访问该模块的状态/方法需要加模块名前缀：
 *   store.dispatch('user/login')      → 调用 user 模块的 login action
 *   store.getters['user/token']       → 访问 user 模块的 token（注意：getters 在本项目用全局 getters 代替）
 *   store.commit('user/SET_TOKEN', v) → 提交 user 模块的 SET_TOKEN mutation
 *
 * 这样避免不同模块的 action/mutation 名称冲突。
 *
 * 【getters 为什么放在 store/index.js 这层？】
 *
 * getters.js 中定义的是跨模块的快捷访问方式：
 *   store.getters.token  →  store.state.user.token（不需要写完整路径）
 *   store.getters.name   →  store.state.user.name
 *
 * 组件中用 this.$store.getters.token 比 this.$store.state.user.token 更简洁。
 */

import { createStore } from 'vuex'
import getters from './getters'         // 全局 getters（跨模块快捷访问）
import app from './modules/app'         // app 模块（侧边栏、屏幕尺寸）
import settings from './modules/settings'  // settings 模块（主题等设置）
import user from './modules/user'       // user 模块（登录状态、权限等）

/**
 * 创建并导出 Vuex Store 实例
 *
 * modules：注册所有子模块
 * getters：全局 getters（覆盖模块 getters，提供扁平化访问）
 *
 * 在 main.js 中通过 app.use(store) 安装到 Vue 应用，
 * 之后在任何组件中可以通过 this.$store 访问。
 */
export default createStore({
  modules: {
    app,       // 应用状态（侧边栏、设备类型）
    settings,  // 系统设置（主题、标签）
    user,      // 用户状态（登录信息、权限）
  },
  getters    // 全局 getters（在 getters.js 中定义）
})
