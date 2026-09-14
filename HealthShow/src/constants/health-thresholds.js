/**
 * 健康指标阈值常量 + 通用分级函数
 * 所有页面共享，避免硬编码散落在各 .vue 文件中
 */

export const HR = { LOW: 55, HIGH: 120 }
export const SPO2 = { DANGER: 90, LOW: 95, EXCELLENT: 99 }
export const TEMP = { LOW: 36.0, HIGH: 37.3, DANGER: 38.5 }
export const BP_SYS = { NORMAL: 120, PRE: 140 }
export const BP_DIA = { NORMAL: 80, PRE: 90 }
export const PRESSURE = { RELAXED: 50, ELEVATED: 70, HIGH: 85 }

/** 心率等级：low / normal / high */
export function hrLevel(v) { return v > HR.HIGH ? 'high' : v < HR.LOW ? 'low' : 'normal' }

/** 血氧等级：danger / low / normal / excellent */
export function spo2Level(v) {
  return v < SPO2.DANGER ? 'danger' : v < SPO2.LOW ? 'low' : v >= SPO2.EXCELLENT ? 'excellent' : 'normal'
}

/** 体温等级：low / normal / high / danger */
export function tempLevel(v) {
  return v < TEMP.LOW ? 'low' : v > TEMP.DANGER ? 'danger' : v > TEMP.HIGH ? 'high' : 'normal'
}

/** 血压等级（收缩压）：normal / pre / stage1 / danger */
export function bpLevel(sys) {
  return sys < BP_SYS.NORMAL ? 'normal' : sys < BP_SYS.PRE ? 'pre' : 'stage1'
}

/** 压力等级：relaxed / normal / elevated / high */
export function pressureLevel(v) {
  return v < PRESSURE.RELAXED ? 'relaxed' : v < PRESSURE.ELEVATED ? 'normal'
    : v < PRESSURE.HIGH ? 'elevated' : 'high'
}

/** 通用等级→颜色映射 */
export const LEVEL_COLORS = {
  normal: '#52c41a', excellent: '#4FC3F7', good: '#4FC3F7',
  low: '#4FC3F7', high: '#FFB84D', elevated: '#FFB84D',
  pre: '#FFB84D', stage1: '#ff7043', danger: '#ff5252',
  relaxed: '#4FC3F7', poor: '#FFB84D'
}
