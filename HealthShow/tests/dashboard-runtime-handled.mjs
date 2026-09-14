import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const dashboardRuntime = path.resolve(__dirname, '../src/views/health-monitor/dashboard/dashboard-runtime-data.js')

test('dashboard warning runtime reuses shared handled normalizer', () => {
  const source = readFileSync(dashboardRuntime, 'utf8')

  assert.match(source, /import \{ normalizeWarningLevel, isWarningHandled \} from '\.\.\/\.\.\/alert-management\/common\/warning-lifecycle'/)
  assert.match(source, /handled:\s*[^\n]*isWarningHandled\(event\)[^\n]*event\.status === 1/)
  assert.doesNotMatch(source, /handled:\s*event\.handled === true \|\| event\.handled === 1/)
})
