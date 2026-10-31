package com.example.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌单实体（对应表 playlist）
 */
@Data
@TableName("playlist")
public class Playlist {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 歌单名 */
    private String name;

    /** 歌单描述 */
    private String description;

    /** 封面相对路径 */
    private String coverPath;

    /** 归属用户 ID（NOT NULL） */
    private Long userId;

    /** 类型：0-普通歌单 1-我喜欢的音乐（每用户唯一） */
    private Integer type;

    /** 歌曲数量（冗余字段，增删歌曲后重算） */
    private Integer songCount;

    /** 被收藏次数（冗余字段，收藏/取消时维护） */
    private Integer collectCount;
    /** 播放次数（点播放全部时 +1；V3.3 新增） */
    private Integer playCount;

    /** 状态：1-公开 0-私有 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
