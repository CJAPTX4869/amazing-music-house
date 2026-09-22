package com.example.music.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全相关 Bean
 * <p>
 * 仅引入 spring-security-crypto 用于密码加密，不引入完整的 Spring Security 认证链。
 */
@Configuration
public class SecurityBeanConfig {

    /**
     * BCrypt 密码编码器
     * <p>
     * 支持 $2a$ / $2b$ / $2y$ 前缀，与数据库初始化的 admin 密文兼容。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
