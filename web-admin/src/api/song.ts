import request from './request'
import type { PageResult, SongQuery, SongUpdateDTO, SongVO } from '@/types'

/** 歌曲列表（公开，仅上架） */
export function getSongList(params: SongQuery) {
  return request.get<never, PageResult<SongVO>>('/songs', { params })
}

/** 单曲详情（状态总览点歌名 → 直达编辑弹窗用） */
export function getSongDetail(id: number | string) {
  return request.get<never, SongVO>(`/songs/${id}`)
}

/**
 * 上传歌曲（仅管理员）
 * @param file 音频文件
 * @param meta 可选元数据
 * @param onProgress 进度回调（0-100）
 */
export function uploadSong(
  file: File,
  meta: { title?: string; artist?: string; album?: string; genre?: string; md5?: string } = {},
  onProgress?: (percent: number) => void
) {
  const formData = new FormData()
  formData.append('file', file)
  if (meta.title) formData.append('title', meta.title)
  if (meta.artist) formData.append('artist', meta.artist)
  if (meta.album) formData.append('album', meta.album)
  if (meta.genre) formData.append('genre', meta.genre)
  if (meta.md5) formData.append('md5', meta.md5)

  return request.post<never, SongVO>('/songs/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000, // 大文件上传放宽超时
    onUploadProgress: e => {
      if (onProgress && e.total) {
        onProgress(Math.round((e.loaded / e.total) * 100))
      }
    }
  })
}

/** MD5 预检（秒传），命中返回已有歌曲，未命中返回 null */
export function checkMd5(md5: string) {
  return request.post<never, SongVO | null>('/songs/check-md5', { md5 })
}

/** 编辑歌曲（仅管理员） */
export function updateSong(id: number, data: SongUpdateDTO) {
  return request.put<never, SongVO>(`/songs/${id}`, data)
}

/** 删除歌曲（仅管理员） */
export function deleteSong(id: number) {
  return request.delete<never, null>(`/songs/${id}`)
}

/** 批量删除（仅管理员） */
export function batchDeleteSongs(ids: number[]) {
  return request.post<never, null>('/songs/batch-delete', { ids })
}

/**
 * 下载歌曲
 * 使用 Blob 方式以便显示加载状态并捕获错误
 */
export async function downloadSong(id: number, fallbackName = 'song') {
  const response = await request.get(`/songs/${id}/download`, { responseType: 'blob' })

  // 从响应头解析文件名（后端需暴露 Content-Disposition，见需求文档 8.2 NF-S10）
  const disposition = response.headers?.['content-disposition'] as string | undefined
  let filename = fallbackName
  if (disposition) {
    // 优先取 RFC 5987 编码文件名
    const match = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
    if (match) {
      filename = decodeURIComponent(match[1])
    } else {
      const ascii = /filename="([^"]+)"/i.exec(disposition)
      if (ascii) filename = ascii[1]
    }
  }

  const url = URL.createObjectURL(new Blob([response.data]))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/** 播放地址（供 <audio> 使用） */
export function streamUrl(id: number) {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  return `${base}/songs/${id}/stream`
}

/**
 * 歌词上传 / 替换（仅管理员）
 * <p>
 * 支持 .lrc / .txt（≤1MB，UTF-8）；网易云下载的逐字歌词 JSON 由后端自动转成标准 LRC。
 */
export function uploadLyric(id: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<never, SongVO>(`/songs/${id}/lyric`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

/**
 * 封面上传 / 替换（仅管理员）
 * <p>
 * file 需为已压缩的正方形 JPEG（见 utils/image.ts 的 compressToSquare）。
 */
export function uploadCover(id: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<never, SongVO>(`/songs/${id}/cover`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

/**
 * 封面地址（供 <img> 使用）
 * <p>
 * 后端 SongVO.coverUrl 为 null 时表示无封面，此处返回空串由调用方走占位图。
 * 传入 version 可在换封面后强制刷新浏览器缓存。
 */
export function coverUrl(id: number, version?: number | string) {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  const suffix = version ? `?v=${version}` : ''
  return `${base}/songs/${id}/cover${suffix}`
}
