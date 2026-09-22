package com.example.music.vo;

import com.example.music.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户响应对象
 * <p>
 * 注意：绝不可包含 password 字段。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;

    /** 个性签名 */
    private String signature;

    /** 地区（如：吉林省 长春市，个人主页展示） */
    private String region;

    /** 关注数（好友请求制下为发起且已互关数量；公开主页填充，其余场景为 null） */
    private Integer followCount;

    /** 粉丝数（指向我且已互关数量；公开主页填充，其余场景为 null） */
    private Integer fanCount;

    private Integer role;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    public static UserVO of(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setSignature(user.getSignature());
        vo.setRegion(user.getRegion());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
