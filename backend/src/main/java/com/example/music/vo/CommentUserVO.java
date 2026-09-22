package com.example.music.vo;

import lombok.Data;

/**
 * 评论人精简信息（评论列表内嵌使用）
 */
@Data
public class CommentUserVO {

    private Long id;

    private String nickname;

    /**
     * 头像接口路径 /users/{id}/avatar（相对 API 路径，前端拼 baseUrl）；
     * 统一走头像接口：该用户无头像时接口返回 404，前端 n-avatar 自动降级默认占位
     */
    private String avatarUrl;

    public static CommentUserVO of(Long id, String nickname) {
        CommentUserVO vo = new CommentUserVO();
        vo.setId(id);
        vo.setNickname(nickname == null ? "" : nickname);
        vo.setAvatarUrl("/users/" + id + "/avatar");
        return vo;
    }
}
