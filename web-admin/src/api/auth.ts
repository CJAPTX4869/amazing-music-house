import request from './request'
import type { LoginDTO, LoginVO, RegisterDTO, UserVO } from '@/types'

/** 注册 */
export function register(data: RegisterDTO) {
  return request.post<never, UserVO>('/auth/register', data)
}

/** 登录 */
export function login(data: LoginDTO) {
  return request.post<never, LoginVO>('/auth/login', data)
}

/** 获取当前登录用户 */
export function getProfile() {
  return request.get<never, UserVO>('/auth/profile')
}
