package com.example.music.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 登录请求
 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 抓娃娃人机验证票据（登录必带，服务端校验并一次性消费） */
    private String ticket;
}
