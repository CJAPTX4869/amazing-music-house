package com.example.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏歌单实体（对应表 playlist_collect）
 * <p>
 * 收藏他人公开歌单（与"把歌曲收进歌单"的 playlist_song 语义严格区分）；
 * 物理删除，UNIQUE(user_id, playlist_id) 防重复收藏。
 */
@Data
@TableName("playlist_collect")
public class PlaylistCollect {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收藏人 ID */
    private Long userId;

    /** 歌单 ID */
    private Long playlistId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
