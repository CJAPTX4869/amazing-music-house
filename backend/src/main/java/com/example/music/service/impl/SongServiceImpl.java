package com.example.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.ResultCode;
import com.example.music.common.util.InMemoryMultipartFile;
import com.example.music.constant.SortWhitelist;
import com.example.music.constant.StorageConstants;
import com.example.music.dto.SongQueryDTO;
import com.example.music.dto.SongUpdateDTO;
import com.example.music.dto.SongUploadDTO;
import com.example.music.entity.DownloadRecord;
import com.example.music.entity.Song;
import com.example.music.entity.User;
import com.example.music.mapper.DownloadRecordMapper;
import com.example.music.entity.SongComment;
import com.example.music.mapper.SongMapper;
import com.example.music.mapper.UserMapper;
import com.example.music.service.FileStorageService;
import com.example.music.service.PlaylistService;
import com.example.music.service.SongService;
import com.example.music.utils.AudioMetadataUtils;
import com.example.music.utils.LyricConvertUtils;
import com.example.music.vo.PageVO;
import com.example.music.vo.SongVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.io.IoUtil;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.DigestUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 歌曲服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SongServiceImpl implements SongService {

    /* ---------- 字段长度上限（与数据库 DDL 保持一致，防止超长入库失败） ---------- */
    private static final int MAX_TITLE_LEN = 200;
    private static final int MAX_ARTIST_LEN = 100;
    private static final int MAX_ALBUM_LEN = 100;
    private static final int MAX_GENRE_LEN = 50;
    private static final int MAX_ORIGINAL_NAME_LEN = 255;
    private static final int MAX_USER_AGENT_LEN = 500;

    private static final int STATUS_ONLINE = 1;
    private static final int STATUS_OFFLINE = 0;

    private static final String MD5_PATTERN = "^[a-f0-9]{32}$";

    /** UTF-8 BOM 字符（部分 LRC 文件带此字符，读取时需剥离） */
    private static final String BOM = "\uFEFF";

    /**
     * 排序字段映射：前端字段名 → 实体属性（使用 SFunction，彻底避免 SQL 字符串拼接）
     */
    private static final Map<String, SFunction<Song, ?>> SORT_MAPPING = new LinkedHashMap<>();

    static {
        SORT_MAPPING.put("create_time", Song::getCreateTime);
        SORT_MAPPING.put("download_count", Song::getDownloadCount);
        SORT_MAPPING.put("play_count", Song::getPlayCount);
        SORT_MAPPING.put("comment_count", Song::getCommentCount);
        SORT_MAPPING.put("title", Song::getTitle);
    }

    private final SongMapper songMapper;
    private final UserMapper userMapper;
    private final DownloadRecordMapper downloadRecordMapper;
    private final FileStorageService fileStorageService;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final PlaylistService playlistService;
    private final com.example.music.mapper.SongCommentMapper songCommentMapper;
    private final com.example.music.mapper.CommentLikeMapper commentLikeMapper;
    private final com.example.music.mapper.PlayHistoryMapper playHistoryMapper;
    private final com.example.music.mapper.SongPlayDailyMapper songPlayDailyMapper;

    /* ==================== 上传 ==================== */

    @Override
    public SongVO upload(SongUploadDTO dto, Long uploaderId) {
        evictSongPageCache();
        // 1. 前端已提供 MD5 → 先做秒传判断，命中则完全不需要传输/落盘
        String clientMd5 = normalizeMd5(dto.getMd5());
        if (clientMd5 != null) {
            Song exists = findOneByMd5(clientMd5);
            if (exists != null) {
                log.info("秒传命中（客户端 MD5）：md5={}, songId={}", clientMd5, exists.getId());
                return toVO(exists);
            }
        }

        // 2. 落盘（写入过程同步计算 MD5）
        FileStorageService.StoredFile stored = fileStorageService.store(dto.getFile(), StorageConstants.AUDIO_DIR);

        try {
            // 3. 服务端计算的 MD5 命中 → 复用已有记录，删除刚落盘的文件
            Song exists = findOneByMd5(stored.getMd5());
            if (exists != null) {
                fileStorageService.delete(stored.getRelativePath());
                log.info("秒传命中（服务端 MD5）：md5={}, songId={}", stored.getMd5(), exists.getId());
                return toVO(exists);
            }

            // 4. 解析 ID3 元数据（失败自动降级，不阻断上传）
            AudioMetadataUtils.AudioMetadata metadata = AudioMetadataUtils.parse(
                    fileStorageService.resolveAbsolute(stored.getRelativePath()));

            // 5. 组装实体：前端参数 > ID3 标签 > 文件名
            String originalName = truncate(dto.getFile().getOriginalFilename(), MAX_ORIGINAL_NAME_LEN);
            Song song = new Song();
            // 标题取值：前端参数 > ID3 标签 > 文件名（三级兜底）
            String resolvedTitle = firstNonBlank(dto.getTitle(), metadata.getTitle(), baseName(originalName));
            song.setTitle(truncate(resolvedTitle, MAX_TITLE_LEN));
            song.setArtist(truncate(firstNonBlank(dto.getArtist(), metadata.getArtist()), MAX_ARTIST_LEN));
            song.setAlbum(truncate(firstNonBlank(dto.getAlbum(), metadata.getAlbum()), MAX_ALBUM_LEN));
            song.setTags(normalizeTags(firstNonBlank(dto.getTags(), metadata.getGenre())));
            song.setDuration(metadata.getDuration() == null ? 0 : metadata.getDuration());
            song.setOriginalName(originalName);
            song.setFileName(stored.getFileName());
            song.setFilePath(stored.getRelativePath());
            song.setFileSize(stored.getSize());
            song.setFileMd5(stored.getMd5());
            song.setMimeType(dto.getFile().getContentType());
            song.setExt(fileStorageService.extractExtension(originalName));
            song.setUploaderId(uploaderId);
            song.setDownloadCount(0);
            song.setPlayCount(0);
            song.setStatus(STATUS_ONLINE);

            // 6. 入库；失败则删除已落盘文件（补偿回滚，先落盘后入库的逆操作）
            try {
                songMapper.insert(song);
            } catch (DuplicateKeyException e) {
                // 并发场景兜底：另一请求已插入相同 MD5 的记录（命中 uk_md5 唯一约束）
                fileStorageService.delete(stored.getRelativePath());
                Song concurrent = findOneByMd5(stored.getMd5());
                if (concurrent != null) {
                    log.info("并发秒传命中（唯一约束兜底）：md5={}, songId={}",
                            stored.getMd5(), concurrent.getId());
                    return toVO(concurrent);
                }
                throw new BusinessException(ResultCode.FILE_WRITE_FAILED, "上传失败，请重试");
            }
            log.info("歌曲上传成功：id={}, title={}, size={}B", song.getId(), song.getTitle(), song.getFileSize());
            return toVO(song);

        } catch (BusinessException e) {
            fileStorageService.delete(stored.getRelativePath());
            throw e;
        } catch (Exception e) {
            fileStorageService.delete(stored.getRelativePath());
            log.error("歌曲上传失败：path={}", stored.getRelativePath(), e);
            throw new BusinessException(ResultCode.FILE_WRITE_FAILED, "上传失败，请重试");
        }
    }

    /* ==================== 查询 ==================== */

    @Override
    public PageVO<SongVO> page(SongQueryDTO query, boolean adminView) {
        // 公开端列表走 Redis 缓存（TTL 5 分钟），管理端实时查询
        if (!adminView) {
            String raw = String.join("|",
                    String.valueOf(query.getPage()), String.valueOf(query.getSize()),
                    query.getSort() == null ? "" : query.getSort(),
                    query.getKeyword() == null ? "" : query.getKeyword(),
                    query.getStatus() == null ? "" : String.valueOf(query.getStatus()));
            String key = "songs:page:" + DigestUtils.md5DigestAsHex(raw.getBytes());
            try {
                String cached = redis.opsForValue().get(key);
                if (cached != null) {
                    return objectMapper.readValue(cached, new TypeReference<PageVO<SongVO>>() { });
                }
            } catch (Exception ignore) {
                // 缓存读取失败按未命中处理
            }
            PageVO<SongVO> vo = doPage(query, adminView);
            try {
                redis.opsForValue().set(key, objectMapper.writeValueAsString(vo), java.time.Duration.ofMinutes(5));
            } catch (Exception ignore) {
                // 序列化失败不影响主流程
            }
            return vo;
        }
        return doPage(query, adminView);
    }

    private PageVO<SongVO> doPage(SongQueryDTO query, boolean adminView) {
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();

        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Song::getTitle, keyword)
                    .or().like(Song::getArtist, keyword)
                    .or().like(Song::getAlbum, keyword));
        }

        // 标签筛选：FIND_IN_SET 精确匹配单个标签（类型 tab 用）
        wrapper.apply(StringUtils.hasText(query.getTag()),
                "FIND_IN_SET({0}, tags) > 0", query.getTag());

        if (adminView) {
            // 管理端：可按状态筛选（不传则含全部）
            wrapper.eq(query.getStatus() != null, Song::getStatus, query.getStatus());
        } else {
            // 公开端：强制只返回上架歌曲
            wrapper.eq(Song::getStatus, STATUS_ONLINE);
        }

        applySort(wrapper, query.getSort());

        Page<Song> pageParam = new Page<>(query.getPage(), query.getSize());
        Page<Song> result = songMapper.selectPage(pageParam, wrapper);

        PageVO<SongVO> vo = new PageVO<>();
        vo.setRecords(toVOList(result.getRecords()));
        vo.setTotal(result.getTotal());
        vo.setPage(result.getCurrent());
        vo.setSize(result.getSize());
        return vo;
    }

    @Override
    public SongVO detail(Long id) {
        Song song = songMapper.selectById(id);
        if (song == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        return toVO(song);
    }

    @Override
    public SongVO checkMd5(String md5) {
        evictSongPageCache();
        String normalized = normalizeMd5(md5);
        if (normalized == null) {
            return null;
        }
        Song song = findOneByMd5(normalized);
        return song == null ? null : toVO(song);
    }

    @Override
    public Song getPlayableSong(Long id) {
        Song song = songMapper.selectById(id);
        if (song == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        if (!Integer.valueOf(STATUS_ONLINE).equals(song.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该歌曲已下架");
        }
        return song;
    }

    /* ==================== 修改 ==================== */

    @Override
    public SongVO update(Long id, SongUpdateDTO dto) {
        evictSongPageCache();
        Song song = songMapper.selectById(id);
        if (song == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }

        // 仅覆盖传入的字段（null 表示不修改）
        if (StringUtils.hasText(dto.getTitle())) {
            song.setTitle(truncate(dto.getTitle(), MAX_TITLE_LEN));
        }
        if (dto.getArtist() != null) {
            song.setArtist(truncate(dto.getArtist(), MAX_ARTIST_LEN));
        }
        if (dto.getAlbum() != null) {
            song.setAlbum(truncate(dto.getAlbum(), MAX_ALBUM_LEN));
        }
        if (dto.getTags() != null) {
            song.setTags(normalizeTags(dto.getTags()));
        }

        songMapper.updateById(song);
        log.info("歌曲信息已更新：id={}, title={}", song.getId(), song.getTitle());
        return toVO(song);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        evictSongPageCache();
        Song song = songMapper.selectById(id);
        if (song == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        if (status == null || (status != STATUS_ONLINE && status != STATUS_OFFLINE)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态值不合法");
        }
        if (Objects.equals(song.getStatus(), status)) {
            return;
        }
        Song update = new Song();
        update.setId(id);
        update.setStatus(status);
        songMapper.updateById(update);
        log.info("歌曲状态已更新：id={}, status={}", id, status);
    }

    /* ==================== 删除 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        evictSongPageCache();
        Song song = songMapper.selectById(id);
        if (song == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        // 1. 清歌单关联：playlist_song 是物理删除的关联表，不在这里清就会留下指向已删歌曲的孤儿行
        int purged = playlistService.purgeSongFromPlaylists(id);
        // 2. 级联清除关联数据：评论点赞 → 评论 → 播放历史 → 下载记录 → 每日播放统计（全部物理删除）
        List<Long> commentIds = songCommentMapper.selectList(new LambdaQueryWrapper<SongComment>()
                        .eq(SongComment::getSongId, id))
                .stream().map(SongComment::getId).collect(Collectors.toList());
        if (!commentIds.isEmpty()) {
            commentLikeMapper.delete(new LambdaQueryWrapper<com.example.music.entity.CommentLike>()
                    .in(com.example.music.entity.CommentLike::getCommentId, commentIds));
        }
        songCommentMapper.physicalDeleteBySongId(id);
        playHistoryMapper.delete(new LambdaQueryWrapper<com.example.music.entity.PlayHistory>()
                .eq(com.example.music.entity.PlayHistory::getSongId, id));
        downloadRecordMapper.delete(new LambdaQueryWrapper<com.example.music.entity.DownloadRecord>()
                .eq(com.example.music.entity.DownloadRecord::getSongId, id));
        songPlayDailyMapper.deleteBySongId(id);
        // 3. 物理删除歌曲行（用户要求：删除即全部清除，不保留逻辑删除行）
        songMapper.physicalDeleteById(id);
        // 4. 物理清理文件：音频 + 封面 + 歌词（失败仅记日志，不影响主流程）
        fileStorageService.delete(song.getFilePath());
        fileStorageService.delete(song.getCoverPath());
        fileStorageService.delete(song.getLyricPath());
        log.info("歌曲已彻底删除：id={}, title={}, 从 {} 个歌单中移除，关联数据与文件已清除",
                song.getId(), song.getTitle(), purged);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        evictSongPageCache();
        if (CollectionUtils.isEmpty(ids)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要删除的歌曲");
        }
        List<Song> songs = songMapper.selectBatchIds(ids);
        if (CollectionUtils.isEmpty(songs)) {
            return;
        }
        List<Long> existIds = songs.stream().map(Song::getId).collect(Collectors.toList());
        existIds.forEach(playlistService::purgeSongFromPlaylists);
        // 级联清除关联数据（与单个删除一致）
        List<Long> commentIds = songCommentMapper.selectList(new LambdaQueryWrapper<SongComment>()
                        .in(SongComment::getSongId, existIds))
                .stream().map(SongComment::getId).collect(Collectors.toList());
        if (!commentIds.isEmpty()) {
            commentLikeMapper.delete(new LambdaQueryWrapper<com.example.music.entity.CommentLike>()
                    .in(com.example.music.entity.CommentLike::getCommentId, commentIds));
        }
        existIds.forEach(songCommentMapper::physicalDeleteBySongId);
        playHistoryMapper.delete(new LambdaQueryWrapper<com.example.music.entity.PlayHistory>()
                .in(com.example.music.entity.PlayHistory::getSongId, existIds));
        downloadRecordMapper.delete(new LambdaQueryWrapper<com.example.music.entity.DownloadRecord>()
                .in(com.example.music.entity.DownloadRecord::getSongId, existIds));
        existIds.forEach(songPlayDailyMapper::deleteBySongId);
        existIds.forEach(songMapper::physicalDeleteById);
        // 物理文件：音频 + 封面 + 歌词（失败不抛异常，避免影响整体结果）
        songs.forEach(song -> {
            fileStorageService.delete(song.getFilePath());
            fileStorageService.delete(song.getCoverPath());
            fileStorageService.delete(song.getLyricPath());
        });
        log.info("批量删除歌曲完成（含级联）：请求 {} 条，实际删除 {} 条", ids.size(), songs.size());
    }

    /* ==================== 行为记录（异步） ==================== */

    @Async
    @Override
    public void recordDownloadAsync(Long songId, Long userId, String ip, String userAgent) {
        try {
            songMapper.incrementDownloadCount(songId);
            DownloadRecord record = new DownloadRecord();
            record.setSongId(songId);
            record.setUserId(userId);
            record.setIp(truncate(ip, 50));
            record.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LEN));
            downloadRecordMapper.insert(record);
        } catch (Exception e) {
            // 行为记录失败不应影响用户下载
            log.warn("记录下载行为失败（已忽略）：songId={}, err={}", songId, e.getMessage());
        }
    }

    @Async
    @Override
    public void recordPlayAsync(Long songId) {
        try {
            songMapper.incrementPlayCount(songId);
            songPlayDailyMapper.recordDailyPlay(songId);   // 每日播放统计实时累加
        } catch (Exception e) {
            log.warn("记录播放行为失败（已忽略）：songId={}, err={}", songId, e.getMessage());
        }
    }

    /* ==================== 歌词（V3.0 新增） ==================== */

    @Override
    public SongVO uploadLyric(Long id, MultipartFile file) {
        Song song = songMapper.selectById(id);
        if (song == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }

        // 0. 逐字歌词 JSON 规范化为标准 LRC（网易云客户端下载的 .lrc 常见该格式，可能是 JSON+LRC 混合）；
        //    标准 LRC / 纯文本原样保留。失败时按原文件落盘，不影响上传本身。
        MultipartFile lyricFile = normalizeLyric(file);

        // 1. 先落盘新歌词（失败则原有歌词不受影响）
        FileStorageService.StoredFile stored = fileStorageService.storeLyric(lyricFile);
        String oldPath = song.getLyricPath();

        // 2. 更新数据库；失败则删除刚落盘的文件（补偿回滚）
        Song update = new Song();
        update.setId(id);
        update.setLyricPath(stored.getRelativePath());
        try {
            songMapper.updateById(update);
        } catch (Exception e) {
            fileStorageService.delete(stored.getRelativePath());
            log.error("歌词记录更新失败：songId={}", id, e);
            throw new BusinessException(ResultCode.FILE_WRITE_FAILED, "歌词保存失败，请重试");
        }

        // 3. 更新成功后再清理旧歌词文件（失败仅记日志）
        if (StringUtils.hasText(oldPath)) {
            fileStorageService.delete(oldPath);
        }

        song.setLyricPath(stored.getRelativePath());
        log.info("歌词上传成功：songId={}, path={}", id, stored.getRelativePath());
        return toVO(song);
    }

    /**
     * 逐字歌词 JSON → 标准 LRC 规范化（仅管理员上传入口）。
     * <p>
     * 逐行判断：逐字 JSON 行（{@code {"t":ms,"c":[{"tx":".."}]}}）转 {@code [mm:ss.xx]文本}，
     * 标准 LRC 行原样保留——网易云下载的文件常见两种格式混合，不能整体判定。
     */
    private MultipartFile normalizeLyric(MultipartFile file) {
        try {
            String raw = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (!raw.contains("\"t\"") || !raw.contains("\"c\"")) {
                return file;
            }
            String normalized = LyricConvertUtils.normalize(raw);
            if (!StringUtils.hasText(normalized) || normalized.equals(raw)) {
                return file;
            }
            String name = StringUtils.hasText(file.getOriginalFilename())
                    ? file.getOriginalFilename() : "lyric.lrc";
            log.info("歌词为逐字 JSON 格式，已转换为标准 LRC：{}", name);
            return new InMemoryMultipartFile(
                    "file", name, "text/plain", normalized.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("歌词格式规范化失败，按原文件处理：{}", e.getMessage());
            return file;
        }
    }

    @Override
    public String getLyricContent(Long id) {
        Song song = songMapper.selectById(id);
        if (song == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        if (!StringUtils.hasText(song.getLyricPath())) {
            return "";
        }
        Resource resource = fileStorageService.loadAsResource(song.getLyricPath());
        try (InputStream in = resource.getInputStream()) {
            String content = IoUtil.read(in, StandardCharsets.UTF_8);
            // 去除 UTF-8 BOM：部分编辑器保存的 LRC 文件带 BOM，会导致首行时间标签解析失败
            return content.startsWith(BOM) ? content.substring(1) : content;
        } catch (IOException e) {
            log.warn("歌词读取失败：songId={}, path={}", id, song.getLyricPath(), e);
            throw new BusinessException(ResultCode.FILE_MISSING, "歌词文件读取失败");
        }
    }

    @Override
    public SongVO uploadCover(Long id, MultipartFile file) {
        Song song = songMapper.selectById(id);
        if (song == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }

        // 1. 先落盘新封面（失败则原封面不受影响）
        FileStorageService.StoredFile stored = fileStorageService.storeCover(file);
        String oldPath = song.getCoverPath();

        // 2. 更新数据库；失败则删除刚落盘的文件（补偿回滚）
        Song update = new Song();
        update.setId(id);
        update.setCoverPath(stored.getRelativePath());
        try {
            songMapper.updateById(update);
        } catch (Exception e) {
            fileStorageService.delete(stored.getRelativePath());
            log.error("封面记录更新失败：songId={}", id, e);
            throw new BusinessException(ResultCode.FILE_WRITE_FAILED, "封面保存失败，请重试");
        }

        // 3. 更新成功后再清理旧封面文件（失败仅记日志）
        if (StringUtils.hasText(oldPath)) {
            fileStorageService.delete(oldPath);
        }

        song.setCoverPath(stored.getRelativePath());
        log.info("封面上传成功：songId={}, path={}", id, stored.getRelativePath());
        return toVO(song);
    }

    @Override
    public Resource getCoverResource(Long id) {
        Song song = songMapper.selectById(id);
        if (song == null) {
            return null;
        }
        if (!StringUtils.hasText(song.getCoverPath())) {
            return null;
        }
        try {
            return fileStorageService.loadAsResource(song.getCoverPath());
        } catch (BusinessException e) {
            // 记录存在但文件已丢失：按无封面处理，避免前端收到 500
            log.warn("封面文件缺失：songId={}, path={}", id, song.getCoverPath());
            return null;
        }
    }

    /* ==================== 内部工具 ==================== */

    /**
     * 应用排序：字段必须命中白名单，否则回退默认排序
     */
    private void applySort(LambdaQueryWrapper<Song> wrapper, String sort) {
        String field = null;
        boolean asc = false;

        if (StringUtils.hasText(sort)) {
            String[] parts = sort.split(",");
            if (parts.length > 0) {
                // 白名单校验，防止 order by 注入
                field = SortWhitelist.resolve(parts[0]);
            }
            if (parts.length > 1) {
                asc = "asc".equalsIgnoreCase(parts[1].trim());
            }
        }

        SFunction<Song, ?> column = field == null ? null : SORT_MAPPING.get(field);
        if (column == null) {
            column = SORT_MAPPING.get(SortWhitelist.DEFAULT_SORT_FIELD);
            asc = SortWhitelist.DEFAULT_ASC;
        }

        wrapper.orderBy(true, asc, column);
        // 稳定排序：同值时按 id 倒序，避免分页抖动
        wrapper.orderByDesc(Song::getId);
    }

    /**
     * 批量转换 VO，并一次性填充上传者名称（避免 N+1 查询）
     */
    @Override
    public List<SongVO> convertToVO(List<Song> songs) {
        return toVOList(songs);
    }

    private List<SongVO> toVOList(List<Song> songs) {
        if (CollectionUtils.isEmpty(songs)) {
            return Collections.emptyList();
        }

        Set<Long> uploaderIds = songs.stream()
                .map(Song::getUploaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> uploaderNameMap = new HashMap<>(uploaderIds.size());
        if (!uploaderIds.isEmpty()) {
            List<User> uploaders = userMapper.selectBatchIds(uploaderIds);
            for (User user : uploaders) {
                uploaderNameMap.put(user.getId(), displayName(user));
            }
        }

        return songs.stream().map(song -> {
            SongVO vo = SongVO.of(song);
            if (vo != null) {
                vo.setUploaderName(uploaderNameMap.get(song.getUploaderId()));
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private SongVO toVO(Song song) {
        List<SongVO> list = toVOList(Collections.singletonList(song));
        return list.isEmpty() ? null : list.get(0);
    }

    private String displayName(User user) {
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private Song findOneByMd5(String md5) {
        return songMapper.selectOne(new LambdaQueryWrapper<Song>()
                .eq(Song::getFileMd5, md5)
                .last("LIMIT 1"));
    }

    /**
     * 标签规范化：按逗号/中文逗号/顿号切分，trim、去空、去重、单个截断 20 字，
     * 最多保留 3 个（多写的丢弃），返回逗号拼接串；无有效标签返回 null。
     */
    private String normalizeTags(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String t : raw.split("[,，、]")) {
            String v = t.trim();
            if (v.isEmpty()) continue;
            if (v.length() > 20) v = v.substring(0, 20);
            set.add(v);
            if (set.size() >= 3) break;
        }
        return set.isEmpty() ? null : String.join(",", set);
    }

    private String normalizeMd5(String md5) {
        if (!StringUtils.hasText(md5)) {
            return null;
        }
        String value = md5.trim().toLowerCase(Locale.ROOT);
        return value.matches(MD5_PATTERN) ? value : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String baseName(String fileName) {
        if (fileName == null) {
            return null;
        }
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private void evictSongPageCache() {
        try {
            java.util.Set<String> keys = redis.keys("songs:page:*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception ignore) {
        }
    }
}
