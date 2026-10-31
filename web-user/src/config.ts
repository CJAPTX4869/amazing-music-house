/**
 * 运行时配置（后端地址）
 * <p>
 * 背景：原来 axios 的 baseURL 硬编码为 http://localhost:8080/api，
 * 换一台电脑安装客户端后请求会打到自己身上（本机没有后端）→ 必然全部失败。
 * <p>
 * 现在按"运行时配置"解析，优先级：
 *   1) 同目录下的 config.json（部署时改这个文件即可，不必重新构建）
 *   2) localStorage.apiBase（可在控制台 setApiBase('http://x.x.x.x:8080/api') 临时覆盖）
 *   3) 构建期环境变量 VITE_API_BASE_URL
 *   4) 兜底 http://localhost:8080/api
 */

declare global {
  interface Window {
    __API_BASE__?: string;
    /** Electron 预加载脚本注入的配置（打包后页面是 file:// 协议，读不到 config.json） */
    __MUSIC_CONFIG__?: { apiBase?: string; platform?: string; version?: string };
  }
}

const STORAGE_KEY = 'apiBase';
const DEFAULT_API_BASE = 'http://localhost:8080/api';

/** 规范化：补协议、去尾斜杠、补 /api 上下文路径 */
export function normalizeApiBase(value: string): string {
  if (!value) return '';
  let v = String(value).trim();
  if (!v) return '';
  // "auto"：跟随当前页面的主机名推导后端地址。
  // 网页端要给局域网多台设备用，而 config.json 是同一份文件，
  // 写死 localhost 会让手机去连手机自己；写死某个 IP 又要求每台设备手改。
  if (/^(auto|\*)$/i.test(v)) {
    const host = location.hostname;
    // Electron 下页面是 file://，hostname 为空 → 回落到本机
    v = (!host || host === 'null') ? '127.0.0.1:8080' : `${location.protocol}//${host}:8080`;
  }
  if (!/^https?:\/\//i.test(v)) {
    v = `http://${v}`;
  }
  v = v.replace(/\/+$/, '');
  if (!/\/api$/i.test(v)) {
    v = `${v}/api`;
  }
  return v;
}

/** 取当前生效的后端地址 */
export function getApiBase(): string {
  if (window.__API_BASE__) return window.__API_BASE__;
  // 桌面客户端（Electron）由 preload 注入 —— file:// 协议下 fetch(config.json) 会被拦掉
  const injected = window.__MUSIC_CONFIG__?.apiBase;
  if (injected) {
    const normalized = normalizeApiBase(injected);
    if (normalized) {
      window.__API_BASE__ = normalized;
      return normalized;
    }
  }
  const stored = localStorage.getItem(STORAGE_KEY);
  const value = (stored && normalizeApiBase(stored))
    || normalizeApiBase(import.meta.env.VITE_API_BASE_URL as string)
    || DEFAULT_API_BASE;
  window.__API_BASE__ = value;
  return value;
}

/** 运行时切换后端地址（持久化到 localStorage） */
export function setApiBase(value: string): string {
  const normalized = normalizeApiBase(value);
  if (!normalized) return getApiBase();
  window.__API_BASE__ = normalized;
  localStorage.setItem(STORAGE_KEY, normalized);
  return normalized;
}

/**
 * 启动时读取同目录 config.json
 * <p>
 * 开发（dev server）与打包后（dist 目录）都是相对本应用根目录读取，
 * 所以换后端只改这一个文件；读不到时保持上面的回落链。
 */
export async function loadRuntimeConfig(): Promise<string> {
  try {
    const res = await fetch(`./config.json?t=${Date.now()}`, { cache: 'no-store' });
    if (res.ok) {
      const cfg = await res.json();
      if (cfg && cfg.apiBase) {
        return setApiBase(cfg.apiBase);
      }
    }
  } catch (e) {
    // 无 config.json / 被拦截：保持回落值
    console.warn('[config] 读取 config.json 失败，使用默认后端地址', e);
  }
  return getApiBase();
}
