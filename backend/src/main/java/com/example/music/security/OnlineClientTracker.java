package com.example.music.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线客户端心跳：每个已认证请求摸一次 Redis，90 秒 TTL 自动判离线。
 * <p>
 * key  = online:{平台}:{用户id}
 * val  = username|ip|最后活跃毫秒
 * <p>
 * 平台归一化：web(网页/管理端) / desktop(桌面·Electron·Qt) / mobile(安卓·iOS)。
 * 优先取前端注入的 X-Client-Platform 头，缺失时按 User-Agent 兜底识别。
 */
@Component
@RequiredArgsConstructor
public class OnlineClientTracker {

    private static final String KEY_PREFIX = "online:";
    private static final Duration ONLINE_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    /** 每个认证请求调用一次（JwtInterceptor 认证成功后） */
    public void touch(Long userId, String username, String platformHeader, String userAgent, String ip, int port) {
        String platform = normalize(platformHeader, userAgent);
        // 回环地址统一为 127.0.0.1（IPv6 的 ::1 / 0:0:...:1 对用户无意义）
        if (ip != null && (ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1"))) {
            ip = "127.0.0.1";
        }
        String value = username + "|" + (ip == null ? "-" : ip) + "|" + port + "|" + System.currentTimeMillis();
        redis.opsForValue().set(KEY_PREFIX + platform + ":" + userId, value, ONLINE_TTL);
    }

    /**
     * 列出全部在线条目：{platform, userId, username, ip, lastSeenMs}
     */
    public List<Map<String, Object>> listOnline() {
        List<Map<String, Object>> out = new ArrayList<>();
        var keys = redis.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return out;
        }
        for (String key : keys) {
            try {
                String value = redis.opsForValue().get(key);
                if (value == null) continue;
                String[] rest = key.substring(KEY_PREFIX.length()).split(":", 2);
                if (rest.length != 2) continue;
                String[] parts = value.split("\\|", 4);
                if (parts.length < 4) continue;   // 重启后残留的旧格式（无端口）条目：跳过
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("platform", rest[0]);
                m.put("userId", Long.valueOf(rest[1]));
                m.put("username", parts[0]);
                m.put("ip", parts[1]);
                m.put("port", Integer.valueOf(parts[2]));
                m.put("lastSeenMs", Long.valueOf(parts[3]));
                out.add(m);
            } catch (Exception ignore) {
                // 任何解析异常（旧格式/脏数据）跳过该条，不影响整页
            }
        }
        return out;
    }

    /** 登出时立即下线：删除该用户全部平台的心跳键 */
    public void removeUser(Long userId) {
        var keys = redis.keys(KEY_PREFIX + "*:" + userId);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    /** 平台归一化：请求头优先，UA 兜底（Qt/Electron=桌面，Android/iPhone=移动，其余=网页） */
    public static String normalize(String platformHeader, String userAgent) {
        if (platformHeader != null && !platformHeader.isBlank()) {
            String h = platformHeader.trim().toLowerCase();
            if (h.equals("admin")) return "admin";
            if (h.equals("desktop") || h.equals("qt") || h.equals("electron")) return "desktop";
            if (h.equals("mobile") || h.equals("android") || h.equals("ios")) return "mobile";
            return "web";
        }
        String ua = userAgent == null ? "" : userAgent;
        String uaLower = ua.toLowerCase();
        if (uaLower.contains("electron") || uaLower.contains("qt")) return "desktop";
        if (uaLower.contains("android") || uaLower.contains("iphone") || uaLower.contains("ipad")
                || uaLower.contains("mobile") || uaLower.contains("okhttp")) return "mobile";
        return "web";
    }
}
