import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const healthShowRoot = path.resolve(__dirname, '..')

function readSource(relativePath) {
  return readFileSync(path.join(healthShowRoot, relativePath), 'utf8')
}

test('target beautification pages do not keep banned emoji UI shortcuts', () => {
  const targetFiles = [
    'src/views/health-monitor/trend-warning/index.vue',
    'src/views/health-monitor/risk-warning/index.vue',
    'src/views/alert-management/config/index.vue',
    'src/views/health-monitor/workbench/index.vue',
    'src/views/health-monitor/employee-profile/index.vue',
    'src/views/health-monitor/employee-profile/employee-profile-view-model.js',
    'src/views/health-monitor/mine-entry/index.vue',
    'src/views/personnel-management/health-portrait/index.vue',
    'src/views/personnel-management/health-portrait/health-portrait-view-model.js',
    'src/views/safety-command/index.vue',
    'src/views/safety-command/components/SafetyCommandDialogs.vue',
    'src/views/safety-command/components/EventPanel.vue',
    'src/views/safety-command/components/KpiCardRow.vue',
    'src/views/safety-command/components/RiskPersonPanel.vue',
    'src/views/safety-command/components/PersonDetailDrawer.vue',
    'src/views/safety-command/safety-command-view-model.js',
    'src/views/safety-command/safety-command-interactions.js',
    'src/views/alert-management/sos/index.vue'
  ]
  const bannedUiTokens = [
    '❤',
    '♥',
    '❤️',
    '🩸',
    '💓',
    '💖',
    '💘',
    '📞',
    '📢',
    '🚨',
    '💨',
    '🫀',
    '📈',
    '👟',
    '⚡',
    '🌡',
    '🌡️',
    '📅',
    '✅',
    '✓',
    '⚠',
    '⚠️',
    '🆘',
    '🔇',
    '😴',
    '⛏️',
    '⌚',
    '🫂'
  ]

  const offenders = targetFiles.filter((relativePath) => {
    const source = readSource(relativePath)
    return bannedUiTokens.some((token) => source.includes(token))
  })
  assert.deepEqual(offenders, [], `Remove banned emoji UI shortcuts from: ${offenders.join(', ')}`)
})

test('shared hero shell is adopted on the remaining beautification targets', () => {
  const pages = [
    'src/views/health-monitor/employee-archive/index.vue',
    'src/views/health-monitor/employee-profile/index.vue',
    'src/views/health-monitor/trend-warning/index.vue',
    'src/views/alert-management/config/index.vue'
  ]

  for (const relativePath of pages) {
    const source = readSource(relativePath)
    assert.match(source, /PageHeroHeader/, `${relativePath} should use PageHeroHeader`)
  }
})

test('admin CRUD pages use the shared hero shell', () => {
  const adminPages = [
    'src/views/device-management/index.vue',
    'src/views/user-list/index.vue',
    'src/views/role-management/index.vue',
    'src/views/org-management/department/index.vue',
    'src/views/org-management/job-type/index.vue'
  ]

  for (const relativePath of adminPages) {
    const source = readSource(relativePath)
    assert.match(source, /PageHeroHeader/, `${relativePath} should use PageHeroHeader`)
    assert.match(source, /hm-admin-page/, `${relativePath} should use the shared hm-admin-page shell class`)
  }
})

test('empty-state-sensitive pages use the shared PageEmptyState component', () => {
  const pages = [
    'src/views/health-monitor/trend-warning/index.vue',
    'src/views/alert-management/config/index.vue',
    'src/views/health-monitor/mine-entry/index.vue',
    'src/views/ai-chat/index.vue',
    'src/views/health-monitor/employee-archive/index.vue'
  ]

  for (const relativePath of pages) {
    const source = readSource(relativePath)
    assert.match(source, /PageEmptyState/, `${relativePath} should use PageEmptyState`)
  }
})

