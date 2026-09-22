-- =============================================================
-- V3.1 数据库迁移：评论 / 好友关注 / 收藏歌单 / 表情包
-- 执行方式: mysql -u root -p --default-character-set=utf8mb4 < migration-v3.1.sql
-- 说明: ALTER 语句不具备可重入性（重复执行报 Duplicate column，无害）；
--       建表语句均为 IF NOT EXISTS，可重复执行
-- =============================================================
USE music_db;

-- 1. 既有表加字段
ALTER TABLE sys_user ADD COLUMN region VARCHAR(100) DEFAULT NULL COMMENT '地区' AFTER signature;
ALTER TABLE song ADD COLUMN like_count INT NOT NULL DEFAULT 0 COMMENT '喜欢数' AFTER play_count;
ALTER TABLE song ADD COLUMN comment_count INT NOT NULL DEFAULT 0 COMMENT '评论数' AFTER like_count;
ALTER TABLE playlist ADD COLUMN collect_count INT NOT NULL DEFAULT 0 COMMENT '被收藏次数' AFTER song_count;

-- 2. 歌曲评论表（根评论与楼中楼回复共用；parent_id 恒指根评论，显示平铺两层）
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

-- 3. 评论点赞表
CREATE TABLE IF NOT EXISTS comment_like (
  id          BIGINT   PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  comment_id  BIGINT   NOT NULL COMMENT '评论 ID',
  user_id     BIGINT   NOT NULL COMMENT '点赞用户 ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_comment_user (comment_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论点赞表';

-- 4. 好友关系表（请求制：发起 → 对方同意后双向互为好友）
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

-- 5. 收藏歌单表
CREATE TABLE IF NOT EXISTS playlist_collect (
  id          BIGINT   PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT   NOT NULL COMMENT '收藏人 ID',
  playlist_id BIGINT   NOT NULL COMMENT '歌单 ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_playlist (user_id, playlist_id),
  KEY idx_playlist (playlist_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏歌单表';

-- 6. 表情包表（服务端统一管理：管理员上传，用户只消费）
CREATE TABLE IF NOT EXISTS emoji (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(50)  NOT NULL COMMENT '表情名（评论占位符 [name]）',
  path        VARCHAR(500) NOT NULL COMMENT '图片相对路径',
  sort        INT          NOT NULL DEFAULT 0 COMMENT '面板展示顺序',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表情包表';

-- 7. 存量数据重算：song.like_count（"我喜欢的音乐"歌单持有数）
UPDATE song s
LEFT JOIN (
  SELECT ps.song_id, COUNT(*) AS c
  FROM playlist_song ps
  JOIN playlist p ON ps.playlist_id = p.id
  WHERE p.type = 1 AND p.deleted = 0
  GROUP BY ps.song_id
) t ON t.song_id = s.id
SET s.like_count = IFNULL(t.c, 0)
WHERE s.deleted = 0;

-- 8. 验证
SELECT '=== 新表 ===' AS info;
SHOW TABLES LIKE 'song_comment';
SHOW TABLES LIKE 'comment_like';
SHOW TABLES LIKE 'user_follow';
SHOW TABLES LIKE 'playlist_collect';
SHOW TABLES LIKE 'emoji';
SELECT '=== song 计数抽样 ===' AS info;
SELECT id, title, like_count, comment_count FROM song ORDER BY like_count DESC LIMIT 5;
