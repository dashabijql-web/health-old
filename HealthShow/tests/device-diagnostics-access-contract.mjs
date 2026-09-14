import assert from 'node:assert/strict'
import fs from 'node:fs'

const router = fs.readFileSync('src/router/health-monitor.mjs', 'utf8')
const controller = fs.readFileSync('../HealthData/src/main/java/com/xzkj/health/controller/WatchRawPacketController.java', 'utf8')

for (const routeName of ['WatchRawPackets', 'WatchControl']) {
  const routeStart = router.indexOf(`name: '${routeName}'`)
  assert.ok(routeStart >= 0, `${routeName} route must exist`)
  const routeBlock = router.slice(routeStart, routeStart + 420)
  assert.match(routeBlock, /permCode:\s*'device:list'/)
  assert.match(routeBlock, /navGroup:\s*'admin'/)
}

assert.equal((controller.match(/diagnosticsAuthorizer\.checkUser\(StpUtil\.getLoginIdAsLong\(\)\)/g) || []).length, 2)

console.log('device diagnostics access contract passed')
