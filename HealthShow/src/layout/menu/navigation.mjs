export const NAV_GROUPS = [
  { key: 'command', title: '指挥中心', icon: 'HomeFilled', order: 1, path: '/__nav__/command' },
  { key: 'monitor', title: '监测中心', icon: 'DataAnalysis', order: 2, path: '/__nav__/monitor' },
  { key: 'warning', title: '预警中心', icon: 'Bell', order: 3, path: '/__nav__/warning' },
  { key: 'people', title: '人员中心', icon: 'UserFilled', order: 4, path: '/__nav__/people' },
  { key: 'report', title: '报告与AI', icon: 'Document', order: 5, path: '/__nav__/report' },
  { key: 'admin', title: '系统管理', icon: 'Setting', order: 6, path: '/__nav__/admin' }
]

export const MOBILE_NAV_SLOTS = [
  {
    key: 'command',
    icon: 'DataAnalysis',
    label: '指挥',
    groups: ['command'],
    preferredPaths: ['/health-monitor/dashboard', '/safety-command/index']
  },
  {
    key: 'monitor',
    icon: 'Monitor',
    label: '监测',
    groups: ['monitor'],
    preferredPaths: ['/health-monitor/real-time']
  },
  {
    key: 'entry',
    icon: 'Stamp',
    label: '准入',
    includePaths: ['/health-monitor/mine-entry'],
    preferredPaths: ['/health-monitor/mine-entry']
  },
  {
    key: 'warning',
    icon: 'Bell',
    label: '预警',
    groups: ['warning'],
    preferredPaths: ['/alert-management/notifications', '/health-monitor/risk-warning']
  },
  {
    key: 'people',
    icon: 'UserFilled',
    label: '人员',
    groups: ['people'],
    excludePaths: ['/health-monitor/mine-entry'],
    preferredPaths: ['/health-monitor/employee-archive', '/health-monitor/workbench']
  }
]

export function resolveMenuPath(basePath, routePath = '') {
  if (!routePath) return basePath || '/'
  if (routePath.startsWith('/')) return routePath
  if (!basePath) return routePath.startsWith('/') ? routePath : `/${routePath}`
  return `${basePath.replace(/\/$/, '')}/${routePath}`
}

function collectLeafRoutes(routes, basePath = '') {
  const leaves = []

  for (const route of routes || []) {
    const fullPath = resolveMenuPath(basePath, route.path)

    if (route.children && route.children.length > 0) {
      leaves.push(...collectLeafRoutes(route.children, fullPath))
      continue
    }

    if (route.hidden || !route.meta?.navGroup) continue

    leaves.push({
      ...route,
      path: fullPath,
      children: undefined
    })
  }

  return leaves
}

export function buildGroupedMenuRoutes(routes) {
  const leaves = collectLeafRoutes(routes)

  return NAV_GROUPS
    .map((group) => {
      const children = leaves
        .filter((route) => route.meta?.navGroup === group.key)
        .sort((a, b) => (a.meta?.navOrder || 999) - (b.meta?.navOrder || 999))
        .map((route) => ({
          ...route,
          path: route.path.startsWith('/') ? route.path : `/${route.path}`
        }))

      return {
        path: group.path,
        name: `NavGroup${group.key}`,
        alwaysShow: true,
        meta: {
          title: group.title,
          icon: group.icon
        },
        children
      }
    })
    .filter((group) => group.children.length > 0)
}

export function buildMobileNavItems(routes) {
  const leaves = collectLeafRoutes(routes)
  const pathSet = new Set(leaves.map((route) => route.path))

  return MOBILE_NAV_SLOTS
    .map((slot) => {
      const slotLeaves = leaves.filter((route) => {
        const path = route.path
        if (slot.includePaths?.includes(path)) return true
        if (slot.excludePaths?.includes(path)) return false
        return slot.groups?.includes(route.meta?.navGroup)
      })

      if (!slotLeaves.length) return null

      const matches = [...new Set(slotLeaves.map((route) => route.path))]
      const preferredPath = (slot.preferredPaths || []).find((path) => pathSet.has(path))
      const path = preferredPath || slotLeaves[0].path

      return {
        key: slot.key,
        path,
        icon: slot.icon,
        label: slot.label,
        matches
      }
    })
    .filter(Boolean)
}

export function isActiveNavigationTarget(currentPath, item) {
  const matchers = item.matches || [item.path]
  return matchers.some((prefix) => currentPath.startsWith(prefix))
}
