/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║              健康数据 API 模块（新手必读）                             ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * 【API 模块化的好处】
 *
 * 如果所有请求都写在 .vue 组件里，会导致：
 *   1. 同一个接口在多个组件中重复写（冗余）
 *   2. URL 路径散落在各处，后端改了 URL 需要到处找
 *   3. 组件文件臃肿，业务逻辑和 HTTP 请求混在一起
 *
 * API 模块化：
 *   - 每个业务模块的 API 函数集中在一个文件中
 *   - 组件只调用函数，不关心请求细节
 *   - URL 修改只需改一处
 *   - 单元测试可以只 mock API 函数，不需要模拟整个网络
 *
 * 【request 函数说明】
 *
 * import request from '@/utils/request'
 * request() 就是封装好的 Axios 实例，自动：
 *   - 添加 baseURL 前缀
 *   - 携带 Token 请求头
 *   - 统一处理错误（弹窗、跳转登录）
 *
 * 【请求配置对象说明】
 *
 * {
 *   url: '/dashboard/overview',  // 接口路径（加 baseURL 后的完整路径）
 *   method: 'get',                       // HTTP 方法：get/post/put/delete
 *   params: { pageNum: 1, pageSize: 10 }, // URL 查询参数（?key=value 拼接到 URL）
 *   data: { name: '张三' },              // 请求体（POST/PUT 时用，JSON 格式发送）
 *   responseType: 'blob'                 // 响应类型：'blob' 用于文件下载
 * }
 *
 * 【params vs data 的区别】
 *
 * params（URL 参数）：GET 请求常用，拼接到 URL
 *   request({ url: '/records', params: { pageNum: 1 } })
 *   → 实际请求：GET /records?pageNum=1
 *
 * data（请求体）：POST/PUT 请求用，放在请求体中（JSON 格式）
 *   request({ url: '/users', method: 'post', data: { name: '张三' } })
 *   → 请求体：{ "name": "张三" }
 *
 * 【完整 URL 说明】
 *
 * baseURL = /dev-api（vite.config.mjs 代理配置）
 * 代理规则：/dev-api 开头的请求转发到 http://localhost:8080/health
 *
 * 所以：
 *   url = '/dashboard/overview'
 *   实际请求 = baseURL + url = /dev-api/dashboard/overview
 *   代理后实际到达 = http://localhost:8080/health/dashboard/overview
 *
 * 注意：URL 里不要包含 context-path（/health），vite 代理已经加上了
 */

import request from '@/utils/request'

/**
 * 获取 Dashboard 总览数据（当月检测人数统计）
 *
 * 后端接口：GET /dashboard/overview
 * 返回数据示例：
 *   {
 *     "totalCount": 248,      // 当月总检测人次
 *     "todayCount": 12,       // 今日检测人次
 *     "deptCount": 5          // 参与检测的部门数
 *   }
 *
 * @returns {Promise} Axios 请求 Promise
 */
export function getDashboardOverview(params, requestOptions = {}) {
  return request({ ...requestOptions, url: '/dashboard/overview', method: 'get', params })
}

/** 获取统一管控首屏聚合快照，替代十余个并发面板请求。 */
export function getUnifiedControlSnapshot(params, requestOptions = {}) {
  return request({
    ...requestOptions,
    url: '/dashboard/unified-control-snapshot',
    method: 'get',
    params
  })
}

/**
 * 获取当月主要身体指标分析
 *
 * 后端接口：GET /dashboard/body-indicators
 * 返回数据示例：
 *   {
 *     "avgHeartRate": 74.2,     // 当月平均心率（bpm）
 *     "avgBloodOxygen": 97.8,   // 当月平均血氧（%）
 *     "avgTemperature": 36.5,   // 当月平均体温（°C）
 *     "avgSteps": 7234          // 当月平均步数
 *   }
 *
 * @returns {Promise}
 */
export function getBodyIndicators(params, requestOptions = {}) {
  return request({ ...requestOptions, url: '/dashboard/body-indicators', method: 'get', params })
}

/**
 * 获取部门数据 TOP5（按检测人次排名）
 *
 * 后端接口：GET /dashboard/top5
 * 返回数据示例（数组）：
 *   [
 *     { "deptName": "软件开发部", "count": 62, "avgHeartRate": 73.1 },
 *     { "deptName": "生产部",     "count": 58, "avgHeartRate": 76.8 },
 *     ...
 *   ]
 *
 * @returns {Promise}
 */
export function getDataTop5(params, requestOptions = {}) {
  return request({
    ...requestOptions,
    url: '/dashboard/top5',
    method: 'get',
    params
  })
}

