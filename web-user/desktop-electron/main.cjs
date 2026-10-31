/**
 * 奇妙音乐屋 · Electron 主进程
 * <p>
 * 目标：打包成独立安装包后，目标机器**不需要装 Node / Edge**，双击即用。
 * - 渲染层是已构建的 dist（相对路径，file:// 可用）
 * - 后端地址通过 preload 注入（file:// 下 fetch config.json 会被安全策略拦掉）
 * - 配置读取顺序：%APPDATA%\MusicHouse\config.json → 安装目录\config.json → 内置默认
 * - 可选：若同目录存在 music-server jar 且本机有 java，可自动拉起后端（默认关闭，见 AUTO_START_BACKEND）
 */
const { app, BrowserWindow, Menu, shell, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const { spawn, execSync } = require('child_process');

const APP_NAME = '奇妙音乐屋';
const DEFAULT_API_BASE = 'http://localhost:8080/api';
/** 是否自动拉起本机后端（仅当安装目录里带了 jar 且本机有 java 时才有意义） */
const AUTO_START_BACKEND = false;

function userConfigPath() {
  return path.join(app.getPath('userData'), 'config.json');
}

function ensureUserConfig() {
  const p = userConfigPath();
  try {
    if (!fs.existsSync(p)) {
      fs.mkdirSync(path.dirname(p), { recursive: true });
      fs.writeFileSync(p, JSON.stringify({
        _说明: '后端地址。改成后端所在机器的地址即可，例如 http://192.168.5.8:8080/api（只填 IP:端口 也行）',
        apiBase: DEFAULT_API_BASE
      }, null, 2), 'utf8');
    }
  } catch (e) {
    console.warn('[config] 写入用户配置失败', e);
  }
  return p;
}

function readApiBase() {
  const candidates = [
    userConfigPath(),                                   // 用户可写（推荐）
    path.join(path.dirname(app.getPath('exe')), 'config.json'), // 便携版：exe 同目录
    path.join(__dirname, '..', 'dist', 'config.json')    // 开发/绿色版：dist 里
  ];
  for (const p of candidates) {
    try {
      if (fs.existsSync(p)) {
        const cfg = JSON.parse(fs.readFileSync(p, 'utf8'));
        if (cfg && cfg.apiBase) return String(cfg.apiBase);
      }
    } catch (e) {
      console.warn('[config] 解析失败：', p, e.message);
    }
  }
  return DEFAULT_API_BASE;
}

function startBackendIfPresent() {
  if (!AUTO_START_BACKEND) return;
  try {
    const jar = path.join(process.resourcesPath || __dirname, 'backend', 'music-server-1.0.1.jar');
    if (!fs.existsSync(jar)) return;
    execSync('java -version', { stdio: 'ignore' });   // 本机没有 java 就跳过
    const child = spawn('java', ['-jar', jar], {
      detached: true,
      stdio: 'ignore',
      windowsHide: true
    });
    child.unref();
  } catch (e) {
    console.warn('[backend] 未能自动拉起：', e.message);
  }
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 940,
    minHeight: 620,
    title: APP_NAME,
    icon: path.join(__dirname, 'icon.png'),
    autoHideMenuBar: true,
    backgroundColor: '#101014',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      // 后端地址由主进程读好后从命令行参数递进去。
      // 不能让 preload 自己 require('fs'/'path') 去读 config.json —— Electron 20 起渲染进程
      // 默认沙箱化，沙箱里的 preload 只有 electron 等少数模块，require 内置模块会直接
      // "Unable to load preload script / module not found: path"，配置注入整个失效。
      additionalArguments: ['--music-api-base=' + readApiBase()]
    }
  });

  Menu.setApplicationMenu(null);

  // 开发态（未打包）直接读项目里的 dist；打包后读 resources/web
  const indexHtml = app.isPackaged
    ? path.join(process.resourcesPath, 'web', 'index.html')
    : path.join(__dirname, '..', 'dist', 'index.html');
  win.loadFile(indexHtml);

  // 站外链接用系统浏览器打开，窗口内不跑
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/i.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });

  return win;
}

app.whenReady().then(() => {
  ensureUserConfig();
  startBackendIfPresent();
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  app.quit();
});
