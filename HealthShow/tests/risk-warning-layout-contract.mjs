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

test('risk warning desktop layout extends downward instead of squeezing charts and table into one screen', () => {
  const source = readSource('src/views/health-monitor/risk-warning/risk-warning.scss')
  const runtimeSource = readSource('src/views/health-monitor/risk-warning/risk-warning-runtime.js')

  assert.match(
    source,
    /\.rw-root\s*\{[\s\S]*height:\s*auto;[\s\S]*min-height:\s*calc\(100vh - 50px\);[\s\S]*overflow:\s*visible;/,
    'risk warning root should allow the taller chart-first work area to scroll instead of clipping at one viewport'
  )
  assert.match(
    source,
    /\.rw-bd\s*\{[\s\S]*flex:\s*none;[\s\S]*align-items:\s*flex-start;[\s\S]*overflow:\s*visible;/,
    'risk warning body should size to the work area instead of stretching the table to the full viewport'
  )
  assert.match(
    source,
    /\.rw-aside\s*\{[\s\S]*width:\s*clamp\(320px,\s*20vw,\s*400px\);/,
    'risk warning aside should give the department distribution chart a wider desktop rail'
  )
  assert.match(
    source,
    /\.rw-aside-bot\s*\{[\s\S]*height:\s*clamp\(560px,\s*56vh,\s*760px\);/,
    'department distribution panel should keep an expanded chart-first height and let the page extend downward'
  )
  assert.match(
    source,
    /\.rw-panel-list\s*\{[\s\S]*height:\s*clamp\(480px,\s*44vh,\s*680px\);[\s\S]*max-height:\s*none;/,
    'warning list panel should get enough vertical room while page scrolling handles the extra height'
  )
  assert.doesNotMatch(
    source,
    /\.rw-panel-list\s*\{[\s\S]*max-height:\s*320px;/,
    'warning list panel should not be capped to a single-screen compromise'
  )
  assert.match(
    runtimeSource,
    /legend:\{[\s\S]*textStyle:\{color:'#8ba6c8',fontSize:12\}/,
    'department chart legend text should match the statistics panel text scale'
  )
  assert.match(
    runtimeSource,
    /xAxis:\{[\s\S]*axisLabel:\{color:'#8ba6c8',fontSize:12\}/,
    'department chart x-axis labels should match the statistics panel text scale'
  )
  assert.match(
    runtimeSource,
    /yAxis:\{[\s\S]*axisLabel:\{color:'#a8c5e6',fontSize:12/,
    'department chart department labels should match the statistics panel text scale'
  )
  assert.match(
    runtimeSource,
    /label:\{show:true,position:'inside',color:'#fff',fontSize:12/,
    'department chart bar value labels should match the statistics panel text scale'
  )
})
