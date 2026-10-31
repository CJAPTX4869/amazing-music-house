-- =============================================================
-- 音乐文件管理系统 - 数据库初始化脚本
-- -------------------------------------------------------------
-- 版本      : V3.0
-- 对应文档  : 需求分析.md 第 4.7 节（数据库设计）
-- 数据库    : MySQL 8.x（utf8mb4 / utf8mb4_unicode_ci）
-- 执行方式  : mysql -u root -p --default-character-set=utf8mb4 -e "source schema.sql"
-- 可重入    : 是（CREATE TABLE IF NOT EXISTS + INSERT IGNORE，重复执行不会清空数据）
--
-- V3.0 修订说明：
--   1. 补全 playlist / playlist_song / play_history 三张表（V2.0 脚本遗漏）
--   2. song 表补 lyric_path 列（歌词文件相对路径，供 LRC 歌词功能使用）
--   3. 全部表显式声明 COLLATE=utf8mb4_unicode_ci，与库默认保持一致
-- =============================================================

CREATE DATABASE IF NOT EXISTS music_db
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE music_db;

-- -------------------------------------------------------------
-- 1. 用户表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  username     VARCHAR(50)  NOT NULL UNIQUE  COMMENT '用户名',
  password     VARCHAR(100) NOT NULL         COMMENT '密码（BCrypt）',
  nickname     VARCHAR(50)  DEFAULT NULL     COMMENT '昵称',
  avatar       VARCHAR(255) DEFAULT NULL     COMMENT '头像路径',
  signature    VARCHAR(255) DEFAULT NULL     COMMENT '个性签名',
  region       VARCHAR(100) DEFAULT NULL     COMMENT '地区（如：吉林省 长春市，V3.1 新增）',
  role         TINYINT      NOT NULL DEFAULT 0 COMMENT '角色：0-普通用户 1-管理员',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除'
  -- 说明：username 的 UNIQUE 约束已自动创建唯一索引，无需再建普通索引（避免重复索引）
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- -------------------------------------------------------------
-- 2. 歌曲表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS song (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  title           VARCHAR(200) NOT NULL          COMMENT '歌曲名',
  artist          VARCHAR(100) DEFAULT NULL      COMMENT '歌手',
  album           VARCHAR(100) DEFAULT NULL      COMMENT '专辑',
  tags            VARCHAR(150) DEFAULT NULL      COMMENT '标签（逗号分隔，最多3个）',
  -- V3.2 迁移：genre 语义升级为"类型"（华语/欧美/日本/韩国/纯音乐/其他），旧流派值已归一映射；
  -- 新增 tags 逗号分隔多标签（后端强制最多 3 个）
  duration        INT          DEFAULT 0         COMMENT '时长（秒）',
  original_name   VARCHAR(255) DEFAULT NULL      COMMENT '上传时的原始文件名',
  file_name       VARCHAR(255) NOT NULL          COMMENT '落盘文件名（UUID）',
  file_path       VARCHAR(500) NOT NULL          COMMENT '相对存储路径',
  file_size       BIGINT       NOT NULL          COMMENT '文件大小（字节）',
  file_md5        VARCHAR(32)  DEFAULT NULL      COMMENT '文件 MD5（用于去重/秒传）',
  mime_type       VARCHAR(100) DEFAULT NULL      COMMENT 'MIME 类型',
  ext             VARCHAR(20)  DEFAULT NULL      COMMENT '扩展名（小写）',
  cover_path      VARCHAR(500) DEFAULT NULL      COMMENT '封面相对路径',
  lyric_path      VARCHAR(500) DEFAULT NULL      COMMENT '歌词文件相对路径（LRC，V3.0 新增）',
  uploader_id     BIGINT       NOT NULL          COMMENT '上传者 ID（仅管理员上传，不可为空）',
  download_count  INT          NOT NULL DEFAULT 0 COMMENT '下载次数',
  play_count      INT          NOT NULL DEFAULT 0 COMMENT '播放次数',
  like_count      INT          NOT NULL DEFAULT 0 COMMENT '喜欢数（冗余：用户收藏进"我喜欢的音乐"时维护，V3.1 新增）',
  comment_count   INT          NOT NULL DEFAULT 0 COMMENT '评论数（冗余：含楼中楼回复，评论增删时维护，V3.1 新增）',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-正常（上架） 0-下架',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  KEY idx_title       (title),
  KEY idx_uploader    (uploader_id),
  KEY idx_create_time (create_time),
  -- file_md5 唯一约束：防止并发上传同一文件时产生重复记录
  -- 说明：MySQL 唯一索引允许多个 NULL，因此未计算 MD5 的记录不受影响
  UNIQUE KEY uk_md5   (file_md5),
  KEY idx_status      (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='歌曲表';

-- -------------------------------------------------------------
-- 3. 下载记录表（日志表，仅记录，不做逻辑删除）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS download_record (
  id          BIGINT   PRIMARY KEY AUTO_INCREMENT,
  song_id     BIGINT   NOT NULL COMMENT '歌曲 ID',
  user_id     BIGINT   DEFAULT NULL COMMENT '下载用户 ID（可为空=匿名/游客）',
  ip          VARCHAR(50)  DEFAULT NULL COMMENT '来源 IP',
  user_agent  VARCHAR(500) DEFAULT NULL COMMENT '浏览器 UA',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_song (song_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='下载记录表';

-- -------------------------------------------------------------
-- 4. 歌单表（V3.0 补全）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS playlist (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  name         VARCHAR(100) NOT NULL          COMMENT '歌单名',
  description  VARCHAR(500) DEFAULT NULL      COMMENT '歌单描述',
  cover_path   VARCHAR(500) DEFAULT NULL      COMMENT '封面相对路径',
  user_id      BIGINT       NOT NULL          COMMENT '归属用户 ID',
  type         TINYINT      NOT NULL DEFAULT 0 COMMENT '类型：0-普通歌单 1-我喜欢的音乐（每用户唯一）',
  song_count   INT          NOT NULL DEFAULT 0 COMMENT '歌曲数量（冗余字段，增删歌曲后重算）',
  collect_count INT         NOT NULL DEFAULT 0 COMMENT '被收藏次数（冗余字段，收藏/取消时维护，V3.1 新增）',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-公开 0-私有',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  KEY idx_user (user_id),
  KEY idx_type (type),
  -- 「我喜欢的音乐」每用户唯一：生成列仅对 type=1 取 user_id（其余为 NULL 不参与唯一），
  -- 防并发首次收藏建出多个收藏歌单（普通歌单 type=0 仍可建任意多个）
  fav_unique    BIGINT GENERATED ALWAYS AS (IF(type=1, user_id, NULL)) STORED,
  UNIQUE KEY uk_favorite (fav_unique)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='歌单表';

-- -------------------------------------------------------------
-- 5. 歌单-歌曲关联表（V3.0 补全）
--    设计说明：关联记录采用物理删除（移出歌单即删行），故无 deleted 字段
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS playlist_song (
  id          BIGINT   PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  playlist_id BIGINT   NOT NULL COMMENT '歌单 ID',
  song_id     BIGINT   NOT NULL COMMENT '歌曲 ID',
  sort_order  INT      NOT NULL DEFAULT 0 COMMENT '排序号（预留，当前统一为 0）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  -- 唯一约束：防止同一首歌重复加入同一歌单（并发场景由此兜底）
  UNIQUE KEY uk_playlist_song (playlist_id, song_id),
  KEY idx_song (song_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='歌单-歌曲关联表';

-- -------------------------------------------------------------
-- 6. 播放历史表（V3.0 补全）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS play_history (
  id        BIGINT   PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id   BIGINT   NOT NULL COMMENT '用户 ID',
  song_id   BIGINT   NOT NULL COMMENT '歌曲 ID',
  play_time DATETIME NOT NULL COMMENT '播放时间',
  KEY idx_user_time (user_id, play_time),
  KEY idx_song (song_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='播放历史表';

-- -------------------------------------------------------------
-- 7. 歌曲评论表（V3.1 新增）
--    设计说明：
--    1. 根评论与楼中楼回复共用一张表：parent_id = NULL 表示根评论；
--       所有回复的 parent_id 统一指向所属根评论（显示平铺两层，
--       reply_user_id 标记"回复@谁"），任何人可无限追加追评且无递归深度
--    2. 评论配图单张（支持 GIF 动图），由 /comments/{id}/image 接口直出
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS song_comment (
  id            BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  song_id       BIGINT        NOT NULL COMMENT '歌曲 ID',
  user_id       BIGINT        NOT NULL COMMENT '评论人 ID',
  parent_id     BIGINT        DEFAULT NULL COMMENT '父评论 ID（NULL=根评论，回复统一指向根评论）',
  reply_user_id BIGINT        DEFAULT NULL COMMENT '被回复用户 ID（楼中楼"回复@xx"）',
  content       VARCHAR(1000) NOT NULL COMMENT '内容（含 [表情名] 占位符）',
  image_path    VARCHAR(500)  DEFAULT NULL COMMENT '配图相对路径（单图，可为 GIF）',
  like_count    INT           NOT NULL DEFAULT 0 COMMENT '点赞数（冗余，点赞时维护）',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（删根评论=整楼隐藏）',
  KEY idx_song_time (song_id, create_time),
  KEY idx_parent (parent_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='歌曲评论表';

-- -------------------------------------------------------------
-- 8. 评论点赞表（V3.1 新增，物理删除，唯一约束防并发重复点赞）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS comment_like (
  id          BIGINT   PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  comment_id  BIGINT   NOT NULL COMMENT '评论 ID',
  user_id     BIGINT   NOT NULL COMMENT '点赞用户 ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_comment_user (comment_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论点赞表';

-- -------------------------------------------------------------
-- 9. 好友关系表（V3.1 新增，好友请求制：发起 → 对方同意后双向互为好友）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_follow (
  id           BIGINT   PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  requester_id BIGINT   NOT NULL COMMENT '发起方用户 ID',
  target_id    BIGINT   NOT NULL COMMENT '接收方用户 ID',
  status       TINYINT  NOT NULL DEFAULT 0 COMMENT '状态：0-待对方处理 1-已互关',
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_requester_target (requester_id, target_id),
  KEY idx_target (target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友关系表';

-- -------------------------------------------------------------
-- 10. 收藏歌单表（V3.1 新增，物理删除；收藏他人公开歌单）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS playlist_collect (
  id          BIGINT   PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT   NOT NULL COMMENT '收藏人 ID',
  playlist_id BIGINT   NOT NULL COMMENT '歌单 ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_playlist (user_id, playlist_id),
  KEY idx_playlist (playlist_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏歌单表';

-- -------------------------------------------------------------
-- 11. 表情包表（V3.1 新增，服务端统一管理：管理员上传，用户只消费）
--     内置 Twemoji 图片表情；name 即评论占位符 [name]
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS emoji (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(50)  NOT NULL COMMENT '表情名（评论占位符 [name]）',
  path        VARCHAR(500) NOT NULL COMMENT '图片相对路径',
  sort        INT          NOT NULL DEFAULT 0 COMMENT '面板展示顺序',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表情包表';

-- -------------------------------------------------------------
-- 12. 管理员初始账号
--    账号：admin
--    密码：部署时由运维设定（此处为强口令 Music@2026!House 的 BCrypt 密文）
--    ⚠️ 上线前必须再次修改密码！不要把明文写进任何文档或脚本！
-- -------------------------------------------------------------
INSERT IGNORE INTO sys_user (username, password, nickname, role, status)
VALUES ('admin', '$2a$10$8K36reQuxum8CAXqHAg1euaQpjSwudAzanEkcpyQVTwe7L7VHxL8a', '系统管理员', 1, 1);

-- -------------------------------------------------------------
-- 8. 验证
-- -------------------------------------------------------------
SELECT '=== 表清单（应为 11 张） ===' AS info;
SHOW TABLES;

SELECT '=== 管理员账号 ===' AS info;
SELECT id, username, nickname, role, status, create_time FROM sys_user WHERE username = 'admin';

SELECT '=== song 表歌词字段 ===' AS info;
SHOW COLUMNS FROM song LIKE 'lyric_path';

-- -------------------------------------------------------------
-- V3.3 歌曲每日播放统计（播放接口实时累加，无需定时任务）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS song_play_daily (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  song_id    BIGINT  NOT NULL COMMENT '歌曲 ID',
  stat_date  DATE    NOT NULL COMMENT '统计日期',
  play_count INT     NOT NULL DEFAULT 0 COMMENT '当日播放次数',
  UNIQUE KEY uk_song_date (song_id, stat_date),
  KEY idx_date (stat_date)
) COMMENT '歌曲每日播放统计（实时累加）';

-- V3.3 歌单播放次数（播放全部时 +1，推荐歌单"高播放"排序用）
ALTER TABLE playlist ADD COLUMN play_count INT NOT NULL DEFAULT 0 COMMENT '播放次数';
