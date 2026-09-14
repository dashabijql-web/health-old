import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const dialogPath = path.resolve(__dirname, '../src/views/health-monitor/dashboard/components/DashboardDialogs.vue')

test('dashboard employee drawer warning list uses normalized level helpers', () => {
  const source = readFileSync(dialogPath, 'utf8')

  assert.match(source, /import \{ normalizeWarningLevel \} from '\.\.\/\.\.\/\.\.\/alert-management\/common\/warning-lifecycle'/)
  assert.match(source, /normalizeWarningLevel\(w\.warningLevel\)/)
  assert.match(source, /dashboardWarningLevelDrawerClass\(normalizeWarningLevel\(w\.warningLevel\)\)/)
  assert.match(source, /dashboardWarningLevelLabel\(normalizeWarningLevel\(w\.warningLevel\)\)/)
  assert.doesNotMatch(source, /w\.warningLevel === 3 \? 'lv-danger'/)
  assert.doesNotMatch(source, /w\.warningLevel === 3 \? '严重'/)
})

test('dashboard warning curve dialog uses shared tag type helper', () => {
  const source = readFileSync(dialogPath, 'utf8')

  assert.match(source, /dashboardWarningLevelTagType\(warnCurveModal\.event\.level\)/)
  assert.doesNotMatch(source, /warnCurveModal\.event\.level === 'danger' \? 'danger'/)
})

test('dashboard warning dialogs keep the command-center dark theme when teleported', () => {
  const source = readFileSync(dialogPath, 'utf8')

  assert.match(source, /class="dm-handle-dialog"/)
  assert.match(source, /class="dm-warn-curve-dialog"/)
  assert.match(source, /\.dm-handle-dialog\.el-dialog,[\s\S]*\.dm-warn-curve-dialog\.el-dialog/)
  assert.match(source, /background:\s*#080c20/)
  assert.doesNotMatch(source, /\.dm-handle-dialog\s*\{[\s\S]*:deep\(\.el-dialog\)/)
})
