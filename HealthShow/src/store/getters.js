/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              Vuex 全局 Getters（新手必读）                           ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【Getters 是什么？】
 *
 * Getters 是 Vuex 的"计算属性"，从 state 中派生出数据。
 * 组件不需要知道数据在哪个模块，直接通过 store.getters.xxx 访问。
 *
 * 如果没有 getters，访问 user 模块的 token 需要写：
 *   this.$store.state.user.token
 *
 * 有了 getters，可以简写为：
 *   this.$store.getters.token
 *
 * 【为什么在最外层定义 getters 而不是在模块内部？】
 *
 * 模块内部的 getters 加了命名空间后，访问路径是：
 *   this.$store.getters['user/token']  ← 丑
 *
 * 在 store/index.js 最外层定义的 getters 没有命名空间前缀：
 *   this.$store.getters.token          ← 简洁
 *
 * 所以我们把常用的模块状态提升到全局 getters，方便访问。
 *
 * 【Arrow Function 写法说明】
 *
 * sidebar: state => state.app.sidebar
 * 等价于：
 * sidebar: function(state) { return state.app.sidebar }
 *
 * state 参数是 Vuex 的完整 state 树（包含所有模块的 state）
 * state.app.sidebar → app 模块的 sidebar 状态
 * state.user.token  → user 模块的 token 状态
 *
 * 【在组件中的使用方式】
 *
 * 方式一（直接访问）：
 *   this.$store.getters.token
 *   this.$store.getters.name
 *
 * 方式二（使用 mapGetters 映射到 computed）：
 *   import { mapGetters } from 'vuex'
 *   computed: {
 *     ...mapGetters(['token', 'name', 'roles'])
 *   }
 *   // 然后在模板中直接用 {{ token }}、{{ name }}
 */
const getters = {
  // ─── app 模块相关 ───────────────────────────────────────────────

  /**
   * 侧边栏状态（展开/收起）
   * app 模块的 sidebar.opened = true（展开）或 false（收起）
   * 布局组件 layout/index.vue 使用此状态控制侧边栏宽度
   */
  sidebar: state => state.app.sidebar,

  /**
   * 当前设备类型
   * 'desktop'（PC）或 'mobile'（手机/平板）
   * 用于响应式布局：移动端自动收起侧边栏
   */
  device: state => state.app.device,

  // ─── user 模块相关 ───────────────────────────────────────────────

  /**
   * 当前用户的 Token
   * 由 utils/auth.js 从 Cookie 读取，存入 store
   * request.js 请求拦截器读取此 token 放入请求头
   */
  token: state => state.user.token,

  /**
   * 当前用户的头像 URL
   * 从 /auth/info 接口获取，显示在顶部导航栏
   */
  avatar: state => state.user.avatar,

  /**
   * 当前用户的显示名（优先使用真实姓名 realName，没有则用 username）
   * 显示在顶部导航栏和欢迎页
   */
  name: state => state.user.name,


  /**
   * 当前用户的角色列表（如 ['user', 'admin']）
   * permission.js 中用 roles.length > 0 判断是否已获取用户信息
   * （roles 为空说明 store 还没获取用户信息，需要调用 /auth/info）
   */
  roles: state => state.user.roles,

  /**
   * 当前用户的按钮权限码列表（如 ['user:add', 'user:delete']）
   * 在页面组件中判断某个按钮是否显示：
   *   v-if="$store.getters.buttons.includes('user:add')"
   */
  buttons: state => state.user.buttons,

  /**
   * 经过权限过滤后的路由列表
   * 用于侧边栏菜单渲染（只显示当前用户有权访问的菜单项）
   * 由 store/modules/user.js 中的 getPermittedAppRoutes() 过滤生成
   *
   */
  routes: state => state.user.resultAllRoutes
}

export default getters
