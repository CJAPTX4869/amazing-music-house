package com.example.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.ResultCode;
import com.example.music.dto.UserQueryDTO;
import com.example.music.entity.CommentLike;
import com.example.music.entity.DownloadRecord;
import com.example.music.entity.PlayHistory;
import com.example.music.entity.Playlist;
import com.example.music.entity.PlaylistCollect;
import com.example.music.entity.PlaylistSong;
import com.example.music.entity.Song;
import com.example.music.entity.SongComment;
import com.example.music.entity.User;
import com.example.music.entity.UserFollow;
import com.example.music.mapper.CommentLikeMapper;
import com.example.music.mapper.DownloadRecordMapper;
import com.example.music.mapper.PlayHistoryMapper;
import com.example.music.mapper.PlaylistCollectMapper;
import com.example.music.mapper.PlaylistMapper;
import com.example.music.mapper.PlaylistSongMapper;
import com.example.music.mapper.SongCommentMapper;
import com.example.music.mapper.SongMapper;
import com.example.music.mapper.UserFollowMapper;
import com.example.music.mapper.UserMapper;
import com.example.music.service.AdminService;
import com.example.music.service.FileStorageService;
import com.example.music.service.SongService;
import com.example.music.utils.FormatUtils;
import com.example.music.vo.PageVO;
import com.example.music.vo.StatisticVO;
import com.example.music.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理端服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final int STATUS_ENABLED = 1;
    private static final int ROLE_ADMIN = 1;

    private final UserMapper userMapper;
    private final SongMapper songMapper;
    private final SongService songService;
    private final FileStorageService fileStorageService;
    private final SongCommentMapper songCommentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final PlayHistoryMapper playHistoryMapper;
    private final DownloadRecordMapper downloadRecordMapper;
    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final PlaylistCollectMapper playlistCollectMapper;
    private final UserFollowMapper userFollowMapper;

    @Override
    public PageVO<UserVO> pageUsers(UserQueryDTO query) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword));
        }
        wrapper.eq(query.getRole() != null, User::getRole, query.getRole());
        wrapper.eq(query.getStatus() != null, User::getStatus, query.getStatus());
        wrapper.orderByDesc(User::getCreateTime).orderByDesc(User::getId);

        Page<User> page = userMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        return PageVO.of(page, UserVO::of);
    }

    @Override
    public void updateUserStatus(Long userId, Integer status, Long currentAdminId) {
        if (userId == null || status == null || (status != 0 && status != STATUS_ENABLED)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态值不合法");
        }
        // 管理员自保护：禁止禁用自己，避免把自己锁死
        if (userId.equals(currentAdminId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能操作自己的账号");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (Objects.equals(user.getStatus(), status)) {
            return;
        }

        User update = new User();
        update.setId(userId);
        update.setStatus(status);
        userMapper.updateById(update);
        log.info("用户状态已变更：userId={}, status={}, 操作人={}", userId, status, currentAdminId);
    }

    @Override
    public void updateSongStatus(Long songId, Integer status) {
        songService.updateStatus(songId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId, Long currentAdminId) {
        if (userId == null || userId.equals(currentAdminId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能删除自己的账号");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (user.getRole() != null && user.getRole() == ROLE_ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能删除管理员账号");
        }

        // 先记下该用户的评论 id：删评论后，他人对这些评论的点赞也要清掉
        List<Long> commentIds = songCommentMapper.selectList(
                        new LambdaQueryWrapper<SongComment>().select(SongComment::getId).eq(SongComment::getUserId, userId))
                .stream().map(SongComment::getId).collect(Collectors.toList());
        if (!commentIds.isEmpty()) {
            commentLikeMapper.delete(new LambdaQueryWrapper<CommentLike>().in(CommentLike::getCommentId, commentIds));
        }
        songCommentMapper.delete(new LambdaQueryWrapper<SongComment>().eq(SongComment::getUserId, userId));
        commentLikeMapper.delete(new LambdaQueryWrapper<CommentLike>().eq(CommentLike::getUserId, userId));

        // 该用户创建的歌单：连带歌单-歌曲关系一起删
        List<Long> playlistIds = playlistMapper.selectList(
                        new LambdaQueryWrapper<Playlist>().select(Playlist::getId).eq(Playlist::getUserId, userId))
                .stream().map(Playlist::getId).collect(Collectors.toList());
        if (!playlistIds.isEmpty()) {
            playlistSongMapper.delete(new LambdaQueryWrapper<PlaylistSong>().in(PlaylistSong::getPlaylistId, playlistIds));
            playlistCollectMapper.delete(new LambdaQueryWrapper<PlaylistCollect>().in(PlaylistCollect::getPlaylistId, playlistIds));
            playlistMapper.deleteBatchIds(playlistIds);
        }

        playHistoryMapper.delete(new LambdaQueryWrapper<PlayHistory>().eq(PlayHistory::getUserId, userId));
        downloadRecordMapper.delete(new LambdaQueryWrapper<DownloadRecord>().eq(DownloadRecord::getUserId, userId));
        playlistCollectMapper.delete(new LambdaQueryWrapper<PlaylistCollect>().eq(PlaylistCollect::getUserId, userId));
        // 关注关系：他关注别人的、别人关注他的都要清
        userFollowMapper.delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getRequesterId, userId).or().eq(UserFollow::getTargetId, userId));

        userMapper.deleteById(userId);
        log.info("用户已删除：userId={}, username={}, 操作人={}", userId, user.getUsername(), currentAdminId);
    }

    @Override
    public StatisticVO statistics() {
        StatisticVO vo = new StatisticVO();

        vo.setSongCount(songMapper.selectCount(null));
        vo.setOnlineSongCount(songMapper.selectCount(
                new LambdaQueryWrapper<Song>().eq(Song::getStatus, 1)));
        vo.setUserCount(userMapper.selectCount(null));
        vo.setTotalDownload(nullSafe(songMapper.sumDownloadCount()));
        vo.setTotalPlay(nullSafe(songMapper.sumPlayCount()));

        long total = fileStorageService.getTotalSpace();
        long usable = fileStorageService.getUsableSpace();
        long used = total > 0 ? Math.max(total - usable, 0) : 0;
        vo.setDiskUsed(used);
        vo.setDiskTotal(total);
        vo.setDiskUsedText(FormatUtils.fileSize(used) + " / " + FormatUtils.fileSize(total));

        return vo;
    }

    private long nullSafe(Long value) {
        return value == null ? 0L : value;
    }
}
