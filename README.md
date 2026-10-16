# 奇妙音乐屋子（Amazing Music House）

一个仿网易云音乐 PC 端的全栈音乐流媒体平台：**Spring Boot 后端 + Vue3 网页端（用户端/管理端）+ Electron 桌面端**，支持在线播放、歌单、收藏、评论、后台管理等完整功能，并提供 Windows 一键启动与 Kali/Linux 一体化部署方案。

> **开源声明**：用户端前端 UI 基于开源项目 [path-yu/vue3-cloud-music](https://github.com/path-yu/vue3-cloud-music)（354 stars）进行二次开发，在此感谢原作者；本项目独立实现了全部后端服务（Spring Boot + MySQL）、管理端、Electron 桌面端封装、数据库设计与部署脚本。安卓端基于 `koi_music` 改造。详见 `docs/` 中的功能分析与审查报告。

## 系统架构

```
浏览器 / Electron 桌面端 (Vue3 + Vite + naive-ui)
        │  /api
        ▼
Nginx 反向代理（可选） ──► Spring Boot 2.7 (Java 11, 端口 8080, context-path: /api)
                                │ MyBatis-Plus + Druid + JWT
                                ▼
                          MySQL 8 (数据库: music_db)
                                │
                          storage/ 本地音乐文件存储
```

## 技术栈与环境清单

| 组件 | 版本要求 | 说明 |
|---|---|---|
| JDK | 11 | 后端运行环境 |
| Maven | 3.6+ | 后端构建 |
| MySQL | 8.x（开发使用 8.4） | 数据库 `music_db`，注意 8.4 的 `caching_sha2_password` 认证 |
| Node.js | 18+ | 前端构建（用户端 + 管理端） |
| npm / pnpm | - | 前端包管理 |
| Vue | 3.5 + Vite 5 + TypeScript | 前端框架 |
| naive-ui | 2.40 | 用户端 UI 组件库 |
| element-plus | 2.8 | 管理端 UI 组件库 |
| Pinia / Vue Router / Axios | - | 状态管理 / 路由 / 请求 |
| Electron | 44 | 桌面端封装（electron-builder 打包） |
| Spring Boot | 2.7.18 | 后端框架（Web/Validation） |
| MyBatis-Plus / Druid / JJWT / BCrypt | - | ORM / 连接池 / 登录令牌 / 密码哈希 |

## 目录结构

```
├── backend/      Spring Boot 服务端（music-server）
├── web-user/     用户端网页 + Electron 桌面端（vue3-cloud-music 二开）
├── web-admin/    管理端后台
├── sql/          schema.sql（建表）+ music_db.sql（结构与演示数据完整导出）
├── scripts/      Windows 启动脚本 / Kali 部署与导出脚本
├── docs/         功能分析、部署方案、审查报告等项目文档
└── sample说明    音乐文件（storage/）与安装包（exe/msi）不在仓库内，见下文
```

## 快速开始（Windows）

### 1. 初始化数据库

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS music_db DEFAULT CHARSET utf8mb4;"
mysql -uroot -p music_db < sql/music_db.sql
```

### 2. 启动后端

```bash
cd backend
set MYSQL_PASSWORD=你的mysql密码   # Linux/Kali: export MYSQL_PASSWORD=...
set JWT_SECRET=至少32位的随机字符串
mvn spring-boot:run                # 或 mvn package 后 java -jar target/music-server-1.0.0.jar
```

音乐文件默认读取 `backend/storage/`（可用环境变量 `STORAGE_PATH` 覆盖）。
⚠️ **仓库不包含曲库**：音频文件体积远超 Git 限制，请自行放置合法拥有的音频文件到 `backend/storage/`；需要演示曲库请联系作者。

### 3. 启动前端

```bash
cd web-user  && npm install && npm run dev    # 用户端 http://localhost:5175
cd web-admin && npm install && npm run dev    # 管理端 http://localhost:5174
```

也可使用 `scripts/启动-服务端.bat`、`scripts/启动-网页端.bat` 一键启动。

### 4. 桌面端打包（可选）

```bash
cd web-user && npm run electron:build   # 产物在 release/（exe/msi，不入库）
```

桌面端安装包（exe/msi）体积超出 Git 单文件限制，**未随仓库发布**；需要成品请联系作者，或按上面 `electron:build` 自行打包。

## Linux / Kali 部署

参见 `docs/在Kali上一体化部署.md` 与 `scripts/deploy-kali.sh`、`scripts/export-for-kali.bat`：在 Windows 上导出「源码 + jar + 曲库」打包 → 传到 Kali → 一键完成 MariaDB 建库导数据、后端启动与健康检查、AppImage 客户端配置。

> 说明：`deploy-kali.sh` 目前只负责 **MariaDB + 后端**，静态站点由 Electron/AppImage 客户端或 `npm run dev` 提供；生产环境的 Nginx 反向代理在 `docs/项目检查.md` 中列为待办，**仓库内尚无 nginx 配置**，不要按已实现来讲。

## 安全说明

- 所有密码/密钥通过环境变量注入（`MYSQL_PASSWORD`、`JWT_SECRET`），仓库与配置文件不含任何真实凭据
- 用户密码使用 BCrypt 存储；演示账号见 `docs/` 文档
- 本仓库中的 SQL 导出仅包含测试数据

## License

前端部分：上游 [path-yu/vue3-cloud-music](https://github.com/path-yu/vue3-cloud-music) 仓库**未声明开源许可证**，此处按署名方式注明来源并保留原作者信息；如需正式分发前端改动，将联系原作者确认授权。自研代码（`backend/`、`web-admin/`、`scripts/`、`docs/`、`sql/`）的许可证待作者本人确定后再补充 `LICENSE` 文件。
