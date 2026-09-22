import { isArray, isPlainObject } from 'lodash';

export const markSearchKeyword = (
  data:any[], keys:(string | string[])[], keyword:string, color:string
) => {
  return data.map(item => {
    keys.forEach(key => {
      if (typeof key === 'string') {
        const itemValue = item[key];
        if (typeof itemValue === 'string') {
          const value = replaceMarkValue(
            itemValue, keyword, color
          );
          item[key+'RichText'] = value;
        }
        if (isArray(itemValue) && typeof itemValue[0] === 'string') {
          item[key] = item[key].map((target:string) => {
            const replaceValue = replaceMarkValue(
              target, keyword, color
            );
            if (replaceValue) return replaceValue;
            return target;
          });
        }
      } else {
        const itemValue = item[key[0]];
        const target = item[key[0]][key[1]];
        if (isPlainObject(itemValue)) {
          item[key[0]][key[1]+'RichText'] = replaceMarkValue(
            target, keyword, color
          );
        }
      }
    });
    return item;
  });
};
/**
 * HTML 转义
 * <p>
 * 安全修复：原实现把歌名/歌手/歌单名等后端可控字符串原样拼进 v-html，
 * 配合 8 处 v-html 汇点可构成存储型 XSS（例如把歌单名设为带 onerror 的 img 标签）。
 * 这里对进入 HTML 的所有动态片段做转义；颜色值也做一次白名单校验。
 */
const escapeHtml = (text: string) =>
  String(text).replace(/[&<>"']/g, ch => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    '\'': '&#39;'
  }[ch] as string));

/** 颜色只允许 hex / rgb() / 简单色名，非法值回落 inherit */
const safeColor = (color: string) =>
  (/^[#a-zA-Z0-9(),.%\s-]*$/).test(color || '')
    ? color
    : 'inherit';

const replaceMarkValue = (
  value:string, keyword:string, color:string
) => {
  const text = value ?? '';
  const safeValue = escapeHtml(text);
  const themeColor = safeColor(color);
  if (keyword && text.includes(keyword)) {
    const safeKeyword = escapeHtml(keyword);
    let markValue = safeValue.
      replace(safeKeyword, `<span style="color:${themeColor}">${safeKeyword}</span>`);
    if (text !== keyword) {
      markValue = `<span>${markValue}</span>`;
    }
    return markValue;
  }
  return `<span>${safeValue}</span>`;
};