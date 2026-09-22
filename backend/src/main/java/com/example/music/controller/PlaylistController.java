package com.example.music.controller;

import com.example.music.common.response.Result;
import com.example.music.dto.AddSongDTO;
import com.example.music.dto.OrderSongsDTO;
import com.example.music.dto.PlaylistSaveDTO;
import com.example.music.security.PublicApi;
import com.example.music.security.RequireLogin;
import com.example.music.security.UserContext;
import com.example.music.service.PlaylistService;
import com.example.music.vo.PlaylistVO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;

/**
 * 歌单接口
 * <p>
 * 全部接口均需登录（类级 {@link RequireLogin}）；越权操作由 Service 层校验并返回 403。
 */
@Slf4j
@RestController
@RequestMapping("/playlists")
@RequiredArgsConstructor
@RequireLogin
public class PlaylistController {

    /** 封面缓存策略：URL 固定（按歌单 id），换封面后需协商校验，故短缓存 + 协商 */
    private static final String COVER_CACHE_CONTROL = "public, max-age=3600";

    private final PlaylistService playlistService;

    /* ==================== 歌单 CRUD ==================== */

    /**
     * 新建歌单
     */
    @PostMapping
    public Result<PlaylistVO> create(@Valid @RequestBody PlaylistSaveDTO dto) {
        return Result.success("创建成功", playlistService.create(dto, UserContext.getUserId()));
    }

    /**
     * 我的歌单列表
     */
    @GetMapping
    public Result<List<PlaylistVO>> myList() {
        return Result.success(playlistService.myList(UserContext.getUserId()));
    }

    /**
     * 歌单详情（含歌曲列表）
     */
    @GetMapping("/{id}")
    public Result<PlaylistVO> detail(@PathVariable Long id) {
        return Result.success(playlistService.detail(id, UserContext.getUserId()));
    }

    /**
     * 编辑歌单
     */
    @PutMapping("/{id}")
    public Result<PlaylistVO> update(@PathVariable Long id, @Valid @RequestBody PlaylistSaveDTO dto) {
        return Result.success("修改成功", playlistService.update(id, dto, UserContext.getUserId()));
    }

    /**
     * 删除歌单
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        playlistService.delete(id, UserContext.getUserId());
        return Result.success("删除成功", null);
    }

    /* ==================== 歌单内歌曲 ==================== */

    /**
     * 向歌单添加歌曲
     */
    /** 保存歌单自定义排序（前端拖拽后调用） */
    @PutMapping("/{id}/songs/order")
    public Result<Void> reorderSongs(@PathVariable Long id, @RequestBody OrderSongsDTO dto) {
        playlistService.reorderSongs(id, dto.getSongIds(), UserContext.getUserId());
        return Result.<Void>success("排序已保存", null);
    }
    @PostMapping("/{id}/songs")
    public Result<Void> addSong(@PathVariable Long id, @Valid @RequestBody AddSongDTO dto) {
        playlistService.addSong(id, dto.getSongId(), UserContext.getUserId());
        return Result.success("已添加到歌单", null);
    }

    /**
     * 从歌单移除歌曲
     */
    @DeleteMapping("/{id}/songs/{songId}")
    public Result<Void> removeSong(@PathVariable Long id, @PathVariable Long songId) {
        playlistService.removeSong(id, songId, UserContext.getUserId());
        return Result.success("已从歌单移除", null);
    }

    /* ==================== 我喜欢的音乐 ==================== */

    /**
     * 收藏歌曲
     */
    @PostMapping("/favorite/{songId}")
    public Result<PlaylistVO> favorite(@PathVariable Long songId) {
        return Result.success("已收藏", playlistService.favorite(songId, UserContext.getUserId()));
    }

    /**
     * 取消收藏
     */
    @DeleteMapping("/favorite/{songId}")
    public Result<Void> unfavorite(@PathVariable Long songId) {
        playlistService.unfavorite(songId, UserContext.getUserId());
        return Result.success("已取消收藏", null);
    }

    /**
     * 我喜欢的音乐（含歌曲列表）
     */
    @GetMapping("/favorite")
    public Result<PlaylistVO> favoriteList() {
        return Result.success(playlistService.getFavoriteList(UserContext.getUserId()));
    }

    /**
     * 判断歌曲是否已收藏
     */
    @GetMapping("/favorite/check/{songId}")
    public Result<Boolean> isFavorited(@PathVariable Long songId) {
        return Result.success(playlistService.isFavorited(songId, UserContext.getUserId()));
    }

    /* ==================== 收藏歌单（V3.1） ==================== */

    /**
     * 收藏他人公开歌单（不能收藏自己的；重复收藏幂等）
     */
    @PostMapping("/{id}/collect")
    public Result<Void> collect(@PathVariable Long id) {
        playlistService.collect(id, UserContext.getUserId());
        return Result.success("收藏成功", null);
    }

    /**
     * 取消收藏歌单
     */
    @DeleteMapping("/{id}/collect")
    public Result<Void> uncollect(@PathVariable Long id) {
        playlistService.uncollect(id, UserContext.getUserId());
        return Result.success("已取消收藏", null);
    }

    /**
     * 当前用户是否已收藏该歌单
     */
    @GetMapping("/{id}/collect/check")
    public Result<Boolean> isCollected(@PathVariable Long id) {
        return Result.success(playlistService.isCollected(id, UserContext.getUserId()));
    }

    /* ==================== 歌单封面 ==================== */

    /**
     * 上传 / 替换歌单封面（需登录且为歌单所有者）
     * <p>
     * 支持 jpg / jpeg / png / webp / gif，单文件 ≤ 5MB；前端会先裁剪压缩为正方形再上传，
     * 重复上传将覆盖旧封面并清理旧文件。
     */
    @PostMapping("/{id}/cover")
    public Result<PlaylistVO> uploadCover(@PathVariable Long id,
                                          @RequestParam("file") MultipartFile file) {
        return Result.success("封面上传成功", playlistService.uploadCover(id, file, UserContext.getUserId()));
    }

    /**
     * 读取歌单封面（公开；无封面返回 404）
     * <p>
     * 浏览器的 {@code <img src="/api/playlists/{id}/cover">} 不会携带 Authorization 头，
     * 故用 {@link PublicApi} 免除类级登录要求；直接输出图片字节流，不暴露存储路径。
     */
    @PublicApi
    @GetMapping("/{id}/cover")
    public ResponseEntity<Resource> cover(@PathVariable Long id) {
        Resource resource = playlistService.getCoverResource(id);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(resolveImageMediaType(resource))
                .header(HttpHeaders.CACHE_CONTROL, COVER_CACHE_CONTROL)
                .body(resource);
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
