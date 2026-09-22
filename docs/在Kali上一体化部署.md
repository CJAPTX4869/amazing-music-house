# 在 Kali 上一体化部署（数据库 + 后端 + 客户端）

对应拓扑：**MariaDB + Java 后端 + Electron 客户端** 全在 Kali。

## 一、从 Windows 迁移数据（两步，缺一不可）

在 Windows 上双击项目目录里的 `export-for-kali.bat`，它会：
1. 用 `mysqldump` 导出 `music_db.sql`（库里只有路径与元数据，约 0.7 MB）
2. 提示你还要拷贝：`music-platform\music-server\storage\`（**约 4.6GB**）与 `...\target\music-server-1.0.0.jar`（后端程序，jar 跨平台，**Kali 上不必装 Maven**）

> 只搬数据库不搬 storage，就会出现"歌在但放不了、封面全空"——数据库里存的是相对路径。

## 二、在 Kali 上部署

```bash
cd 项目目录
chmod +x deploy-kali.sh
./deploy-kali.sh
```

脚本自动完成：装 mariadb-server + Java 11 → 启动并设开机自启 → 建库 `music_db`(utf8mb4) 与用户 `music` → 导入 `music_db.sql`（没有就按 schema.sql 初始化空库）→ 检查 storage → 用 `MYSQL_USERNAME/MYSQL_PASSWORD/JWT_SECRET` 环境变量启动后端并健康检查 →（若已有 AppImage）把客户端配置写成 `http://127.0.0.1:8080/api`。

自定义密码：`MYSQL_PASSWORD=你的密码 ./deploy-kali.sh`

## 三、客户端

```bash
cd vue3-cloud-music-master && ./build-linux.sh
./release/MusicHouse-1.0.0-x86_64.AppImage
```
配置：`~/.config/cloud-music-vue3/config.json` → `{"apiBase":"http://127.0.0.1:8080/api"}`
若其它机器要连这台 Kali：Windows 侧填 Kali 的局域网 IP，并在 Kali 放行 `sudo ufw allow 8080/tcp`。

## 四、开机自启（可选）

```bash
crontab -e
@reboot cd /home/kali/MusicProject && MYSQL_USERNAME=music MYSQL_PASSWORD='music123' JWT_SECRET='固定密钥' nohup java -jar music-platform/music-server/target/music-server-1.0.0.jar >> backend.log 2>&1 &
```

## 五、常见问题

| 现象 | 处理 |
|---|---|
| 封面/歌曲全丢 | storage 没拷全；确认 `music-platform/music-server/storage/{audio,cover,lyric,emoji}` 都在 |
| 连不上数据库 | `sudo systemctl status mariadb`；核对库名/用户/密码与 `MYSQL_*` 环境变量 |
| 后端起来就退出 | 看 `backend.log`；常见是数据库连接失败或 jar 路径不对 |
| 客户端接口失败 | 检查 `~/.config/cloud-music-vue3/config.json` 的 `apiBase` |
| 换位置后文件又找不到 | 不需要配路径：后端起时会自动向上找含 `pom.xml`/`storage` 的目录（storage 要与 jar 一起搬） |
| 想重置所有登录 | 换一个 `JWT_SECRET` 并重启后端 |

## 六、备份

```bash
mysqldump -umusic -p music_db > backup_$(date +%F).sql
tar czf storage_$(date +%F).tar.gz music-platform/music-server/storage
```