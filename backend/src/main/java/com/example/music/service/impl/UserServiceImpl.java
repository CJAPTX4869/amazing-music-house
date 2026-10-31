package com.example.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.ResultCode;
import com.example.music.constant.RoleConstants;
import com.example.music.dto.ChangePasswordDTO;
import com.example.music.dto.ForgotPasswordDTO;
import com.example.music.dto.LoginDTO;
import com.example.music.dto.ProfileUpdateDTO;
import com.example.music.dto.RegisterDTO;
import com.example.music.entity.User;
import com.example.music.entity.UserFollow;
import com.example.music.mapper.UserFollowMapper;
import com.example.music.mapper.UserMapper;
import com.example.music.security.JwtUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.example.music.service.FileStorageService;
import com.example.music.service.UserService;
import com.example.music.vo.LoginVO;
import com.example.music.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;

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
    /** 登录失败记录与锁定状态存 Redis（10 分钟窗口），重启不丢、多实例共享 */
    private final StringRedisTemplate redis;

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    /** 忘记密码的部署恢复码（RECOVERY_CODE，放 .env.local）；为空时找回功能整体禁用 */
    @Value("${app.recovery-code:}")
    private String recoveryCode;

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
    public boolean isAdminAccount(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username).last("LIMIT 1"));
        return user != null && Integer.valueOf(1).equals(user.getRole());
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
        redis.delete("login:fail:" + key);

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
        String lockKey = "login:lock:" + key;
        Boolean locked = redis.hasKey(lockKey);
        if (Boolean.TRUE.equals(locked)) {
            Long ttl = redis.getExpire(lockKey);
            long minutes = (ttl == null || ttl < 0 ? LOCK_DURATION_MS / 60000 : ttl / 60 + 1);
            log.warn("登录尝试过于频繁，已临时锁定：username={}, 剩余 {} 分钟", key, minutes);
            throw new BusinessException(ResultCode.LOGIN_FAILED,
                    "密码错误次数过多，请 " + minutes + " 分钟后再试");
        }
    }

    /**
     * 记录一次失败，达到阈值则锁定
     */
    private void recordFailure(String key) {
        String failKey = "login:fail:" + key;
        Long count = redis.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            redis.expire(failKey, Duration.ofMillis(LOCK_DURATION_MS));
        }
        if (count != null && count >= MAX_FAILURES) {
            redis.opsForValue().set("login:lock:" + key, "1", Duration.ofMillis(LOCK_DURATION_MS));
            redis.delete(failKey);
            log.warn("账号因多次登录失败被锁定 10 分钟：username={}", key);
        }
    }


    /**
     * 登录失败记录载体
     */

    @Override
    public void forgotPassword(ForgotPasswordDTO dto) {
        if (!StringUtils.hasText(recoveryCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "服务端未配置恢复码，请联系管理员重置密码");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername().trim()));
        // 用户名与恢复码统一报错，避免被用来枚举有效用户名
        if (user == null || !recoveryCode.equals(dto.getRecoveryCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名或恢复码错误");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
        log.info("用户通过恢复码重置密码成功：userId={}", user.getId());
    }

    @Override
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null || !passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "旧密码错误");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
        log.info("用户修改密码成功：userId={}", userId);
    }
}
