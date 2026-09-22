@echo off
scp D:\music_db.sql root@192.168.16.223:/root/
if errorlevel 1 goto fail
scp "D:\Desktop\MusicProject\music-platform\music-server\target\music-server-1.0.0.jar" root@192.168.16.223:/opt/
if errorlevel 1 goto fail
scp D:\storage.tar root@192.168.16.223:/opt/
if errorlevel 1 goto fail
echo DONE > D:\Desktop\MusicProject\scp.done
exit /b 0
:fail
echo FAILED > D:\Desktop\MusicProject\scp.fail
