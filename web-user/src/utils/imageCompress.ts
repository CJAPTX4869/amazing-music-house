/**
 * 图片压缩工具（浏览器端 canvas 处理）
 * <p>
 * 1. 头像：居中裁剪为正方形 + JPEG，减少上传体积（与后端 2MB 限制配套）；
 * 2. 页面背景：等比缩放（不裁剪）+ JPEG，把体积控制在 localStorage 配额内。
 */

/** 允许的图片扩展名 */
export const ALLOWED_IMAGE_EXT = ['jpg', 'jpeg', 'png', 'webp', 'gif', 'bmp'];

/** 原图体积上限（超过直接拒绝，避免浏览器解码超大图卡死） */
export const MAX_IMAGE_SIZE = 20 * 1024 * 1024;

/** 背景图 dataURL 字符数上限（约 2MB，为 localStorage 留出余量） */
const BG_DATA_URL_LIMIT = 2600000;

/** 校验图片文件（扩展名 + 体积），返回错误信息；通过时返回 null */
export function validateImageFile(file: File): string | null {
  const ext = (file.name.split('.').pop() || '').toLowerCase();
  if (!ALLOWED_IMAGE_EXT.includes(ext)) {
    return `「${file.name}」格式不支持，仅支持 ${ALLOWED_IMAGE_EXT.join(' / ')}`;
  }
  if (file.size > MAX_IMAGE_SIZE) {
    return `「${file.name}」超过 20MB，请先自行压缩`;
  }
  return null;
}

/** 居中裁剪为正方形并压缩为 JPEG（头像用） */
export async function compressToSquare(file: File, size = 512, quality = 0.9): Promise<File> {
  const bitmap = await loadBitmap(file);
  const { width, height } = bitmap;
  const side = Math.min(width, height);
  const sx = (width - side) / 2;
  const sy = (height - side) / 2;
  const outputSize = Math.max(1, Math.min(size, side));

  const canvas = document.createElement('canvas');
  canvas.width = outputSize;
  canvas.height = outputSize;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('当前浏览器不支持 canvas，无法处理图片');
  }
  // PNG 透明区域在深色主题下会发黑，先铺白底
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, outputSize, outputSize);
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(bitmap as CanvasImageSource, sx, sy, side, side, 0, 0, outputSize, outputSize);
  closeBitmap(bitmap);

  const blob = await canvasToBlob(canvas, quality);
  const baseName = file.name.replace(/\.[^.]+$/, '');
  return new File([blob], `${baseName}.jpg`, { type: 'image/jpeg' });
}

/**
 * 等比缩放（不裁剪）为背景图，返回 dataURL
 * <p>
 * 逐级降低分辨率与质量，直到体积落在 localStorage 可用范围内。
 */
export async function compressForBackground(file: File): Promise<string> {
  const attempts = [
    { maxWidth: 1920, quality: 0.85 },
    { maxWidth: 1440, quality: 0.72 },
    { maxWidth: 1024, quality: 0.6 },
  ];
  const bitmap = await loadBitmap(file);
  let result = '';
  for (const attempt of attempts) {
    const scale = Math.min(1, attempt.maxWidth / bitmap.width);
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));

    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      closeBitmap(bitmap);
      throw new Error('当前浏览器不支持 canvas，无法处理图片');
    }
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(bitmap as CanvasImageSource, 0, 0, width, height);
    result = canvas.toDataURL('image/jpeg', attempt.quality);
    if (result.length <= BG_DATA_URL_LIMIT) {
      break;
    }
  }
  closeBitmap(bitmap);
  if (result.length > BG_DATA_URL_LIMIT) {
    throw new Error('图片过大，压缩后仍超出本地存储上限，请换一张图片');
  }
  return result;
}

/**
 * 等比压缩（不裁剪、保持构图），返回 File —— 评论配图用
 * <p>
 * 与 {@link compressToSquare} 的区别：不裁正方形，仅限制最长边；
 * GIF 直接原样返回（canvas 压缩会丢失动画帧）。
 *
 * @param file    原图
 * @param maxEdge 最长边上限（像素）
 * @param quality JPEG 质量
 */
export async function compressKeepRatio(file: File, maxEdge = 1080, quality = 0.85): Promise<File> {
  if ((file.type || '').toLowerCase() === 'image/gif') {
    return file;
  }
  const bitmap = await loadBitmap(file);
  const scale = Math.min(1, maxEdge / Math.max(bitmap.width, bitmap.height));
  const width = Math.max(1, Math.round(bitmap.width * scale));
  const height = Math.max(1, Math.round(bitmap.height * scale));

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    closeBitmap(bitmap);
    throw new Error('当前浏览器不支持 canvas，无法处理图片');
  }
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(bitmap as CanvasImageSource, 0, 0, width, height);
  closeBitmap(bitmap);

  const blob = await canvasToBlob(canvas, quality);
  const baseName = file.name.replace(/\.[^.]+$/, '');
  return new File([blob], `${baseName}.jpg`, { type: 'image/jpeg' });
}

/** 校验网络图片地址是否可正常加载，成功返回该地址 */
export function validateImageUrl(url: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(url);
    img.onerror = () => reject(new Error('图片地址无法加载，请检查链接是否正确'));
    img.src = url;
  });
}

/** 解码图片：优先 createImageBitmap（自动处理 EXIF 方向），失败回退 <img> */
async function loadBitmap(file: File): Promise<ImageBitmap | HTMLImageElement> {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(file, { imageOrientation: 'from-image' });
    } catch {
      // 部分浏览器不支持 imageOrientation，走回退逻辑
    }
  }
  return loadImageElement(file);
}

function loadImageElement(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      resolve(img);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('图片解析失败，请更换图片'));
    };
    img.src = url;
  });
}

function closeBitmap(bitmap: ImageBitmap | HTMLImageElement) {
  if ('close' in bitmap && typeof bitmap.close === 'function') {
    bitmap.close();
  }
}

function canvasToBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      blob => (blob ? resolve(blob) : reject(new Error('图片压缩失败'))),
      'image/jpeg',
      quality
    );
  });
}
