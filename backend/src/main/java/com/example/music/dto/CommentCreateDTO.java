package com.example.music.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 发表歌曲评论请求（根评论）
 */
@Data
public class CommentCreateDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论最多 1000 字")
    private String content;

    /** 配图相对路径（先调 POST /comments/image 上传获得；可空 = 无配图） */
    @Size(max = 500, message = "配图路径非法")
    private String imagePath;
}
