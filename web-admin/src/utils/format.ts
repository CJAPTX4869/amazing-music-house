/**
 * 展示层格式化工具
 */

/** 字节 → 可读文本（1 KB = 1024 B） */
export function formatFileSize(bytes?: number | null): string {
  if (bytes == null || bytes < 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  const kb = bytes / 1024
  if (kb < 1024) return `${kb.toFixed(2)} KB`
  const mb = kb / 1024
  if (mb < 1024) return `${mb.toFixed(2)} MB`
  return `${(mb / 1024).toFixed(2)} GB`
}

/** 秒 → mm:ss（超过 1 小时显示 hh:mm:ss） */
export function formatDuration(seconds?: number | null): string {
  if (seconds == null || seconds <= 0) return '00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  // 修复：audio.duration 是小数（如 274.35），原实现未取整会显示成 "04:34.35"
  const s = Math.floor(seconds % 60)
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
}

/** 秒 → 进度条显示的当前时间（播放器用） */
export function formatCurrentTime(seconds?: number): string {
  return formatDuration(Math.floor(seconds || 0))
}
