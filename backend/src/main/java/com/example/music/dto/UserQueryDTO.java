package com.example.music.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 用户列表查询条件（管理端）
 */
@Data
public class UserQueryDTO {

    /** 关键词：匹配用户名 / 昵称 */
    private String keyword;

    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Integer size = 20;

    /** 角色筛选：0-普通用户 1-管理员 */
    private Integer role;

    /** 状态筛选：1-启用 0-禁用 */
    private Integer status;
}
