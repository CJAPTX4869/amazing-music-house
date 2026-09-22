import ColorThief from 'colorthief';

/**
 * 提取图片主色调
 * <p>
 * 兼容空/无效图片地址：本项目的封面图可能为空（未上传封面），
 * 此时原实现会 reject，导致调用方（如 MusicDetail 的 fillBackground）抛出未捕获异常。
 * 现改为兜底返回中性灰，保证背景渐变仍能正常渲染。
 */
export function analyze(src: string): Promise<string> {
  const DEFAULT_COLOR = 'rgb(200, 200, 200)';
  if (!src) {
    return Promise.resolve(DEFAULT_COLOR);
  }
  return new Promise((resolve) => {
    const colorThief = new ColorThief();
    const image = new Image();
    image.crossOrigin = 'Anonymous';
    function handleLoad() {
      try {
        const color = colorThief.getColor(image);
        resolve(`rgb(${color.join(',')})`);
      } catch (e) {
        resolve(DEFAULT_COLOR);
      }
      image.removeEventListener('load', handleLoad);
    }
    image.addEventListener('load', handleLoad);
    image.onerror = () => {
      resolve(DEFAULT_COLOR);
    };
    image.src = src;
  });
}
