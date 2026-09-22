#!/usr/bin/env bash
# ============================================================
#  奇妙音乐屋 —— Kali 一体化部署（MariaDB + 后端）
#  前置：把 music_db.sql、storage/、target/music-server-1.0.0.jar 一起拷到本项目目录
#  用法：cd 项目目录 && chmod +x deploy-kali.sh && ./deploy-kali.sh
# ============================================================
set -e
cd "$(dirname "$0")"
SRV="music-platform/music-server"
JAR="$SRV/target/music-server-1.0.0.jar"
DB_NAME="music_db"
DB_USER="music"
DB_PASS="${MYSQL_PASSWORD:-music123}"
JWT="${JWT_SECRET:-}"

echo "== 1/7 安装依赖 =="
sudo apt update
sudo apt install -y mariadb-server curl
if ! command -v java >/dev/null 2>&1; then
  sudo apt install -y openjdk-11-jre-headless || sudo apt install -y default-jre-headless
fi
java -version 2>&1 | head -1

echo "== 2/7 启动 MariaDB =="
sudo systemctl enable --now mariadb

echo "== 3/7 建库与账号（utf8mb4，表情功能需要）=="
sudo mysql <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL
echo "   库 ${DB_NAME} / 用户 ${DB_USER} 就绪"

echo "== 4/7 导入数据 =="
if [ -f "music_db.sql" ]; then
  sudo mysql < music_db.sql && echo "   已导入 music_db.sql"
elif [ -f "$SRV/src/main/resources/db/schema.sql" ]; then
  for f in "$SRV/src/main/resources/db/schema.sql" "$SRV/src/main/resources/db/migration-v3.1.sql" "$SRV/src/main/resources/db/migration-v3.2.sql" "$SRV/src/main/resources/db/emoji-init.sql"; do
    [ -f "$f" ] && sudo mysql ${DB_NAME} < "$f" && echo "   已执行 $(basename "$f")"
  done
else
  echo "   未找到 music_db.sql，也没有 schema.sql —— 跳过"
fi

echo "== 5/7 检查 storage（音频/封面/表情，约 4.6GB）=="
if [ -d "$SRV/storage" ]; then
  echo "   storage 就绪：$(du -sh "$SRV/storage" | cut -f1)  文件数 $(find "$SRV/storage" -type f | wc -l)"
else
  echo "   [!] 缺少 $SRV/storage —— 页面会 歌曲在但放不了、封面全空，请从 Windows 拷过来"
fi

echo "== 6/7 启动后端（8080）=="
if [ -z "$JWT" ]; then
  JWT="$(head -c 48 /dev/urandom | base64 | tr -d '\n')"
  echo "   已生成本次会话的 JWT_SECRET（长度 ${#JWT}），建议固定： export JWT_SECRET='$JWT'"
fi
export MYSQL_USERNAME="$DB_USER"
export MYSQL_PASSWORD="$DB_PASS"
export JWT_SECRET="$JWT"
nohup java -jar "$JAR" > backend.log 2>&1 &
sleep 15
echo -n "   健康检查: "
curl -s "http://127.0.0.1:8080/api/top/song?type=1" | head -c 120
echo

echo "== 7/7 客户端（若已打包 AppImage）=="
APPIMG="$(ls -1 vue3-cloud-music-master/release/*.AppImage 2>/dev/null | head -1 || true)"
if [ -n "$APPIMG" ]; then
  CFG="$HOME/.config/cloud-music-vue3/config.json"
  mkdir -p "$(dirname "$CFG")"
  echo '{"apiBase":"http://127.0.0.1:8080/api"}' > "$CFG"
  chmod +x "$APPIMG"
  echo "   客户端：$APPIMG（后端地址已写为 127.0.0.1:8080）"
else
  echo "   还没打包客户端 —— 先执行 ./build-linux.sh 生成 AppImage/deb"
fi

echo
echo "==================== 部署完成 ===================="
echo " 后端 : http://127.0.0.1:8080/api   （日志 backend.log）"
echo " 数据库: MariaDB / ${DB_NAME} / ${DB_USER} / ${DB_PASS}"
echo " 开机自启(可选)：crontab -e 加入"
echo "   @reboot cd $(pwd) && MYSQL_USERNAME=${DB_USER} MYSQL_PASSWORD='${DB_PASS}' JWT_SECRET='${JWT}' nohup java -jar ${JAR} >> backend.log 2>&1 &"