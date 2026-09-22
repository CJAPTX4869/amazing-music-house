package com.example.music.controller;

import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.Result;
import com.example.music.dto.CommentCreateDTO;
import com.example.music.dto.ReplyCreateDTO;
import com.example.music.security.PublicApi;
import com.example.music.security.RequireLogin;
import com.example.music.security.UserContext;
import com.example.music.service.CommentService;
import com.example.music.service.FileStorageService;
import com.example.music.vo.CommentVO;
import com.example.music.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

/**
 * 歌曲评论接口
 * <p>
 * 鉴权与 {@link PlaylistController} 同模式：类级 {@link RequireLogin}，
 * 浏览类接口（评论列表/配图/表情）标 {@link PublicApi} 匿名可访问
 * （评论配图由 {@code <img src>} 加载，不携带 Authorization 头）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequireLogin
public class CommentController {

    /** 配图缓存策略：图片内容不变（配图文件不覆盖，随评论删除） */
    private static final String IMAGE_CACHE_CONTROL = "public, max-age=3600";

    private final CommentService commentService;
    private final FileStorageService fileStorageService;

    /* ==================== 评论浏览 ==================== */

    /**
     * 歌曲评论分页（公开浏览；sort=hot 按点赞排序 / new 按时间倒序）
     */
    @PublicApi
    @GetMapping("/songs/{songId}/comments")
    public Result<PageVO<CommentVO>> list(@PathVariable Long songId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(defaultValue = "new") String sort) {
        return Result.success(commentService.page(songId, page, size, sort, UserContext.getUserId()));
    }

    /**
     * 读取评论配图（公开；无配图返回 404）
     */
    @PublicApi
    @GetMapping("/comments/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        Resource resource = commentService.getImageResource(id);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(resolveImageMediaType(resource))
                .header(HttpHeaders.CACHE_CONTROL, IMAGE_CACHE_CONTROL)
                .body(resource);
    }

    /**
     * 按路径读取配图（公开）——发布前的预览专用：
     * 此时评论尚未创建、没有 id 可寻址，前端拿上传接口返回的相对路径来取图。
     * 仅允许 comment/ 目录（防伪造路径越权读取其它存储文件）。
     */
    @PublicApi
    @GetMapping("/comments/image")
    public ResponseEntity<Resource> imageByPath(@RequestParam("path") String path) {
        if (!StringUtils.hasText(path) || !path.startsWith("comment/")) {
            return ResponseEntity.badRequest().build();
        }
        Resource resource;
        try {
            resource = fileStorageService.loadAsResource(path);
        } catch (BusinessException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(resolveImageMediaType(resource))
                .header(HttpHeaders.CACHE_CONTROL, IMAGE_CACHE_CONTROL)
                .body(resource);
    }

    /* ==================== 评论操作 ==================== */

    /**
     * 发表根评论（可带配图）
     */
    @PostMapping("/songs/{songId}/comments")
    public Result<CommentVO> create(@PathVariable Long songId,
                                    @Valid @RequestBody CommentCreateDTO dto) {
        return Result.success("评论成功", commentService.create(songId, dto, UserContext.getUserId()));
    }

    /**
     * 楼中楼回复/追评
     */
    @PostMapping("/comments/{id}/replies")
    public Result<CommentVO> reply(@PathVariable Long id,
                                   @Valid @RequestBody ReplyCreateDTO dto) {
        return Result.success("回复成功", commentService.reply(id, dto, UserContext.getUserId()));
    }

    /**
     * 删除评论（本人或管理员；删根评论=整楼隐藏）
     */
    @DeleteMapping("/comments/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.delete(id, UserContext.getUserId());
        return Result.success("删除成功", null);
    }

    /**
     * 点赞评论（重复点赞幂等）
     */
    @PostMapping("/comments/{id}/like")
    public Result<Void> like(@PathVariable Long id) {
        commentService.like(id, UserContext.getUserId());
        return Result.success("已点赞", null);
    }

    /**
     * 取消点赞
     */
    @DeleteMapping("/comments/{id}/like")
    public Result<Void> unlike(@PathVariable Long id) {
        commentService.unlike(id, UserContext.getUserId());
        return Result.success("已取消点赞", null);
    }

    /**
     * 上传评论配图（jpg / jpeg / png / webp / gif ≤ 5MB；GIF 动图保留动画）
     *
     * @return 配图相对路径（发评论时作为 imagePath 提交）
     */
    @PostMapping("/comments/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        FileStorageService.StoredFile stored = fileStorageService.storeCommentImage(file);
        return Result.success("图片上传成功", stored.getRelativePath());
    }

    /* ==================== 内部工具 ==================== */

    private MediaType resolveImageMediaType(Resource resource) {
        String filename = resource.getFilename();
        if (StringUtils.hasText(filename)) {
            return MediaTypeFactory.getMediaType(filename)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
