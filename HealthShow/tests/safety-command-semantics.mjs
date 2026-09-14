import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  getEventSourceText,
  mapWarningToEvent
} from '../src/views/safety-command/safety-command-view-model.js'
import { buildSafetyCommandQueue } from '../src/views/safety-command/safety-command-workflow.js'

const __dirname = dirname(fileURLToPath(import.meta.url))
const source = (relativePath) => readFileSync(resolve(__dirname, '..', relativePath), 'utf8')

test('structured severity, status and SLA fields remain authoritative', () => {
  const event = mapWarningToEvent({
    warningId: 101,
    typeLabel: '普通体温提醒',
    severity: 'critical',
    eventSource: 'health_threshold',
    status: 'acked',
    sla: { configured: true, status: 'overdue' }
  })

  assert.equal(event.level, 'critical')
  assert.equal(event.severity, 'CRITICAL')
  assert.equal(event.status, 'ACKED')
  assert.equal(event.statusLabel, '已确认')
  assert.equal(event.slaStatus, 'OVERDUE')
  assert.equal(event.slaText, '已超时')
})

test('SOS requires DEVICE_ALARM and legacy SOS records infer that source first', () => {
  const invalidHealthSos = mapWarningToEvent({
    warningId: 102,
    typeLabel: 'SOS求救',
    eventCode: 'SOS',
    eventSource: 'HEALTH_THRESHOLD',
    severity: 'CRITICAL'
  })
  const legacySos = mapWarningToEvent({
    warningId: 103,
    typeLabel: 'SOS求救',
    eventCode: 'SOS',
    severity: 'CRITICAL'
  })

  assert.equal(invalidHealthSos.eventSource, 'HEALTH_THRESHOLD')
  assert.equal(invalidHealthSos.eventType, 'abnormal')
  assert.equal(legacySos.eventSource, 'DEVICE_ALARM')
  assert.equal(legacySos.eventType, 'sos')
})

test('event source labels and queue tones use normalized structured fields', () => {
  const event = mapWarningToEvent({
    warningId: 104,
    typeLabel: '趋势风险',
    eventSource: 'TREND_WARNING',
    severity: 'CRITICAL'
  })
  const queue = buildSafetyCommandQueue([event])

  assert.equal(getEventSourceText(event.eventSource), '趋势风险')
  assert.equal(queue[0].tone, 'danger')
  assert.equal(queue[0].action, '处置')
})

test('safety command page binds queue source and desktop height to the corrected fields', () => {
  const page = source('src/views/safety-command/index.vue')
  const styles = source('src/views/safety-command/safety-command.scss')

  assert.match(page, /getEventSourceText\(event\.eventSource\)/)
  assert.doesNotMatch(page, /getEventSourceText\(event\.source\)/)
  assert.match(styles, /height: clamp\(552px, calc\(34vw \+ 120px\), 580px\)/)
})
