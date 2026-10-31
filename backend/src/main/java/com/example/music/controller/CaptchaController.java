package com.example.music.controller;

import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.Result;
import com.example.music.common.response.ResultCode;
import com.example.music.security.CaptchaTicketManager;
import com.example.music.security.PublicApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 抓娃娃人机验证票据接口。
 * challenge 颁发票据（10 分钟有效）；verify 在前端通过抓娃娃后标记已验证。
 * 两个接口均为公开接口（发的是空票，标记动作本身不构成绕过——
 * 真正的门槛在登录时要求"已验证且未使用过"的票据）。
 */
@RestController
@RequestMapping("/auth/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaTicketManager ticketManager;

    @PostMapping("/challenge")
    @PublicApi
    public Result<Map<String, String>> challenge() {
        return Result.success(Map.of("ticket", ticketManager.issue()));
    }

    @PostMapping("/verify")
    @PublicApi
    public Result<Void> verify(@RequestBody Map<String, String> body) {
        String ticket = body.get("ticket");
        if (ticket == null || !ticketManager.markVerified(ticket)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "验证票据无效或已过期，请刷新重试");
        }
        return Result.success(null);
    }
}
