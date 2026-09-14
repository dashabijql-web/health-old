import test from 'node:test'
import assert from 'node:assert/strict'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { existsSync, readFileSync } from 'node:fs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const dashboardRuntime = path.resolve(__dirname, '../src/views/health-monitor/dashboard/dashboard-runtime-data.js')
const dashboardMethods = path.resolve(__dirname, '../src/views/health-monitor/dashboard/dashboard-runtime.js')
const dashboardLifecycle = path.resolve(__dirname, '../src/views/health-monitor/dashboard/dashboard-lifecycle.js')
const healthApi = path.resolve(__dirname, '../src/api/health.js')

function resolveRelativeImport(fromFile, importPath) {
  return path.resolve(path.dirname(fromFile), `${importPath}.js`)
}

test('dashboard runtime imports warning lifecycle helper from existing relative path', () => {
  const target = resolveRelativeImport(dashboardRuntime, '../../alert-management/common/warning-lifecycle')
  assert.equal(existsSync(target), true, `${target} should exist`)
})

test('unified control initial load stays within three business requests', () => {
  const methods = readFileSync(dashboardMethods, 'utf8')
  const lifecycle = readFileSync(dashboardLifecycle, 'utf8')
  const api = readFileSync(healthApi, 'utf8')
  const fetchDataBody = methods.slice(methods.indexOf('async fetchData'), methods.indexOf('async fetchKpiData'))

  assert.match(api, /url:\s*'\/dashboard\/unified-control-snapshot'/)
  assert.match(fetchDataBody, /fetchUnifiedControlSnapshot/)
  assert.match(fetchDataBody, /this\.fetchWarningEvents\(\)/)
  assert.doesNotMatch(fetchDataBody, /this\.(?:fetchDashboardData|fetchBodyIndicators|fetchHealthSnapshot|fetchPersonCounts|fetchDeviceData|fetchTop5Data|fetchTrendDaily|fetchWarningDist|fetchWarningTypes|fetchPreShiftRate|fetchCommandSummary|loadDeptData)\(/)
  assert.match(lifecycle, /vm\.fetchData\(\)\s*\n\s*vm\.loadMineAiCache\(\)/)
  assert.doesNotMatch(lifecycle, /vm\.fetchData\(\)\.then/)
})
