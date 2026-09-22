package com.example.music.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 歌单创建 / 编辑请求
 */
@Data
public class PlaylistSaveDTO {

    @NotBlank(message = "歌单名不能为空")
    @Size(max = 100, message = "歌单名不能超过 100 字符")
    private String name;

    @Size(max = 500, message = "歌单描述不能超过 500 字符")
    private String description;

    /**
     * 隐私设置（null = 默认公开）：1-私密歌单（仅自己可见），0/其他-公开
     */
    private Integer privacy;
}
