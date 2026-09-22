package com.example.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表情包实体（对应表 emoji）
 * <p>
 * 服务端统一管理（管理员上传，用户只消费）；
 * name 即评论内容中的占位符 [name]，图片由 /emojis/{id}/image 接口直出。
 */
@Data
@TableName("emoji")
public class Emoji {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 表情名（评论占位符 [name]，唯一） */
    private String name;

    /** 图片相对路径 */
    private String path;

    /** 面板展示顺序 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
