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

/** 抓娃娃验证：领取票据（10 分钟有效） */
export function captchaChallenge() {
  return request.post<never, { ticket: string }>('/auth/captcha/challenge')
}

/** 抓娃娃验证通过后：向服务端确认票据 */
export function captchaVerify(ticket: string) {
  return request.post<never, void>('/auth/captcha/verify', { ticket })
}

/** 获取当前登录用户 */
export function getProfile() {
  return request.get<never, UserVO>('/auth/profile')
}

/** 忘记密码：用户名 + 恢复码直接设置新密码（免登录） */
export function forgotPassword(data: { username: string; recoveryCode: string; newPassword: string }) {
  return request.post<never, void>('/auth/forgot-password', data)
}

/** 登录后修改密码（需验证旧密码） */
export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request.put<never, void>('/users/password', data)
}
