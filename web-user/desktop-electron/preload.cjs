/**
 * Electron 预加载脚本：把后端地址注入渲染层
 * <p>
 * 打包后页面是 file:// 协议，fetch('config.json') 会被安全策略拦掉，
 * 所以由主进程侧读文件、这里用 contextBridge 暴露给页面（见 src/config.ts 的读取顺序）。
 */
const { contextBridge } = require('electron');
const path = require('path');
const fs = require('fs');

const DEFAULT_API_BASE = 'http://localhost:8080/api';

function readApiBase() {
  const candidates = [];
  try {
    const { app } = require('electron');
    candidates.push(path.join(app.getPath('userData'), 'config.json'));
    candidates.push(path.join(path.dirname(app.getPath('exe')), 'config.json'));
  } catch (e) { /* ignore */ }

  for (const p of candidates) {
    try {
      if (fs.existsSync(p)) {
        const cfg = JSON.parse(fs.readFileSync(p, 'utf8'));
        if (cfg && cfg.apiBase) return String(cfg.apiBase);
      }
    } catch (e) { /* ignore */ }
  }
  return DEFAULT_API_BASE;
}

try {
  contextBridge.exposeInMainWorld('__MUSIC_CONFIG__', {
    apiBase: readApiBase(),
    platform: process.platform,
    version: process.env.npm_package_version || ''
  });
} catch (e) {
  console.warn('[preload] 注入配置失败', e);
}
