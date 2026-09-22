package com.example.music.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;

/**
 * JWT 工具：签发与解析
 * <p>
 * 使用 HS256 签名，密钥从配置注入（生产环境通过环境变量 JWT_SECRET 覆盖）。
 * <p>
 * 安全修复：原来 application.yml 里带一个"可猜的默认密钥"，任何人读过源码即可离线
 * 伪造管理员 token（实测可拿到 /api/admin/statistics）。现在：
 * 未配置 JWT_SECRET 时生成本次启动的随机密钥并打印告警；配置了则强制 ≥32 字节。
 */
@Slf4j
@Component
public class JwtUtil {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final long expiration;

    public JwtUtil(@Value("${app.jwt.secret:}") String secret,
                   @Value("${app.jwt.expiration}") long expiration) {
        String configured = secret == null
                ? ""
                : secret.trim();
        if (configured.isEmpty()) {
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            this.key = Keys.hmacShaKeyFor(random);
            log.warn("未配置 JWT_SECRET：已为本次启动生成随机密钥。"
                    + "重启后所有登录状态会失效；长期使用请在环境变量 JWT_SECRET 配置固定密钥（≥32 字节）。");
        } else {
            byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("JWT_SECRET 长度必须 ≥ 32 字节（当前 " + bytes.length + "）");
            }
            this.key = Keys.hmacShaKeyFor(bytes);
        }
        this.expiration = expiration;
    }

    /**
     * 签发 Token
     *
     * @param userId   用户 ID（放入 subject）
     * @param username 用户名
     * @param role     角色（0 普通 / 1 管理员）
     */
    public String generateToken(Long userId, String username, Integer role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expiration))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 Token
     *
     * @return 载荷
     * @throws JwtException 签名不合法或已过期
     */
    public Claims parseToken(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
