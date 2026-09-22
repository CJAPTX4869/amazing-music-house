# 在 Kali 上打包与运行「奇妙音乐屋」客户端

## 一、准备（只做一次）

1. 在 Windows 上把 **整个 `vue3-cloud-music-master` 目录**拷到 Kali
   （**不要拷 `node_modules` 和 `release`**，它们是 Windows 版且体积 1GB+）
2. Kali 上装 Node（18+）：
   ```bash
   sudo apt update && sudo apt install -y nodejs npm
   node -v
   ```
3. 打包：
   ```bash
   cd vue3-cloud-music-master
   chmod +x build-linux.sh
   ./build-linux.sh
   ```

产物在 `release/`：
- `MusicHouse-1.0.0-x86_64.AppImage` —— **免安装单文件，推荐**
- `MusicHouse-1.0.0-amd64.deb` —— 系统安装包

## 二、运行

```bash
# AppImage（免安装）
chmod +x release/MusicHouse-*.AppImage
./release/MusicHouse-*.AppImage
# 若报 FUSE 相关错误（Kali 常见）：
./release/MusicHouse-*.AppImage --appimage-extract-and-run

# deb
sudo apt install ./release/MusicHouse-*.deb
```

**启动报沙箱错误时**（Electron 需要用户命名空间）：
```bash
sudo sysctl -w kernel.unprivileged_userns_clone=1
# 或临时：./release/MusicHouse-*.AppImage --no-sandbox
```

**deb 依赖装不上时**：Kali 新版把 `libasound2`→`libasound2t64`、`libgtk-3-0`→`libgtk-3-0t64`，
可先 `sudo apt install -f`；不行就用 AppImage（不依赖系统库）。

## 三、连接后端（关键）

配置文件：**`~/.config/cloud-music-vue3/config.json`**（首次运行自动生成）
```json
{ "apiBase": "http://192.168.5.8:8080/api" }
```
只写 `192.168.5.8:8080` 也行（自动补 `http://` 与 `/api`）。

Windows 那台（跑后端的机器）放行防火墙，管理员 PowerShell 执行一次：
```
netsh advfirewall firewall add rule name="music-server-8080" dir=in action=allow protocol=TCP localport=8080
```

Kali 上验证连通：
```bash
curl http://192.168.5.8:8080/api/top/song?type=1 | head -c 200
```

## 四、说明

- 包内**自带 Chromium + Node**（Electron），Kali 上运行时**不需要装 Node/Edge**（Node 只在打包时需要）
- 功能与 Windows 版完全一致：开屏动画、歌单排序（含拖拽）、歌曲真移除、我喜欢的音乐封面继承、好友列表等
- Java + MySQL 仍然跑在 Windows 那台机器上，Kali 只跑客户端