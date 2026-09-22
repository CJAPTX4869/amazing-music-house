# 奇妙音乐屋子（Amazing Music House）

一个仿网易云音乐 PC 端的全栈音乐流媒体平台：**Spring Boot 后端 + Vue3 网页端（用户端/管理端）+ Electron 桌面端**，支持在线播放、歌单、收藏、评论、后台管理等完整功能，并提供 Windows 一键启动与 Kali/Linux 一体化部署方案。

> **开源声明**：用户端前端 UI 基于开源项目 vue3-cloud-music（上游地址：`<补充上游仓库链接>`）进行二次开发；本项目独立实现了全部后端服务（Spring Boot + MySQL）、管理端、Electron 桌面端封装、数据库设计与部署脚本。详见 `docs/` 中的功能分析与审查报告。

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
⚠️ **仓库不包含曲库**：请自行放置合法拥有的音频文件到 `backend/storage/`，完整演示曲库见 Releases 附件或联系作者。

### 3. 启动前端

```bash
cd web-user  && npm install && npm run dev    # 用户端 http://localhost:5173
cd web-admin && npm install && npm run dev    # 管理端
```

也可使用 `scripts/启动-服务端.bat`、`scripts/启动-网页端.bat` 一键启动。

### 4. 桌面端打包（可选）

```bash
cd web-user && npm run electron:build   # 产物在 release/（exe/msi，不入库）
```

发布版安装包请到本仓库 **Releases** 页面下载。

## Linux / Kali 部署

参见 `docs/在Kali上一体化部署.md` 与 `scripts/deploy-kali.sh`、`scripts/export-for-kali.bat`：导出源码 → 传输到虚拟机 → 一键部署（MySQL + 后端 + Nginx 静态站点）。

## 安全说明

- 所有密码/密钥通过环境变量注入（`MYSQL_PASSWORD`、`JWT_SECRET`），仓库与配置文件不含任何真实凭据
- 用户密码使用 BCrypt 存储；演示账号见 `docs/` 文档
- 本仓库中的 SQL 导出仅包含测试数据

## License

前端部分遵循上游 vue3-cloud-music 的开源协议；自研代码（backend/、web-admin/、scripts/、docs/、sql/）以 MIT 协议发布（LICENSE 文件待补充）。