test('dashboard support band keeps device and environment panels on equal-height rails', () => {
  const source = readSource('src/views/health-monitor/dashboard/dashboard.scss')

  assert.match(
    source,
    /\.dm-support-band\s*\{[\s\S]*align-items:\s*stretch;/,
    'dashboard support band should stretch both columns to the same height'
  )
  assert.match(
    source,
    /\.dm-support-band\s*>\s*\*\s*\{[\s\S]*height:\s*100%;[\s\S]*min-height:\s*0;/,
    'dashboard support band children should inherit the stretched height'
  )
  assert.match(
    source,
    /\.dm-main-device\s*\{[\s\S]*height:\s*100%;/,
    'device status panel should fill the stretched rail height'
  )
  assert.match(
    source,
    /\.dm-main-env\s*\{[\s\S]*height:\s*100%;/,
    'environment panel should fill the stretched rail height'
  )
})

test('flagship desktop shells avoid one-screen locking and allow full-page cinematic scrolling', () => {
  const realtimeStyle = readSource('src/views/health-monitor/real-time/realtime.scss')

  assert.match(
    realtimeStyle,
    /\.rt-root\s*\{[\s\S]*\n\s*height:\s*auto;/,
    'realtime desktop shell should allow content height to extend the page'
  )
  assert.match(
    realtimeStyle,
    /\.rt-root\s*\{[\s\S]*\n\s*min-height:\s*calc\(100vh - 50px\);/,
    'realtime desktop shell should preserve a full-screen minimum height baseline'
  )
  assert.match(
    realtimeStyle,
    /\.rt-root\s*\{[\s\S]*\n\s*overflow:\s*visible;/,
    'realtime desktop shell should not clip the flagship cinematic page shell'
  )
})

test('second-wave cockpit pages share the flagship shell language', () => {
  const riskWarningSource = readSource('src/views/health-monitor/risk-warning/index.vue')
  const employeeProfileSource = readSource('src/views/health-monitor/employee-profile/index.vue')

  assert.match(
    riskWarningSource,
    /class="hm-page-shell rw-root"/,
    'risk warning should opt into the shared flagship page shell'
  )
  assert.match(
    employeeProfileSource,
    /MetricStrip/,
    'employee profile should expose the shared metric strip in its flagship header band'
  )
})

test('employee profile metric strip derives from the computed summary ref without runtime crashes', () => {
  const source = readSource('src/views/health-monitor/employee-profile/index.vue')

  assert.match(
    source,
    /profileSummaryCards\.value\.map\(/,
    'employee profile metric strip should map the computed summary ref through .value inside script setup'
  )
})

test('risk warning desktop shell avoids fixed viewport locking', () => {
  const styleSource = readSource('src/views/health-monitor/risk-warning/risk-warning.scss')

  assert.match(
    styleSource,
    /\.rw-root\s*\{[\s\S]*\n\s*height:\s*auto;/,
    'risk warning desktop shell should allow content height to extend the page'
  )
  assert.match(
    styleSource,
    /\.rw-root\s*\{[\s\S]*\n\s*min-height:\s*calc\(100vh - 50px\);/,
    'risk warning desktop shell should preserve a full-screen minimum height baseline'
  )
  assert.match(
    styleSource,
    /\.rw-root\s*\{[\s\S]*\n\s*overflow:\s*visible;/,
    'risk warning desktop shell should not clip the cinematic page shell'
  )
})

test('mine entry and report center share the flagship hero and metric strip shell', () => {
  const mineEntrySource = readSource('src/views/health-monitor/mine-entry/index.vue')
  const reportCenterSource = readSource('src/views/health-monitor/report-center/index.vue')

  assert.match(
    mineEntrySource,
    /class="hm-page-shell me-root"/,
    'mine entry should opt into the shared flagship page shell'
  )
  assert.match(
    mineEntrySource,
    /PageHeroHeader/,
    'mine entry should adopt the shared cockpit hero shell'
  )
  assert.match(
    mineEntrySource,
    /MetricStrip/,
    'mine entry should expose a shared metric strip under the cockpit hero'
  )
  assert.match(
    reportCenterSource,
    /class="hm-page-shell rc-page"/,
    'report center should opt into the shared flagship page shell'
  )
  assert.match(
    reportCenterSource,
    /MetricStrip/,
    'report center should expose a shared metric strip for its overview band'
  )
})

test('trend warning and ai chat share the flagship hero and metric strip shell', () => {
  const trendWarningSource = readSource('src/views/health-monitor/trend-warning/index.vue')
  const aiChatSource = readSource('src/views/ai-chat/index.vue')

  assert.match(
    trendWarningSource,
    /class="tw-page hm-page-shell"|class="hm-page-shell tw-page"/,
    'trend warning should opt into the shared flagship page shell'
  )
  assert.match(
    trendWarningSource,
    /MetricStrip/,
    'trend warning should expose a shared metric strip under the cockpit hero'
  )
  assert.match(
    aiChatSource,
    /class="hm-page-shell ai-chat-page"|class="ai-chat-page hm-page-shell"/,
    'ai chat should opt into the shared flagship page shell'
  )
  assert.match(
    aiChatSource,
    /MetricStrip/,
    'ai chat should expose a shared metric strip under the cockpit hero'
  )
})

test('ai chat desktop shell avoids fixed viewport locking', () => {
  const styleSource = readSource('src/views/ai-chat/ai-chat.scss')

  assert.match(
    styleSource,
    /\.ai-chat-page\s*\{[\s\S]*\n\s*height:\s*auto;/,
    'ai chat desktop shell should allow content height to extend the page'
  )
  assert.match(
    styleSource,
    /\.ai-chat-page\s*\{[\s\S]*\n\s*min-height:\s*calc\(100vh - 50px\);/,
    'ai chat desktop shell should preserve a full-screen minimum height baseline'
  )
  assert.match(
    styleSource,
    /\.ai-chat-page\s*\{[\s\S]*\n\s*overflow:\s*visible;/,
    'ai chat desktop shell should not clip the flagship cinematic page shell'
  )
})

test('report center desktop shell avoids fixed viewport locking', () => {
  const styleSource = readSource('src/views/health-monitor/report-center/report-center.scss')

  assert.match(
    styleSource,
    /\.rc-page\s*\{[\s\S]*\n\s*height:\s*auto;/,
    'report center desktop shell should allow content height to extend the page'
  )
  assert.match(
    styleSource,
    /\.rc-page\s*\{[\s\S]*\n\s*min-height:\s*calc\(100vh - 50px\);/,
    'report center desktop shell should preserve a full-screen minimum height baseline'
  )
  assert.match(
    styleSource,
    /\.rc-page\s*\{[\s\S]*\n\s*overflow:\s*visible;/,
    'report center desktop shell should not clip the cinematic page shell'
  )
})

test('dashboard warning stream action cluster keeps pending label and handle button on a dedicated inline rail', () => {
  const viewSource = readSource('src/views/health-monitor/dashboard/components/DashboardWarningStream.vue')
  const styleSource = readSource('src/views/health-monitor/dashboard/dashboard.scss')

  assert.match(
    viewSource,
    /class="dm-ev-actions"/,
    'dashboard warning stream should use a dedicated action wrapper for pending state controls'
  )
  assert.match(
    styleSource,
    /\.dm-ev-actions\s*\{[\s\S]*display:\s*inline-flex;[\s\S]*gap:\s*[0-9]+px;/,
    'dashboard warning stream action wrapper should reserve explicit spacing between label and button'
  )
})

test('dashboard overview rail keeps the health snapshot in a single-column layout with readable two-column vital cards', () => {
  const viewSource = readSource('src/views/health-monitor/dashboard/index.vue')
  const styleSource = readSource('src/views/health-monitor/dashboard/dashboard.scss')

  assert.match(viewSource, /class="dm-vital-head"/, 'dashboard vital cards should expose a dedicated header row')
  assert.match(viewSource, /class="dm-vital-reading"/, 'dashboard vital cards should expose a dedicated value row')
  assert.match(viewSource, /class="dm-vital-foot"/, 'dashboard vital cards should expose a dedicated status row')
  assert.match(viewSource, /class="dm-assess-bars"/, 'dashboard overview rail should include the compact health assessment footer')
  assert.doesNotMatch(viewSource, /dm-command-section--dept/, 'dashboard overview rail should not keep the broken hidden dept chart section')

  assert.match(
    styleSource,
    /\.dm-command-overview-body\s*\{[\s\S]*display:\s*flex;[\s\S]*flex-direction:\s*column;/,
    'dashboard overview body should stack its internal sections vertically in the narrow rail'
  )
  assert.match(
    styleSource,
    /\.dm-vitals-grid\s*\{[\s\S]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/,
    'dashboard overview rail should keep vital cards in a readable two-column grid'
  )
  assert.match(
    styleSource,
    /\.dm-vital-card\s*\{[\s\S]*display:\s*flex;[\s\S]*flex-direction:\s*column;/,
    'dashboard vital cards should use a stacked layout instead of the collapsed inline micro-card layout'
  )
})

test('dashboard duty assist keeps unique decision support without a second closure queue', () => {
  const source = readSource('src/views/health-monitor/dashboard/components/DashboardDispatchPanel.vue')

  assert.match(source, /dm-dispatch-mission/, 'dashboard duty panel should expose the mission banner shell')
  assert.match(source, /class="dm-dispatch-mission-cta"/, 'dashboard duty panel should expose the primary workflow CTA')
  assert.match(source, /class="dm-dispatch-workspace"/, 'dashboard duty panel should expose the assist workspace')
  assert.match(source, /class="dm-dispatch-assist-card dm-dispatch-assist-card--summary"/, 'dashboard duty panel should keep the AI summary in a dedicated assist card')
  assert.match(source, /class="dm-dispatch-assist-card dm-dispatch-assist-card--focus"/, 'dashboard duty panel should keep the focus people list in a dedicated assist card')
  assert.doesNotMatch(source, /class="dm-dispatch-queue"/, 'dashboard duty assist should not duplicate the authoritative closure queue')
  assert.doesNotMatch(source, /dm-dispatch-queue-item/, 'dashboard duty assist should not repeat closure metrics')
  assert.doesNotMatch(source, /class="dm-dispatch-actions"/, 'dashboard duty panel should retire the old three-card action strip')
  assert.doesNotMatch(source, /class="dm-dispatch-grid"/, 'dashboard duty panel should retire the old duplicated stat grid')
})

test('dashboard medium desktop breakpoint rebalances the three-column command band before the center workflow collapses', () => {
  const source = readSource('src/views/health-monitor/dashboard/dashboard.scss')

  assert.match(source, /@media \(max-width:\s*1500px\) and \(min-width:\s*1101px\)/)
  assert.match(
    source,
    /@media \(max-width:\s*1500px\) and \(min-width:\s*1101px\)[\s\S]*\.dm-command-band\s*\{[\s\S]*grid-template-columns:\s*[0-9]+px minmax\(0,\s*1fr\);/,
    'dashboard medium desktop layout should collapse the right rail under the main workspace before the center column becomes unreadable'
  )
  assert.match(
    source,
    /@media \(max-width:\s*1500px\) and \(min-width:\s*1101px\)[\s\S]*\.dm-command-sidebar\s*\{[\s\S]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/,
    'dashboard medium desktop layout should turn the right rail into a two-card strip below the main columns'
  )
})

test('employee profile desktop shell prioritizes full-page scrolling over fixed one-screen locking', () => {
  const styleSource = readSource('src/views/health-monitor/employee-profile/employee-profile.scss')
  const desktopShellSection = styleSource.split('.ep-hero')[0]

  assert.match(
    desktopShellSection,
    /\.ep-page\s*\{[\s\S]*\n\s*height:\s*auto;/,
    'employee profile desktop shell should allow content height to extend the page'
  )
  assert.match(
    desktopShellSection,
    /\.ep-page\s*\{[\s\S]*\n\s*min-height:\s*calc\(100vh - 50px\);/,
    'employee profile desktop shell should still preserve a full-screen minimum height baseline'
  )
  assert.match(
    styleSource,
    /\.ep-body\s*\{[\s\S]*overflow:\s*visible;[\s\S]*min-height:\s*0;/,
    'employee profile body should not clip content inside the cockpit grid'
  )
  assert.match(
    styleSource,
    /\.ep-left\s*\{[\s\S]*overflow:\s*visible;/,
    'employee profile left rail should allow full content to extend the page'
  )
  assert.match(
    styleSource,
    /\.ep-center\s*\{[\s\S]*overflow:\s*visible;/,
    'employee profile center stage should allow full content to extend the page'
  )
  assert.match(
    styleSource,
    /\.ep-right\s*\{[\s\S]*overflow:\s*visible;/,
    'employee profile right rail should allow full content to extend the page'
  )
})

test('safety command narrow desktop leaderboard drops the status pill before columns collide', () => {
  const source = readSource('src/views/safety-command/components/DeptRankTable.vue')

  assert.match(source, /@media \(max-width: 1500px\) and \(min-width: 769px\)/)
  assert.match(
    source,
    /th:nth-child\(7\),[\s\S]*td:nth-child\(7\)[\s\S]*display:\s*none;/,
    'narrow desktop leaderboard should hide the status column before text overlaps'
  )
})

test('safety command adopts the shared flagship command shell and summary strip', () => {
  const source = readSource('src/views/safety-command/index.vue')

  assert.match(
    source,
    /class="hm-page-shell sc-war-room"/,
    'safety command should opt into the shared flagship page shell'
  )
  assert.match(source, /PageHeroHeader/, 'safety command should use the shared cockpit hero')
  assert.match(source, /MetricStrip/, 'safety command should expose the shared metric strip')
  assert.doesNotMatch(source, /<KpiCardRow/, 'safety command should not keep a second bespoke KPI strip in the body grid')
})

test('safety command desktop shell avoids fixed one-screen locking after redesign', () => {
  const styleSource = readSource('src/views/safety-command/safety-command.scss')
  const desktopShellSection = styleSource.split('@media (max-width: 768px)')[0]

  assert.match(
    desktopShellSection,
    /\.cc\s*\{[\s\S]*\n\s*height:\s*auto;/,
    'safety command desktop shell should allow content height to extend the page'
  )
  assert.match(
    desktopShellSection,
    /\.cc\s*\{[\s\S]*\n\s*min-height:\s*calc\(100vh - 50px\);/,
    'safety command desktop shell should preserve a full-screen minimum height baseline'
  )
  assert.match(
    desktopShellSection,
    /\.cc\s*\{[\s\S]*\n\s*overflow-y:\s*auto;/,
    'safety command desktop shell should keep vertical page scrolling available'
  )
})

test('dashboard command hero and command band prioritize unified control decisions', () => {
  const viewSource = readSource('src/views/health-monitor/dashboard/index.vue')
  const styleSource = readSource('src/views/health-monitor/dashboard/dashboard.scss')

  assert.match(
    viewSource,
    /title="统一管控"/,
    'dashboard hero should make unified control the primary page identity'
  )
  assert.match(
    styleSource,
    /\.dm-command-band\s*\{[\s\S]*grid-template-columns:\s*minmax\(260px,\s*0\.72fr\)\s+minmax\(560px,\s*1\.45fr\)\s+minmax\(280px,\s*0\.74fr\);/,
    'dashboard command band should reserve the widest desktop rail for the duty decision panel'
  )
})

test('command center A+C redesign keeps explicit legacy backups', () => {
  const backupFiles = [
    'src/views/safety-command/legacy-20260601/index.vue',
    'src/views/safety-command/legacy-20260601/safety-command.scss',
    'src/views/health-monitor/dashboard/legacy-20260601/index.vue',
    'src/views/health-monitor/dashboard/legacy-20260601/dashboard.scss'
  ]

  for (const relativePath of backupFiles) {
    assert.doesNotThrow(
      () => readSource(relativePath),
      `${relativePath} should preserve the pre-redesign page as an explicit local backup`
    )
  }
})

test('safety command A+C redesign reads as a field command war room', () => {
  const viewSource = readSource('src/views/safety-command/index.vue')
  const styleSource = readSource('src/views/safety-command/safety-command.scss')

  assert.match(viewSource, /class="hm-page-shell sc-war-room"/, 'safety command should use the new war-room shell')
  assert.match(viewSource, /sc-incident-hero/, 'safety command should lead with an incident response hero')
  assert.match(viewSource, /sc-situation-stage/, 'safety command should include a central situation stage')
  assert.match(viewSource, /sc-response-queue/, 'safety command should include a primary response queue')
  assert.match(viewSource, /sc-command-ribbon/, 'safety command should keep only a compact metric ribbon above the stage')
  assert.match(
    styleSource,
    /\.sc-war-grid\s*\{[\s\S]*grid-template-columns:\s*minmax\(520px,\s*1\.18fr\)\s+minmax\(360px,\s*0\.82fr\);/,
    'safety command desktop layout should reserve the dominant width for the situation stage'
  )
})

test('dashboard A+C redesign reads as a duty closure control system', () => {
  const viewSource = readSource('src/views/health-monitor/dashboard/index.vue')
  const styleSource = readSource('src/views/health-monitor/dashboard/dashboard.scss')

  assert.match(viewSource, /class="hm-page-shell db-control-system"/, 'dashboard should use the new control-system shell')
  assert.match(viewSource, /db-duty-hero/, 'dashboard should lead with a duty decision hero')
  assert.match(viewSource, /db-closure-lane/, 'dashboard should expose the warning closure lane')
  assert.match(viewSource, /db-governance-workspace/, 'dashboard should include the governance workspace')
  assert.match(viewSource, /db-health-rail/, 'dashboard should keep a compact health snapshot rail')
  assert.match(
    styleSource,
    /\.db-control-grid\s*\{[\s\S]*grid-template-columns:\s*minmax\(360px,\s*0\.76fr\)\s+minmax\(620px,\s*1\.24fr\);/,
    'dashboard desktop layout should reserve the dominant width for the closure workspace'
  )
})

test('risk warning medium desktop breakpoint preserves dept chart height', () => {
  const source = readSource('src/views/health-monitor/risk-warning/risk-warning.scss')

  assert.match(source, /@media \(max-width: 1600px\) and \(min-width: 769px\)/)
  assert.match(
    source,
    /\.rw-aside-top\s*\{[\s\S]*height:\s*165px;/,
    'risk warning medium desktop breakpoint should shorten the stats panel to free chart space'
  )
  assert.match(
    source,
    /\.rw-aside-bot[\s\S]*\.rw-pc\s*>\s*div\s*\{[\s\S]*min-height:\s*160px;/,
    'risk warning medium desktop breakpoint should enforce a minimum chart body height'
  )
})

test('dashboard charts stay outside vue reactivity and are reused across refreshes', () => {
  const pageState = readSource('src/views/health-monitor/dashboard/dashboard-page-state.js')
  const chartMethods = readSource('src/views/health-monitor/dashboard/dashboard-chart-methods.js')
  const viewActions = readSource('src/views/health-monitor/dashboard/dashboard-view-actions.js')
  const detailMethods = readSource('src/views/health-monitor/dashboard/dashboard-detail-methods.js')
  const runtime = readSource('src/views/health-monitor/dashboard/dashboard-runtime.js')
  const lifecycle = readSource('src/views/health-monitor/dashboard/dashboard-lifecycle.js')
  const pageHook = readSource('src/views/health-monitor/dashboard/use-dashboard-page.ts')

  assert.match(pageState, /charts:\s*markRaw\(\{\}\)/, 'chart map must not be deeply observed')
  assert.match(pageState, /seenAlertIds:\s*markRaw\(new Set\(\)\)/, 'alert id set must not be deeply observed')
  assert.match(chartMethods, /export function bindDashboardChart/)
  assert.match(chartMethods, /markRaw\(theme \? echarts\.init\(dom, theme\) : echarts\.init\(dom\)\)/)
  assert.doesNotMatch(
    chartMethods,
    /this\.charts\.\w+\s*=\s*(chart|echarts\.init)/,
    'refresh must not assign raw echarts instances onto the reactive page model'
  )
  assert.match(viewActions, /bindDashboardChart\(/)
  assert.match(viewActions, /CHART_RETRY_LIMIT/)
  assert.match(viewActions, /_chartRetryToken/)
  assert.match(detailMethods, /bindDashboardChart\(/)
  assert.match(runtime, /finally\s*\{\s*this\.isRefreshing = false/)
  assert.match(runtime, /runLimited\(/)
  assert.match(runtime, /runLimited\(\[[\s\S]*fetchWarningEvents\(\)[\s\S]*loadDeptData\(force\)[\s\S]*\], 2\)/)
  assert.match(lifecycle, /disposeChart\(vm\._envChart\)/)
  assert.match(lifecycle, /_chartRetryToken/)
  assert.match(pageHook, /fn\.bind\(ctx\)/, 'template refresh must keep dashboard methods bound to page state')
})

test('dashboard loading does not schedule repeated forced scroll-to-top resets', () => {
  const source = readSource('src/views/health-monitor/dashboard/dashboard-lifecycle.js')

  assert.doesNotMatch(source, /const checkpoints\s*=\s*\[/)
  assert.doesNotMatch(source, /window\.setTimeout\(\(\)\s*=>\s*forceDashboardScrollTop/)
  assert.doesNotMatch(source, /vm\._scrollResetTimers/)
  assert.doesNotMatch(source, /vm\.fetchData\(\)\.then\(\(\)\s*=>\s*\{\s*resetDashboardScroll\(vm\)/)
})

test('dashboard background mine AI cache preload stays silent on empty reports', () => {
  const aiSource = readSource('src/api/ai.js')
  const requestSource = readSource('src/utils/request.js')

  assert.match(
    aiSource,
    /getMineAiReport\(\)\s*\{[\s\S]*silentError:\s*true/,
    'dashboard mine AI cache preload should not raise a global message when no cached report exists'
  )
  assert.match(
    requestSource,
    /response\.config\?\.silentError/,
    'business-code failures should respect request silentError'
  )
  assert.match(
    requestSource,
    /config\?\.silentError/,
    'HTTP failures should respect request silentError'
  )
})

test('real-time desktop table keeps a deliberate horizontal scroll strategy instead of clipping action columns', () => {
  const source = readSource('src/views/health-monitor/real-time/realtime.scss')
  const viewSource = readSource('src/views/health-monitor/real-time/components/RealtimeUserTable.vue')

  assert.doesNotMatch(
    viewSource,
    /<el-table-column label="#"/,
    'real-time desktop table should drop the serial number column to free space for the right-side fields'
  )

  assert.match(
    source,
    /\.rt-tbl-wrap\s*\{[\s\S]*overflow-x:\s*auto;/,
    'real-time table wrapper should preserve horizontal scrolling on desktop'
  )
  assert.match(
    source,
    /\.el-table\s*\{[\s\S]*min-width:\s*13[0-9]{2}px;/,
    'real-time table should keep an explicit desktop min-width baseline'
  )
  assert.match(
    source,
    /@media \(max-width:\s*1600px\)\s*and\s*\(min-width:\s*769px\)/,
    'real-time table should define a medium desktop breakpoint for denser table layouts'
  )
  assert.match(
    source,
    /\.rt-tbl-wrap \.el-table\s*\{[\s\S]*min-width:\s*12[0-9]{2}px;/,
    'real-time medium desktop breakpoint should reduce table min-width to keep more columns visible'
  )
})

test('employee profile desktop body must remain vertically scrollable when content exceeds one screen', () => {
  const styleSource = readSource('src/views/health-monitor/employee-profile/employee-profile.scss')
  const desktopShellSection = styleSource.split('@media (max-width: 768px)')[0]

  assert.match(
    desktopShellSection,
    /\.ep-page\s*\{[\s\S]*\n\s*overflow-y:\s*auto;/,
    'employee profile desktop shell should allow vertical page scrolling'
  )
  assert.match(
    desktopShellSection,
    /\.ep-body\s*\{[\s\S]*\n\s*overflow:\s*visible;/,
    'employee profile body should not trap vertical overflow inside a clipped cockpit grid'
  )
})

test('employee profile scroll mode keeps cockpit proportions bounded on desktop', () => {
  const styleSource = readSource('src/views/health-monitor/employee-profile/employee-profile.scss')

  assert.match(
    styleSource,
    /\.ep-body\s*\{[\s\S]*align-items:\s*start;/,
    'employee profile body should stop stretching all three columns to the tallest content'
  )
  assert.match(
    styleSource,
    /\.ep-side-panels\s*\{[\s\S]*justify-content:\s*flex-start;/,
    'employee profile side panels should stack from the top instead of centering through a giant column'
  )
  assert.match(
    styleSource,
    /\.ep-scroll-panel\s*\{[\s\S]*flex:\s*0 0 auto;/,
    'employee profile side scroll panels should use bounded panel heights in page-scroll mode'
  )
  assert.match(
    styleSource,
    /\.ep-miner-stage\s*\{[\s\S]*max-height:\s*[0-9]+px;/,
    'employee profile miner stage should have a desktop max-height guardrail'
  )
})

test('employee profile trend and warning modules use summary rails instead of raw marquee blocks', () => {
  const viewSource = readSource('src/views/health-monitor/employee-profile/index.vue')
  const styleSource = readSource('src/views/health-monitor/employee-profile/employee-profile.scss')
  const viewModelSource = readSource('src/views/health-monitor/employee-profile/use-employee-profile-page.js')
  const leftSection = viewSource.split('<main class="ep-center">')[0]

  assert.match(viewSource, /class="ep-trend-stats"/)
  assert.match(viewSource, /class="ep-warn-summary"/)
  assert.match(viewSource, /class="ep-warn-footer"/)
  assert.match(viewSource, /class="ep-panel ep-warning-band"/)
  assert.doesNotMatch(leftSection, /class="ep-panel ep-warns"/)
  assert.doesNotMatch(viewSource, /warnings\.concat\(warnings\)/)
  assert.match(styleSource, /\.ep-trend-stats\s*\{/)
  assert.match(styleSource, /\.ep-warn-summary\s*\{/)
  assert.match(styleSource, /\.ep-warning-band__body\s*\{/)
  assert.match(styleSource, /\.ep-trend-chart\s*\{[\s\S]*height:\s*1[0-5][0-9]px;/)
  assert.match(styleSource, /\.ep-warning-band__list\s*\{/)
  assert.match(viewModelSource, /recentWarnings\s*=\s*computed\(\(\)\s*=>\s*warnings\.value\.slice\(0,\s*4\)\)/)
})
