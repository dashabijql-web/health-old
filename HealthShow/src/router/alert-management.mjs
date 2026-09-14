import Layout from './layout-component.mjs'

export default {
  path: '/alert-management',
  component: Layout,
  name: 'AlertManagement',
  meta: { title: '预警管理', icon: 'Bell' },
  children: [
    {
      path: 'notifications',
      name: 'NotificationCenter',
      component: () => import('@/views/alert-management/notifications/index.vue'),
      meta: {
        title: '待办事件',
        icon: 'Bell',
        permCode: 'alert:notifications',
        navGroup: 'warning',
        navOrder: 12
      }
    },
    {
      path: 'sos',
      name: 'SosPage',
      component: () => import('@/views/alert-management/sos/index.vue'),
      meta: {
        title: '紧急事件',
        icon: 'Warning',
        permCode: 'alert:sos',
        navGroup: 'warning',
        navOrder: 15
      }
    },
    {
      path: 'config',
      name: 'AlertConfig',
      component: () => import('@/views/alert-management/config/index.vue'),
      meta: {
        title: '规则配置',
        icon: 'Setting',
        permCode: 'alert:config',
        navGroup: 'warning',
        navOrder: 14
      }
    },
    {
      path: 'records',
      name: 'AlertRecords',
      component: () => import('@/views/alert-management/records/index.vue'),
      meta: {
        title: '处置记录',
        icon: 'List',
        permCode: 'alert:records',
        navGroup: 'warning',
        navOrder: 13
      }
    }
  ]
}
