import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const chartOptions = path.resolve(__dirname, '../src/views/health-monitor/dashboard/dashboard-chart-options.js')
const detailMethods = path.resolve(__dirname, '../src/views/health-monitor/dashboard/dashboard-detail-methods.js')

test('warn curve chart marker uses event warning level semantics', () => {
  const source = readFileSync(chartOptions, 'utf8')

  assert.match(source, /dashboardWarningLevelMarkerColor/)
  assert.match(source, /dashboardWarningLevelLabel/)
  assert.match(source, /const markerLabel = `\$\{dashboardWarningLevelLabel\(warnLevel\)\}时刻`/)
  assert.match(source, /name:\s*markerLabel/)
  assert.match(source, /formatter:\s*markerLabel/)
  assert.match(source, /color:\s*markerColor/)
  assert.doesNotMatch(source, /name:\s*'预警时刻'/)
  assert.doesNotMatch(source, /formatter:\s*'预警'/)
})

test('warn curve chart receives event level from detail methods', () => {
  const source = readFileSync(detailMethods, 'utf8')

  assert.match(source, /warnLevel:\s*event\?\.level/)
})
