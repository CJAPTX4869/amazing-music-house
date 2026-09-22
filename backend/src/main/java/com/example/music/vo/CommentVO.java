package com.example.music.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论响应对象（根评论与楼中楼回复共用）
 */
@Data
public class CommentVO {

    private Long id;

    /** 内容（含 [表情名] 占位符，由前端渲染层替换为表情图片） */
    private String content;

    /** 配图可访问地址（/comments/{id}/image 相对 API 路径；空串 = 无配图） */
    private String imageUrl;

    private Integer likeCount;

    /** 当前登录用户是否已点赞（未登录恒 false） */
    private Boolean liked;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 评论人 */
    private CommentUserVO user;

    /** 楼中楼"回复@谁"的昵称（仅回复有，根评论为 null） */
    private String replyUserNickname;

    /** 楼中楼回复（仅根评论填充，按时间正序；回复自身此字段为 null） */
    private List<CommentVO> replies;
}
