import { getHealthPortrait } from '@/api/health-portrait'
import { getHealthRecords } from '@/api/health'

function formatWarnCurveDate(ms) {
  const date = new Date(ms)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`
}

function buildWarnCurveTitle(event) {
  return `${event.userName} · ${event.type} 附近时段健康曲线`
}

export function buildDashboardEmployeeVitals(vitals) {
  const v = vitals || {}
  return [
    {
      label: '心率',
      val: v.heartRate || '--',
      unit: 'bpm',
      color: '#ff5252',
      statusCls: (v.heartRate >= 60 && v.heartRate <= 100) ? 'dv-ok' : 'dv-warn',
      statusText: (v.heartRate >= 60 && v.heartRate <= 100) ? '正常' : '异常'
    },
    {
      label: '血氧',
      val: v.bloodOxygen || '--',
      unit: '%',
      color: '#00d4ff',
      statusCls: (v.bloodOxygen >= 95) ? 'dv-ok' : 'dv-warn',
      statusText: (v.bloodOxygen >= 95) ? '正常' : '偏低'
    },
    {
      label: '体温',
      val: v.temperature || '--',
      unit: '°C',
      color: '#ffd200',
      statusCls: (v.temperature >= 36 && v.temperature <= 37.3) ? 'dv-ok' : 'dv-warn',
      statusText: (v.temperature >= 36 && v.temperature <= 37.3) ? '正常' : '异常'
    },
    {
      label: '血压',
      val: (v.systolic && v.diastolic) ? `${v.systolic}/${v.diastolic}` : '--',
      unit: 'mmHg',
      color: '#38ef7d',
      statusCls: (v.systolic && v.diastolic && v.systolic >= 90 && v.systolic <= 140 && v.diastolic >= 60 && v.diastolic <= 90)
        ? 'dv-ok'
        : (v.systolic || v.diastolic) ? 'dv-warn' : '',
      statusText: (v.systolic && v.diastolic && v.systolic >= 90 && v.systolic <= 140 && v.diastolic >= 60 && v.diastolic <= 90)
        ? '正常'
        : (v.systolic || v.diastolic) ? '异常' : '--'
    }
  ]
}

export function buildDashboardWarnCurveSeries(records, type) {
  const indicatorMap = {
    '心率': { key: 'heartRate', label: '心率(bpm)', color: '#00d4ff' },
    '血氧': { key: 'bloodOxygen', label: '血氧(%)', color: '#38ef7d' },
    '体温': { key: 'temperature', label: '体温(°C)', color: '#ffd200', divisor: 10 },
    '压力': { key: 'pressure', label: '压力指数', color: '#3eb7ff' },
    '血压': { key: 'bloodPressureHigh', label: '收缩压(mmHg)', color: '#ff7043' }
  }
  const typeKey = Object.keys(indicatorMap).find((key) => type?.includes(key)) || '心率'
  const seriesDef = [indicatorMap[typeKey]]

  Object.entries(indicatorMap).forEach(([key, item]) => {
    if (key !== typeKey && records.some((record) => record[item.key] != null)) {
      seriesDef.push(item)
    }
  })

  return seriesDef
}

export async function fetchDashboardEmployeeDetail(empCode, userName = '') {
  try {
    const res = await getHealthPortrait(empCode)
    if (res.code !== 200 || !res.data) return null
    const data = res.data
    return {
      profile: {
        empName: data.empName || data.employee?.empName || userName,
        deptName: data.deptName || data.employee?.deptName || '--',
        jobTypeName: data.jobTypeName || data.employee?.jobTypeName || '--'
      },
      vitals: buildDashboardEmployeeVitals(data.vitals || data.realtime || {}),
      warnings: data.warnings || data.recentWarnings || [],
      trend: data.trend,
      healthScores: data.healthScores
    }
  } catch {
    return null
  }
}

export async function fetchDashboardWarnCurveState(event) {
  const title = buildWarnCurveTitle(event)
  const warnMs = new Date(event.time).getTime()
  if (Number.isNaN(warnMs)) {
    return { title, records: [], hasData: false }
  }

  const startTime = formatWarnCurveDate(warnMs - 30 * 60 * 1000)
  const endTime = formatWarnCurveDate(warnMs + 30 * 60 * 1000)

  try {
    const res = await getHealthRecords({ userCode: event.userCode, startTime, endTime, page: 1, size: 200 })
    const records = res.code === 200 ? (res.data?.records || []).reverse() : []
    return {
      title,
      records,
      hasData: records.length > 0
    }
  } catch {
    return { title, records: [], hasData: false }
  }
}
