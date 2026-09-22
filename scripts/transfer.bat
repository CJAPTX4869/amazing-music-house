@echo off
rem 传产物到部署虚拟机（Windows 10+ 自带 OpenSSH 的 scp）
rem 用法：先构建后端（mvn package）；按需修改下一行虚拟机地址，或运行前 set VM=root@<IP>
setlocal
if "%VM%"=="" set VM=root@192.168.16.223

scp sql\music_db.sql %VM%:/root/                      || goto fail
scp backend\target\music-server-1.0.0.jar %VM%:/opt/ || goto fail
if exist storage.tar scp storage.tar %VM%:/opt/       || goto fail

echo 传输完成
exit /b 0

:fail
echo 传输失败：检查 %VM% 可达、SSH 正常、jar 已构建（mvn package）
exit /b 1
