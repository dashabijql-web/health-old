import axios from 'axios'
import { ElMessage } from 'element-plus'
import store from '@/store'
import { getToken } from '@/utils/auth'
import router from '@/router'

const service = axios.create({
  baseURL: import.meta.env.VITE_BASE_API || '/health',
  timeout: 10000
})

// 防重复跳转锁：多个并发请求同时 401 时，只跳转一次
let isRedirectingToLogin = false

function redirectToLogin() {
  if (isRedirectingToLogin) return
  isRedirectingToLogin = true
  store.dispatch('user/resetToken').then(() => {
    router.replace({ path: '/login' }).finally(() => {
      isRedirectingToLogin = false
    })
  })
}

// 请求拦截器：携带 token
service.interceptors.request.use(
  config => {
    if (store.getters.token) {
      config.headers['satoken'] = getToken()
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器
service.interceptors.response.use(
  response => {
    const res = response.data

    if (res.code !== 200) {
      // ✅ 业务层 401：静默跳转，不弹错误框
      if (res.code === 401) {
        redirectToLogin()
        return Promise.reject(new Error('未登录或登录已过期'))
      }

      ElMessage({ message: res.message || 'Error', type: 'error', duration: 5000 })
      return Promise.reject(new Error(res.message || 'Error'))
    }

    return res
  },
  error => {
    // ✅ HTTP 层 401：静默跳转，不弹错误框
    if (error.response?.status === 401) {
      redirectToLogin()
      return Promise.reject(error)
    }

    let message = '请求失败'
    if (error.response) {
      switch (error.response.status) {
        case 403: message = '没有权限访问'; break
        case 404: message = '请求的资源不存在'; break
        case 500: message = '服务器错误'; break
        default:  message = error.response.data?.message || '请求失败'
      }
    } else if (error.message?.includes('timeout')) {
      message = '请求超时，请稍后重试'
    } else if (error.message?.includes('Network Error')) {
      message = '网络连接失败，请检查网络'
    }

    ElMessage({ message, type: 'error', duration: 5000 })
    return Promise.reject(error)
  }
)

export default service