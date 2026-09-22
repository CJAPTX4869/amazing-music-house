package com.example.music.service;

import com.example.music.dto.LoginDTO;
import com.example.music.dto.ProfileUpdateDTO;
import com.example.music.dto.RegisterDTO;
import com.example.music.vo.LoginVO;
import com.example.music.vo.UserVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户与认证服务
 */
public interface UserService {

    /**
     * 注册（默认角色为普通用户）
     */
    UserVO register(RegisterDTO dto);

    /**
     * 登录，成功返回 JWT 与用户信息
     */
    LoginVO login(LoginDTO dto);

    /**
     * 获取当前登录用户信息
     */
    UserVO getProfile(Long userId);

    /**
     * 公开用户主页资料（任意用户可访问；含好友数量统计）
     *
     * @param targetId 目标用户 ID
     * @param viewerId 当前登录用户（匿名时为 null，供后续隐私/关系判定）
     */
    UserVO getPublicProfile(Long targetId, Long viewerId);

    /**
     * 修改个人资料（昵称 / 个性签名）
     * <p>
     * DTO 中为 null 的字段表示"不修改"；修改后返回最新的用户信息。
     *
     * @param userId 用户 ID
     * @param dto    待修改字段
     */
    UserVO updateProfile(Long userId, ProfileUpdateDTO dto);

    /**
     * 上传 / 替换用户头像
     * <p>
     * 新文件先落盘、再更新数据库；数据库更新失败时删除新文件（补偿回滚），
     * 与歌曲封面的处理策略保持一致。
     *
     * @param userId 用户 ID
     * @param file   头像图片（jpg / jpeg / png / webp / gif，≤ 2MB）
     * @return 头像相对路径（形如 avatar/2026/09/16/xxx.jpg）
     */
    String uploadAvatar(Long userId, MultipartFile file);

    /**
     * 读取用户头像资源
     *
     * @return 头像资源；用户未设置头像或文件丢失时返回 null（由调用方返回 404）
     */
    Resource getAvatarResource(Long userId);
}
