import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'

const ROOT = process.cwd()
const apiLatencyPath = path.join(ROOT, 'tests', 'performance', 'api-latency.mjs')
const source = fs.readFileSync(apiLatencyPath, 'utf8')

test('api latency report builds threshold-utilization triage', () => {
  assert.match(source, /function buildPerformanceTriage\(endpoints\)/)
  assert.match(source, /slowestEndpoints:\s*ranked\.slice\(0, 5\)/)
  assert.match(source, /outlierCandidates:\s*ranked\.filter/)
  assert.match(source, /utilization >= 0\.8/)
  assert.match(source, /headroomPercent/)
})

test('api latency markdown explains slow endpoints and no-outlier state', () => {
  assert.match(source, /## Slowest endpoints \/ threshold utilization/)
  assert.match(source, /\| endpoint \| status \| p95 \| max \| utilization \| headroom \|/)
  assert.match(source, /## Outlier candidates/)
  assert.match(source, /no endpoint reached 80% of its p95\/max threshold/)
})

test('api latency json includes performance triage block before report write', () => {
  assert.match(source, /summary\.performanceTriage\s*=\s*buildPerformanceTriage\(summary\.endpoints\)/)
  assert.ok(
    source.indexOf('summary.performanceTriage = buildPerformanceTriage(summary.endpoints)') < source.indexOf('await fs.writeFile(REPORT_JSON'),
    'triage should be calculated before JSON is written'
  )
})
