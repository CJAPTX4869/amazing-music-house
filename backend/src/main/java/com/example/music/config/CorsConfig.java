package com.example.music.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域配置
 * <p>
 * 允许的来源通过 {@code app.cors.allowed-origins} 配置：
 * 开发环境默认 "*"；生产环境应设置为具体域名（多个用逗号分隔）。
 */
@Configuration
public class CorsConfig {

    /** 允许的来源列表，支持 pattern（如 https://*.example.com） */
    @Value("${app.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许的来源（pattern 支持通配符，且可与 allowCredentials 共存）
        for (String origin : allowedOrigins) {
            if (origin != null && !origin.trim().isEmpty()) {
                config.addAllowedOriginPattern(origin.trim());
            }
        }
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        // 关键：暴露响应头，否则前端无法读取下载文件名
        config.addExposedHeader("Content-Disposition");
        config.addExposedHeader("Content-Length");

        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
