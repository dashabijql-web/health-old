import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'

const ROOT = process.cwd()
const scssPath = path.join(ROOT, 'src', 'views', 'ai-chat', 'ai-chat.scss')
const vuePath = path.join(ROOT, 'src', 'views', 'ai-chat', 'index.vue')
const runnerPath = path.join(ROOT, 'scripts', 'health-test-runner.mjs')

function read(filePath) {
  return fs.readFileSync(filePath, 'utf8')
}

test('AI chat query result table has horizontal overflow protection', () => {
  const scss = read(scssPath)

  assert.match(scss, /\.viz-table-wrap\s*\{[\s\S]*overflow-x:\s*auto;/)
  assert.match(scss, /\.viz-table\s*\{[\s\S]*min-width:\s*max-content;/)
  assert.match(scss, /\.viz-table\s*\{[\s\S]*max-width:\s*none;/)
})

test('AI chat mobile layout lets result bubbles use available viewport width', () => {
  const scss = read(scssPath)

  assert.match(scss, /@media\s*\(max-width:\s*768px\)/)
  assert.match(scss, /\.msg-body\s*\{[\s\S]*max-width:\s*calc\(100% - 44px\);/)
  assert.match(scss, /\.bubble\s*\{[\s\S]*max-width:\s*100%;/)
  assert.match(scss, /\.sql-block\s*\{[\s\S]*overflow-x:\s*auto;/)
})

test('AI chat result template keeps table inside the scroll wrapper', () => {
  const vue = read(vuePath)
  const tableWrapIndex = vue.indexOf('class="viz-table-wrap"')
  const tableIndex = vue.indexOf('class="viz-table"')

  assert.ok(tableWrapIndex >= 0, 'expected viz table wrapper')
  assert.ok(tableIndex > tableWrapIndex, 'expected table to render inside the wrapper')
})

test('fast runner includes AI chat layout guard', () => {
  const runner = read(runnerPath)

  assert.match(runner, /test:ai-chat-layout/)
})
