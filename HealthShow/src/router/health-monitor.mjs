/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              健康监测模块路由配置（新手必读）                          ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【为什么单独一个文件？】
 *
 * 健康监测是本系统的核心功能模块，包含多个子页面。
 * 如果把所有路由都写在 router/index.js 中，文件会很长、难以维护。
 *
 * 按功能模块拆分路由文件是最佳实践：
 *   router/index.js         → 路由总配置（引入各模块）
 *   router/health-monitor.mjs → 健康监测模块的路由
 *   router/system.js        → 系统管理模块的路由（如果有的话）
 *
 * 【路由嵌套（父子路由）说明】
 *
 * 本模块的路由结构：
 *
 *   /health-monitor（父路由，使用 Layout 布局组件）
 *   ├── dashboard    → /health-monitor/dashboard（统一管控页面）
 *   ├── real-time    → /health-monitor/real-time（实时监控）
 *   ├── heart-rate   → /health-monitor/heart-rate（心率分析）
 *   ├── blood-oxygen → /health-monitor/blood-oxygen（血氧分析）
 *   ├── sleep        → /health-monitor/sleep（睡眠分析）
 *   └── risk-warning → /health-monitor/risk-warning（风险预警）
 *
 * 父路由的 component 是 Layout（通用布局：顶部导航 + 侧边栏 + 内容区）
 * 子路由的 component 是具体的业务页面组件
 *
 * 当访问 /health-monitor/dashboard 时：
 *   Layout 组件被渲染（包含导航栏和侧边栏）
 *   Layout 内部有一个 <router-view /> 标签
 *   dashboard 对应的组件被渲染到这个 <router-view /> 中
 *
 * 【meta.permCode 权限码说明】
 *
 * 每个子路由都有 permCode（权限码），如 'health:dashboard'
 * store/modules/user.js 中的 getPermittedAppRoutes() 会根据后端返回的权限码列表，
 * 过滤掉当前用户没有权限的菜单项。
 *
 * 例如：后端返回 routes = ['health:dashboard', 'health:realtime']
 * getPermittedAppRoutes() 会保留 dashboard 和 real-time 两个子菜单，
 * 删除 heart-rate、blood-oxygen、sleep、risk-warning。
 * 用户在侧边栏就只能看到两个菜单项。
 *
 * 【meta.affix 说明】
 *
 * affix: true 表示这个标签固定在标签页栏（Tab Bar）中，不可关闭。
 * 通常把"首页"或"统一管控"设置为固定标签，防止用户误关闭。
 *
 * 【懒加载（动态导入）的好处】
 *
 * component: () => import('@/views/health-monitor/dashboard/index.vue')
 *
 * 使用箭头函数返回 import()：
 *   - 只在用户第一次访问该路由时才下载对应 JS 文件
 *   - 其他路由的 JS 不会被下载（节省首屏加载时间）
 *   - Vite 会自动将每个 import() 打包为独立的 chunk 文件
 */

import Layout from './layout-component.mjs' // 主布局组件按需加载
const useUnifiedControlV2 = (import.meta.env?.VITE_UNIFIED_CONTROL_V2 ?? 'true') !== 'false'

/**
 * 健康监测模块路由配置对象
 *
 * 这个对象会被 router/index.js 引入，合并到 constantRoutes 数组中
 */
