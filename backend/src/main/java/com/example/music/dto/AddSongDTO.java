package com.example.music.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 歌单添加歌曲请求
 */
@Data
public class AddSongDTO {

    @NotNull(message = "歌曲 ID 不能为空")
    private Long songId;
}
