package com.example.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友关系实体（对应表 user_follow）
 * <p>
 * 好友请求制：A 发起（status=0 待处理）→ B 同意（status=1）后双方互为好友；
 * B 拒绝或任一方解除好友即物理删除该行。UNIQUE(requester_id, target_id) 防重复发起。
 */
@Data
@TableName("user_follow")
public class UserFollow {

    /** 状态：待对方处理 */
    public static final int STATUS_PENDING = 0;
    /** 状态：已互关（对方已同意） */
    public static final int STATUS_ACCEPTED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起方用户 ID */
    private Long requesterId;

    /** 接收方用户 ID */
    private Long targetId;

    /** 状态：0-待对方处理 1-已互关 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
