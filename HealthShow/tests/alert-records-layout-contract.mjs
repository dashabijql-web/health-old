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

test('alert records desktop table extends downward instead of being compressed into one viewport', () => {
  const source = readSource('src/views/alert-management/records/records.scss')

  assert.match(
    source,
    /\.page-container\s*\{[^}]*\n\s*height:\s*auto;[^}]*\n\s*min-height:\s*calc\(100vh - 50px\);[^}]*\n\s*overflow-y:\s*auto;[^}]*\n\s*overflow-x:\s*hidden;/,
    'records page should keep a full-screen minimum while allowing the page to scroll downward'
  )
  assert.match(
    source,
    /\.panel\.table-panel\s*\{[^}]*\n\s*flex:\s*none;[^}]*\n\s*height:\s*clamp\(640px,\s*64vh,\s*920px\);[^}]*\n\s*max-height:\s*none;/,
    'warning records table panel should get an explicit tall desktop rail instead of flexing into leftover viewport space'
  )
  assert.match(
    source,
    /\.table-body\s*\{[^}]*flex:\s*1;[^}]*min-height:\s*0;[^}]*overflow:\s*hidden;/,
    'table body should keep internal Element Plus scrolling inside the expanded table panel'
  )
  assert.doesNotMatch(
    source,
    /\.page-container\s*\{[^}]*\n\s*height:\s*calc\(100vh - 50px\);[^}]*\n\s*overflow:\s*hidden;/,
    'records page should not lock the whole desktop page to one clipped viewport'
  )
})
