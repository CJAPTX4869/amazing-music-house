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
 * 歌曲评论实体（对应表 song_comment）
 * <p>
 * 根评论与楼中楼回复共用一张表：
 * parent_id = NULL 表示根评论；所有回复的 parent_id 统一指向所属根评论
 * （显示平铺两层，reply_user_id 标记"回复@谁"），可无限追加追评且无递归深度。
 */
@Data
@TableName("song_comment")
public class SongComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 歌曲 ID */
    private Long songId;

    /** 评论人 ID */
    private Long userId;

    /** 父评论 ID（NULL=根评论，回复统一指向根评论） */
    private Long parentId;

    /** 被回复用户 ID（楼中楼"回复@xx"，可空） */
    private Long replyUserId;

    /** 内容（含 [表情名] 占位符） */
    private String content;

    /** 配图相对路径（单图，可为 GIF，可空） */
    private String imagePath;

    /** 点赞数（冗余，点赞时维护） */
    private Integer likeCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除（删根评论=整楼隐藏） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
