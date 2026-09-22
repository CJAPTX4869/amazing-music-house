/**
 * 全局类型定义
 * 字段严格对齐后端 VO / DTO（见需求文档附录 C 接口字段级契约）
 */

/* ==================== 通用 ==================== */

/** 统一响应体 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/* ==================== 用户 ==================== */

/** 用户角色：0 普通用户 / 1 管理员 */
export type UserRole = 0 | 1

export interface UserVO {
  id: number
  username: string
  nickname: string | null
  avatar: string | null
  role: UserRole
  /** 状态：1 启用 / 0 禁用 */
  status: 0 | 1
  createTime: string
}

export interface LoginVO {
  token: string
  user: UserVO
}

export interface RegisterDTO {
  username: string
  password: string
  nickname?: string
}

export interface LoginDTO {
  username: string
  password: string
}

/* ==================== 歌曲 ==================== */

export interface SongVO {
  id: number
  title: string
  artist: string | null
  album: string | null
  genre: string | null
  /** 时长（秒） */
  duration: number
  /** 时长文本，如 04:06 */
  durationText: string
  fileName: string
  originalName: string | null
  /** 文件大小（字节） */
  fileSize: number
  fileSizeText: string
  fileMd5: string | null
  mimeType: string | null
  ext: string | null
  coverUrl: string | null
  /** 是否已有歌词 */
  hasLyric: boolean
  uploaderId: number
  uploaderName: string | null
  downloadCount: number
  playCount: number
  /** 状态：1 上架 / 0 下架 */
  status: 0 | 1
  createTime: string
  updateTime: string
  /** 下载地址（相对 API 基础路径，不含 /api 前缀） */
  downloadUrl: string
  /** 播放地址（相对 API 基础路径） */
  streamUrl: string
}

/** 歌曲查询条件 */
export interface SongQuery {
  keyword?: string
  page?: number
  size?: number
  /** 排序，如 create_time,desc */
  sort?: string
  status?: 0 | 1
}

/** 歌曲编辑 */
export interface SongUpdateDTO {
  title?: string
  artist?: string
  album?: string
  genre?: string
}

/** 用户查询条件（管理端） */
export interface UserQuery {
  keyword?: string
  page?: number
  size?: number
  role?: UserRole
  status?: 0 | 1
}

/* ==================== 管理端 ==================== */

export interface StatisticVO {
  songCount: number
  onlineSongCount: number
  userCount: number
  totalDownload: number
  totalPlay: number
  diskUsed: number
  diskTotal: number
  diskUsedText: string
}

/** 状态切换（启用/禁用、上架/下架） */
export interface StatusDTO {
  status: 0 | 1
}
