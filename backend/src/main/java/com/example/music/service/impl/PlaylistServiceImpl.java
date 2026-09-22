package com.example.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.ResultCode;
import com.example.music.dto.PlaylistSaveDTO;
import com.example.music.entity.Playlist;
import com.example.music.entity.PlaylistCollect;
import com.example.music.entity.PlaylistSong;
import com.example.music.entity.Song;
import com.example.music.entity.User;
import com.example.music.mapper.PlaylistCollectMapper;
import com.example.music.mapper.PlaylistMapper;
import com.example.music.mapper.PlaylistSongMapper;
import com.example.music.mapper.SongMapper;
import com.example.music.mapper.UserMapper;
import com.example.music.security.UserContext;
import com.example.music.service.FileStorageService;
import com.example.music.service.PlaylistService;
import com.example.music.vo.PlaylistVO;
import com.example.music.vo.PageVO;
import com.example.music.vo.SongVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 歌单服务实现
 * <p>
 * 设计要点：
 * 1. 所有写操作都校验歌单归属，越权返回 403；
 * 2. 添加歌曲依赖 uk_playlist_song 唯一约束兜底并发重复插入；
 * 3. "我喜欢的音乐"是 type=1 的特殊歌单，每个用户唯一，首次收藏时自动创建，且不可删除/重命名。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistServiceImpl implements PlaylistService {

    /** 歌单类型：普通歌单 */
    private static final int TYPE_NORMAL = 0;
    /** 歌单类型：我喜欢的音乐 */
    private static final int TYPE_FAVORITE = 1;
    /** 状态：公开 */
    private static final int STATUS_PUBLIC = 1;
    /** 歌曲状态：上架 */
    private static final int SONG_STATUS_ONLINE = 1;
    private static final String FAVORITE_NAME = "我喜欢的音乐";

    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final PlaylistCollectMapper playlistCollectMapper;
    private final SongMapper songMapper;
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;

    /* ==================== 歌单 CRUD ==================== */

    @Override
    public PlaylistVO create(PlaylistSaveDTO dto, Long userId) {
        Playlist playlist = new Playlist();
        playlist.setName(dto.getName().trim());
        playlist.setDescription(trimToNull(dto.getDescription()));
        playlist.setUserId(userId);
        playlist.setType(TYPE_NORMAL);
        playlist.setSongCount(0);
        // 隐私设置：前端传 privacy=1 创建为私密歌单（V3.1 起支持，此前被忽略）
        playlist.setStatus(Integer.valueOf(1).equals(dto.getPrivacy()) ? 0 : STATUS_PUBLIC);
        playlistMapper.insert(playlist);

        log.info("创建歌单成功：id={}, name={}, userId={}", playlist.getId(), playlist.getName(), userId);
        return toVO(playlist);
    }

    @Override
    public List<PlaylistVO> myList(Long userId) {
        List<Playlist> list = playlistMapper.selectList(new LambdaQueryWrapper<Playlist>()
                .eq(Playlist::getUserId, userId)
                // "我喜欢的音乐"（type=1）排最前，其余按创建时间倒序
                .orderByDesc(Playlist::getType)
                .orderByDesc(Playlist::getCreateTime));
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public PlaylistVO detail(Long id, Long userId) {
        Playlist playlist = getOwnedPlaylist(id, userId);
        PlaylistVO vo = toVO(playlist);
        vo.setSongs(loadSongs(id));
        return vo;
    }

    @Override
    public PlaylistVO detailPublic(Long id) {
        Playlist playlist = playlistMapper.selectById(id);
        if (playlist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }
        // 安全修复：私有歌单（status=0）只对本人与管理员可见。
        // 原实现完全不校验 status，匿名者可凭 id 直接读取他人私密歌单的名称/描述/歌曲
        // （/api/playlist/detail、/api/playlist/track/all 均走这里）。
        boolean isPrivate = playlist.getStatus() != null && playlist.getStatus() == 0;
        if (isPrivate) {
            Long viewerId = UserContext.isLogin()
                    ? UserContext.getUserId()
                    : null;
            boolean isOwner = viewerId != null && viewerId.equals(playlist.getUserId());
            if (!isOwner && !UserContext.isAdmin()) {
                throw new BusinessException(ResultCode.FORBIDDEN, "该歌单为私密歌单");
            }
        }
        PlaylistVO vo = toVO(playlist);
        vo.setSongs(loadSongs(id));
        return vo;
    }

    @Override
    public List<PlaylistVO> listPublic(int limit) {
        // 限制条数上限，避免 LIMIT 被滥用（取值已收敛为 1–50，无注入风险）
        int size = Math.max(1, Math.min(limit, 50));
        // 排除 type=1「我喜欢的音乐」：它是每个用户首次收藏时由系统自动创建的私有歌单，
        // 若不排除，用户越多推荐位出现的重名「我喜欢的音乐」越多
        List<Playlist> list = playlistMapper.selectList(new LambdaQueryWrapper<Playlist>()
                .eq(Playlist::getStatus, STATUS_PUBLIC)
                .eq(Playlist::getType, TYPE_NORMAL)
                .orderByDesc(Playlist::getCreateTime)
                .last("LIMIT " + size));
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public PlaylistVO update(Long id, PlaylistSaveDTO dto, Long userId) {
        Playlist playlist = getOwnedPlaylist(id, userId);
        if (Integer.valueOf(TYPE_FAVORITE).equals(playlist.getType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "「我喜欢的音乐」不可重命名");
        }
        playlist.setName(dto.getName().trim());
        playlist.setDescription(trimToNull(dto.getDescription()));
        playlistMapper.updateById(playlist);

        log.info("编辑歌单成功：id={}, userId={}", id, userId);
        return toVO(playlist);
    }

    @Override
    public PlaylistVO uploadCover(Long id, MultipartFile file, Long userId) {
        Playlist playlist = getOwnedPlaylist(id, userId);
        // 特例：「我喜欢的音乐」是系统歌单，封面由最近收藏的歌曲自动继承，不提供手动修改
        if (Integer.valueOf(TYPE_FAVORITE).equals(playlist.getType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "「我喜欢的音乐」的封面由收藏的歌曲自动决定，无需手动设置");
        }

        // 1. 先落盘新封面（失败则原封面不受影响）
        FileStorageService.StoredFile stored = fileStorageService.storeCover(file);
        String oldPath = playlist.getCoverPath();

        // 2. 更新数据库；失败则删除刚落盘的文件（补偿回滚）
        Playlist update = new Playlist();
        update.setId(id);
        update.setCoverPath(stored.getRelativePath());
        try {
            playlistMapper.updateById(update);
        } catch (Exception e) {
            fileStorageService.delete(stored.getRelativePath());
            log.error("歌单封面记录更新失败：playlistId={}", id, e);
            throw new BusinessException(ResultCode.FILE_WRITE_FAILED, "封面保存失败，请重试");
        }

        // 3. 更新成功后再清理旧封面文件（失败仅记日志）
        if (StringUtils.hasText(oldPath)) {
            fileStorageService.delete(oldPath);
        }

        playlist.setCoverPath(stored.getRelativePath());
        log.info("歌单封面上传成功：playlistId={}, path={}", id, stored.getRelativePath());
        return toVO(playlist);
    }

    @Override
    public Resource getCoverResource(Long id) {
        Playlist playlist = playlistMapper.selectById(id);
        if (playlist == null || !StringUtils.hasText(playlist.getCoverPath())) {
            return null;
        }
        try {
            return fileStorageService.loadAsResource(playlist.getCoverPath());
        } catch (BusinessException e) {
            // 记录存在但文件已丢失：按无封面处理，避免前端收到 500
            log.warn("歌单封面文件缺失：playlistId={}, path={}", id, playlist.getCoverPath());
            return null;
        }
    }

    /* ==================== 用户主页与收藏歌单（V3.1） ==================== */

    @Override
    public Map<String, Object> userPlaylists(Long targetId, Long viewerId) {
        if (userMapper.selectById(targetId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        boolean isSelf = viewerId != null && viewerId.equals(targetId);

        // 创建的歌单（type=0）：他人仅可见公开歌单
        List<PlaylistVO> created = playlistMapper.selectList(new LambdaQueryWrapper<Playlist>()
                        .eq(Playlist::getUserId, targetId)
                        .eq(Playlist::getType, TYPE_NORMAL)
                        .eq(!isSelf, Playlist::getStatus, STATUS_PUBLIC)
                        .orderByDesc(Playlist::getCreateTime))
                .stream().map(this::toVO).collect(Collectors.toList());

        // 「我喜欢的音乐」（type=1）：他人仅可见公开
        Playlist favoritePlaylist = findFavorite(targetId);
        PlaylistVO favorite = (favoritePlaylist != null
                && (isSelf || Integer.valueOf(STATUS_PUBLIC).equals(favoritePlaylist.getStatus())))
                ? toVO(favoritePlaylist) : null;

        // 收藏的歌单：playlist_collect 关联 → 批量取歌单 → 过滤可见性（保收藏时间倒序）
        List<Long> collectIds = playlistCollectMapper.selectList(new LambdaQueryWrapper<PlaylistCollect>()
                        .eq(PlaylistCollect::getUserId, targetId)
                        .orderByDesc(PlaylistCollect::getCreateTime))
                .stream().map(PlaylistCollect::getPlaylistId).collect(Collectors.toList());
        Map<Long, Playlist> collectedMap = collectIds.isEmpty()
                ? Collections.emptyMap()
                : playlistMapper.selectBatchIds(collectIds).stream()
                        .collect(Collectors.toMap(Playlist::getId, p -> p, (a, b) -> a));
        List<PlaylistVO> collected = collectIds.stream()
                .map(collectedMap::get)
                .filter(Objects::nonNull)
                .filter(p -> isSelf || Integer.valueOf(STATUS_PUBLIC).equals(p.getStatus()))
                .map(this::toVO)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("created", created);
        result.put("favorite", favorite);
        result.put("collected", collected);
        return result;
    }

    @Override
    public PageVO<PlaylistVO> searchPublic(String keyword, int page, int size) {
        Page<Playlist> p = playlistMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                new LambdaQueryWrapper<Playlist>()
                        .eq(Playlist::getType, TYPE_NORMAL)
                        .eq(Playlist::getStatus, STATUS_PUBLIC)
                        .like(StringUtils.hasText(keyword), Playlist::getName, keyword)
                        .orderByDesc(Playlist::getCreateTime));
        return PageVO.of(p, this::toVO);
    }

    @Override
    public void collect(Long playlistId, Long userId) {
        Playlist playlist = playlistMapper.selectById(playlistId);
        if (playlist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }
        if (playlist.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能收藏自己的歌单");
        }
        if (!Integer.valueOf(STATUS_PUBLIC).equals(playlist.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "私密歌单无法收藏");
        }
        PlaylistCollect record = new PlaylistCollect();
        record.setUserId(userId);
        record.setPlaylistId(playlistId);
        try {
            playlistCollectMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 幂等：重复收藏视为成功
            return;
        }
        changeCollectCount(playlistId, 1);
        log.info("收藏歌单成功：playlistId={}, userId={}", playlistId, userId);
    }

    @Override
    public void uncollect(Long playlistId, Long userId) {
        int removed = playlistCollectMapper.delete(new LambdaQueryWrapper<PlaylistCollect>()
                .eq(PlaylistCollect::getUserId, userId)
                .eq(PlaylistCollect::getPlaylistId, playlistId));
        if (removed > 0) {
            changeCollectCount(playlistId, -1);
            log.info("取消收藏歌单：playlistId={}, userId={}", playlistId, userId);
        }
    }

    @Override
    public boolean isCollected(Long playlistId, Long userId) {
        Long count = playlistCollectMapper.selectCount(new LambdaQueryWrapper<PlaylistCollect>()
                .eq(PlaylistCollect::getUserId, userId)
                .eq(PlaylistCollect::getPlaylistId, playlistId));
        return count != null && count > 0;
    }

    /**
     * 歌单被收藏数原子增减（GREATEST 兜底防负数；delta 为内部受控值）
     */
    private void changeCollectCount(Long playlistId, int delta) {
        playlistMapper.update(null, new LambdaUpdateWrapper<Playlist>()
                .eq(Playlist::getId, playlistId)
                .setSql("collect_count = GREATEST(collect_count + (" + delta + "), 0)"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        Playlist playlist = getOwnedPlaylist(id, userId);
        if (Integer.valueOf(TYPE_FAVORITE).equals(playlist.getType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "「我喜欢的音乐」不可删除");
        }
        // 先清理关联记录，再删除歌单
        playlistSongMapper.delete(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, id));
        playlistMapper.deleteById(id);

        log.info("删除歌单成功：id={}, userId={}", id, userId);
    }

    /* ==================== 歌单内歌曲 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSong(Long id, Long songId, Long userId) {
        // 校验归属
        getOwnedPlaylist(id, userId);
        addSongInternal(id, songId);
        refreshSongCount(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSong(Long id, Long songId, Long userId) {
        getOwnedPlaylist(id, userId);
        playlistSongMapper.delete(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, id)
                .eq(PlaylistSong::getSongId, songId));
        refreshSongCount(id);

        log.info("从歌单移除歌曲：playlistId={}, songId={}, userId={}", id, songId, userId);
    }

    /* ==================== 我喜欢的音乐 ==================== */

    @Override
    public PlaylistVO favorite(Long songId, Long userId) {
        Playlist favorite = getOrCreateFavorite(userId);
        addSongInternal(favorite.getId(), songId);
        refreshSongCount(favorite.getId());
        // 特例：「我喜欢的音乐」封面继承最近一次收藏歌曲的专辑图
        refreshFavoriteCover(favorite.getId());
        // 维护歌曲喜欢数冗余计数（V3.1 列表角标）
        changeSongLikeCount(songId, 1);
        log.info("收藏歌曲：songId={}, userId={}", songId, userId);
        // 重新读取，返回刷新后的歌曲数量
        return toVO(playlistMapper.selectById(favorite.getId()));
    }

    @Override
    public void unfavorite(Long songId, Long userId) {
        Playlist favorite = findFavorite(userId);
        if (favorite == null) {
            return;
        }
        int removed = playlistSongMapper.delete(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, favorite.getId())
                .eq(PlaylistSong::getSongId, songId));
        refreshSongCount(favorite.getId());
        // 取消收藏后回退到上一首仍在歌单里的歌（歌单空则清空封面）
        refreshFavoriteCover(favorite.getId());
        if (removed > 0) {
            changeSongLikeCount(songId, -1);
        }
        log.info("取消收藏：songId={}, userId={}", songId, userId);
    }

    /**
     * 歌曲喜欢数原子增减（GREATEST 兜底防负数；delta 为内部受控值）
     */
    private void changeSongLikeCount(Long songId, int delta) {
        songMapper.update(null, new LambdaUpdateWrapper<Song>()
                .eq(Song::getId, songId)
                .setSql("like_count = GREATEST(like_count + (" + delta + "), 0)"));
    }

    @Override
    public PlaylistVO getFavoriteList(Long userId) {
        Playlist favorite = getOrCreateFavorite(userId);
        PlaylistVO vo = toVO(favorite);
        vo.setSongs(loadSongs(favorite.getId()));
        return vo;
    }

    @Override
    public boolean isFavorited(Long songId, Long userId) {
        Playlist favorite = findFavorite(userId);
        if (favorite == null) {
            return false;
        }
        return playlistSongMapper.selectCount(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, favorite.getId())
                .eq(PlaylistSong::getSongId, songId)) > 0;
    }

    /* ==================== 内部工具 ==================== */

    /**
     * 查询歌单并校验归属（越权抛 403）
     */
    private Playlist getOwnedPlaylist(Long id, Long userId) {
        Playlist playlist = playlistMapper.selectById(id);
        if (playlist == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }
        if (!Objects.equals(playlist.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作他人的歌单");
        }
        return playlist;
    }

    /**
     * 添加歌曲（不做归属校验，供收藏等内部场景复用）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderSongs(Long id, List<Long> songIds, Long userId) {
        // 仅歌单所有者可排序（我喜欢的音乐同理，归属当前用户）
        getOwnedPlaylist(id, userId);
        if (CollectionUtils.isEmpty(songIds)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "排序列表不能为空");
        }
        List<PlaylistSong> relations = playlistSongMapper.selectList(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, id));
        Set<Long> current = relations.stream().map(PlaylistSong::getSongId).collect(Collectors.toSet());
        // 必须是当前歌曲的完整排列：防止漏传/多传导致丢歌或塞入其它歌单的歌
        if (current.size() != songIds.size() || !current.containsAll(songIds)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "排序列表与歌单内歌曲不一致，请刷新后重试");
        }
        for (int i = 0; i < songIds.size(); i++) {
            final int order = i + 1;
            playlistSongMapper.update(null, new LambdaUpdateWrapper<PlaylistSong>()
                    .eq(PlaylistSong::getPlaylistId, id)
                    .eq(PlaylistSong::getSongId, songIds.get(i))
                    .set(PlaylistSong::getSortOrder, order));
        }
        log.info("歌单自定义排序已保存：playlistId={}, count={}", id, songIds.size());
    }
    private void addSongInternal(Long playlistId, Long songId) {
        Song song = songMapper.selectById(songId);
        if (song == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        if (!Integer.valueOf(SONG_STATUS_ONLINE).equals(song.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该歌曲已下架，无法加入歌单");
        }

        PlaylistSong relation = new PlaylistSong();
        relation.setPlaylistId(playlistId);
        relation.setSongId(songId);
        // 排到末尾：取当前最大 sort_order + 1（未自定义排序时全为 0，效果等同按加入顺序）
        PlaylistSong last = playlistSongMapper.selectOne(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, playlistId)
                .orderByDesc(PlaylistSong::getSortOrder)
                .orderByDesc(PlaylistSong::getId)
                .last("LIMIT 1"));
        relation.setSortOrder(last == null || last.getSortOrder() == null ? 1 : last.getSortOrder() + 1);
        try {
            playlistSongMapper.insert(relation);
        } catch (DuplicateKeyException e) {
            // 并发兜底：唯一约束 uk_playlist_song
            throw new BusinessException(ResultCode.PARAM_ERROR, "该歌曲已在歌单中");
        }
    }

    /**
     * 重算并更新歌单的歌曲数量
     */
    private void refreshSongCount(Long playlistId) {
        Long count = playlistSongMapper.selectCount(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, playlistId));
        Playlist update = new Playlist();
        update.setId(playlistId);
        update.setSongCount(count == null ? 0 : count.intValue());
        playlistMapper.updateById(update);
    }

    /**
     * 加载歌单内的歌曲（按加入顺序）
     */
    private List<SongVO> loadSongs(Long playlistId) {
        List<Long> songIds = playlistSongMapper.selectSongIdsByPlaylistId(playlistId);
        if (CollectionUtils.isEmpty(songIds)) {
            return Collections.emptyList();
        }
        List<Song> songs = songMapper.selectBatchIds(songIds);
        if (CollectionUtils.isEmpty(songs)) {
            return Collections.emptyList();
        }

        // 批量查询上传者昵称，避免 N+1
        Set<Long> uploaderIds = songs.stream().map(Song::getUploaderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNameMap = new HashMap<>(uploaderIds.size());
        if (!uploaderIds.isEmpty()) {
            for (User user : userMapper.selectBatchIds(uploaderIds)) {
                userNameMap.put(user.getId(), user.getNickname() != null ? user.getNickname() : user.getUsername());
            }
        }

        // 按歌单内的顺序输出
        Map<Long, Song> songMap = songs.stream().collect(Collectors.toMap(Song::getId, s -> s, (a, b) -> a));
        List<SongVO> result = new ArrayList<>(songs.size());
        for (Long songId : songIds) {
            Song song = songMap.get(songId);
            if (song == null) {
                continue;
            }
            SongVO vo = SongVO.of(song);
            if (vo != null) {
                vo.setUploaderName(userNameMap.get(song.getUploaderId()));
                result.add(vo);
            }
        }
        return result;
    }

    private Playlist findFavorite(Long userId) {
        return playlistMapper.selectOne(new LambdaQueryWrapper<Playlist>()
                .eq(Playlist::getUserId, userId)
                .eq(Playlist::getType, TYPE_FAVORITE)
                .last("LIMIT 1"));
    }

    /**
     * 维护「我喜欢的音乐」的封面
     * <p>
     * 规则（用户指定的特例）：封面始终等于"最近一次收藏的歌曲"的专辑图；
     * 取消收藏后自动回退到上一首仍在歌单里的歌；歌单为空则清空（前端显示"暂无封面"）。
     * 该歌单不提供手动改封面（前端隐藏入口，后端 updateCover 也拒绝）。
     */
    private void refreshFavoriteCover(Long favoritePlaylistId) {
        PlaylistSong latest = playlistSongMapper.selectOne(new LambdaQueryWrapper<PlaylistSong>()
                // 用自增 id 倒序取"最近加入"的那条，不依赖 create_time 字段是否存在
                .eq(PlaylistSong::getPlaylistId, favoritePlaylistId)
                .orderByDesc(PlaylistSong::getId)
                .last("LIMIT 1"));
        String coverPath = null;
        if (latest != null) {
            Song latestSong = songMapper.selectById(latest.getSongId());
            if (latestSong != null) {
                coverPath = latestSong.getCoverPath();
            }
        }
        playlistMapper.update(null, new LambdaUpdateWrapper<Playlist>()
                .eq(Playlist::getId, favoritePlaylistId)
                .set(Playlist::getCoverPath, coverPath));
        log.info("「我喜欢的音乐」封面已刷新：playlistId={}, coverPath={}", favoritePlaylistId, coverPath);
    }
    private Playlist getOrCreateFavorite(Long userId) {
        Playlist favorite = findFavorite(userId);
        if (favorite != null) {
            return favorite;
        }
        Playlist playlist = new Playlist();
        playlist.setName(FAVORITE_NAME);
        playlist.setUserId(userId);
        playlist.setType(TYPE_FAVORITE);
        playlist.setSongCount(0);
        playlist.setStatus(STATUS_PUBLIC);
        playlistMapper.insert(playlist);
        log.info("自动创建「我喜欢的音乐」歌单：id={}, userId={}", playlist.getId(), userId);
        return playlist;
    }

    private PlaylistVO toVO(Playlist playlist) {
        PlaylistVO vo = PlaylistVO.of(playlist);
        if (vo != null && playlist.getUserId() != null) {
            User user = userMapper.selectById(playlist.getUserId());
            if (user != null) {
                vo.setUserName(user.getNickname() != null ? user.getNickname() : user.getUsername());
            }
        }
        return vo;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
