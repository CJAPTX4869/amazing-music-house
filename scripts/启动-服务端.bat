@echo off
chcp 65001 >nul
title 奇妙音乐屋 - 服务端(8080)
set "ROOT=%~dp0"
echo 正在启动后端（JWT_SECRET 已配置，登录状态可保持 7 天）...
cd /d "%ROOT%music-platform\music-server"
java -jar target\music-server-1.0.0.jar
pause