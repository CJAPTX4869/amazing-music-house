/**
 * 评论内容处理工具
 * <p>
 * 1. 评论正文中的表情以 [表情名] 占位符存储（后端同款约定），
 *    渲染时解析为「文本段 + 表情图片段」序列，由模板以组件方式渲染
 *    （不使用 v-html，天然防 XSS）；
 * 2. 角标数字格式化：≤999 原样、999+ 封顶、万级 1w+/10.5w+/100w+、亿级 x.x亿+。
 */

export type ContentSegment =
  | { type: 'text'; text: string }
  | { type: 'emoji'; name: string; url: string };

const EMOJI_PLACEHOLDER_RE = /\[([^\[\]]{1,20})\]/g;

/**
 * 把含 [表情名] 占位符的评论文本拆分为渲染段
 *
 * @param content  评论文本
 * @param emojiMap 表情名 → 图片地址（相对或绝对均可，由 toAbsolute 统一处理）
 * @param toAbsolute 相对地址 → 可访问地址的转换函数
 */
export function parseContent(
  content: string,
  emojiMap: Map<string, string>,
  toAbsolute: (url: string) => string
): ContentSegment[] {
  if (!content) {
    return [{ type: 'text', text: '' }];
  }
  const segments: ContentSegment[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  EMOJI_PLACEHOLDER_RE.lastIndex = 0;

  while ((match = EMOJI_PLACEHOLDER_RE.exec(content)) !== null) {
    const name = match[1];
    const url = emojiMap.get(name);
    if (!url) {
      continue;
    }
    if (match.index > lastIndex) {
      segments.push({ type: 'text', text: content.slice(lastIndex, match.index) });
    }
    segments.push({ type: 'emoji', name, url: toAbsolute(url) });
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < content.length) {
    segments.push({ type: 'text', text: content.slice(lastIndex) });
  }
  return segments.length ? segments : [{ type: 'text', text: content }];
}

/**
 * 计数格式化（网易云风格角标）：
 * ≤999 → 原样；999<n<1万 → 999+；1万~1亿 → 1w+ / 10.5w+ / 100w+；≥1亿 → x.x亿+
 */
export function formatCount(n: number | null | undefined): string {
  const v = Number(n) || 0;
  if (v <= 999) {
    return String(v);
  }
  if (v < 10000) {
    return '999+';
  }
  if (v < 100000000) {
    const w = v / 10000;
    const text = w >= 100 ? String(Math.floor(w)) : w.toFixed(1).replace(/\.0$/, '');
    return `${text}w+`;
  }
  const y = v / 100000000;
  const text = y >= 100 ? String(Math.floor(y)) : y.toFixed(1).replace(/\.0$/, '');
  return `${text}亿+`;
}
