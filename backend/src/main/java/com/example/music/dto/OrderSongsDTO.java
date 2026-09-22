package com.example.music.dto;

import lombok.Data;

import java.util.List;

/**
 * 歌单自定义排序请求体
 */
@Data
public class OrderSongsDTO {

    /** 歌单内歌曲的完整排列（按目标顺序） */
    private List<Long> songIds;
}