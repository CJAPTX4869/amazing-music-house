package com.example.music.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法为公开接口（免除类级登录要求）
 * <p>
 * 使用场景：控制器类上已标注 {@link RequireLogin}，但个别接口需要匿名可访问——
 * 典型是图片读取接口，浏览器 {@code <img src>} 请求不会携带 Authorization 头，
 * 若强制登录会返回 401 导致破图。
 * <p>
 * 处理逻辑见 {@link JwtInterceptor}：方法上存在本注解时，忽略类级 {@link RequireLogin}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicApi {
}
