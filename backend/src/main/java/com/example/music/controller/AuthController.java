package com.example.music.controller;

import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.Result;
import com.example.music.common.response.ResultCode;
import com.example.music.dto.ForgotPasswordDTO;
import com.example.music.dto.LoginDTO;
import com.example.music.dto.RegisterDTO;
import com.example.music.security.CaptchaTicketManager;
import com.example.music.security.OnlineClientTracker;
import com.example.music.security.RequireLogin;
import com.example.music.security.UserContext;
import com.example.music.service.UserService;
import com.example.music.vo.LoginVO;
import com.example.music.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;


import javax.validation.Valid;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final CaptchaTicketManager captchaTickets;
    private final OnlineClientTracker onlineTracker;

    /**
     * 注册（默认普通用户）
     */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success("注册成功", userService.register(dto));
    }

    /**
     * 登录：必须携带已通过抓娃娃验证且未使用过的票据（一次性消费）。
     * 旧版验证码是纯前端变量，绕过页面即可无限撞库；票据化后无票/假票/重放全部拒绝。
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        // 票据仅对管理员账号强制：管理端(music-web)登录有抓娃娃 UI 配合；
        // Qt/安卓/听歌端也走本接口但没有验证码，一刀切会把它们全部弄断。
        if (userService.isAdminAccount(dto.getUsername())
                && !captchaTickets.consumeForLogin(dto.getTicket())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "请先完成抓娃娃人机验证");
        }
        LoginVO vo = userService.login(dto);
        // 登录成功即计入"在线客户端"（否则要等用户发出下一个请求才出现）
        onlineTracker.touch(vo.getUser().getId(), dto.getUsername(),
                request.getHeader("X-Client-Platform"),
                request.getHeader("User-Agent"),
                request.getRemoteAddr(),
                request.getRemotePort());
        return Result.success("登录成功", vo);
    }

    /**
     * 忘记密码：用户名 + 部署恢复码直接设置新密码（免登录；与登录同用 /auth 前缀，拦截器放行）
     */
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto) {
        userService.forgotPassword(dto);
        return Result.success("密码已重置，请使用新密码登录", null);
    }

    /**
     * 当前登录用户信息
     */
    @GetMapping("/profile")
    @RequireLogin
    public Result<UserVO> profile() {
        return Result.success(userService.getProfile(UserContext.getUserId()));
    }
}
