/**
 * 颜色工具：hex 与 RGB/HSL 互转、亮暗派生
 * <p>
 * 供主题换色使用：选定主色后自动派生 hover（变亮）/ pressed（变暗），
 * 以及艺术字渐变所需的三档色阶。
 */

export interface Rgb {
  r: number;
  g: number;
  b: number;
}

/** 校验是否为合法 6 位 hex 颜色（#RRGGBB 或 RRGGBB） */
export function isValidHex(color: string): boolean {
  return /^#?([0-9a-fA-F]{6})$/.test(color);
}

/** 规范化为 #RRGGBB 小写形式；非法输入返回 null */
export function normalizeHex(color: string): string | null {
  if (!isValidHex(color)) return null;
  const hex = color.startsWith('#') ? color.slice(1) : color;
  return `#${hex.toLowerCase()}`;
}

export function hexToRgb(hex: string): Rgb {
  const h = normalizeHex(hex) ?? '#000000';
  return {
    r: parseInt(h.slice(1, 3), 16),
    g: parseInt(h.slice(3, 5), 16),
    b: parseInt(h.slice(5, 7), 16)
  };
}

export function rgbToHex({ r, g, b }: Rgb): string {
  const to = (v: number) => Math.round(Math.min(255, Math.max(0, v))).toString(16).padStart(2, '0');
  return `#${to(r)}${to(g)}${to(b)}`;
}

interface Hsl {
  h: number; // 0-360
  s: number; // 0-1
  l: number; // 0-1
}

function rgbToHsl({ r, g, b }: Rgb): Hsl {
  const rn = r / 255;
  const gn = g / 255;
  const bn = b / 255;
  const max = Math.max(rn, gn, bn);
  const min = Math.min(rn, gn, bn);
  const l = (max + min) / 2;
  if (max === min) return { h: 0, s: 0, l };

  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  let h: number;
  switch (max) {
    case rn: h = ((gn - bn) / d + (gn < bn ? 6 : 0)); break;
    case gn: h = (bn - rn) / d + 2; break;
    default: h = (rn - gn) / d + 4;
  }
  return { h: h * 60, s, l };
}

function hslToRgb({ h, s, l }: Hsl): Rgb {
  const hn = ((h % 360) + 360) % 360 / 360;
  if (s === 0) {
    const v = l * 255;
    return { r: v, g: v, b: v };
  }
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
  const p = 2 * l - q;
  const channel = (t: number) => {
    if (t < 0) t += 1;
    if (t > 1) t -= 1;
    if (t < 1 / 6) return p + (q - p) * 6 * t;
    if (t < 1 / 2) return q;
    if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
    return p;
  };
  return {
    r: channel(hn + 1 / 3) * 255,
    g: channel(hn) * 255,
    b: channel(hn - 1 / 3) * 255
  };
}

/**
 * 调整亮度：amount > 0 变亮（趋向白），< 0 变暗（趋向黑），范围 -1 ~ 1
 */
export function adjustLightness(hex: string, amount: number): string {
  const hsl = rgbToHsl(hexToRgb(hex));
  hsl.l = Math.min(1, Math.max(0, hsl.l + amount));
  return rgbToHex(hslToRgb(hsl));
}

/** 变亮（用于 hover 态），amount 0~1 */
export function lighten(hex: string, amount = 0.08): string {
  return adjustLightness(hex, amount);
}

/** 变暗（用于 pressed 态），amount 0~1 */
export function darken(hex: string, amount = 0.08): string {
  return adjustLightness(hex, -amount);
}
