/**
 * Electron 预加载脚本：把后端地址注入渲染层
 * <p>
 * 打包后页面是 file:// 协议，fetch('config.json') 会被安全策略拦掉，所以由主进程读文件、
 * 通过 webPreferences.additionalArguments 传进来（见 main.cjs 的 readApiBase）。
 * <p>
 * 这里刻意不 require 任何 Node 内置模块：渲染进程默认沙箱化，沙箱里的 preload 只能用
 * electron 暴露的少数模块，require('fs')/require('path') 会让整个 preload 加载失败，
 * 表现为页面拿不到 __MUSIC_CONFIG__、后端地址静默回落到默认的 localhost。
 */
const { contextBridge } = require('electron');

const DEFAULT_API_BASE = 'http://localhost:8080/api';
const ARG_PREFIX = '--music-api-base=';

function apiBaseFromArgv() {
  const hit = process.argv.find(a => typeof a === 'string' && a.startsWith(ARG_PREFIX));
  if (!hit) return DEFAULT_API_BASE;
  const value = hit.slice(ARG_PREFIX.length).trim();
  return value || DEFAULT_API_BASE;
}

try {
  contextBridge.exposeInMainWorld('__MUSIC_CONFIG__', {
    apiBase: apiBaseFromArgv(),
    platform: process.platform,
    version: ''
  });
} catch (e) {
  console.warn('[preload] 注入配置失败', e);
}
