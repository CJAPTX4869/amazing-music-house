package com.example.music.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 忘记密码 · 恢复码重置请求（免登录）
 * <p>
 * 恢复码是部署时配置的口令（RECOVERY_CODE），交给可信用户；
 * 用户名与恢复码任一错误都返回同一个报错，避免枚举出有效用户名。
 */
@Data
public class ForgotPasswordDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "恢复码不能为空")
    private String recoveryCode;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度需在 6~64 位之间")
    private String newPassword;
}
