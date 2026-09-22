-- V3.2：网易云内容导入（管理端）
-- song 加网易云歌曲 ID（幂等锚点：同一首歌只绑一次，防止重复导入）

ALTER TABLE song
    ADD COLUMN netease_id BIGINT DEFAULT NULL COMMENT '网易云歌曲ID（导入歌词/封面的来源锚点）';

ALTER TABLE song
    ADD UNIQUE KEY uk_song_netease_id (netease_id);
