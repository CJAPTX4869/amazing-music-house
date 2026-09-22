package com.example.music.controller;

import com.example.music.common.response.Result;
import com.example.music.dto.ProfileUpdateDTO;
import com.example.music.security.PublicApi;
import com.example.music.security.RequireLogin;
import com.example.music.security.UserContext;
import com.example.music.service.PlaylistService;
import com.example.music.service.UserService;
import com.example.music.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.IOException;
import java.util.Map;

/**
 * 用户资料接口（头像上传 / 读取）
 * <p>
 * 与静态资源映射无关：头像一律通过接口直出，避免存储目录被整体暴露。
 */
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    /**
     * 头像缓存策略：no-cache（协商缓存）
     * <p>
     * 不能像封面那样用 max-age：头像 URL 固定为 /users/{id}/avatar，用户换图后地址不变，
     * 一旦被浏览器强缓存就会"换完看不到新图"。no-cache 表示每次都带 If-Modified-Since 校验，
     * 未变时返回 304（几乎零开销），变了则立刻返回新图。
     */
    private static final String AVATAR_CACHE_CONTROL = "no-cache";

    private final UserService userService;
    private final PlaylistService playlistService;

    /**
     * 用户公开主页资料（任意用户可访问；含好友数量统计）
     */
    @PublicApi
    @GetMapping("/{id}/profile")
    public Result<UserVO> publicProfile(@PathVariable Long id) {
        return Result.success(userService.getPublicProfile(id, UserContext.getUserId()));
    }

    /**
     * 用户主页歌单集合（公开）：{created: 创建的, favorite: 我喜欢的音乐, collected: 收藏的}
     * <p>
     * 本人访问时可见私有歌单；他人仅可见公开歌单。
     */
    @PublicApi
    @GetMapping("/{id}/playlists")
    public Result<Map<String, Object>> userPlaylists(@PathVariable Long id) {
        return Result.success(playlistService.userPlaylists(id, UserContext.getUserId()));
    }

    /**
     * 修改个人资料（昵称 / 个性签名）
     * <p>
     * 只更新传了值的字段，返回修改后的最新资料。
     */
    @PutMapping("/profile")
    @RequireLogin
    public Result<UserVO> updateProfile(@Valid @RequestBody ProfileUpdateDTO dto) {
        Long userId = UserContext.getUserId();
        UserVO vo = userService.updateProfile(userId, dto);
        log.info("用户资料修改成功：userId={}", userId);
        return Result.success("保存成功", vo);
    }

    /**
     * 上传 / 替换当前登录用户的头像
     *
     * @return 新的头像绝对地址（客户端可直接用于 img src）
     */
    @PostMapping("/avatar")
    @RequireLogin
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file,
                                       HttpServletRequest request) {
        Long userId = UserContext.getUserId();
        String relativePath = userService.uploadAvatar(userId, file);
        log.info("用户头像上传成功：userId={}, path={}", userId, relativePath);
        return Result.success("头像更新成功", avatarUrl(userId, request));
    }

    /**
     * 读取用户头像（公开；未设置头像返回 404）
     * <p>
     * 前端 {@code <img src="/api/users/{id}/avatar">} 即可展示。
     */
    @GetMapping("/{id}/avatar")
    public ResponseEntity<Resource> avatar(@PathVariable Long id) {
        Resource resource = userService.getAvatarResource(id);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(resolveImageMediaType(resource))
                .header("Cache-Control", AVATAR_CACHE_CONTROL);
        try {
            // 附 Last-Modified：浏览器后续请求携带 If-Modified-Since，图片未变时返回 304（无 body）
            builder.lastModified(resource.lastModified());
        } catch (IOException e) {
            log.warn("读取头像最后修改时间失败：{}", e.getMessage());
        }
        return builder.body(resource);
    }

    /**
     * 拼接头像绝对地址（含 context-path，如 http://host:8080/api/users/1/avatar）
     */
    private String avatarUrl(Long userId, HttpServletRequest request) {
        String prefix = request.getScheme() + "://" + request.getServerName() + ":"
                + request.getServerPort() + request.getContextPath();
        return prefix + "/users/" + userId + "/avatar";
    }

    /**
     * 依据文件后缀推断图片 MIME，无法识别时回退 octet-stream
     */
    private MediaType resolveImageMediaType(Resource resource) {
        String filename = resource.getFilename();
        if (StringUtils.hasText(filename)) {
            return MediaTypeFactory.getMediaType(filename)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
