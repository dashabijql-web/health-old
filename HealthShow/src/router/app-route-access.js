import appRoutes from './app-routes.mjs'

function cloneRouteRecord(route) {
  return {
    ...route,
    meta: route.meta ? { ...route.meta } : route.meta,
    children: route.children ? route.children.map(cloneRouteRecord) : route.children
  }
}

export function getAppRouteDefinitions() {
  return appRoutes.map(cloneRouteRecord)
}

export function filterRoutesByPermissions(routes, permCodes) {
  if (permCodes === null || permCodes === undefined) return routes.map(cloneRouteRecord)
  if (permCodes.length === 0) return []

  const visibleRoutes = []

  for (const route of routes) {
    const record = cloneRouteRecord(route)

    if (record.hidden) {
      visibleRoutes.push(record)
      continue
    }

    if (record.children && record.children.length > 0) {
      record.children = filterRoutesByPermissions(record.children, permCodes)
      if (record.children.length > 0) {
        visibleRoutes.push(record)
      }
      continue
    }

    if (!record.meta?.permCode || permCodes.includes(record.meta.permCode)) {
      visibleRoutes.push(record)
    }
  }

  return visibleRoutes
}

export function getPermittedAppRoutes(permCodes) {
  return filterRoutesByPermissions(getAppRouteDefinitions(), permCodes)
}
