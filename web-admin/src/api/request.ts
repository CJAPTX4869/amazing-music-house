import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { Result } from '@/types'

const TOKEN_KEY = 'music_token'
const USER_KEY = 'music_user'

/**
 * 全局登录令牌（响应式）
 * <p>
 * 由 `userStore` 与请求拦截器**共用同一个 ref**。
 * 原因：后端业务错误（含 401）同样返回 HTTP 200，登录态失效只能在响应体里判断；
 * 若拦截器只清理 localStorage 而不重置 store 中的 token，路由守卫会认为仍处于登录态，
 * 导致「已登录访问登录页 → 跳首页」的死循环，用户永远无法重新登录。
 */
export const authToken = ref<string>(localStorage.getItem(TOKEN_KEY) || '')

/** 统一写入/清除登录令牌（同时同步 localStorage 与响应式状态） */
export function setAuthToken(token: string) {
  authToken.value = token
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }
}

/**
 * Axios 实例
 * baseURL 取自环境变量：开发环境 /api（走 Vite 代理），生产环境由 Nginx 反代
 */
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 60000
})

/* ---------------- 请求拦截：注入 Token ---------------- */
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    config.headers['X-Client-Platform'] = 'admin'   // 管理后台独立分组
    if (authToken.value && config.headers) {
      config.headers.Authorization = `Bearer ${authToken.value}`
    }
    return config
  },
  error => Promise.reject(error)
)

/* ---------------- 响应拦截：统一拆包与错误处理 ---------------- */
service.interceptors.response.use(
  async response => {
    // ⚠️ 关键：二进制流（文件下载）直接返回原始响应，不做业务拆包
    // 否则 res.data 是 Blob，解构 code 会得到 undefined，导致下载功能报错
    if (response.config.responseType === 'blob') {
      // 🔧 修复：后端"业务错误同样返回 HTTP 200"（body 是 JSON 而非文件流）。
      // 原实现把 Blob 直接交给调用方，浏览器会把 {"code":404,...} 存成 .mp3（78 字节假文件），
      // 界面还提示"开始下载"。这里按 Content-Type 识别错误体并走统一错误提示。
      const blob = response.data as Blob
      const type = (blob && typeof blob === 'object' && 'type' in blob)
        ? String(blob.type || '')
        : ''
      if (type.includes('application/json') || type.includes('text/plain') || type.includes('text/html')) {
        let message = '下载失败，文件不存在或不可用'
        try {
          const body = JSON.parse(await blob.text())
          message = body?.message || message
        } catch {
          // 非 JSON：保持默认提示
        }
        ElMessage.error(message)
        return Promise.reject(new Error(message))
      }
      return response
    }

    const res = response.data as Result
    if (res.code === 200) {
      return res.data as never
    }

    // 401：登录过期，清除本地状态并跳转登录页
    if (res.code === 401) {
      setAuthToken('')
      ElMessage.error('登录已过期，请重新登录')
      if (!location.hash.startsWith('#/login')) {
        location.hash = '#/login'
      }
      return Promise.reject(new Error(res.message))
    }

    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  error => {
    const msg = error?.message?.includes('Network Error')
      ? '网络异常，请检查后端服务是否已启动'
      : error?.response?.data?.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default service

// 在线心跳：每 30 秒上报一次（未登录静默跳过）
setInterval(() => {
  if (authToken.value) {
    service.get('/auth/profile').catch(() => {})
  }
}, 30000);
