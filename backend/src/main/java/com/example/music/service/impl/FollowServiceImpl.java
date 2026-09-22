package com.example.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.ResultCode;
import com.example.music.entity.User;
import com.example.music.entity.UserFollow;
import com.example.music.mapper.UserFollowMapper;
import com.example.music.mapper.UserMapper;
import com.example.music.service.FollowService;
import com.example.music.vo.CommentUserVO;
import com.example.music.vo.FollowRequestVO;
import com.example.music.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 好友关系服务实现
 * <p>
 * 设计要点：
 * 1. 关系行单向存储（requester → target），"好友" = status 已互关；
 * 2. 发请求时的智能合并：若对方此前已向我发过请求，直接接受合并为好友；
 * 3. UNIQUE(requester_id, target_id) 兜底并发重复请求。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final UserFollowMapper userFollowMapper;
    private final UserMapper userMapper;

    @Override
    public String requestFollow(Long targetId, Long userId) {
        if (targetId.equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能关注自己");
        }
        if (userMapper.selectById(targetId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 我已发出的请求：好友则幂等返回，待处理则幂等返回
        UserFollow mine = findOne(userId, targetId);
        if (mine != null) {
            return mine.getStatus() == UserFollow.STATUS_ACCEPTED ? "friends" : "pending_by_me";
        }
        // 对方曾向我发过请求：直接接受合并为好友（省去双方各发一次）
        UserFollow reverse = findOne(targetId, userId);
        if (reverse != null) {
            if (reverse.getStatus() == UserFollow.STATUS_ACCEPTED) {
                return "friends";
            }
            reverse.setStatus(UserFollow.STATUS_ACCEPTED);
            userFollowMapper.updateById(reverse);
            log.info("好友请求智能合并：user={} 与 {} 已互为好友", userId, targetId);
            return "friends";
        }
        UserFollow follow = new UserFollow();
        follow.setRequesterId(userId);
        follow.setTargetId(targetId);
        follow.setStatus(UserFollow.STATUS_PENDING);
        userFollowMapper.insert(follow);
        log.info("发出好友请求：requester={}, target={}", userId, targetId);
        return "pending_by_me";
    }

    @Override
    public void acceptFollow(Long requesterId, Long userId) {
        UserFollow follow = findOne(requesterId, userId);
        if (follow == null || follow.getStatus() != UserFollow.STATUS_PENDING) {
            throw new BusinessException(ResultCode.NOT_FOUND, "没有待处理的好友请求");
        }
        follow.setStatus(UserFollow.STATUS_ACCEPTED);
        userFollowMapper.updateById(follow);
        log.info("同意好友请求：requester={}, target={}", requesterId, userId);
    }

    @Override
    public void removeFollow(Long targetId, Long userId) {
        // 两个方向都可能：我发出的（撤回）/ 对方发来的（拒绝）/ 已互关（解除）
        UserFollow mine = findOne(userId, targetId);
        if (mine != null) {
            userFollowMapper.deleteById(mine.getId());
            log.info("移除好友关系：{} 与 {}（我发起方向）", userId, targetId);
            return;
        }
        UserFollow reverse = findOne(targetId, userId);
        if (reverse != null) {
            userFollowMapper.deleteById(reverse.getId());
            log.info("移除好友关系：{} 与 {}（对方发起方向）", userId, targetId);
        }
    }

    @Override
    public String getFollowStatus(Long targetId, Long userId) {
        UserFollow mine = findOne(userId, targetId);
        if (mine != null) {
            return mine.getStatus() == UserFollow.STATUS_ACCEPTED ? "friends" : "pending_by_me";
        }
        UserFollow reverse = findOne(targetId, userId);
        if (reverse != null) {
            return reverse.getStatus() == UserFollow.STATUS_ACCEPTED ? "friends" : "pending_to_me";
        }
        return "none";
    }

    @Override
    public List<FollowRequestVO> getPendingRequests(Long userId) {
        List<UserFollow> rows = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getTargetId, userId)
                .eq(UserFollow::getStatus, UserFollow.STATUS_PENDING)
                .orderByDesc(UserFollow::getCreateTime));
        if (CollectionUtils.isEmpty(rows)) {
            return Collections.emptyList();
        }
        // 批量取请求者信息，避免 N+1
        List<Long> requesterIds = rows.stream().map(UserFollow::getRequesterId).collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(requesterIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<FollowRequestVO> result = new ArrayList<>();
        for (UserFollow row : rows) {
            FollowRequestVO vo = new FollowRequestVO();
            vo.setId(row.getId());
            vo.setCreateTime(row.getCreateTime());
            User requester = userMap.get(row.getRequesterId());
            if (requester != null) {
                vo.setUser(CommentUserVO.of(requester.getId(),
                        requester.getNickname() != null ? requester.getNickname() : requester.getUsername()));
            } else {
                vo.setUser(CommentUserVO.of(row.getRequesterId(), ""));
            }
            result.add(vo);
            // 按创建时间排序已由 orderByDesc 保证，无需再排
        }
        // 兜底排序（防御 orderBy 未生效的极端情况）
        result.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));
        return result;
    }

    /**
     * 精确查找单向关系行（UNIQUE 约束保证至多一条）
     */
    @Override
    public List<UserVO> getFriends(Long userId) {
        // 好友 = 单向关系行（status=已互关）出现在任一方向
        List<UserFollow> rows = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getStatus, UserFollow.STATUS_ACCEPTED)
                .and(w -> w.eq(UserFollow::getRequesterId, userId)
                        .or()
                        .eq(UserFollow::getTargetId, userId)));
        if (CollectionUtils.isEmpty(rows)) {
            return Collections.emptyList();
        }
        List<Long> friendIds = new ArrayList<>();
        for (UserFollow row : rows) {
            Long friendId = userId.equals(row.getRequesterId()) ? row.getTargetId() : row.getRequesterId();
            if (friendId != null && !friendId.equals(userId) && !friendIds.contains(friendId)) {
                friendIds.add(friendId);
            }
        }
        if (friendIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(friendIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        List<UserVO> result = new ArrayList<>();
        for (Long id : friendIds) {
            User friend = userMap.get(id);
            if (friend != null) {
                result.add(UserVO.of(friend));
            }
        }
        return result;
    }

    private UserFollow findOne(Long requesterId, Long targetId) {
        return userFollowMapper.selectOne(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getRequesterId, requesterId)
                .eq(UserFollow::getTargetId, targetId)
                .last("LIMIT 1"));
    }
}
