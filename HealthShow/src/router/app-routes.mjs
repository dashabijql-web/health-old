import healthMonitorRouter from './health-monitor.mjs'
import alertManagementRouter from './alert-management.mjs'
import Layout from './layout-component.mjs'
// Keep rollback at the route boundary while preserving the public path.
const useSafetyCommandV2 = (import.meta.env?.VITE_SAFETY_COMMAND_V2 ?? 'true') !== 'false'

export const appRoutes = [
  {
    path: '/safety-command',
    component: Layout,
    name: 'SafetyCommand',
    redirect: '/safety-command/index',
    meta: { title: '安全指挥中心', icon: 'Aim', breadcrumb: false },
    children: [
      {
        path: 'index',
        name: 'SafetyCommandIndex',
        component: () => useSafetyCommandV2
          ? import('@/views/safety-command/index.vue')
          : import('@/views/safety-command/legacy-20260601/index.vue'),
        meta: {
          title: '安全指挥中心',
          icon: 'Aim',
          navGroup: 'command',
          navOrder: 11
        }
      }
    ]
  },

  healthMonitorRouter,

  alertManagementRouter,

  {
    path: '/admin',
    component: Layout,
    name: 'Admin',
    meta: { title: '后台管理', icon: 'Setting' },
    children: [
      {
        path: 'device-list',
        name: 'DeviceList',
        component: () => import('@/views/device-management/index.vue'),
        meta: {
          title: '设备列表',
          icon: 'Monitor',
          permCode: 'device:list',
          navGroup: 'admin',
          navOrder: 51
        }
      },
      {
        path: 'user-list',
        name: 'UserList',
        component: () => import('@/views/user-list/index.vue'),
        meta: {
          title: '用户列表',
          icon: 'UserFilled',
          permCode: 'user:list',
          navGroup: 'admin',
          navOrder: 52
        }
      },
      {
        path: 'role',
        name: 'RoleManagement',
        component: () => import('@/views/role-management/index.vue'),
        meta: {
          title: '角色管理',
          icon: 'Key',
          permCode: 'role:list',
          navGroup: 'admin',
          navOrder: 53
        }
      },
      {
        path: 'department',
        name: 'Department',
        component: () => import('@/views/org-management/department/index.vue'),
        meta: {
          title: '部门管理',
          icon: 'Grid',
          permCode: 'org:department',
          navGroup: 'admin',
          navOrder: 54
        }
      },
      {
        path: 'job-type',
        name: 'JobType',
        component: () => import('@/views/org-management/job-type/index.vue'),
        meta: {
          title: '工种管理',
          icon: 'SetUp',
          permCode: 'org:job-type',
          navGroup: 'admin',
          navOrder: 55
        }
      }
    ]
  },

  {
    path: '/ai-chat',
    component: Layout,
    meta: { title: 'AI健康助手' },
    children: [
      {
        path: 'index',
        name: 'AiChat',
        component: () => import('@/views/ai-chat/index.vue'),
        meta: {
          title: 'AI健康助手',
          icon: 'ChatDotRound',
          navGroup: 'report',
          navOrder: 42
        }
      }
    ]
  },

  {
    path: '/command-center',
    redirect: '/health-monitor/dashboard',
    hidden: true
  },
  {
    path: '/monitoring-center',
    redirect: '/health-monitor/real-time',
    hidden: true
  },
  {
    path: '/warning-center',
    redirect: '/alert-management/notifications',
    hidden: true
  },
  {
    path: '/people-center',
    redirect: '/health-monitor/employee-archive',
    hidden: true
  },
  {
    path: '/report-ai',
    redirect: '/health-monitor/report-center',
    hidden: true
  },

  {
    path: '/health-monitor/warnings',
    redirect: '/alert-management/records',
    hidden: true
  },

  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
    hidden: true
  }
]

export default appRoutes
