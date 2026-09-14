export const LOGIN_CREDENTIALS = Object.freeze({
  username: process.env.HEALTH_TEST_USERNAME || 'admin',
  password: process.env.HEALTH_TEST_PASSWORD || 'admin123'
})

export const API_TARGETS = Object.freeze([
  { label: 'vite-127', origin: 'http://127.0.0.1:9528', apiPrefix: '/dev-api' },
  { label: 'vite-localhost', origin: 'http://localhost:9528', apiPrefix: '/dev-api' },
  { label: 'backend-127', origin: 'http://127.0.0.1:8080', apiPrefix: '/health' },
  { label: 'backend-localhost', origin: 'http://localhost:8080', apiPrefix: '/health' }
])

export const FRONTEND_BASE_URLS = Object.freeze([
  'http://127.0.0.1:9528',
  'http://localhost:9528',
  'http://127.0.0.1:4173',
  'http://localhost:4173'
])

export const DEFAULT_E2E_BASE_URL = 'http://127.0.0.1:4173'

export function truncate(value, max = 260) {
  if (!value) return ''
  const text = String(value)
  return text.length > max ? `${text.slice(0, max - 3)}...` : text
}

export function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

export function assert(condition, message) {
  if (!condition) throw new Error(message)
}

export function formatLocalDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export async function pruneArtifacts(rootDir, keep = 10, fsModule) {
  if (!Number.isFinite(keep) || keep <= 0) return
  const fs = fsModule || await import('node:fs/promises')
  const path = await import('node:path')
  const entries = await fs.readdir(rootDir, { withFileTypes: true }).catch(() => [])
  const dirs = entries.filter((entry) => entry.isDirectory()).map((entry) => entry.name).sort()
  const obsolete = dirs.slice(0, Math.max(0, dirs.length - keep))
  await Promise.allSettled(
    obsolete.map((dirName) => fs.rm(path.join(rootDir, dirName), { recursive: true, force: true }))
  )
}

export async function tryLogin(target, credentials = LOGIN_CREDENTIALS) {
  assert(credentials.username && credentials.password, 'test login credentials are required')
  const response = await fetch(`${target.origin}${target.apiPrefix}/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(credentials)
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const payload = await response.json()
  assert(payload?.code === 200, payload?.message || 'login rejected')
  assert(payload?.data?.token, 'login response missing token')

  return { target, token: payload.data.token }
}

export async function resolveSession({ targets = API_TARGETS, credentials = LOGIN_CREDENTIALS } = {}) {
  const errors = []
  for (const target of targets) {
    try {
      return await tryLogin(target, credentials)
    } catch (error) {
      errors.push(`${target.label}: ${truncate(String(error))}`)
    }
  }
  throw new Error(`unable to login to any target: ${errors.join(' | ')}`)
}

export function buildAuthHeaders(session, extraHeaders = {}, dataSource = '') {
  const cookies = [`User-Token=${session.token}`, `satoken=${session.token}`]
  if (dataSource) cookies.push(`Health-Data-Source=${dataSource}`)
  return {
    accept: 'application/json',
    satoken: session.token,
    cookie: cookies.join('; '),
    ...(dataSource ? { 'X-Health-Data-Source': dataSource } : {}),
    ...extraHeaders
  }
}

export async function requestJson(session, method, routePath, options = {}) {
  const url = new URL(`${session.target.origin}${session.target.apiPrefix}${routePath}`)
  const { query, data, headers, dataSource } = options

  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value))
      }
    }
  }

  const response = await fetch(url, {
    method,
    headers: buildAuthHeaders(session, data ? { 'content-type': 'application/json', ...headers } : headers, dataSource),
    body: data ? JSON.stringify(data) : undefined
  })

  const text = await response.text()
  let payload = null
  try {
    payload = text ? JSON.parse(text) : null
  } catch {
    payload = null
  }

  return { method, url: url.toString(), status: response.status, payload, raw: text }
}

export function assertResultOk(result) {
  assert(result.status >= 200 && result.status < 300, `HTTP ${result.status}`)
  assert(isObject(result.payload), 'response body is not JSON object')
  assert(result.payload.code === 200, result.payload.message || `unexpected code ${result.payload.code}`)
}

export async function resolveFrontendBaseUrl({ baseUrl = process.env.BASE_URL, candidates = FRONTEND_BASE_URLS } = {}) {
  if (baseUrl) return baseUrl
  for (const candidate of candidates) {
    try {
      const response = await fetch(`${candidate}/dev-api/auth/login`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(LOGIN_CREDENTIALS)
      })
      if (!response.ok) continue
      const payload = await response.json().catch(() => null)
      if (payload?.code === 200 && payload?.data?.token) return candidate
    } catch {
      // Try the next candidate.
    }
  }
  return DEFAULT_E2E_BASE_URL
}
