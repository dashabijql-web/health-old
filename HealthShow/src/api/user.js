//引入axios(axios进行二次封装)
import request from '@/utils/request'

// ==================== 登录相关接口 ====================

/**
 * 用户登录
 * @param {Object} data {username, password}
 */
export function login(data) {
  return request({
    url: '/auth/login',  // 对接后端 /health/auth/login
    method: 'post',
    data
  })
}

/**
 * 获取用户信息
 * @param {String} token 
 */
export function getInfo(token) {
  return request({
    url: '/auth/info',  // 对接后端 /health/auth/info
    method: 'get'
  })
}

/**
 * 退出登录
 */
export function logout() {
  return request({
    url: '/auth/logout',  // 对接后端 /health/auth/logout
    method: 'post'
  })
}

// ==================== 用户管理接口 ====================

/**
 * 获取用户列表
 * @param {Object} params 查询参数
 * @param {String} params.keyword 搜索关键词
 * @param {Number} params.status 用户状态 0-正常 1-禁用
 * @param {Number} params.deptId 部门ID
 * @param {Number} params.page 页码
 * @param {Number} params.size 每页条数
 */
export function getUserList(params) {
  return request({
    url: '/user/list',
    method: 'get',
    params
  })
}

/**
 * 获取用户详细信息
 * @param {Number} id 用户ID
 */
export function getUserDetail(id) {
  return request({
    url: `/user/detail/${id}`,
    method: 'get'
  })
}

/**
 * 获取用户统计数据
 */
export function getUserStats() {
  return request({
    url: '/user/stats',
    method: 'get'
  })
}

/**
 * 获取用户健康统计
 * @param {String} userCode 用户编码
 */
export function getUserHealthStats(userCode) {
  return request({
    url: `/user/health-stats/${userCode}`,
    method: 'get'
  })
}

// ==================== 用户 CRUD ====================

/** 新增用户 */
export function createUser(data) {
  return request({ url: '/user/create', method: 'post', data })
}

/** 编辑用户 */
export function updateUser(data) {
  return request({ url: '/user/update', method: 'put', data })
}

/** 删除用户 */
export function deleteUser(id) {
  return request({ url: `/user/delete/${id}`, method: 'delete' })
}

/** 修改用户状态 */
export function updateUserStatus(id, status) {
  return request({ url: '/user/status', method: 'put', data: { id, status } })
}
