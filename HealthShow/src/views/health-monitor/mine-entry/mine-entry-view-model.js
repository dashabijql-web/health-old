export function mineEntryRateClass(rate) {
  if (rate === null) return ''
  return rate >= 90 ? 'me-stat-rate-ok' : rate >= 70 ? 'me-stat-rate-warn' : 'me-stat-rate-bad'
}

export function vitalClass(value, min, max, isHeartRate) {
  if (value === null || value === undefined) return ''
  if (isHeartRate) return (value < min || value > max) ? 'vital-bad' : 'vital-ok'
  return value < min ? 'vital-bad' : 'vital-ok'
}

export function bloodPressureClass(systolic, diastolic) {
  if ((systolic !== null && systolic >= 140) || (diastolic !== null && diastolic >= 90)) return 'vital-bad'
  return 'vital-ok'
}

export function mineEntryFailReasons(item) {
  const reasons = []
  if (item.heartRate !== null && (item.heartRate < 60 || item.heartRate > 100)) {
    reasons.push(`心率${item.heartRate}bpm`)
  }
  if (item.bloodOxygen !== null && item.bloodOxygen < 95) {
    reasons.push(`血氧${item.bloodOxygen}%`)
  }
  if (item.systolic !== null && item.systolic >= 140) {
    reasons.push(`高压${item.systolic}`)
  }
  if (item.diastolic !== null && item.diastolic >= 90) {
    reasons.push(`低压${item.diastolic}`)
  }
  return reasons
}
