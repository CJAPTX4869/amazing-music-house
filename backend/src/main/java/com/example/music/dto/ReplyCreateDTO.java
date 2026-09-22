package com.example.music.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 楼中楼回复/追评请求
 * <p>
 * 回复统一挂到所属根评论（parent_id 恒指根），前端只需传被回复评论 id；
 * replyUserId 不传时默认回复被回复评论的作者。
 */
@Data
public class ReplyCreateDTO {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 1000, message = "回复最多 1000 字")
    private String content;

    /** 被回复用户 ID（可空，默认取被回复评论的作者） */
    private Long replyUserId;
}
