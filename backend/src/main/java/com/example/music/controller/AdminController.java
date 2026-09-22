package com.example.music.controller;

import com.example.music.common.response.Result;
import com.example.music.dto.SongQueryDTO;
import com.example.music.dto.StatusDTO;
import com.example.music.dto.UserQueryDTO;
import com.example.music.security.RequireAdmin;
import com.example.music.security.UserContext;
import com.example.music.service.AdminService;
import com.example.music.service.SongService;
import com.example.music.service.UserService;
import com.example.music.vo.PageVO;
import com.example.music.vo.SongVO;
import com.example.music.vo.StatisticVO;
import com.example.music.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

/**
 * 管理端接口
 * <p>
 * 类级 {@link RequireAdmin}：本控制器所有接口都需要管理员权限。
 */
@RestController
@RequestMapping("/admin")
@RequireAdmin
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final SongService songService;
    private final UserService userService;

    /**
     * 管理端歌曲列表（含下架歌曲，可按状态筛选）
     */
    @GetMapping("/songs")
    public Result<PageVO<SongVO>> songs(@Valid SongQueryDTO query) {
        return Result.success(songService.page(query, true));
    }

    /**
     * 用户列表
     */
    @GetMapping("/users")
    public Result<PageVO<UserVO>> users(@Valid UserQueryDTO query) {
        return Result.success(adminService.pageUsers(query));
    }

    /**
     * 启用 / 禁用用户
     */
    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @Valid @RequestBody StatusDTO dto) {
        adminService.updateUserStatus(id, dto.getStatus(), UserContext.getUserId());
        return Result.success("操作成功", null);
    }

    /**
     * 上传 / 替换指定用户的头像（仅管理员）
     * <p>
     * 与用户自助上传共用同一存储与校验逻辑；管理端换图后用 ?v= 时间戳破缓存。
     */
    @PostMapping("/users/{id}/avatar")
    public Result<Void> uploadUserAvatar(@PathVariable Long id,
                                         @RequestParam("file") MultipartFile file) {
        userService.uploadAvatar(id, file);
        return Result.success("头像更新成功", null);
    }

    /**
     * 歌曲上架 / 下架
     */
    @PutMapping("/songs/{id}/status")
    public Result<Void> updateSongStatus(@PathVariable Long id, @Valid @RequestBody StatusDTO dto) {
        adminService.updateSongStatus(id, dto.getStatus());
        return Result.success("操作成功", null);
    }

    /**
     * 数据统计
     */
    @GetMapping("/statistics")
    public Result<StatisticVO> statistics() {
        return Result.success(adminService.statistics());
    }
}
