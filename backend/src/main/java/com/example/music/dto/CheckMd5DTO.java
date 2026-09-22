package com.example.music.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * MD5 预检请求（秒传判断）
 */
@Data
public class CheckMd5DTO {

    @NotBlank(message = "MD5 不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{32}$", message = "MD5 格式不正确")
    private String md5;
}
