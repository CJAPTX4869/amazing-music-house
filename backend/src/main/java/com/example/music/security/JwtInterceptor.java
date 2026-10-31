package com.example.music.security;

import com.example.music.common.exception.BusinessException;
import com.example.music.common.response.ResultCode;
import com.example.music.entity.User;
import com.example.music.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JWT 鉴权拦截器
 * <p>
 * 职责：
 * 1. 尝试解析请求头中的 Token 并填充 {@link UserContext}（无论接口是否需要登录）；
 * 2. 对标注了 {@link RequireAdmin} / {@link RequireLogin} 的接口做权限校验；
 * 3. 请求结束时清理 ThreadLocal，避免线程复用导致上下文串号。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final OnlineClientTracker onlineTracker;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 必须优先清理：本方法可能在鉴权失败时抛异常提前退出，
        // 而 Spring 仅在 preHandle 正常返回后才推进拦截器下标，
        // 抛异常时 afterCompletion 不会被回调（ThreadLocal 残留 → 线程复用后身份串号），
        // 故在入口先清一次兜底
        UserContext.clear();

        // CORS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 1. 解析 Token（有则填充上下文，无则保持匿名）
        resolveToken(request);

        // 2. 注解驱动的权限校验
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Class<?> beanType = handlerMethod.getBeanType();

            boolean requireAdmin = handlerMethod.hasMethodAnnotation(RequireAdmin.class)
                    || beanType.isAnnotationPresent(RequireAdmin.class);
            // 方法上标注 @PublicApi 时免除类级登录要求（图片读取等匿名场景）
            boolean requireLogin = (handlerMethod.hasMethodAnnotation(RequireLogin.class)
                    || beanType.isAnnotationPresent(RequireLogin.class))
                    && !handlerMethod.hasMethodAnnotation(PublicApi.class);

            if (requireAdmin) {
                // 管理接口只允许本机回环访问。
                // 8080 要为局域网用户端开放，而管理接口与用户接口同端口，
                // 所以这里按 TCP 直连来源地址拦一道：远程设备即使拿到管理员口令也调不到。
                // 用 getRemoteAddr() 而非 X-Forwarded-For —— 后者可随意伪造。
                if (!isLoopback(request)) {
                    log.warn("拒绝非本机访问管理接口：{} {} 来源={}",
                            request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
                    throw new BusinessException(ResultCode.FORBIDDEN, "管理接口仅限本机访问");
                }
                if (!UserContext.isLogin()) {
                    throw new BusinessException(ResultCode.UNAUTHORIZED);
                }
                if (!UserContext.isAdmin()) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "需要管理员权限");
                }
            } else if (requireLogin && !UserContext.isLogin()) {
                throw new BusinessException(ResultCode.UNAUTHORIZED);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清理：Tomcat 线程池会复用线程
        UserContext.clear();
    }

    /**
     * 是否本机回环来源。IPv6 回环在不同 JDK 下可能写作 ::1 或展开形式，两种都认。
     */
    private static boolean isLoopback(HttpServletRequest request) {
        String addr = request.getRemoteAddr();
        return "127.0.0.1".equals(addr)
                || "::1".equals(addr)
                || "0:0:0:0:0:0:0:1".equals(addr);
    }

    /**
     * 解析并填充用户上下文；Token 无效时不抛异常（由具体接口的注解决定是否 401）
     */
    private void resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_AUTHORIZATION);
        if (header == null || !header.startsWith(TOKEN_PREFIX)) {
            return;
        }
        String token = header.substring(TOKEN_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return;
        }
        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get("username", String.class);
            Integer role = claims.get("role", Integer.class);
            // 安全修复：身份以数据库为准。
            // 原实现完全信任 token 里的 claim，导致：① 账号被禁用/删除后旧 token 仍能用（最长 24h）；
            // ② 只要密钥可猜就能伪造 role=1 提权。这里回查一次用户并校验启用状态。
            User dbUser = userMapper.selectById(userId);
            if (dbUser == null || dbUser.getStatus() == null || dbUser.getStatus() != 1) {
                log.debug("Token 对应用户不存在或已禁用，按匿名处理：userId={}", userId);
                return;
            }
            UserContext.set(new UserContext.LoginUser(
                    dbUser.getId(),
                    dbUser.getUsername(),
                    dbUser.getRole()
            ));
            // 在线客户端心跳：90 秒无活动自动判离线（运维后台展示用）
            onlineTracker.touch(dbUser.getId(), dbUser.getUsername(),
                    request.getHeader("X-Client-Platform"),
                    request.getHeader("User-Agent"),
                    request.getRemoteAddr(),
                    request.getRemotePort());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token 解析失败（已忽略）：{}", e.getMessage());
        }
    }
}
