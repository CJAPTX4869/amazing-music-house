@echo off
chcp 65001 >nul
title 奇妙音乐屋 - 网页端(5180)
set "WEB=%~dp0vue3-cloud-music-master"
echo 正在启动网页端（后端地址见 dist\config.json）...
cd /d "%WEB%"
start "" http://127.0.0.1:5180/
node desktop\server.cjs
pause