import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import {
  buildReportExportState,
  hasReportExportData
} from '../src/views/health-monitor/report-center/report-center-view-model.js'

const reportCenterSource = [
  'src/views/health-monitor/report-center/index.vue',
  'src/views/health-monitor/report-center/use-report-center-page.ts'
].map((file) => readFileSync(file, 'utf8')).join('\n')

test('hasReportExportData detects whether any report export source has rows', () => {
  assert.equal(hasReportExportData({ monthlySummary: [], deptSummary: [], trendData: [] }), false)
  assert.equal(hasReportExportData({ monthlySummary: [{ id: 1 }], deptSummary: [], trendData: [] }), true)
  assert.equal(hasReportExportData({ monthlySummary: [], deptSummary: [{ deptName: '安全部' }], trendData: [] }), true)
  assert.equal(hasReportExportData({ monthlySummary: [], deptSummary: [], trendData: [{ date: '2026-05-11' }] }), true)
})

test('buildReportExportState explains disabled export state and loading state', () => {
  assert.deepEqual(
    buildReportExportState({ loading: true, exporting: false, exportingPdf: false, monthlySummary: [], deptSummary: [], trendData: [] }),
    {
      canExportExcel: false,
      canExportPdf: false,
      exportDisabledReason: '报表数据加载中，请稍后导出',
      excelDisabledReason: '报表数据加载中，请稍后导出',
      pdfDisabledReason: '报表数据加载中，请稍后导出',
      hasExportData: false
    }
  )

  assert.deepEqual(
    buildReportExportState({ loading: false, exporting: false, exportingPdf: false, monthlySummary: [], deptSummary: [], trendData: [] }),
    {
      canExportExcel: false,
      canExportPdf: false,
      exportDisabledReason: '当前筛选范围暂无可导出的报表数据',
      excelDisabledReason: '当前筛选范围暂无可导出的报表数据',
      pdfDisabledReason: '当前筛选范围暂无可导出的报表数据',
      hasExportData: false
    }
  )

  assert.deepEqual(
    buildReportExportState({ loading: false, exporting: false, exportingPdf: false, monthlySummary: [{ empName: '张三' }], deptSummary: [], trendData: [] }),
    {
      canExportExcel: true,
      canExportPdf: true,
      exportDisabledReason: '',
      excelDisabledReason: '',
      pdfDisabledReason: '',
      hasExportData: true
    }
  )
})

test('buildReportExportState explains per-format exporting state', () => {
  assert.deepEqual(
    buildReportExportState({
      loading: false,
      exporting: true,
      exportingPdf: false,
      monthlySummary: [{ empName: '张三' }],
      deptSummary: [],
      trendData: []
    }),
    {
      canExportExcel: false,
      canExportPdf: true,
      exportDisabledReason: '',
      excelDisabledReason: 'Excel 正在导出，请稍后',
      pdfDisabledReason: '',
      hasExportData: true
    }
  )

  assert.deepEqual(
    buildReportExportState({
      loading: false,
      exporting: false,
      exportingPdf: true,
      monthlySummary: [],
      deptSummary: [{ deptName: '安全部' }],
      trendData: []
    }),
    {
      canExportExcel: true,
      canExportPdf: false,
      exportDisabledReason: '',
      excelDisabledReason: '',
      pdfDisabledReason: 'PDF 正在导出，请稍后',
      hasExportData: true
    }
  )
})

test('report center export methods guard disabled state before starting export work', () => {
  assert.match(reportCenterSource, /ensureExportReady\(format(?::[^)]*)?\)/)
  assert.match(reportCenterSource, /state\.canExportPdf\s*:\s*state\.canExportExcel/)
  assert.match(reportCenterSource, /ElMessage\.warning\(reason \|\| '当前报表暂不可导出'\)/)
  assert.match(reportCenterSource, /if \(!ensureExportReady\('excel'\)\) return[\s\S]*exporting\.value = true/)
  assert.match(reportCenterSource, /if \(!ensureExportReady\('pdf'\)\) return[\s\S]*exportingPdf\.value = true/)
})

test('report center export buttons bind disabled state and disabled reason', () => {
  assert.match(reportCenterSource, /:disabled="[^"]*!reportExportState\.canExportExcel"/)
  assert.match(reportCenterSource, /:disabled="[^"]*!reportExportState\.canExportPdf"/)
  assert.match(reportCenterSource, /excelDisabledReason/)
  assert.match(reportCenterSource, /pdfDisabledReason/)
})
