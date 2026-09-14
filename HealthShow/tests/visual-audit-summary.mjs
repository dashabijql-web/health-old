import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'

const ROOT = process.cwd()
const auditPath = path.join(ROOT, 'tests', 'visual', 'page-layout-audit.mjs')
const auditSource = fs.readFileSync(auditPath, 'utf8')

test('visual audit default route inventory includes warning lifecycle records route', () => {
  assert.match(auditSource, /slug:\s*'alert-notifications'/)
  assert.match(auditSource, /slug:\s*'alert-records'/)
  assert.match(auditSource, /path:\s*'\/alert-management\/records'/)
  assert.match(auditSource, /slug:\s*'workbench'/)
  assert.match(auditSource, /path:\s*'\/health-monitor\/workbench'/)
  assert.match(auditSource, /slug:\s*'employee-profile'/)
  assert.match(auditSource, /path:\s*'\/health-monitor\/employee-profile'/)
  assert.match(auditSource, /slug:\s*'mine-entry'/)
  assert.match(auditSource, /path:\s*'\/health-monitor\/mine-entry'/)
  assert.match(auditSource, /slug:\s*'device-management'/)
  assert.match(auditSource, /path:\s*'\/admin\/device-list'/)
  assert.match(auditSource, /slug:\s*'trend-warning'/)
  assert.match(auditSource, /path:\s*'\/health-monitor\/trend-warning'/)
  for (const slug of ['heart-rate', 'pressure', 'blood-pressure', 'blood-oxygen']) {
    assert.match(auditSource, new RegExp(`slug:\\s*'${slug}'`))
  }
})

test('visual audit rejects unknown route filters and checks mobile metric canvases', () => {
  assert.match(auditSource, /Unknown VISUAL_ROUTES/)
  assert.match(auditSource, /mobileCanvasMinimum/)
  assert.match(auditSource, /mobile_chart_missing/)
})

await import('./metric-analysis-contract.mjs')

test('visual audit writes route-level artifact summaries', () => {
  assert.match(auditSource, /summary\.routeSummaries\s*=/)
  assert.match(auditSource, /passedViewports/)
  assert.match(auditSource, /failedViewports/)
  assert.match(auditSource, /issueCount/)
  assert.match(auditSource, /rerunCommand:\s*`node scripts\/with-env\.mjs VISUAL_ROUTES=\$\{routeSlug\} -- npm run audit:visual`/)
  assert.match(auditSource, /path\.basename\(result\.screenshot\)/)
})

test('visual audit markdown includes triage table and rerun hints', () => {
  assert.match(auditSource, /## Route Summary/)
  assert.match(auditSource, /\| route \| status \| passed viewports \| failed viewports \| issues \| rerun \| screenshots \|/)
  assert.match(auditSource, /rerun_all: npm run audit:visual/)
  assert.match(auditSource, /rerun_one_route: node scripts\/with-env\.mjs VISUAL_ROUTES=<route-slug> -- npm run audit:visual/)
})

test('visual audit groups issues by type for faster failure triage', () => {
  assert.match(auditSource, /summary\.issueTypeSummaries\s*=/)
  assert.match(auditSource, /## Issue Type Summary/)
  assert.match(auditSource, /\| issue type \| count \| routes \| viewports \|/)
  assert.match(auditSource, /routes:\s*Array\.from\(item\.routes\)\.sort\(\)/)
  assert.match(auditSource, /viewports:\s*Array\.from\(item\.viewports\)\.sort\(\)/)
})

test('visual audit supports a reliable windows desktop viewport matrix', () => {
  assert.match(auditSource, /VISUAL_VIEWPORT_PROFILE/)
  assert.match(auditSource, /windows-desktop-matrix/)
  assert.match(auditSource, /slug:\s*'fhd-100'/)
  assert.match(auditSource, /width:\s*1920,\s*height:\s*1080/)
  assert.match(auditSource, /slug:\s*'fhd-125'/)
  assert.match(auditSource, /width:\s*1536,\s*height:\s*864/)
  assert.match(auditSource, /slug:\s*'fhd-150'/)
  assert.match(auditSource, /width:\s*1280,\s*height:\s*720/)
  assert.match(auditSource, /slug:\s*'qhd-100'/)
  assert.match(auditSource, /width:\s*2560,\s*height:\s*1440/)
  assert.match(auditSource, /slug:\s*'qhd-125'/)
  assert.match(auditSource, /width:\s*2048,\s*height:\s*1152/)
  assert.match(auditSource, /slug:\s*'qhd-150'/)
  assert.match(auditSource, /width:\s*1707,\s*height:\s*960/)
})

test('visual audit validates runtime viewport metrics and non-fullpage png dimensions', () => {
  assert.match(auditSource, /window\.innerWidth/)
  assert.match(auditSource, /window\.innerHeight/)
  assert.match(auditSource, /window\.devicePixelRatio/)
  assert.match(auditSource, /visualViewport\?\.scale/)
  assert.match(auditSource, /readPngSize|pngDimensions|screenshotDimensions/)
  assert.match(auditSource, /page\.screenshot\(\{[\s\S]*fullPage:\s*false/)
  assert.match(auditSource, /viewport_mismatch/)
  assert.match(auditSource, /device_pixel_ratio_mismatch/)
  assert.match(auditSource, /screenshot_size_mismatch/)
})

test('visual audit supports optional scroll evidence capture for page and inner scroll containers', () => {
  assert.match(auditSource, /VISUAL_SCROLL_AUDIT/)
  assert.match(auditSource, /scrollArtifacts/)
  assert.match(auditSource, /pageScrollStates/)
  assert.match(auditSource, /scrollContainers/)
  assert.match(auditSource, /scroll-mid|page-mid/)
  assert.match(auditSource, /scroll-bottom|page-bottom/)
  assert.match(auditSource, /container-top|scroll-container-top/)
  assert.match(auditSource, /container-bottom|scroll-container-bottom/)
  assert.match(auditSource, /largest scroll container|scrollable container/i)
})

test('package scripts expose a windows desktop scroll audit entrypoint', () => {
  const packagePath = path.join(ROOT, 'package.json')
  const pkg = JSON.parse(fs.readFileSync(packagePath, 'utf8'))
  assert.equal(
    pkg.scripts['audit:visual:windows-desktop-scroll'],
    'node scripts/with-env.mjs VISUAL_VIEWPORT_PROFILE=windows-desktop-matrix VISUAL_SCROLL_AUDIT=1 -- npm run audit:visual'
  )
})
