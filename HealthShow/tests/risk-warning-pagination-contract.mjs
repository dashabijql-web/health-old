import assert from 'node:assert/strict'
import fs from 'node:fs'

const runtime = fs.readFileSync('src/views/health-monitor/risk-warning/risk-warning-runtime.js', 'utf8')
const template = fs.readFileSync('src/views/health-monitor/risk-warning/index.vue', 'utf8')
const api = fs.readFileSync('src/api/risk-warning.js', 'utf8')
const controller = fs.readFileSync('../HealthData/src/main/java/com/xzkj/health/controller/RiskWarningController.java', 'utf8')

assert.match(runtime, /page:\s*this\.currentPage/)
assert.match(runtime, /size:\s*this\.pageSize/)
assert.match(runtime, /keyword:\s*this\.filterName\.trim\(\)/)
assert.match(runtime, /this\.totalWarnings\s*=\s*Number\(r\.data\.total/)
assert.match(template, /\{\{\s*currentPage\s*\}\}\s*\/\s*\{\{\s*totalPages\s*\}\}/)
assert.match(api, /data:\s*locators/)
assert.match(runtime, /warningId:\s*row\.id,\s*occurredAt:\s*row\.createTime/)
assert.match(controller, /List<RiskWarningLocatorRequest>\s+locators/)

console.log('risk warning pagination contract passed')
