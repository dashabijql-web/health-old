import Layout from '@/layout/index.vue'

export default {
  path: '/org-management',
  component: Layout,
  name: 'OrgManagement',
  meta: { title: '组织管理', icon: 'OfficeBuilding' },
  children: [
    {
      path: 'department',
      name: 'Department',
      component: () => import('@/views/org-management/department/index.vue'),
      meta: { title: '部门管理', icon: 'Grid', permCode: 'org:department' }
    },
    {
      path: 'job-type',
      name: 'JobType',
      component: () => import('@/views/org-management/job-type/index.vue'),
      meta: { title: '工种管理', icon: 'SetUp', permCode: 'org:job-type' }
    },
  ]
}
