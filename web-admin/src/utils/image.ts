/**
 * 图片处理工具
 * <p>
 * 封面在上传前统一由浏览器压缩裁剪，理由：
 * 1. 原图动辄数 MB，压缩到百 KB 级可显著减少上传耗时与后端存储占用；
 * 2. 唱片封面需要正方形（CD 旋转、列表缩略图都是 1:1），在客户端裁剪可保证比例一致，
 *    避免后端再引入图片处理依赖；
 * 3. 统一输出 JPEG，避免 PNG 透明底在深色主题下显示异常。
 */

/** 输出边长（正方形，px） */
const DEFAULT_SIZE = 600

/** JPEG 质量（0–1） */
const DEFAULT_QUALITY = 0.85

/** 允许的图片扩展名 */
export const ALLOWED_IMAGE_EXT = ['jpg', 'jpeg', 'png', 'webp', 'gif', 'bmp']

/** 原图体积上限（超过则直接拒绝，避免浏览器解码超大图卡死） */
export const MAX_IMAGE_SIZE = 20 * 1024 * 1024

export interface CompressResult {
  /** 压缩后的文件（JPEG） */
  file: File
  /** 原始宽 × 高 */
  originWidth: number
  originHeight: number
  /** 压缩后边长 */
  outputSize: number
}

/**
 * 把图片等比缩放并居中裁剪为正方形，输出 JPEG
 *
 * @param file    原始图片文件
 * @param size    目标边长（不会放大超过原图短边）
 * @param quality JPEG 质量
 */
export async function compressToSquare(
  file: File,
  size: number = DEFAULT_SIZE,
  quality: number = DEFAULT_QUALITY
): Promise<CompressResult> {
  const bitmap = await loadBitmap(file)
  const { width, height } = bitmap

  // 居中裁剪出正方形
  const side = Math.min(width, height)
  const sx = (width - side) / 2
  const sy = (height - side) / 2

  // 不放大：输出边长取 min(目标边长, 原图短边)
  const outputSize = Math.max(1, Math.min(size, side))

  const canvas = document.createElement('canvas')
  canvas.width = outputSize
  canvas.height = outputSize

  const ctx = canvas.getContext('2d')
  if (!ctx) {
    throw new Error('当前浏览器不支持 canvas，无法压缩图片')
  }
  // 深色背景下 PNG 透明区域会发黑，先铺白底再绘制
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, outputSize, outputSize)
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(bitmap, sx, sy, side, side, 0, 0, outputSize, outputSize)

  // 释放 ImageBitmap
  if ('close' in bitmap && typeof bitmap.close === 'function') {
    bitmap.close()
  }

  const blob = await canvasToBlob(canvas, quality)
  const baseName = file.name.replace(/\.[^.]+$/, '')
  const compressed = new File([blob], `${baseName}.jpg`, { type: 'image/jpeg' })

  return {
    file: compressed,
    originWidth: width,
    originHeight: height,
    outputSize
  }
}

/** 解码图片：优先 createImageBitmap（可自动处理 EXIF 方向），失败回退 <img> */
async function loadBitmap(file: File): Promise<ImageBitmap | HTMLImageElement> {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(file, { imageOrientation: 'from-image' })
    } catch {
      // 部分浏览器不支持 imageOrientation（或 gif 解码失败），走回退逻辑
    }
  }
  return loadImageElement(file)
}

function loadImageElement(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片解析失败，请更换图片'))
    }
    img.src = url
  })
}

function canvasToBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      blob => (blob ? resolve(blob) : reject(new Error('图片压缩失败'))),
      'image/jpeg',
      quality
    )
  })
}

/** 校验图片文件（扩展名 + 体积） */
export function validateImageFile(file: File): string | null {
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!ALLOWED_IMAGE_EXT.includes(ext)) {
    return `「${file.name}」格式不支持，仅支持 ${ALLOWED_IMAGE_EXT.join(' / ')}`
  }
  if (file.size > MAX_IMAGE_SIZE) {
    return `「${file.name}」超过 20MB，请先自行压缩`
  }
  return null
}
