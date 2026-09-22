# 奇妙音乐屋 · 桌面客户端（Windows）

不用打开浏览器：双击 `start.vbs`（或桌面快捷方式「奇妙音乐屋」）就得到一个独立窗口，没有地址栏和标签页。

## 它做了什么

1. **检查后端**：探测 `http://127.0.0.1:8080/api` —— 没起来就尝试用
   `music-platform\music-server\target\music-server-1.0.0.jar` 起一个（隐藏窗口，日志写 `desktop\backend.log`）
2. **起本地静态服务**：`server.cjs` 把 `..\dist` 目录以 `http://127.0.0.1:5180` 提供出来
   （用 `file://` 直接打开的话，`config.json` 会被浏览器安全策略拦掉，所以必须有个本地 http 源）
3. **应用模式窗口**：用 Edge 的 `--app=` 打开，独立 `user-data-dir`（登录状态与日常浏览器互不干扰）

## 换后端地址（关键）

编辑 **`dist\config.json`**：

```json
{ "apiBase": "http://192.168.5.8:8080/api" }
```

只填 `192.168.5.8:8080` 也行，程序会自动补 `http://` 和 `/api`。改完**重启客户端**生效，**不需要重新构建**。

## 其他电脑怎么连这台后端

1. 后端所在机器：确认 8080 在监听（`netstat -ano | findstr :8080`），并放行防火墙
   （管理员 PowerShell：`netsh advfirewall firewall add rule name="music-server-8080" dir=in action=allow protocol=TCP localport=8080`）
2. 查这台机器的局域网 IP：`ipconfig`（本机是 `192.168.5.8`）
3. 其他电脑：把客户端整个目录拷过去，**只改 `dist\config.json` 里的 apiBase** 为该 IP，双击 `start.vbs`
4. 想免安装：把 `start.vbs` 发送到桌面快捷方式，图标选 `dist\favicon.ico`

> 注意：后端返回的封面/音频地址是按请求里的 Host 自动拼的，所以客户端用 IP 访问时，
> 图片与播放地址也会自动变成那个 IP，无需额外配置。

## 目录说明

| 文件 | 作用 |
|---|---|
| `server.cjs` | 本地静态服务（零依赖，Node 即可） |
| `start.vbs` | 启动器：后端探测 + 静态服务 + 应用窗口 |
| `backend.log` / `desktop.log` | 后端与静态服务的日志（自动生成） |
| `edge-profile\` | 应用专用浏览器配置（登录状态存在这里） |

## 想做成真正的 .exe 安装包

现在的形态是"Chromium 应用窗口"，已经不依赖浏览器界面。若要单文件安装包（Electron/Tauri），
需要额外下载 Electron 运行时（约 100MB）并用 electron-builder 打包，可以后续再做。
