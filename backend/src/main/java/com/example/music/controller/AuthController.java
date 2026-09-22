package com.example.music.controller;

import com.example.music.common.response.Result;
import com.example.music.dto.LoginDTO;
import com.example.music.dto.RegisterDTO;
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

import javax.validation.Valid;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 注册（默认普通用户）
     */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success("注册成功", userService.register(dto));
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success("登录成功", userService.login(dto));
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
