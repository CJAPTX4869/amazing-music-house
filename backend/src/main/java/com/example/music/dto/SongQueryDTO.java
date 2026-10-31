package com.example.music.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 歌曲列表查询条件
 * <p>
 * sort 格式：字段,asc|desc（字段必须命中 {@code SortWhitelist}，否则回退默认排序）
 */
@Data
public class SongQueryDTO {

    /** 关键词：匹配歌名 / 歌手 / 专辑 */
    private String keyword;

    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Integer size = 20;

    /** 排序，如 create_time,desc */
    private String sort;

    /** 状态筛选：仅管理端使用（1-上架 0-下架），公开列表强制只查上架 */
    private Integer status;
    /** 标签筛选（FIND_IN_SET 精确匹配单个标签，如 华语/纯音乐；V3.2 新增） */
    private String tag;
}
