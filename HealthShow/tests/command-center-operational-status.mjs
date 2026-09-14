import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import {
  applyDeviceRouteFilters,
  buildFilteredDeviceList,
  deviceAbnormalLabel
} from '../src/views/device-management/device-management-view-model.js'

const __dirname = dirname(fileURLToPath(import.meta.url))
const source = (path) => readFileSync(resolve(__dirname, '..', path), 'utf8')

test('device routes filter real interruption and fault states', () => {
  const filters = {
    searchImei: '',
    filterOnline: null,
    filterBind: null,
    filterWarning: false,
    filterLowBattery: false,
    filterWarningVal: '',
    filterBatteryVal: '',
    filterOperationalVal: ''
  }
  applyDeviceRouteFilters({ filter: 'dataInterrupted' }, filters)
  const devices = [
    { id: 1, currentAbnormal: 'DATA_INTERRUPTED', dataInterrupted: true },
    { id: 2, currentAbnormal: 'FAULT', dataInterrupted: false },
    { id: 3, currentAbnormal: 'NORMAL', dataInterrupted: false }
  ]
  assert.deepEqual(buildFilteredDeviceList(devices, filters).map(item => item.id), [1])

  applyDeviceRouteFilters({ filter: 'faulted' }, filters)
  assert.deepEqual(buildFilteredDeviceList(devices, filters).map(item => item.id), [2])
  assert.equal(deviceAbnormalLabel(devices[1]), '设备故障')
})

test('review workflow keeps pending and overdue semantics separate', () => {
  const workflow = source('src/views/health-monitor/mine-entry/mine-entry-review-workflow.js')
  const page = source('src/views/health-monitor/mine-entry/index.vue')
  assert.match(workflow, /\['pass', 'fail', 'review', 'overdue'\]/)
  assert.match(workflow, /filterStatus === 'overdue'/)
  assert.match(workflow, /\['PENDING', 'IN_REVIEW'\]\.includes/)
  assert.match(workflow, /action === 'REQUEST_RETEST'/)
  assert.match(page, /CONFIRM_PROHIBITED/)
})