const healthMonitorRouter = {
  path: '/health-monitor',           // 父路由路径
  component: Layout,                 // 使用主布局（含侧边栏、顶部导航）
  redirect: '/health-monitor/dashboard',  // 访问 /health-monitor 时重定向到 dashboard
  name: 'HealthMonitor',             // 路由命名（编程式导航使用）
  meta: {
    title: '健康监测',               // 侧边栏一级菜单标题
    icon: 'DataAnalysis'             // Element Plus Icons 中的图标名
  },
  children: [
    {
      path: 'employee-profile',
      name: 'EmployeeProfile',
      hidden: true,
      component: () => import('@/views/health-monitor/employee-profile/index.vue'),
      meta: {
        title: '职工健康画像',
        icon: 'User',
        activeMenu: '/health-monitor/employee-archive',
        navGroup: 'people',
        navOrder: 22
      }
    },
    {
      path: 'miner-portrait-showcase',
      name: 'MinerPortraitShowcase',
      hidden: true,
      component: () => import('@/views/health-monitor/miner-portrait-showcase/index.vue'),
      meta: {
        title: '矿工健康画像展示',
        icon: 'User',
        activeMenu: '/health-monitor/employee-archive',
        navGroup: 'people',
        navOrder: 22.5
      }
    },
    {
      path: 'health-portrait-showcase',
      name: 'HealthPortraitShowcase',
      component: () => import('@/views/health-monitor/health-portrait-showcase/index.vue'),
      meta: {
        title: '3D健康画像',
        icon: 'User',
        navGroup: 'people',
        navOrder: 21.5
      }
    },
    {
      path: 'health-portrait',
      name: 'HealthPortrait',
      hidden: true,
      component: () => import('@/views/personnel-management/health-portrait/index.vue'),
      meta: {
        title: '健康画像',
        activeMenu: '/health-monitor/employee-archive',
        navGroup: 'people',
        navOrder: 23
      }
    },
    {
      // 工作台日历（每日健康均值 + 预警日历视图）
      path: 'workbench',
      name: 'Workbench',
      component: () => import('@/views/health-monitor/workbench/index.vue'),
      meta: {
        title: '工作台日历',
        icon: 'Calendar',
        permCode: 'health:workbench',
        navGroup: 'people',
        navOrder: 23
      }
    },
    {
      // 统一管控页面（数据总览）
      path: 'dashboard',             // 完整路径：/health-monitor/dashboard
      name: 'HealthDashboard',
      component: () => useUnifiedControlV2
        ? import('@/views/health-monitor/dashboard/index.vue')
        : import('@/views/health-monitor/dashboard/legacy-20260601/index.vue'),
      meta: {
        title: '统一管控',
        icon: 'Odometer',
        affix: true,                 // 固定在标签页栏（不可关闭）
        permCode: 'health:dashboard', // 权限码：需要拥有此权限才显示此菜单
        navGroup: 'command',
        navOrder: 12
      }
    },
    {
      // 实时监控页面（设备在线状态、实时健康数据）
      path: 'real-time',
      name: 'RealTimeMonitor',
      component: () => import('@/views/health-monitor/real-time/index.vue'),
      meta: {
        title: '实时监控',
        icon: 'View',
        permCode: 'health:realtime',
        navGroup: 'monitor',
        navOrder: 21
      }
    },
    {
      // 心率分析页面（历史心率数据查询、趋势图）
      path: 'heart-rate',
      name: 'HeartRateAnalysis',
      component: () => import('@/views/health-monitor/heart-rate/index.vue'),
      meta: {
        title: '心率分析',
        icon: 'Share',
        permCode: 'health:heart',
        navGroup: 'monitor',
        navOrder: 22
      }
    },
    {
      // 压力指数分析页面
      path: 'pressure',
      name: 'PressureAnalysis',
      component: () => import('@/views/health-monitor/pressure/index.vue'),
      meta: {
        title: '压力分析',
        icon: 'Cpu',
        permCode: 'health:pressure',
        navGroup: 'monitor',
        navOrder: 23
      }
    },
    {
      // 血压分析页面
      path: 'blood-pressure',
      name: 'BloodPressureAnalysis',
      component: () => import('@/views/health-monitor/blood-pressure/index.vue'),
      meta: {
        title: '血压分析',
        icon: 'Pointer',
        permCode: 'health:bloodpressure',
        navGroup: 'monitor',
        navOrder: 24
      }
    },
    {
      // 血氧分析页面（血氧饱和度历史数据）
      path: 'blood-oxygen',
      name: 'BloodOxygenAnalysis',
      component: () => import('@/views/health-monitor/blood-oxygen/index.vue'),
      meta: {
        title: '血氧分析',
        icon: 'MagicStick',
        permCode: 'health:oxygen',
        navGroup: 'monitor',
        navOrder: 25
      }
    },
    {
      // 睡眠分析页面 — 暂时隐藏：手表仅在井下（4G内网）佩戴，无法采集睡眠数据
      path: 'sleep',
      name: 'SleepAnalysis',
      hidden: true,
      component: () => import('@/views/health-monitor/sleep/index.vue'),
      meta: {
        title: '睡眠分析',
        icon: 'Moon',
        permCode: 'health:sleep',
        navGroup: 'monitor',
        navOrder: 26
      }
    },
    {
      // 风险预警页面（健康异常预警列表、预警规则配置）
      path: 'risk-warning',
      name: 'RiskWarning',
      component: () => import('@/views/health-monitor/risk-warning/index.vue'),
      meta: {
        title: '风险预警',
        icon: 'Warning',
        permCode: 'health:risk',
        navGroup: 'warning',
        navOrder: 11
      }
    },
    {
      path: 'employee-archive',
      name: 'EmployeeArchive',
      component: () => import('@/views/health-monitor/employee-archive/index.vue'),
      meta: {
        title: '职工健康档案库',
        icon: 'UserFilled',
        permCode: 'health:employee',
        navGroup: 'people',
        navOrder: 21
      }
    },
    {
      path: 'mine-entry',
      name: 'MineEntry',
      component: () => import('@/views/health-monitor/mine-entry/index.vue'),
      meta: {
        title: '入井准入管理',
        icon: 'CircleCheck',
        permCode: 'health:mine-entry',
        navGroup: 'people',
        navOrder: 22
      }
    },
    {
      path: 'report-center',
      name: 'ReportCenter',
      component: () => import('@/views/health-monitor/report-center/index.vue'),
      meta: {
        title: '报表中心',
        icon: 'DataAnalysis',
        permCode: 'health:report',
        navGroup: 'report',
        navOrder: 41
      }
    },
    {
      path: 'trend-warning',
      name: 'TrendWarning',
      component: () => import('@/views/health-monitor/trend-warning/index.vue'),
      meta: {
        title: '趋势预警',
        icon: 'TrendCharts',
        permCode: 'health:trend',
        navGroup: 'monitor',
        navOrder: 26
      }
    },
    {
      path: 'watch-raw',
      name: 'WatchRawPackets',
      component: () => import('@/views/health-monitor/watch-raw/index.vue'),
      meta: {
        title: '设备原始报文',
        icon: 'Document',
        permCode: 'device:list',
        navGroup: 'admin',
        navOrder: 56
      }
    },
    {
      path: 'watch-control',
      name: 'WatchControl',
      component: () => import('@/views/health-monitor/watch-control/index.vue'),
      meta: {
        title: '手表控制',
        icon: 'Cpu',
        permCode: 'device:list',
        navGroup: 'admin',
        navOrder: 57
      }
    }
  ]
}

export default healthMonitorRouter
