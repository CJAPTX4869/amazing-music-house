package com.example.music.security;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 业务层管理员放权的统一口径。
 *
 * 拦截器对 @RequireAdmin 的防线是「管理员角色 + 请求来自本机回环」，
 * 历史上部分 Service 直接信任 UserContext.isAdmin()，绕过了回环限制，
 * 导致远程拿到管理员 token 也能删除任意评论 / 读取私密歌单。
 * 业务层一切「管理员放行」判断必须改走 isLocalAdmin()，与拦截器保持一致。
 */
public final class AdminAccess {

    private AdminAccess() {
    }

    /** 请求是否来自本机回环（与 JwtInterceptor 的判定保持一致，不用可伪造的 X-Forwarded-For） */
    public static boolean isLocalRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return false;
        }
        String addr = attrs.getRequest().getRemoteAddr();
        return "127.0.0.1".equals(addr) || "0:0:0:0:0:0:0:1".equals(addr);
    }

    /** 业务层管理员放行：必须同时满足管理员角色 + 本机请求 */
    public static boolean isLocalAdmin() {
        return UserContext.isAdmin() && isLocalRequest();
    }
}
