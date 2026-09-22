package com.example.music.vo;

import lombok.Data;

/**
 * 表情响应对象（评论表情面板数据源）
 */
@Data
public class EmojiVO {

    private Long id;

    /** 表情名（即评论占位符 [name]） */
    private String name;

    /** 图片访问地址 /emojis/{id}/image（相对 API 路径） */
    private String imageUrl;
}
