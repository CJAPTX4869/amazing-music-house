@echo off
chcp 65001 >nul
setlocal
rem ===== 导出需要在 Kali 上重建的全部数据（数据库 + storage + jar）=====
set "MYSQL_ROOT_USER=root"
set "MYSQL_ROOT_PWD=root"
set "MYSQLDUMP=C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe"
set "OUT=%~dp0music_db.sql"

echo [1/2] 导出数据库 music_db ...
"%MYSQLDUMP%" -u%MYSQL_ROOT_USER% -p%MYSQL_ROOT_PWD% --databases music_db --single-transaction --routines --triggers --default-character-set=utf8mb4 > "%OUT%"
if errorlevel 1 ( echo   导出失败：请检查 MySQL 是否运行、账号密码是否正确 & pause & exit /b 1 )
for %%A in ("%OUT%") do echo   完成: %%A  (%%~zA 字节)

echo.
echo [2/2] 还需手动拷贝（缺了会出现"歌在但放不了/封面全空"）：
echo    %~dp0music-platform\music-server\storage\                       （约 4.6GB：音频/封面/歌词/表情）
echo    %~dp0music-platform\music-server\target\music-server-1.0.0.jar  （后端程序，Kali 上不用装 Maven）
echo.
echo 拷到 Kali 的项目目录后，在 Kali 上执行： ./deploy-kali.sh
pause