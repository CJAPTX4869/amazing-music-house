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

/** 删除用户（不可删自己 / 不可删管理员，连带其评论、点赞、记录、歌单） */
export function deleteUser(id: number) {
  return request.delete<never, null>(`/admin/users/${id}`)
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

/** 运维状态：应用/JVM/系统内存/磁盘/数据库/业务统计 实时快照 */
export function opsStatus() {
  return request.get<never, Record<string, any>>('/admin/ops/status')
}

/** 整机监控快照（OSHI）：CPU/内存/JVM/系统/磁盘，参考 zzyl */
export function opsServer() {
  return request.get<never, Record<string, any>>('/admin/ops/server')
}

/** 在线客户端（90 秒内有活动；网页/桌面/移动 分组） */
export function opsOnline() {
  return request.get<never, Record<string, any>>('/admin/ops/online')
}

/** 歌曲状态统计：上下架/歌词/封面/类型与标签分布 + 三类明细（各截50） */
export function songStats() {
  return request.get<never, Record<string, any>>('/admin/ops/song-stats')
}

/** 某首歌最近 N 天的每日播放量曲线 */
export function songDaily(songId: number, days = 30) {
  return request.get<never, { date: string; count: number }[]>('/admin/ops/song-daily', {
    params: { songId, days }
  })
}

/** 最火歌曲：播放 TOP50 + 评论 TOP50（同量看评论→看播放→先创建的在前） */
export function hotSongs(limit = 50) {
  return request.get<never, { byPlays: Record<string, any>[]; byComments: Record<string, any>[] }>(
    '/admin/ops/hot-songs', { params: { limit } })
}

/** 歌曲选项（id+标题，选歌下拉用） */
export function songOptions() {
  return request.get<never, { id: number; title: string }[]>('/admin/ops/song-options')
}

/** 歌单播放计数上报（播放全部按钮触发） */
export function reportPlaylistPlay(id: number | string) {
  return request.get('/playlist/play', { params: { id } })
}

/** 单曲数据页：一首歌的完整实时状态（信息 + 每日播放量） */
export function songData(songId: number | string) {
  return request.get<never, Record<string, any>>('/admin/ops/song-data', { params: { songId } })
}
