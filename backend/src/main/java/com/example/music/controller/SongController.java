package com.example.music.controller;

import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.Result;
import com.example.music.common.response.ResultCode;
import com.example.music.dto.BatchDeleteDTO;
import com.example.music.dto.CheckMd5DTO;
import com.example.music.dto.SongQueryDTO;
import com.example.music.dto.SongUpdateDTO;
import com.example.music.dto.SongUploadDTO;
import com.example.music.entity.Song;
import com.example.music.security.RequireAdmin;
import com.example.music.security.UserContext;
import com.example.music.service.FileStorageService;
import com.example.music.service.SongService;
import com.example.music.vo.PageVO;
import com.example.music.vo.SongVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 歌曲接口（公开 + 管理）
 */
@Slf4j
@RestController
@RequestMapping("/songs")
@RequiredArgsConstructor
public class SongController {

    /** 单次 Range 响应最大字节数（8MB），避免一次返回过大片段 */
    private static final long MAX_STREAM_CHUNK = 8L * 1024 * 1024;

    /** 封面缓存策略：图片内容不变（换封面会换 URL 语义由 id 固定，故取短缓存 + 协商） */
    private static final String COVER_CACHE_CONTROL = "public, max-age=3600";

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN = "unknown";

    private final SongService songService;
    private final FileStorageService fileStorageService;

    /* ==================== 管理端操作 ==================== */

    /**
     * 上传歌曲（仅管理员）
     */
    @PostMapping("/upload")
    @RequireAdmin
    public Result<SongVO> upload(@Valid @ModelAttribute SongUploadDTO dto) {
        return Result.success("上传成功", songService.upload(dto, UserContext.getUserId()));
    }

    /**
     * MD5 预检（秒传，仅管理员）
     */
    @PostMapping("/check-md5")
    @RequireAdmin
    public Result<SongVO> checkMd5(@Valid @RequestBody CheckMd5DTO dto) {
        return Result.success(songService.checkMd5(dto.getMd5()));
    }

    /**
     * 编辑歌曲信息（仅管理员）
     */
    @PutMapping("/{id}")
    @RequireAdmin
    public Result<SongVO> update(@PathVariable Long id, @Valid @RequestBody SongUpdateDTO dto) {
        return Result.success("修改成功", songService.update(id, dto));
    }

    /**
     * 删除歌曲（仅管理员）
     */
    @DeleteMapping("/{id}")
    @RequireAdmin
    public Result<Void> delete(@PathVariable Long id) {
        songService.delete(id);
        return Result.success("删除成功", null);
    }

    /**
     * 批量删除（仅管理员）
     * <p>
     * 使用 POST 而非 DELETE：部分代理/客户端会丢弃 DELETE 的请求体。
     */
    @PostMapping("/batch-delete")
    @RequireAdmin
    public Result<Void> batchDelete(@Valid @RequestBody BatchDeleteDTO dto) {
        songService.batchDelete(dto.getIds());
        return Result.success("删除成功", null);
    }

    /* ==================== 公开接口 ==================== */

    /**
     * 歌曲列表（公开，仅返回上架歌曲）
     */
    @GetMapping
    public Result<PageVO<SongVO>> list(@Valid SongQueryDTO query) {
        // 公开端忽略 status 参数，防止绕过下架限制
        query.setStatus(null);
        return Result.success(songService.page(query, false));
    }

    /**
     * 歌曲详情（公开）
     */
    @GetMapping("/{id}")
    public Result<SongVO> detail(@PathVariable Long id) {
        return Result.success(songService.detail(id));
    }

