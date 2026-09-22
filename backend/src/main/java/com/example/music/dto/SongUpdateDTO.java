package com.example.music.dto;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 歌曲信息编辑请求
 */
@Data
public class SongUpdateDTO {

    @Size(max = 200, message = "歌曲名长度不能超过 200 个字符")
    private String title;

    @Size(max = 100, message = "歌手长度不能超过 100 个字符")
    private String artist;

    @Size(max = 100, message = "专辑长度不能超过 100 个字符")
    private String album;

    @Size(max = 50, message = "流派长度不能超过 50 个字符")
    private String genre;
}