/**
 * 获取设备激活率统计
 *
 * 后端接口：GET /dashboard/device-activation
 * 返回数据示例：
 *   {
 *     "stats": {
 *       "totalDevices": 300,    // 发放设备总数
 *       "activeDevices": 248,   // 激活设备数
 *       "activationRate": 82.7  // 激活率（%）
 *     },
 *     "warningRates": [...]     // 各类预警率
 *   }
 *
 * @returns {Promise}
 */
export function getDeviceActivation(params, requestOptions = {}) {
  return request({ ...requestOptions, url: '/dashboard/device-activation', method: 'get', params })
}

/**
 * 获取最新预警事件列表
 *
 * 后端接口：GET /dashboard/warning-events
 * 返回最近 5 条预警记录，示例：
 *   [
 *     { "userId": "353...", "type": "心率过高", "value": 120, "time": "..." },
 *     { "userId": "353...", "type": "血氧偏低", "value": 88,  "time": "..." },
 *     ...
 *   ]
 *
 * @returns {Promise}
 */
export function getWarningEvents(params) {
  return request({ url: '/dashboard/warning-events', method: 'get', params })
}

/**
 * 获取健康记录列表（分页查询）
 *
 * 后端接口：GET /health/records
 *
 * @param {Object} params - 查询参数
 * @param {number} params.pageNum  - 页码（从1开始）
 * @param {number} params.pageSize - 每页条数
 * @param {string} [params.userCode] - 按设备 IMEI 过滤（可选）
 * @param {string} [params.startTime] - 开始时间（可选，格式 yyyy-MM-dd）
 * @param {string} [params.endTime]   - 结束时间（可选）
 * @returns {Promise}
 */
export function getHealthRecords(params) {
  return request({
    url: '/api/health/record/page',
    method: 'get',
    params
  })
}

export function getEmployeeHealthHistory(params) {
  return request({
    url: '/api/health/record/history/trend',
    method: 'get',
    params
  })
}

/**
 * 获取各部门健康数据量统计
 *
 * 后端接口：GET /dashboard/dept-stats
 * 返回数据示例（数组）：
 *   [ { "name": "研发部", "count": 1234 }, ... ]
 */
export function getDeptHealthCounts(params, requestOptions = {}) {
  return request({ ...requestOptions, url: '/dashboard/dept-stats', method: 'get', params })
}

/**
 * 获取过去 N 天每日各指标异常率（用于趋势折线图）
 *
 * 后端接口：GET /dashboard/daily-trend?days=30
 * 返回数据示例（数组，按日期升序）：
 *   [
 *     { "date": "2026-02-01", "heartRateRate": 12.5, "bloodOxygenRate": 3.2, "temperatureRate": 1.0, "pressureRate": 8.7 },
 *     ...
 *   ]
 */
export function getDailyTrend(days, requestOptions = {}) {
  return request({ ...requestOptions, url: '/dashboard/daily-trend', method: 'get', params: { days } })
}

/**
 * 获取预警按日/时统计（柱状图专用）
 * groupBy: 'day' | 'hour'
 */
export function getWarningCounts(params, requestOptions = {}) {
  return request({ ...requestOptions, url: '/dashboard/warning-counts', method: 'get', params })
}

export function getPersonCounts(params, requestOptions = {}) {
  return request({ ...requestOptions, url: '/dashboard/person-counts', method: 'get', params })
}

export function getDeptDailyPersons(params) {
  return request({ url: '/dashboard/dept-daily-persons', method: 'get', params })
}

export function getDeptDailyDetail(params) {
  return request({ url: '/dashboard/dept-daily-detail', method: 'get', params })
}

export function getMetricDailyDetail(params) {
  return request({ url: '/dashboard/metric-daily-detail', method: 'get', params })
}

export function getDeptPersonStats(params, requestOptions = {}) {
  return request({ ...requestOptions, url: '/dashboard/dept-person-stats', method: 'get', params })
}

/** 今日班前健康达标率 */
export function getPreShiftCompliance(requestOptions = {}) {
  return request({ ...requestOptions, url: '/dashboard/pre-shift-compliance', method: 'get' })
}

/** 今日入井准入名单 */
export function getMineEntryList(size = 1000) {
  return request({ url: '/dashboard/mine-entry-list', method: 'get', params: { size }, timeout: 15000 })
    .catch(() => ({ code: 200, data: [] }))  // 列表加载失败时静默处理，不弹错误提示
}

/** 部门健康对比雷达图数据 */
export function getDeptHealthComparison(days = 7) {
  return request({ url: '/dashboard/dept-health-comparison', method: 'get', params: { days } })
}
