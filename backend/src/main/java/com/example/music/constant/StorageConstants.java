package com.example.music.constant;

/**
 * 存储相关常量
 */
public final class StorageConstants {

    /** 音频文件子目录 */
    public static final String AUDIO_DIR = "audio";

    /** 封面图子目录 */
    public static final String COVER_DIR = "cover";

    /** 歌词文件子目录（LRC） */
    public static final String LYRIC_DIR = "lyric";

    /** 用户头像子目录 */
    public static final String AVATAR_DIR = "avatar";

    /** 评论配图子目录（V3.1 新增） */
    public static final String COMMENT_DIR = "comment";

    /** 表情包子目录（V3.1 新增） */
    public static final String EMOJI_DIR = "emoji";

    /** 路径分隔符（统一使用正斜杠，兼容 Windows / Linux） */
    public static final String SEPARATOR = "/";

    private StorageConstants() {
    }
}
