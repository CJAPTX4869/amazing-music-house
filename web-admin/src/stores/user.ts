import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginDTO, RegisterDTO, UserVO } from '@/types'
import { getProfile, login as loginApi, register as registerApi } from '@/api/auth'
import { authToken, setAuthToken } from '@/api/request'

const USER_KEY = 'music_user'

export const useUserStore = defineStore('user', () => {
  // token 与请求拦截器共用同一个响应式 ref：
  // 令牌失效时拦截器会置空它，store 的 isLogin 随之变化，路由守卫即可放行登录页
  const token = authToken
  const userInfo = ref<UserVO | null>(null)

  // 初始化时尝试恢复本地缓存的用户信息
  try {
    const cached = localStorage.getItem(USER_KEY)
    if (cached) userInfo.value = JSON.parse(cached)
  } catch {
    localStorage.removeItem(USER_KEY)
  }

  /** 是否已登录 */
  const isLogin = computed(() => !!token.value)
  /** 是否管理员 */
  const isAdmin = computed(() => userInfo.value?.role === 1)

  /** 登录 */
  async function login(data: LoginDTO) {
    const res = await loginApi(data)
    setAuthToken(res.token)
    userInfo.value = res.user
    localStorage.setItem(USER_KEY, JSON.stringify(res.user))
  }

  /** 注册 */
  async function register(data: RegisterDTO) {
    await registerApi(data)
  }

  /** 拉取当前用户信息（用于刷新后恢复登录态） */
  async function fetchProfile() {
    if (!token.value) return
    try {
      const res = await getProfile()
      userInfo.value = res
      localStorage.setItem(USER_KEY, JSON.stringify(res))
    } catch {
      logout()
    }
  }

  /** 退出登录 */
  function logout() {
    setAuthToken('')
    userInfo.value = null
    localStorage.removeItem(USER_KEY)
  }

  return { token, userInfo, isLogin, isAdmin, login, register, fetchProfile, logout }
})
