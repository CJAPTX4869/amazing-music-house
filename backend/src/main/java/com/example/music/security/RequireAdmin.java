package com.example.music.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记接口需要管理员权限（role = 1）
 * <p>
 * 由 {@link JwtInterceptor} 校验：未登录返回 401，非管理员返回 403。
 * 这是权限的真正安全边界，前端路由守卫仅作体验拦截。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAdmin {
}