    /**
     * 下载歌曲（公开，游客可下载）
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, HttpServletRequest request) {
        Song song = songService.getPlayableSong(id);
        Resource resource = loadSongResource(song);
        if (resource == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        long contentLength = contentLengthOf(resource);

        // 异步记录下载行为，不阻塞文件流输出
        songService.recordDownloadAsync(id, UserContext.getUserId(),
                resolveClientIp(request), request.getHeader(HttpHeaders.USER_AGENT));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(song))
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                .contentType(resolveMediaType(song))
                .body(resource);
    }

    /**
     * 播放流（公开，支持 HTTP Range，播放器可拖拽进度）
     */
    @GetMapping("/{id}/stream")
    public ResponseEntity<ResourceRegion> stream(@PathVariable Long id,
                                                 @RequestHeader HttpHeaders headers) {
        Song song = songService.getPlayableSong(id);
        Resource resource = loadSongResource(song);
        if (resource == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        long contentLength = contentLengthOf(resource);
        MediaType mediaType = resolveMediaType(song);

        List<HttpRange> ranges = headers.getRange();

        // 无 Range：完整响应
        if (ranges.isEmpty()) {
            songService.recordPlayAsync(id);
            return ResponseEntity.status(HttpStatus.OK)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentType(mediaType)
                    .contentLength(contentLength)
                    .body(new ResourceRegion(resource, 0, contentLength));
        }

        // 有 Range：206 部分内容
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);
        long rangeLength = Math.min(MAX_STREAM_CHUNK, end - start + 1);

        // 仅首次请求（start=0）计入播放数，避免拖拽进度被重复计数
        if (start == 0) {
            songService.recordPlayAsync(id);
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(mediaType)
                .body(new ResourceRegion(resource, start, rangeLength));
    }

    /* ==================== 歌词（V3.0 新增） ==================== */

    /**
     * 上传 / 替换歌词（仅管理员）
     * <p>
     * 支持 .lrc / .txt，单文件 ≤ 1MB；重复上传将覆盖旧歌词。
     */
    @PostMapping("/{id}/lyric")
    @RequireAdmin
    public Result<SongVO> uploadLyric(@PathVariable Long id,
                                      @RequestParam("file") MultipartFile file) {
        return Result.success("歌词上传成功", songService.uploadLyric(id, file));
    }

    /**
     * 读取歌词文本（公开；无歌词时返回空串）
     */
    @GetMapping("/{id}/lyric")
    public Result<String> lyric(@PathVariable Long id) {
        return Result.success(songService.getLyricContent(id));
    }

    /* ==================== 封面图片（V3.0 新增） ==================== */

    /**
     * 上传 / 替换封面图片（仅管理员）
     * <p>
     * 支持 jpg / jpeg / png / webp / gif，单文件 ≤ 5MB；
     * 前端会先压缩裁剪为正方形再上传，重复上传将覆盖旧封面。
     */
    @PostMapping("/{id}/cover")
    @RequireAdmin
    public Result<SongVO> uploadCover(@PathVariable Long id,
                                      @RequestParam("file") MultipartFile file) {
        return Result.success("封面上传成功", songService.uploadCover(id, file));
    }

    /**
     * 读取封面图片（公开；无封面返回 404）
     * <p>
     * 直接输出图片字节流，前端 <c>&lt;img src="/api/songs/{id}/cover"&gt;</c> 即可展示。
     * 不使用静态资源映射，避免存储目录被整体暴露。
     */
    @GetMapping("/{id}/cover")
    public ResponseEntity<Resource> cover(@PathVariable Long id) {
        Resource resource = songService.getCoverResource(id);
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

    /* ==================== 内部工具 ==================== */

    /**
     * 加载歌曲文件资源；文件缺失时返回 null（由调用方响应 404）
     * <p>
     * 必须在设置任何响应头之前调用：一旦 Content-Type 被写成 audio/*（二进制流），
     * 之后再抛异常会因无法把 Result 序列化成 audio/* 而产生二次异常，
     * 表现为 500 + 空响应体（播放器拿不到任何提示，只能等超时）。
     */
    private Resource loadSongResource(Song song) {
        try {
            return fileStorageService.loadAsResource(song.getFilePath());
        } catch (BusinessException e) {
            log.warn("歌曲文件缺失，无法提供服务：id={}, path={}", song.getId(), song.getFilePath());
            return null;
        }
    }

    private long contentLengthOf(Resource resource) {
        try {
            return resource.contentLength();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FILE_MISSING);
        }
    }

    /**
     * 构建下载响应头：同时提供 ASCII 兜底文件名与 RFC 5987 编码文件名
     */
    private String buildContentDisposition(Song song) {
        String original = StringUtils.hasText(song.getOriginalName())
                ? song.getOriginalName()
                : song.getTitle() + (StringUtils.hasText(song.getExt()) ? "." + song.getExt() : "");
        // filename*：URL 编码（RFC 5987 要求空格编码为 %20）
        String encoded = URLEncoder.encode(original, StandardCharsets.UTF_8).replace("+", "%20");
        // filename：ASCII 兜底，兼容不支持 filename* 的旧客户端
        String ascii = original.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private MediaType resolveMediaType(Song song) {
        if (StringUtils.hasText(song.getMimeType())) {
            try {
                return MediaType.parseMediaType(song.getMimeType());
            } catch (Exception ignored) {
                // 非法 MIME 时回退
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * 解析客户端真实 IP（兼容 Nginx 反向代理）
     */
    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip)) {
            int index = ip.indexOf(',');
            return index > 0 ? ip.substring(0, index).trim() : ip.trim();
        }
        ip = request.getHeader(HEADER_X_REAL_IP);
        if (StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
