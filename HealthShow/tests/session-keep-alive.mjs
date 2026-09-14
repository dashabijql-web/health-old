import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const source = (relativePath) => readFileSync(resolve(__dirname, '..', relativePath), 'utf8')

test('sidebar menu clicks stay inside Vue Router instead of document-reloading', () => {
  const sidebarItem = source('src/layout/components/Sidebar/SidebarItem.vue')
  const sidebar = source('src/layout/components/Sidebar/index.vue')

  assert.doesNotMatch(sidebarItem, /<(app-link|AppLink|router-link)\b/i)
  assert.match(sidebar, /@select="onMenuSelect"/)
  assert.match(sidebar, /preventNativeMenuNavigation/)
  assert.match(sidebar, /router\.push\(index\)/)
})

test('login hydrates session from /auth/login and does not wait on a second /auth/info', () => {
  const userStore = source('src/store/modules/user.js')
  const loginPage = source('src/views/login/index.vue')

  assert.match(userStore, /commit\('SET_USERINFO', result\.data\)/)
  assert.match(userStore, /getPermittedAppRoutes\(result\.data\.routes/)
  assert.doesNotMatch(loginPage, /dispatch\('user\/getInfo'\)/)
  assert.match(loginPage, /import\('@\/layout\/index\.vue'\)/)
})

test('document reload restores the shell before a slow auth query finishes', () => {
  const userStore = source('src/store/modules/user.js')
  const permission = source('src/permission.js')

  assert.match(userStore, /USER_PROFILE_CACHE_KEY/)
  assert.match(userStore, /profileFromCache/)
  assert.match(permission, /AUTH_RESTORE_BUDGET_MS\s*=\s*900/)
  assert.match(permission, /waitForAuthRestore/)
  assert.match(permission, /refreshCachedUserInfo/)
})

test('HTTP 500 and heartbeat failures must not wipe the login cookie', () => {
  const requestSource = source('src/utils/request.js')
  const heartbeat = source('src/heartbeat.js')
  const permission = source('src/permission.js')
  const auth = source('src/utils/auth.ts')

  assert.doesNotMatch(requestSource, /isTokenIssue/)
  assert.match(requestSource, /只有 401 才清登录态/)
  assert.match(requestSource, /export function isUnauthorizedError/)
  assert.match(permission, /isUnauthorizedError\(error\)/)
  assert.doesNotMatch(heartbeat, /resetToken/)
  assert.doesNotMatch(heartbeat, /replace\(['"]\/login['"]\)/)
  assert.match(auth, /path: '\/'/)
  assert.match(auth, /TOKEN_COOKIE_OPTIONS/)
})
