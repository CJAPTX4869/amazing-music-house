package com.example.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.ResultCode;
import com.example.music.dto.CommentCreateDTO;
import com.example.music.dto.ReplyCreateDTO;
import com.example.music.entity.CommentLike;
import com.example.music.entity.Song;
import com.example.music.entity.SongComment;
import com.example.music.entity.User;
import com.example.music.mapper.CommentLikeMapper;
import com.example.music.mapper.SongCommentMapper;
import com.example.music.mapper.SongMapper;
import com.example.music.mapper.UserMapper;
import com.example.music.security.AdminAccess;
import com.example.music.security.UserContext;
import com.example.music.service.CommentService;
import com.example.music.service.FileStorageService;
import com.example.music.vo.CommentUserVO;
import com.example.music.vo.CommentVO;
import com.example.music.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 歌曲评论服务实现
 * <p>
 * 设计要点：
 * 1. 楼中楼回复统一挂到根评论（parent_id 恒指根），显示平铺两层，无递归查询；
 * 2. 歌曲评论数（含回复）以原子 SQL 自增/自减维护，GREATEST 兜底防止负数；
 * 3. 列表组装全程批量查询（用户、点赞状态），避免 N+1；
 * 4. 删除权限：本人或管理员；删根评论连带隐藏整楼（逻辑删除）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    /** 歌曲状态：上架（仅上架歌曲可评论） */
    private static final int SONG_STATUS_ONLINE = 1;

    /** 单页上限（与分页插件 maxLimit 保持一致） */
    private static final int MAX_PAGE_SIZE = 50;

    private final SongCommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final SongMapper songMapper;
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;

    @Override
    public PageVO<CommentVO> page(Long songId, int page, int size, String sort, Long viewerId) {
        LambdaQueryWrapper<SongComment> rootQuery = new LambdaQueryWrapper<SongComment>()
                .eq(SongComment::getSongId, songId)
                .isNull(SongComment::getParentId);
        if ("hot".equalsIgnoreCase(sort)) {
            rootQuery.orderByDesc(SongComment::getLikeCount)
                    .orderByDesc(SongComment::getCreateTime);
        } else {
            rootQuery.orderByDesc(SongComment::getCreateTime);
        }
        Page<SongComment> p = commentMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)), rootQuery);

        List<SongComment> roots = p.getRecords();
        Map<Long, List<SongComment>> replyMap = groupRepliesByRoot(roots);
        Map<Long, User> userMap = loadUsers(collectUserIds(roots, replyMap));
        Set<Long> likedIds = loadLikedIds(viewerId, roots, replyMap);

        return PageVO.of(p, root -> toVO(root,
                replyMap.getOrDefault(root.getId(), Collections.emptyList()),
                userMap, likedIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO create(Long songId, CommentCreateDTO dto, Long userId) {
        Song song = songMapper.selectById(songId);
        if (song == null || !Integer.valueOf(SONG_STATUS_ONLINE).equals(song.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在或已下架");
        }
        SongComment comment = new SongComment();
        comment.setSongId(songId);
        comment.setUserId(userId);
        comment.setContent(dto.getContent().trim());
        comment.setImagePath(validateImagePath(dto.getImagePath()));
        comment.setLikeCount(0);
        commentMapper.insert(comment);
        changeSongCommentCount(songId, 1);

        log.info("发表评论成功：songId={}, userId={}, commentId={}", songId, userId, comment.getId());
        return toVO(comment, Collections.emptyList(), loadUsers(List.of(userId)), Collections.emptySet());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO reply(Long commentId, ReplyCreateDTO dto, Long userId) {
        SongComment parent = commentMapper.selectById(commentId);
        if (parent == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在或已删除");
        }
        // 回复统一挂到所属根评论（对根评论回复时 parent 即为根）
        Long rootId = parent.getParentId() == null ? parent.getId() : parent.getParentId();
        // 被回复人：前端未指定时默认为被回复评论的作者
        Long replyUserId = dto.getReplyUserId() != null ? dto.getReplyUserId() : parent.getUserId();
        if (userMapper.selectById(replyUserId) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "回复目标用户不存在");
        }

        SongComment reply = new SongComment();
        reply.setSongId(parent.getSongId());
        reply.setUserId(userId);
        reply.setParentId(rootId);
        reply.setReplyUserId(replyUserId);
        reply.setContent(dto.getContent().trim());
        reply.setLikeCount(0);
        commentMapper.insert(reply);
        changeSongCommentCount(parent.getSongId(), 1);

        log.info("回复评论成功：rootId={}, userId={}, replyId={}", rootId, userId, reply.getId());
        CommentVO vo = new CommentVO();
        vo.setId(reply.getId());
        vo.setContent(reply.getContent());
        // 必须带上 createTime：前端把本方法返回的对象直接 append 进回复列表，
        // 缺了它会让 <n-time :time="Date.parse(null)"> 收到 NaN，date-fns 抛 RangeError
        vo.setCreateTime(reply.getCreateTime());
        vo.setLikeCount(0);
        vo.setLiked(false);
        vo.setUser(CommentUserVO.of(userId, userNickname(userId)));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        SongComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在或已删除");
        }
        boolean owner = userId != null && userId.equals(comment.getUserId());
        if (!owner && !AdminAccess.isLocalAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能删除自己的评论");
        }

        List<Long> deadIds = new ArrayList<>();
        deadIds.add(id);
        if (comment.getParentId() == null) {
            // 根评论：连带隐藏整楼回复，先取到回复 id 才能一并清掉它们的点赞
            List<Long> replyIds = commentMapper.selectList(new LambdaQueryWrapper<SongComment>()
                            .select(SongComment::getId)
                            .eq(SongComment::getParentId, id))
                    .stream().map(SongComment::getId).collect(Collectors.toList());
            commentMapper.delete(new LambdaQueryWrapper<SongComment>()
                    .eq(SongComment::getParentId, id));
            deadIds.addAll(replyIds);
        }
        commentMapper.deleteById(id);
        // comment_like 是物理表且无外键级联，不清理就会永久累积指向已删评论的孤儿点赞行
        commentLikeMapper.delete(new LambdaQueryWrapper<CommentLike>()
                .in(CommentLike::getCommentId, deadIds));
        changeSongCommentCount(comment.getSongId(), -(long) deadIds.size());

        log.info("删除评论成功：id={}, by={}, 连带 {} 条", id, userId, deadIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void like(Long id, Long userId) {
        if (commentMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在或已删除");
        }
        CommentLike like = new CommentLike();
        like.setCommentId(id);
        like.setUserId(userId);
        try {
            commentLikeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            // 幂等：重复点赞视为成功
            return;
        }
        changeLikeCount(id, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlike(Long id, Long userId) {
        int deleted = commentLikeMapper.delete(new LambdaQueryWrapper<CommentLike>()
                .eq(CommentLike::getCommentId, id)
                .eq(CommentLike::getUserId, userId));
        if (deleted > 0) {
            changeLikeCount(id, -1);
        }
    }

    @Override
    public Resource getImageResource(Long id) {
        SongComment comment = commentMapper.selectById(id);
        if (comment == null || !StringUtils.hasText(comment.getImagePath())) {
            return null;
        }
        try {
            return fileStorageService.loadAsResource(comment.getImagePath());
        } catch (BusinessException e) {
            // 记录存在但文件已丢失：按无配图处理，避免前端收到 500
            log.warn("评论配图文件缺失：commentId={}, path={}", id, comment.getImagePath());
            return null;
        }
    }

    /* ==================== 内部工具 ==================== */

    /**
     * 组装单条评论 VO（user 映射与点赞状态由调用方批量预取传入）
     */
    private CommentVO toVO(SongComment comment, List<SongComment> replies,
                           Map<Long, User> userMap, Set<Long> likedIds) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setContent(comment.getContent());
        vo.setImageUrl(StringUtils.hasText(comment.getImagePath())
                ? "/comments/" + comment.getId() + "/image"
                : null);
        vo.setLikeCount(comment.getLikeCount() == null ? 0 : comment.getLikeCount());
        vo.setLiked(likedIds.contains(comment.getId()));
        vo.setCreateTime(comment.getCreateTime());

        User author = userMap.get(comment.getUserId());
        vo.setUser(CommentUserVO.of(comment.getUserId(),
                author == null ? "" : (author.getNickname() != null ? author.getNickname() : author.getUsername())));

        if (comment.getReplyUserId() != null) {
            User replyTo = userMap.get(comment.getReplyUserId());
            vo.setReplyUserNickname(replyTo == null ? ""
                    : (replyTo.getNickname() != null ? replyTo.getNickname() : replyTo.getUsername()));
        }

        if (replies != null && !replies.isEmpty()) {
            // 回复内部不再有下级（parent_id 恒指根），传空列表避免递归
            vo.setReplies(replies.stream()
                    .map(r -> toVO(r, Collections.emptyList(), userMap, likedIds))
                    .collect(Collectors.toList()));
        }
        return vo;
    }

    /**
     * 楼中楼回复按根评论分组（时间正序展示）
     */
    private Map<Long, List<SongComment>> groupRepliesByRoot(List<SongComment> roots) {
        if (CollectionUtils.isEmpty(roots)) {
            return Collections.emptyMap();
        }
        List<Long> rootIds = roots.stream().map(SongComment::getId).collect(Collectors.toList());
        List<SongComment> replies = commentMapper.selectList(new LambdaQueryWrapper<SongComment>()
                .in(SongComment::getParentId, rootIds)
                .orderByAsc(SongComment::getCreateTime));
        return replies.stream().collect(Collectors.groupingBy(SongComment::getParentId));
    }

    /**
     * 批量收集并查询涉及用户（评论人 + 被回复人），避免 N+1
     */
    private Map<Long, User> loadUsers(Collection<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private Set<Long> collectUserIds(List<SongComment> roots, Map<Long, List<SongComment>> replyMap) {
        Set<Long> ids = new HashSet<>();
        for (SongComment c : roots) {
            ids.add(c.getUserId());
            if (c.getReplyUserId() != null) {
                ids.add(c.getReplyUserId());
            }
            List<SongComment> replies = replyMap.get(c.getId());
            if (!CollectionUtils.isEmpty(replies)) {
                for (SongComment r : replies) {
                    ids.add(r.getUserId());
                    if (r.getReplyUserId() != null) {
                        ids.add(r.getReplyUserId());
                    }
                }
            }
        }
        return ids;
    }

    /**
     * 批量查询当前登录用户对这批评论的点赞状态（匿名时为空集合）
     */
    private Set<Long> loadLikedIds(Long viewerId, List<SongComment> roots,
                                   Map<Long, List<SongComment>> replyMap) {
        if (viewerId == null || CollectionUtils.isEmpty(roots)) {
            return Collections.emptySet();
        }
        List<Long> allIds = new ArrayList<>();
        for (SongComment c : roots) {
            allIds.add(c.getId());
            List<SongComment> replies = replyMap.get(c.getId());
            if (!CollectionUtils.isEmpty(replies)) {
                replies.forEach(r -> allIds.add(r.getId()));
            }
        }
        return commentLikeMapper.selectList(new LambdaQueryWrapper<CommentLike>()
                        .eq(CommentLike::getUserId, viewerId)
                        .in(CommentLike::getCommentId, allIds))
                .stream().map(CommentLike::getCommentId).collect(Collectors.toSet());
    }

    /**
     * 歌曲评论数原子增减（GREATEST 兜底防负数；delta 为内部受控值，无注入面）
     */
    private void changeSongCommentCount(Long songId, long delta) {
        songMapper.update(null, new LambdaUpdateWrapper<Song>()
                .eq(Song::getId, songId)
                .setSql("comment_count = GREATEST(comment_count + (" + delta + "), 0)"));
    }

    private void changeLikeCount(Long commentId, int delta) {
        commentMapper.update(null, new LambdaUpdateWrapper<SongComment>()
                .eq(SongComment::getId, commentId)
                .setSql("like_count = GREATEST(like_count + (" + delta + "), 0)"));
    }

    private String userNickname(Long userId) {
        if (userId == null) {
            return "";
        }
        User user = userMapper.selectById(userId);
        return user == null ? "" : (user.getNickname() != null ? user.getNickname() : user.getUsername());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 配图路径校验：必须来自评论配图目录（POST /comments/image 的返回值）。
     * 防止伪造 imagePath 指向 audio/cover 等其它存储文件，
     * 经 GET /comments/{id}/image 被越权直出。
     */
    private String validateImagePath(String imagePath) {
        String path = trimToNull(imagePath);
        if (path == null) {
            return null;
        }
        if (!path.startsWith("comment/")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "配图路径非法");
        }
        return path;
    }
}
