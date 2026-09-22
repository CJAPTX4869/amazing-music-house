package com.example.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.ResultCode;
import com.example.music.dto.UserQueryDTO;
import com.example.music.entity.Song;
import com.example.music.entity.User;
import com.example.music.mapper.SongMapper;
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
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 管理端服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final int STATUS_ENABLED = 1;

    private final UserMapper userMapper;
    private final SongMapper songMapper;
    private final SongService songService;
    private final FileStorageService fileStorageService;

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
