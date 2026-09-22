package com.example.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.ResultCode;
import com.example.music.constant.RoleConstants;
import com.example.music.dto.LoginDTO;
import com.example.music.dto.ProfileUpdateDTO;
import com.example.music.dto.RegisterDTO;
import com.example.music.entity.User;
import com.example.music.entity.UserFollow;
import com.example.music.mapper.UserFollowMapper;
import com.example.music.mapper.UserMapper;
import com.example.music.security.JwtUtil;
import com.example.music.service.FileStorageService;
import com.example.music.service.UserService;
import com.example.music.vo.LoginVO;
import com.example.music.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户与认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 状态：启用 */
    private static final int STATUS_ENABLED = 1;

    /* ---------------- 登录限流配置 ---------------- */
    /** 允许的最大连续失败次数 */
    private static final int MAX_FAILURES = 5;
    /** 触发限流后的锁定时长（毫秒），10 分钟 */
    private static final long LOCK_DURATION_MS = 10 * 60 * 1000L;
    /** 记录数量上限，超过则触发一次清理，防止内存无限增长 */
    private static final int MAX_ATTEMPT_RECORDS = 1000;

    /**
     * 登录失败记录：key = 用户名
     * <p>
     * 说明：本地内存实现，适用于单机部署；多实例部署时应改用 Redis 等集中式存储。
     */
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    @Override
    public UserVO register(RegisterDTO dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        // 密码必须 BCrypt 加密存储
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname().trim() : dto.getUsername());
        user.setRole(RoleConstants.USER);
        user.setStatus(STATUS_ENABLED);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册场景下由数据库唯一约束兜底
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        log.info("用户注册成功：username={}, id={}", user.getUsername(), user.getId());
        return UserVO.of(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        String key = dto.getUsername();

        // 1. 限流检查：连续失败过多时临时锁定，防止暴力破解
        checkLoginAttempt(key);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, key)
                .last("LIMIT 1"));

        // 安全要求：不区分"用户不存在"与"密码错误"，统一提示，避免用户名枚举
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.info("登录失败：username={}", key);
            recordFailure(key);
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (!Integer.valueOf(STATUS_ENABLED).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        // 2. 登录成功：清除失败记录
        loginAttempts.remove(key);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        log.info("登录成功：username={}, id={}, role={}", user.getUsername(), user.getId(), user.getRole());
        return new LoginVO(token, UserVO.of(user));
    }

    @Override
    public UserVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或已注销");
        }
        return UserVO.of(user);
    }

    @Override
    public UserVO getPublicProfile(Long targetId, Long viewerId) {
        User user = userMapper.selectById(targetId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        UserVO vo = UserVO.of(user);
        // 好友数量统计（好友请求制，双向互关）：
        // 关注数 = 该用户发起且已互关的数量；粉丝数 = 指向该用户且已互关的数量
        Long followCount = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getRequesterId, targetId)
                .eq(UserFollow::getStatus, UserFollow.STATUS_ACCEPTED));
        Long fanCount = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getTargetId, targetId)
                .eq(UserFollow::getStatus, UserFollow.STATUS_ACCEPTED));
        vo.setFollowCount(followCount.intValue());
        vo.setFanCount(fanCount.intValue());
        return vo;
    }

    @Override
    public UserVO updateProfile(Long userId, ProfileUpdateDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或已注销");
        }

        User update = new User();
        update.setId(userId);
        boolean changed = false;
        // null 表示"不修改"（前端只提交用户实际改动的字段）
        if (StringUtils.hasText(dto.getNickname())) {
            update.setNickname(dto.getNickname().trim());
            changed = true;
        }
        if (dto.getSignature() != null) {
            // 空串表示清空签名
            update.setSignature(dto.getSignature().trim());
            changed = true;
        }
        if (dto.getRegion() != null) {
            // 空串表示清空地区
            update.setRegion(dto.getRegion().trim());
            changed = true;
        }
        if (!changed) {
            return UserVO.of(user);
        }

        userMapper.updateById(update);
        log.info("用户资料已更新：userId={}, nicknameChanged={}, signatureChanged={}",
                userId, update.getNickname() != null, update.getSignature() != null);
        return UserVO.of(userMapper.selectById(userId));
    }

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或已注销");
        }

        // 1. 新文件先落盘（格式 / 体积校验不通过会直接抛业务异常）
        FileStorageService.StoredFile stored = fileStorageService.storeAvatar(file);
        String oldAvatar = user.getAvatar();

        // 2. 更新数据库；失败则删除刚落盘的新文件（补偿回滚）
        try {
            User update = new User();
            update.setId(userId);
            update.setAvatar(stored.getRelativePath());
            userMapper.updateById(update);
        } catch (Exception e) {
            fileStorageService.delete(stored.getRelativePath());
            log.error("头像入库失败，已回滚新文件：userId={}", userId, e);
            throw new BusinessException(ResultCode.FILE_WRITE_FAILED, "头像保存失败，请重试");
        }

        // 3. 删除旧头像（失败仅记录日志，不影响本次结果）
        if (StringUtils.hasText(oldAvatar) && !oldAvatar.equals(stored.getRelativePath())) {
            fileStorageService.delete(oldAvatar);
        }

        log.info("用户头像更新成功：userId={}, path={}", userId, stored.getRelativePath());
        return stored.getRelativePath();
    }

    @Override
    public Resource getAvatarResource(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getAvatar())) {
            return null;
        }
        try {
            return fileStorageService.loadAsResource(user.getAvatar());
        } catch (BusinessException e) {
            // 记录在库但文件丢失：返回 null 让调用方响应 404，而不是抛 500
            log.warn("头像文件缺失：userId={}, path={}", userId, user.getAvatar());
            return null;
        }
    }

    /* ==================== 登录限流 ==================== */

    /**
     * 检查是否处于锁定状态
     */
    private void checkLoginAttempt(String key) {
        LoginAttempt attempt = loginAttempts.get(key);
        if (attempt == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (attempt.lockedUntil > now) {
            long minutes = (attempt.lockedUntil - now) / 60000 + 1;
            log.warn("登录尝试过于频繁，已临时锁定：username={}, 剩余 {} 分钟", key, minutes);
            throw new BusinessException(ResultCode.LOGIN_FAILED,
                    "密码错误次数过多，请 " + minutes + " 分钟后再试");
        }
        // 仅当"曾被锁定且锁定已过期"时才清除记录、重新开始计数。
        // 注意：未锁定（lockedUntil == 0）时必须保留记录，否则失败次数会被清空、
        // 永远累计不到阈值，导致限流形同虚设。
        if (attempt.lockedUntil > 0) {
            loginAttempts.remove(key);
        }
    }

    /**
     * 记录一次失败，达到阈值则锁定
     */
    private void recordFailure(String key) {
        LoginAttempt attempt = loginAttempts.computeIfAbsent(key, k -> new LoginAttempt());
        long now = System.currentTimeMillis();

        // 若上次失败已超过锁定时长，视为新一轮计数
        if (now - attempt.lastFailureAt > LOCK_DURATION_MS) {
            attempt.count = 0;
        }
        attempt.count++;
        attempt.lastFailureAt = now;

        if (attempt.count >= MAX_FAILURES) {
            attempt.lockedUntil = now + LOCK_DURATION_MS;
            log.warn("账号因多次登录失败被锁定 10 分钟：username={}", key);
        }

        // 记录过多时做一次清理，避免内存无限增长
        if (loginAttempts.size() > MAX_ATTEMPT_RECORDS) {
            cleanExpiredAttempts();
        }
    }

    /**
     * 定时清理已过期的失败记录（每 30 分钟执行一次）
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000L, initialDelay = 30 * 60 * 1000L)
    public void cleanExpiredAttempts() {
        long now = System.currentTimeMillis();
        int before = loginAttempts.size();
        // 清理"未锁定且最近一次失败已超过锁定时长"的记录
        loginAttempts.entrySet().removeIf(entry -> {
            LoginAttempt attempt = entry.getValue();
            return attempt.lockedUntil <= now && (now - attempt.lastFailureAt) > LOCK_DURATION_MS;
        });
        int removed = before - loginAttempts.size();
        if (removed > 0) {
            log.debug("清理过期登录失败记录 {} 条，剩余 {}", removed, loginAttempts.size());
        }
    }

    /**
     * 登录失败记录载体
     */
    private static class LoginAttempt {
        /** 连续失败次数 */
        private int count;
        /** 最近一次失败时间 */
        private long lastFailureAt;
        /** 锁定截止时间戳（0 表示未锁定） */
        private long lockedUntil;
    }
}
