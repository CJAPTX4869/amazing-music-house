package com.example.music.controller;

import com.example.music.common.response.Result;
import com.example.music.security.RequireLogin;
import com.example.music.security.UserContext;
import com.example.music.service.FollowService;
import com.example.music.vo.FollowRequestVO;
import com.example.music.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 好友关系接口（好友请求制）
 * <p>
 * 与 {@link UserController} 同挂 /users 前缀；写操作需登录，
 * 关系状态查询也需登录（匿名用户主页按钮默认 none 态）。
 */
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@RequireLogin
public class FollowController {

    private final FollowService followService;

    /**
     * 发出好友请求（对方已发过时直接成为好友）
     *
     * @return 操作后的关系状态（pending_by_me / friends）
     */
    @PostMapping("/{id}/follow")
    public Result<String> request(@PathVariable Long id) {
        return Result.success("操作成功", followService.requestFollow(id, UserContext.getUserId()));
    }

    /**
     * 同意好友请求
     */
    @PostMapping("/{id}/follow/accept")
    public Result<Void> accept(@PathVariable Long id) {
        followService.acceptFollow(id, UserContext.getUserId());
        return Result.success("已同意，你们已成为好友", null);
    }

    /**
     * 移除关系（撤回请求 / 拒绝请求 / 解除好友）
     */
    @DeleteMapping("/{id}/follow")
    public Result<Void> remove(@PathVariable Long id) {
        followService.removeFollow(id, UserContext.getUserId());
        return Result.success("操作成功", null);
    }

    /**
     * 我与目标用户的关系状态（none / pending_by_me / pending_to_me / friends）
     */
    @GetMapping("/{id}/follow/status")
    public Result<String> status(@PathVariable Long id) {
        return Result.success(followService.getFollowStatus(id, UserContext.getUserId()));
    }

    /**
     * 我收到的待处理好友请求（页头通知数据源）
     */
    @GetMapping("/follow/requests")
    public Result<List<FollowRequestVO>> pendingRequests() {
        return Result.success(followService.getPendingRequests(UserContext.getUserId()));
    }
    /**
     * 我的好友列表（双向互关）—— 供客户端"我的好友"页使用
     */
    @GetMapping("/friends")
    public Result<List<UserVO>> friends() {
        return Result.success(followService.getFriends(UserContext.getUserId()));
    }
}
