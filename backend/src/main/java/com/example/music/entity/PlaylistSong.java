package com.example.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌单-歌曲关联实体（对应表 playlist_song）
 * <p>
 * 设计说明：
 * 1. 关联记录采用**物理删除**（移出歌单即删行），故无 deleted 字段；
 * 2. 依赖唯一约束 uk_playlist_song(playlist_id, song_id) 兜底并发重复插入。
 */
@Data
@TableName("playlist_song")
public class PlaylistSong {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long playlistId;

    private Long songId;

    /** 排序号（预留字段，当前统一为 0，列表按 id 升序即加入顺序） */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
