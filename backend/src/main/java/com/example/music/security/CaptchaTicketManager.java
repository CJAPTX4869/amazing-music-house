package com.example.music.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 抓娃娃人机验证票据（服务端凭证，替代"纯前端验证码"）。
 *
 * 流程：challenge 发票 -> 前端通过抓娃娃后调 verify 标记已验证 ->
 * 登录时 consumeForLogin 校验并一次性消费。
 * 票据化后无票/假票/重放全部被拒绝。
 *
 * v2：存储从单机内存迁移到 Redis——10 分钟 TTL 由 Redis 过期自动回收
 * （替代原 @Scheduled 清理），重启服务不丢票，多实例部署天然共享。
 */
@Component
@RequiredArgsConstructor
public class CaptchaTicketManager {

    private static final String KEY_PREFIX = "captcha:ticket:";
    private static final Duration TTL = Duration.ofMinutes(10);

    /**
     * 一次性消费脚本（原子）：值为 '1'（已验证）才删除并放行。
     * 不存在返回 -1（无票/已过期），未验证返回 0（防跳过验证码）。
     */
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) " +
            "if v == false then return -1 end " +
            "if v == '1' then redis.call('DEL', KEYS[1]) return 1 else return 0 end", Long.class);

    private final StringRedisTemplate redis;

    /** 发放新票据 */
    public String issue() {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(KEY_PREFIX + ticket, "0", TTL);
        return ticket;
    }

    /** 标记票据已通过人机验证（票据必须存在且未过期；存在性由 setIfPresent 保证） */
    public boolean markVerified(String ticket) {
        if (ticket == null || ticket.isEmpty()) {
            return false;
        }
        Boolean ok = redis.opsForValue().setIfPresent(KEY_PREFIX + ticket, "1", TTL);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 登录时校验并消费票据：必须已验证、未使用过、未过期。
     * Lua 脚本保证"校验+删除"原子执行，防止并发重放同一张票。
     */
    public boolean consumeForLogin(String ticket) {
        if (ticket == null || ticket.isEmpty()) {
            return false;
        }
        Long result = redis.execute(CONSUME_SCRIPT, Collections.singletonList(KEY_PREFIX + ticket));
        return result != null && result == 1L;
    }
}
