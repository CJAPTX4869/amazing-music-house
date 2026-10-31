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
import com.example.music.security.RequireLogin;
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
import java.time.ZoneId;
import java.util.List;

/**
 * 歌曲接口（公开 + 管理）
 */
@Slf4j
@RestController
@RequestMapping("/songs")
@RequiredArgsConstructor
public class SongController {

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
     * 歌曲列表（需登录，仅返回上架歌曲）
     * <p>
     * 这是「翻库」入口：不带 keyword 时会把整库按分页全列出来。
     * 按产品口径，未登录只允许「搜索 + 单曲播放」，所以这里要求登录；
     * 管理端本来就带 token，不受影响。
     */
    @RequireLogin
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
                .header("X-Content-Type-Options", "nosniff")
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
        String etag = etagOf(song, contentLength);
        // HTTP 日期只有秒级精度，抹掉毫秒才不会让校验器永远对不上
        long lastModified = truncatedToSecond(lastModifiedOf(song));

        List<HttpRange> ranges = headers.getRange();

        // 无 Range：完整响应（客户端已持有同一份时回 304）
        if (ranges.isEmpty()) {
            return fullResponse(song, resource, contentLength, mediaType, etag, lastModified,
                    etagMatches(headers.getFirst(HttpHeaders.IF_NONE_MATCH), etag));
        }

        // 有 Range：If-Range 的校验器与当前内容不一致（文件被换过）时，
        // 按 RFC 7233 必须退回 200 全量，否则客户端会把新文件当成旧内容来定位
        String ifRange = headers.getFirst(HttpHeaders.IF_RANGE);
        if (StringUtils.hasText(ifRange) && !etagMatches(ifRange, etag)) {
            return fullResponse(song, resource, contentLength, mediaType, etag, lastModified, false);
        }

        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);

        // 起点越过文件尾时以前会算出负长度，异常被全局处理器包成「HTTP 200 + 84 字节 JSON」，
        // 播放器把这段 JSON 当音频尾巴吃掉，表现为播到中途突然从头再来。按 RFC 7233 应回 416。
        if (start < 0 || start >= contentLength || end < start) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .eTag(etag)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .build();
        }
        // 不再对区间长度设上限：播放器发 bytes=0- 就是想要到文件尾，
        // 截成 8MB 会让它以为媒体到此结束（ResourceRegion 本身就是分块流式写出，不占整块内存）。
        long rangeLength = end - start + 1;

        // 仅首次请求（start=0）计入播放数，避免拖拽进度被重复计数
        if (start == 0) {
            songService.recordPlayAsync(id);
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .eTag(etag)
                .lastModified(lastModified)
                .header(HttpHeaders.CACHE_CONTROL, STREAM_CACHE_CONTROL)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .body(new ResourceRegion(resource, start, rangeLength));
    }

    /**
     * 完整内容响应，或命中 If-None-Match 时回 304。
     * 304 不计播放数 —— 客户端用的是本地缓存，没有真的再下一遍。
     */
    private ResponseEntity<ResourceRegion> fullResponse(Song song, Resource resource, long contentLength,
                                                        MediaType mediaType, String etag, long lastModified,
                                                        boolean notModified) {
        if (notModified) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .lastModified(lastModified)
                    .header(HttpHeaders.CACHE_CONTROL, STREAM_CACHE_CONTROL)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .build();
        }
        songService.recordPlayAsync(song.getId());
        return ResponseEntity.status(HttpStatus.OK)
                .eTag(etag)
                .lastModified(lastModified)
                .header(HttpHeaders.CACHE_CONTROL, STREAM_CACHE_CONTROL)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .contentLength(contentLength)
                .body(new ResourceRegion(resource, 0, contentLength));
    }

    /**
     * 音频流缓存策略：max-age=0 + must-revalidate —— 每次回源校验，
     * 命中就是几十字节的 304（重听不再重下一整首），文件被替换则立刻失效。
     */
    private static final String STREAM_CACHE_CONTROL = "private, max-age=0, must-revalidate";

    /** 强校验器：优先用库里登记的 MD5（换文件必变）；缺失时退回「大小 + 更新时间」组合 */
    private String etagOf(Song song, long contentLength) {
        String md5 = song.getFileMd5();
        if (StringUtils.hasText(md5)) {
            return "\"" + md5.trim() + "\"";
        }
        return "\"ms-" + contentLength + "-" + lastModifiedOf(song) + "\"";
    }

    private long lastModifiedOf(Song song) {
        return song.getUpdateTime() == null ? 0L
                : song.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private long truncatedToSecond(long epochMillis) {
        return epochMillis / 1000L * 1000L;
    }

    /** ETag 比较：忽略弱校验器 W/ 前缀，支持通配 * */
    private boolean etagMatches(String headerValue, String etag) {
        if (!StringUtils.hasText(headerValue)) {
            return false;
        }
        for (String part : headerValue.split(",")) {
            String v = part.trim();
            if ("*".equals(v)) {
                return true;
            }
            if (v.startsWith("W/")) {
                v = v.substring(2);
            }
            if (v.equals(etag)) {
                return true;
            }
        }
        return false;
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
            // 无封面的歌曲回退内置默认封面：前端任何位置都不会出现空白图
            resource = new org.springframework.core.io.ClassPathResource("static/default-cover.png");
        }
        return ResponseEntity.ok()
                .contentType(resolveImageMediaType(resource))
                .header(HttpHeaders.CACHE_CONTROL, COVER_CACHE_CONTROL)
                .header("X-Content-Type-Options", "nosniff")
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
