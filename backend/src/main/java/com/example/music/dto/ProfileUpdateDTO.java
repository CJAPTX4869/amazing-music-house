package com.example.music.dto;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 个人资料修改请求
 * <p>
 * 字段为 {@code null} 表示"不修改"；本系统暂不存储性别 / 生日，
 * 开放昵称 / 个性签名 / 地区三项（V3.1 个人主页新增地区）。
 */
@Data
public class ProfileUpdateDTO {

    /** 昵称（null = 不修改） */
    @Size(max = 50, message = "昵称不能超过 50 个字符")
    private String nickname;

    /** 个性签名（null = 不修改；传空串表示清空） */
    @Size(max = 255, message = "个性签名不能超过 255 个字符")
    private String signature;

    /** 地区（null = 不修改；传空串表示清空；形如"吉林省 长春市"） */
    @Size(max = 100, message = "地区不能超过 100 个字符")
    private String region;
}
