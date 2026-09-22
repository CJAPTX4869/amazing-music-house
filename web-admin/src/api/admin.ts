import request from './request'
import type { PageResult, StatusDTO, StatisticVO, UserQuery, UserVO, SongQuery, SongVO } from '@/types'

/** 管理端歌曲列表（含下架） */
export function getAdminSongList(params: SongQuery) {
  return request.get<never, PageResult<SongVO>>('/admin/songs', { params })
}

/** 用户列表 */
export function getUserList(params: UserQuery) {
  return request.get<never, PageResult<UserVO>>('/admin/users', { params })
}

/** 启用 / 禁用用户 */
export function updateUserStatus(id: number, data: StatusDTO) {
  return request.put<never, null>(`/admin/users/${id}/status`, data)
}

/**
 * 设置用户头像（仅管理员）
 * <p>
 * file 需为已压缩的正方形 JPEG（见 utils/image.ts 的 compressToSquare）。
 */
export function uploadUserAvatar(id: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<never, null>(`/admin/users/${id}/avatar`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

/** 歌曲上架 / 下架 */
export function updateSongStatus(id: number, data: StatusDTO) {
  return request.put<never, null>(`/admin/songs/${id}/status`, data)
}

/** 数据统计 */
export function getStatistics() {
  return request.get<never, StatisticVO>('/admin/statistics')
}
