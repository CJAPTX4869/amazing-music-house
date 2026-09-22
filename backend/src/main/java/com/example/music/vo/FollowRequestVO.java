package com.example.music.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友请求响应对象（页头"好友请求"列表数据源）
 */
@Data
public class FollowRequestVO {

    /** user_follow 行 ID */
    private Long id;

    /** 请求者信息 */
    private CommentUserVO user;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
