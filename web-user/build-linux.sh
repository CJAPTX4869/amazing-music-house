#!/usr/bin/env bash
# 奇妙音乐屋 Linux 客户端打包脚本（在 Kali / Debian 上运行）
# 用法：把整个 vue3-cloud-music-master 目录拷到 Kali（不需要 node_modules），然后：
#   chmod +x build-linux.sh && ./build-linux.sh
set -e
cd "$(dirname "$0")"

export ELECTRON_MIRROR="${ELECTRON_MIRROR:-https://npmmirror.com/mirrors/electron/}"
export ELECTRON_BUILDER_BINARIES_MIRROR="${ELECTRON_BUILDER_BINARIES_MIRROR:-https://npmmirror.com/mirrors/electron-builder-binaries/}"

echo "== 1/4 检查环境 =="
if ! command -v node >/dev/null 2>&1; then
  echo "缺少 Node.js，请先执行：sudo apt update && sudo apt install -y nodejs npm"
  exit 1
fi
node -v
npm -v

echo "== 2/4 安装依赖（会下载 Linux 版 Electron）=="
npm install

echo "== 3/4 构建前端 =="
npm run build

echo "== 4/4 打包 AppImage + deb =="
npx electron-builder --linux AppImage deb

echo
echo "== 完成，产物在 release/ =="
ls -lh release/ | grep -Ei 'appimage|\.deb' || true
echo
echo "运行方式："
echo "  AppImage: chmod +x release/MusicHouse-*.AppImage && ./release/MusicHouse-*.AppImage"
echo "  deb     : sudo apt install ./release/MusicHouse-*.deb"